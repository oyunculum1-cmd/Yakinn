package com.yakin.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.ParcelFileDescriptor
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlin.concurrent.thread

sealed class Ev {
    data class Found(val id: String, val number: String, val name: String, val mood: Int, val color: Int) : Ev()
    data class Lost(val id: String) : Ev()
    data class Connected(val id: String, val number: String, val name: String, val mood: Int, val color: Int) : Ev()
    data class Gone(val id: String) : Ev()
    data class Text(val id: String, val text: String, val vanish: Boolean) : Ev()
    data class Broadcast(val id: String, val text: String) : Ev()
    data class Nudge(val id: String) : Ev()
    /** kind: 1 = çalıyor, 2 = kabul edildi, 0 = kapatıldı */
    data class Call(val id: String, val kind: Int) : Ev()
}

private data class PeerInfo(val number: String, val name: String, val mood: Int, val color: Int)

/** İnternetsiz: Bluetooth + Wi-Fi Direct üzerinden Nearby Connections. */
class NearbyManager(ctx: Context, private val myNumber: String, myName: String,
                    private val myMood: Int, private val myColor: Int,
                    private val onEvent: (Ev) -> Unit) {
    private val client = Nearby.getConnectionsClient(ctx)
    private val strategy = Strategy.P2P_CLUSTER
    private val service = "com.yakin.app"
    private val me = "$myNumber|${myName.replace("|", "")}|$myMood|$myColor"
    private val info = HashMap<String, PeerInfo>()
    private val requested = HashSet<String>()
    @Volatile private var talking = false
    @Volatile var muted = false

    fun start() {
        client.startAdvertising(me, service, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
        client.startDiscovery(service, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
    }
    fun stop() { client.stopAllEndpoints(); client.stopAdvertising(); client.stopDiscovery() }

    private fun bytes(id: String, s: String) = client.sendPayload(id, Payload.fromBytes(s.toByteArray()))
    fun sendText(id: String, t: String, vanish: Boolean) = bytes(id, (if (vanish) "D:" else "T:") + t)
    fun sendBroadcast(id: String, t: String) = bytes(id, "B:$t")
    fun nudge(id: String) = bytes(id, "N:")
    fun ring(id: String) = bytes(id, "CALL:1")
    fun accept(id: String) = bytes(id, "CALL:2")
    fun hangup(id: String) = bytes(id, "CALL:0")
    fun stopVoice() { talking = false }

    @SuppressLint("MissingPermission")
    fun startVoice(id: String) {
        talking = true
        val pipe = ParcelFileDescriptor.createPipe()
        client.sendPayload(id, Payload.fromStream(pipe[0]))
        thread {
            val rate = 16000
            val buf = ByteArray(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf.size)
            val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            rec.startRecording()
            try {
                while (talking) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) { if (muted) java.util.Arrays.fill(buf, 0, n, 0); out.write(buf, 0, n) }
                }
            } catch (_: Exception) {} finally { rec.stop(); rec.release(); try { out.close() } catch (_: Exception) {} }
        }
    }

    private fun playStream(p: Payload) {
        val input = p.asStream()?.asInputStream() ?: return
        thread {
            val rate = 16000
            val size = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(size).build()
            val buf = ByteArray(size); track.play()
            try { while (true) { val n = input.read(buf); if (n < 0) break; track.write(buf, 0, n) } }
            catch (_: Exception) {} finally { track.stop(); track.release() }
        }
    }

    private fun parse(s: String): PeerInfo {
        val a = s.split("|")
        return PeerInfo(a[0], a.getOrElse(1) { "" }, a.getOrNull(2)?.toIntOrNull() ?: 0, a.getOrNull(3)?.toIntOrNull() ?: -1)
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, i: DiscoveredEndpointInfo) {
            val p = parse(i.endpointName)
            onEvent(Ev.Found(id, p.number, p.name, p.mood, p.color))
            // Çakışmayı önlemek için sadece numarası büyük olan taraf bağlantı ister.
            if (myNumber > p.number && requested.add(id)) client.requestConnection(me, id, lifecycle)
        }
        override fun onEndpointLost(id: String) { requested.remove(id); onEvent(Ev.Lost(id)) }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, i: ConnectionInfo) {
            info[id] = parse(i.endpointName)
            client.acceptConnection(id, payloads)
        }
        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (r.status.isSuccess) info[id]?.let { onEvent(Ev.Connected(id, it.number, it.name, it.mood, it.color)) }
            else requested.remove(id)
        }
        override fun onDisconnected(id: String) { requested.remove(id); onEvent(Ev.Gone(id)) }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) {
            when (p.type) {
                Payload.Type.BYTES -> {
                    val s = String(p.asBytes()!!)
                    when {
                        s.startsWith("T:") -> onEvent(Ev.Text(id, s.substring(2), false))
                        s.startsWith("D:") -> onEvent(Ev.Text(id, s.substring(2), true))
                        s.startsWith("B:") -> onEvent(Ev.Broadcast(id, s.substring(2)))
                        s == "N:" -> onEvent(Ev.Nudge(id))
                        s.startsWith("CALL:") -> onEvent(Ev.Call(id, s.substring(5).toIntOrNull() ?: 0))
                    }
                }
                Payload.Type.STREAM -> playStream(p)
                else -> {}
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }
}
