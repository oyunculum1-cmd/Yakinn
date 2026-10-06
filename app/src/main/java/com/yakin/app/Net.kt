package com.yakin.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** id = karşı tarafın numarası (LAN ya da internet, hangisiyle ulaşılıyorsa). */
sealed class Ev {
    data class Found(val id: String, val number: String, val name: String, val mood: Int, val color: Int) : Ev()
    data class Lost(val id: String) : Ev()
    data class Connected(val id: String, val number: String, val name: String, val mood: Int, val color: Int) : Ev()
    data class Gone(val id: String) : Ev()
    data class Route(val id: String, val route: Int) : Ev()          // 0 yok, 1 aynı ağ, 2 internet
    data class Text(val id: String, val mid: Long, val text: String, val vanish: Boolean) : Ev()
    data class Ack(val id: String, val mid: Long) : Ev()
    data class Read(val id: String, val mids: List<Long>) : Ev()
    data class Typing(val id: String, val on: Boolean) : Ev()
    data class React(val id: String, val mid: Long, val emoji: String) : Ev()
    data class Broadcast(val id: String, val text: String) : Ev()
    data class Nudge(val id: String) : Ev()
    data class Call(val id: String, val kind: Int) : Ev()            // 1 çalıyor, 2 kabul, 0 kapandı
    data class Absent(val number: String) : Ev()
    data class Wan(val state: Int) : Ev()                            // 0 kapalı, 1 bağlanıyor, 2 bağlı
    data class Error(val text: String) : Ev()
    object Taken : Ev()
}

private data class Meta(val name: String, val mood: Int, val color: Int)
private data class Peer(val number: String, val name: String, val mood: Int, val color: Int)

private class Conn(val s: Socket, val out: DataOutputStream) {
    @Synchronized fun send(type: Int, b: ByteArray): Boolean = try {
        out.writeByte(type); out.writeInt(b.size); out.write(b); out.flush(); true
    } catch (_: Exception) { close(); false }
    fun close() { try { s.close() } catch (_: Exception) {} }
}

private class Player {
    private val q = LinkedBlockingQueue<ByteArray>()
    @Volatile private var alive = true
    init {
        val rate = 16000
        val size = maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 3200)
        val track = AudioTrack(AudioManager.STREAM_VOICE_CALL, rate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM)
        thread {
            try {
                track.play()
                while (alive) { val b = q.poll(200, TimeUnit.MILLISECONDS) ?: continue; track.write(b, 0, b.size) }
            } catch (_: Exception) {} finally { try { track.stop() } catch (_: Exception) {}; track.release() }
        }
    }
    fun put(b: ByteArray) { if (q.size > 10) q.clear(); q.offer(b) }
    fun stop() { alive = false }
}

/**
 * Hibrit ağ katmanı:
 *  1) Aynı Wi-Fi / hotspot: UDP yayınıyla numara duyurusu + TCP bağlantısı (internet gerekmez)
 *  2) İnternet: WebSocket sunucusu üzerinden numarayla bulma ve iletme
 * Bluetooth kullanılmaz.
 */
class Net(private val ctx: Context, private val myNumber: String, private val key: String, myName: String,
          private val myMood: Int, private val myColor: Int, private val serverUrl: String,
          private val onEvent: (Ev) -> Unit) {
    private val name = myName.replace("|", "")
    private val port = 47474
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val lan = ConcurrentHashMap<String, Conn>()
    private val wan = ConcurrentHashMap<String, Meta>()
    private val meta = ConcurrentHashMap<String, Meta>()
    private val route = ConcurrentHashMap<String, Int>()
    private val seenAt = ConcurrentHashMap<String, Long>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val watching = ConcurrentHashMap.newKeySet<String>()
    private val lock = Any()
    @Volatile private var running = false
    @Volatile private var tcpPort = 0
    @Volatile private var ws: WebSocket? = null
    @Volatile private var talking = false
    @Volatile var muted = false
    private var player: Player? = null
    private var mc: WifiManager.MulticastLock? = null
    private var tcp: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()

    private fun emit(e: Ev) { main.post { if (running) onEvent(e) } }

    fun start() {
        running = true
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            mc = wm?.createMulticastLock("yakin")?.apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
        thread { tcpLoop() }
        thread { udpListen() }
        thread { beacon() }
        thread { housekeeping() }
        connectWs()
    }

    fun stop() {
        running = false
        stopVoice()
        try { tcp?.close() } catch (_: Exception) {}
        try { udp?.close() } catch (_: Exception) {}
        lan.values.forEach { it.close() }
        lan.clear()
        ws?.close(1000, "bye"); ws = null
        try { mc?.release() } catch (_: Exception) {}
    }

    fun watch(numbers: Collection<String>) { watching.clear(); watching.addAll(numbers); sendWatch() }
    fun find(number: String) {
        watching.add(number); sendWatch()
        ws?.send(JSONObject().put("t", "find").put("number", number).toString())
    }
    private fun sendWatch() {
        ws?.send(JSONObject().put("t", "watch").put("numbers", JSONArray(watching.toList())).toString())
    }

    // ---- gönderme ----
    private fun sendFrame(to: String, s: String) {
        io.execute {
            val c = lan[to]
            if (c != null && c.send(0, s.toByteArray(Charsets.UTF_8))) return@execute
            val w = ws
            if (w != null && wan.containsKey(to)) w.send(JSONObject().put("t", "msg").put("to", to).put("body", s).toString())
        }
    }
    fun sendText(id: String, mid: Long, t: String, vanish: Boolean) = sendFrame(id, (if (vanish) "D:" else "T:") + mid + "|" + t)
    fun sendAck(id: String, mid: Long) = sendFrame(id, "A:$mid")
    fun sendRead(id: String, mids: List<Long>) = sendFrame(id, "R:" + mids.joinToString(","))
    fun sendTyping(id: String, on: Boolean) = sendFrame(id, if (on) "Y:1" else "Y:0")
    fun sendReact(id: String, mid: Long, emoji: String) = sendFrame(id, "X:$mid|$emoji")
    fun sendBroadcast(id: String, t: String) = sendFrame(id, "B:$t")
    fun nudge(id: String) = sendFrame(id, "N:")
    fun ring(id: String) = sendFrame(id, "CALL:1")
    fun accept(id: String) = sendFrame(id, "CALL:2")
    fun hangup(id: String) = sendFrame(id, "CALL:0")

    private fun onFrame(from: String, s: String) {
        when {
            s.startsWith("T:") || s.startsWith("D:") -> {
                val body = s.substring(2); val bar = body.indexOf('|')
                if (bar > 0) emit(Ev.Text(from, body.substring(0, bar).toLongOrNull() ?: 0L, body.substring(bar + 1), s[0] == 'D'))
            }
            s.startsWith("A:") -> s.substring(2).toLongOrNull()?.let { emit(Ev.Ack(from, it)) }
            s.startsWith("R:") -> emit(Ev.Read(from, s.substring(2).split(",").mapNotNull { it.toLongOrNull() }))
            s.startsWith("Y:") -> emit(Ev.Typing(from, s == "Y:1"))
            s.startsWith("X:") -> {
                val body = s.substring(2); val bar = body.indexOf('|')
                if (bar > 0) emit(Ev.React(from, body.substring(0, bar).toLongOrNull() ?: 0L, body.substring(bar + 1)))
            }
            s.startsWith("B:") -> emit(Ev.Broadcast(from, s.substring(2)))
            s == "N:" -> emit(Ev.Nudge(from))
            s.startsWith("CALL:") -> emit(Ev.Call(from, s.substring(5).toIntOrNull() ?: 0))
        }
    }

    // ---- ses ----
    @SuppressLint("MissingPermission")
    fun startVoice(to: String) {
        talking = true
        thread {
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val size = maxOf(min, 1600).coerceAtMost(3200)
            val buf = ByteArray(size)
            var rec: AudioRecord? = null
            try {
                rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, size * 2))
                rec.startRecording()
                while (talking) {
                    val n = rec.read(buf, 0, size)
                    if (n > 0) { if (muted) Arrays.fill(buf, 0, n, 0.toByte()); sendAudio(to, buf.copyOf(n)) }
                }
            } catch (_: Exception) {} finally {
                try { rec?.stop() } catch (_: Exception) {}
                try { rec?.release() } catch (_: Exception) {}
            }
        }
    }
    fun stopVoice() { talking = false; synchronized(lock) { player?.stop(); player = null } }
    private fun audioIn(b: ByteArray) {
        if (!talking) return
        val p = synchronized(lock) { player ?: Player().also { player = it } }
        p.put(b)
    }
    private fun sendAudio(to: String, b: ByteArray) {
        val c = lan[to]
        if (c != null) { c.send(1, b); return }
        val w = ws
        if (w != null && wan.containsKey(to)) {
            val t = to.toByteArray()
            val out = ByteArray(1 + t.size + b.size)
            out[0] = t.size.toByte()
            System.arraycopy(t, 0, out, 1, t.size); System.arraycopy(b, 0, out, 1 + t.size, b.size)
            w.send(ByteString.of(*out))
        }
    }

    // ---- ulaşılabilirlik ----
    private fun refresh(n: String) {
        val r = if (lan.containsKey(n)) 1 else if (wan.containsKey(n)) 2 else 0
        val prev = route.put(n, r) ?: 0
        if (r == prev) return
        if (prev == 0 && r != 0) {
            val m = meta[n]
            if (m != null) emit(Ev.Connected(n, n, m.name, m.mood, m.color))
        }
        if (r == 0 && prev != 0) emit(Ev.Gone(n))
        emit(Ev.Route(n, r))
    }

    private fun parse(s: String): Peer {
        val a = s.split("|")
        return Peer(a[0], a.getOrElse(1) { "" }, a.getOrNull(2)?.toIntOrNull() ?: 0, a.getOrNull(3)?.toIntOrNull() ?: -1)
    }

    // ---- aynı ağ (LAN) ----
    private fun tcpLoop() {
        try {
            val ss = ServerSocket(0); tcp = ss; tcpPort = ss.localPort
            while (running) { val s = ss.accept(); thread { handshake(s) } }
        } catch (_: Exception) {}
    }

    private fun broadcastAddrs(): List<InetAddress> {
        val l = mutableListOf<InetAddress>()
        try {
            l += InetAddress.getByName("255.255.255.255")
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { l += it }
            }
        } catch (_: Exception) {}
        return l.distinct()
    }

    private fun beacon() {
        try {
            val s = DatagramSocket(); s.broadcast = true
            while (running) {
                if (tcpPort > 0) {
                    val data = "YKN1|$myNumber|$name|$myMood|$myColor|$tcpPort".toByteArray()
                    for (a in broadcastAddrs()) try { s.send(DatagramPacket(data, data.size, a, port)) } catch (_: Exception) {}
                }
                Thread.sleep(2000)
            }
            s.close()
        } catch (_: Exception) {}
    }

    private fun udpListen() {
        try {
            val s = DatagramSocket(null as SocketAddress?)
            s.reuseAddress = true; s.bind(InetSocketAddress(port)); s.broadcast = true
            udp = s
            val buf = ByteArray(512)
            while (running) {
                val pk = DatagramPacket(buf, buf.size); s.receive(pk)
                val msg = String(pk.data, 0, pk.length)
                if (!msg.startsWith("YKN1|")) continue
                val a = msg.split("|")
                if (a.size < 6) continue
                val num = a[1]; if (num == myNumber) continue
                val mood = a[3].toIntOrNull() ?: 0; val color = a[4].toIntOrNull() ?: -1
                val tp = a[5].toIntOrNull() ?: continue
                meta[num] = Meta(a[2], mood, color)
                val first = seenAt.put(num, System.currentTimeMillis()) == null
                if (first && !lan.containsKey(num)) emit(Ev.Found(num, num, a[2], mood, color))
                // Çakışmayı önlemek için numarası büyük olan taraf bağlantıyı başlatır.
                if (myNumber > num && !lan.containsKey(num) && connecting.add(num)) {
                    val ip = pk.address
                    thread {
                        try { val sock = Socket(); sock.connect(InetSocketAddress(ip, tp), 4000); handshake(sock) }
                        catch (_: Exception) {} finally { connecting.remove(num) }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun handshake(s: Socket) {
        var conn: Conn? = null; var num: String? = null
        try {
            s.tcpNoDelay = true; s.keepAlive = true; s.soTimeout = 8000
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
            val c = Conn(s, out); conn = c
            c.send(2, "$myNumber|$name|$myMood|$myColor".toByteArray())
            val t0 = inp.readByte().toInt(); val len0 = inp.readInt()
            if (t0 != 2 || len0 !in 1..512) return
            val hb = ByteArray(len0); inp.readFully(hb)
            val p = parse(String(hb))
            if (p.number == myNumber) return
            s.soTimeout = 15000
            num = p.number
            lan.put(p.number, c)?.close()
            meta[p.number] = Meta(p.name, p.mood, p.color)
            seenAt.remove(p.number)
            emit(Ev.Found(p.number, p.number, p.name, p.mood, p.color))
            refresh(p.number)
            while (running) {
                val t = inp.readByte().toInt(); val len = inp.readInt()
                if (len < 0 || len > 1_000_000) break
                val b = ByteArray(len); inp.readFully(b)
                when (t) {
                    0 -> onFrame(p.number, String(b, Charsets.UTF_8))
                    1 -> audioIn(b)
                    else -> {}
                }
            }
        } catch (_: Exception) {
        } finally {
            try { s.close() } catch (_: Exception) {}
            val n = num; val c = conn
            if (n != null && c != null && lan.remove(n, c)) refresh(n)
        }
    }

    private fun housekeeping() {
        while (running) {
            try { Thread.sleep(3000) } catch (_: InterruptedException) { return }
            lan.values.forEach { it.send(3, ByteArray(0)) }
            val now = System.currentTimeMillis()
            for ((n, t) in seenAt) if (now - t > 10_000 && !lan.containsKey(n)) { seenAt.remove(n); emit(Ev.Lost(n)) }
        }
    }

    // ---- internet (WebSocket sunucusu) ----
    private fun connectWs() {
        var u = serverUrl.trim()
        if (u.isEmpty() || !running) { emit(Ev.Wan(0)); return }
        if (!u.contains("://")) u = "wss://$u"
        val req = try { Request.Builder().url(u).build() } catch (_: Exception) {
            emit(Ev.Error("Sunucu adresi geçersiz: $u")); emit(Ev.Wan(0)); return
        }
        emit(Ev.Wan(1))
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("t", "hello").put("number", myNumber).put("key", key)
                    .put("name", name).put("mood", myMood).put("color", myColor).toString())
                sendWatch()
            }
            override fun onMessage(webSocket: WebSocket, text: String) { wsText(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val b = bytes.toByteArray(); if (b.isEmpty()) return
                val n = b[0].toInt() and 0xff
                if (b.size < 1 + n) return
                audioIn(b.copyOfRange(1 + n, b.size))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { wsDown(webSocket) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { wsDown(webSocket) }
        })
    }

    private fun wsDown(w: WebSocket) {
        if (ws !== w) return
        ws = null
        val ns = wan.keys.toList(); wan.clear(); ns.forEach { refresh(it) }
        if (running) {
            emit(Ev.Wan(1))
            main.postDelayed({ if (running && ws == null) connectWs() }, 5000)
        }
    }

    private fun wsText(text: String) {
        val o = try { JSONObject(text) } catch (_: Exception) { return }
        when (o.optString("t")) {
            "ok" -> emit(Ev.Wan(2))
            "taken" -> emit(Ev.Taken)
            "online" -> {
                val n = o.optString("number")
                if (n.isEmpty() || n == myNumber) return
                val m = Meta(o.optString("name"), o.optInt("mood", 0), o.optInt("color", -1))
                wan[n] = m; meta[n] = m
                emit(Ev.Found(n, n, m.name, m.mood, m.color)); refresh(n)
            }
            "offline" -> { val n = o.optString("number"); wan.remove(n); refresh(n) }
            "msg" -> onFrame(o.optString("from"), o.optString("body"))
            "absent" -> emit(Ev.Absent(o.optString("number")))
        }
    }
}
