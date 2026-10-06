package com.yakin.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

val Moods = listOf("🟢 Müsaitim", "🔴 Meşgulüm", "💬 Sohbete açığım", "🌙 Sessizde")
const val SQUARE = "*"          // Meydan: menzildeki herkesle ortak oda
const val VANISH_MS = 30_000L   // kaybolan mesaj süresi

/**
 * st (giden mesaj):  0 = bekliyor (menzil dışı), 1 = gönderildi, 2 = ulaştı, 3 = görüldü
 * st (gelen mesaj):  0 = okunmadı, 2 = okundu (karşıya henüz bildirilmedi), 3 = okundu ve bildirildi
 */
data class Msg(val mine: Boolean, val text: String, val ts: Long, val from: String = "",
               val exp: Long = 0, val sys: Boolean = false, val id: Long = 0,
               val st: Int = 0, val van: Boolean = false, val react: String = "")
data class CallLog(val number: String, val outgoing: Boolean, val ts: Long)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("yakin", Context.MODE_PRIVATE)
    private fun randomNumber() = "%03d %03d %03d".format((100..999).random(), (100..999).random(), (100..999).random())
    var number: String = p.getString("number", null) ?: randomNumber().also { p.edit().putString("number", it).apply() }
        private set
    fun regenNumber(): String { number = randomNumber(); p.edit().putString("number", number).apply(); return number }
    val key: String = p.getString("key", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("key", it).apply() }
    var server: String
        get() = p.getString("server", "") ?: ""
        set(v) { p.edit().putString("server", v.trim()).apply() }
    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) { p.edit().putString("name", v).apply() }
    var mood: Int
        get() = p.getInt("mood", 0)
        set(v) { p.edit().putInt("mood", v).apply() }
    var color: Int
        get() = p.getInt("color", 0)
        set(v) { p.edit().putInt("color", v).apply() }

    fun contacts(): Map<String, String> {
        val o = JSONObject(p.getString("contacts", "{}") ?: "{}")
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
    fun saveContact(num: String, name: String, color: Int) {
        val o = JSONObject(p.getString("contacts", "{}") ?: "{}"); o.put(num, "$name|$color")
        p.edit().putString("contacts", o.toString()).apply()
    }
    fun chatNumbers(): List<String> {
        val a = JSONArray(p.getString("chats", "[]") ?: "[]"); return (0 until a.length()).map { a.getString(it) }
    }
    fun loadChat(num: String): List<Msg> {
        val a = JSONArray(p.getString("chat_$num", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Msg(o.getBoolean("m"), o.getString("t"), o.getLong("s"), o.optString("f", ""), o.optLong("e", 0),
                o.optBoolean("y", false), o.optLong("i", 0), o.optInt("z", 0), o.optBoolean("v", false), o.optString("r", ""))
        }
    }
    fun saveChat(num: String, list: List<Msg>) {
        val a = JSONArray()
        list.takeLast(500).forEach {
            a.put(JSONObject().put("m", it.mine).put("t", it.text).put("s", it.ts).put("f", it.from).put("e", it.exp)
                .put("y", it.sys).put("i", it.id).put("z", it.st).put("v", it.van).put("r", it.react))
        }
        val nums = chatNumbers().toMutableList(); if (num !in nums) nums.add(num)
        p.edit().putString("chat_$num", a.toString()).putString("chats", JSONArray(nums).toString()).apply()
    }
    fun deleteChat(num: String) {
        val nums = chatNumbers().toMutableList(); nums.remove(num)
        p.edit().remove("chat_$num").putString("chats", JSONArray(nums).toString()).apply()
    }
}

class Hub(private val store: Store) {
    var myNumber by mutableStateOf(store.number)
    var myName by mutableStateOf(store.name)
    var myMood by mutableIntStateOf(store.mood)
    var myColor by mutableIntStateOf(store.color)
    val names = mutableStateMapOf<String, String>()
    val requests = mutableStateMapOf<String, ContactRequest>()
    lateinit var stickers: StickerPack
    val colors = mutableStateMapOf<String, Int>()
    val moods = mutableStateMapOf<String, Int>()
    val online = mutableStateMapOf<String, String>()      // numara -> endpointId
    val nearby = mutableStateMapOf<String, String>()      // bulunan ama henüz bağlı olmayan
    val chats = mutableStateMapOf<String, List<Msg>>()
    val unread = mutableStateMapOf<String, Int>()
    val vanish = mutableStateMapOf<String, Boolean>()     // kaybolan mesaj modu (sohbet başına)
    val typingUntil = mutableStateMapOf<String, Long>()   // karşı taraf yazıyor
    val calls = mutableStateListOf<CallLog>()
    var tab by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var server by mutableStateOf(store.server)
    var notice by mutableStateOf<String?>(null)
    private var noticeUntil = 0L
    var wanState by mutableIntStateOf(0)
    val routes = mutableStateMapOf<String, Int>()
    private var pendingOpen: String? = null
    var openChat by mutableStateOf<String?>(null)
    var incomingFrom by mutableStateOf<String?>(null)
    var activeCall by mutableStateOf<String?>(null)
    var callConnected by mutableStateOf(false)
    var muted by mutableStateOf(false)
    private val idNum = HashMap<String, String>()
    private var nm: Net? = null
    private var appCtx: Context? = null
    private var lastId = 0L
    private var lastTypingSent = 0L

    init {
        store.contacts().forEach { (n, v) ->
            val a = v.split("|"); names[n] = a[0]; a.getOrNull(1)?.toIntOrNull()?.let { if (it >= 0) colors[n] = it }
        }
        store.chatNumbers().forEach { chats[it] = store.loadChat(it) }
    }

    fun nameOf(n: String) = names[n]?.takeIf { it.isNotBlank() } ?: n
    fun setName(n: String) { myName = n; store.name = n }

    fun toast(t: String) { notice = t; noticeUntil = System.currentTimeMillis() + 4000 }

    private fun watchList(): List<String> = (names.keys + chats.keys).filter { it != SQUARE && it != myNumber }.distinct()

    fun start(ctx: Context) {
        if (!::stickers.isInitialized) stickers = StickerPack(ctx)
        appCtx = ctx.applicationContext
        if (nm != null || myName.isBlank()) return
        error = null
        nm = Net(ctx.applicationContext, myNumber, store.key, myName, myMood, myColor, server) { handle(it) }
            .also { it.watch(watchList()); it.start() }
    }

    fun setServer(ctx: Context, url: String) { server = url.trim(); store.server = server; retry(ctx) }

    /** Numarayla bul: aynı ağdaysa zaten görünür, değilse sunucudan sorulur. */
    fun acceptContact(number: String) { requests[number]?.let { requests[number] = it.copy(status=1) } }
    fun blockContact(number: String) { requests[number]?.let { requests[number] = it.copy(status=2) } }
    fun find(number: String) {
        if (number == myNumber) { toast("Bu senin numaran"); return }
        if (online.containsKey(number)) { open(number); return }
        pendingOpen = number
        toast(if (wanState == 2) "Aranıyor…" else "Aranıyor… Aynı Wi-Fi'deysen birkaç saniyede bulunur.")
        nm?.find(number)
    }

    fun stop() { nm?.stop(); nm = null }
    fun retry(ctx: Context) { stop(); online.clear(); nearby.clear(); idNum.clear(); routes.clear(); error = null; start(ctx) }

    fun setProfile(ctx: Context, name: String, mood: Int, color: Int) {
        myName = name; myMood = mood; myColor = color
        store.name = name; store.mood = mood; store.color = color
        retry(ctx)
    }

    fun open(n: String) { openChat = n; unread.remove(n); if (n != SQUARE) markRead(n) }

    private fun newId(): Long { val t = System.currentTimeMillis(); lastId = if (t > lastId) t else lastId + 1; return lastId }

    private fun add(n: String, m: Msg) { val l = (chats[n] ?: emptyList()) + m; chats[n] = l; store.saveChat(n, l) }
    private fun replace(n: String, l: List<Msg>) { chats[n] = l; store.saveChat(n, l) }

    fun send(n: String, text: String) {
        val now = System.currentTimeMillis()
        if (n == SQUARE) {
            online.values.forEach { nm?.sendBroadcast(it, text) }
            add(SQUARE, Msg(true, text, now, from = myName)); return
        }
        val m = Msg(true, text, now, id = newId(), van = vanish[n] == true)
        val ep = online[n]
        add(n, if (ep != null) deliver(ep, m) else m)       // menzil dışıysa kuyruğa girer (st = 0)
        typing(n, false)
    }

    private fun deliver(ep: String, m: Msg): Msg {
        nm?.sendText(ep, m.id, m.text, m.van)
        return m.copy(st = 1, exp = if (m.van) System.currentTimeMillis() + VANISH_MS else 0)
    }

    /** Bağlantı kurulunca: bekleyen mesajları gönder, okunmuş ama bildirilmemişleri bildir. */
    private fun flush(n: String) {
        val ep = online[n] ?: return
        val l = chats[n] ?: return
        val unacked = l.filter { !it.mine && !it.sys && it.st == 2 }
        if (unacked.isEmpty() && l.none { it.mine && !it.sys && it.st == 0 }) return
        val nl = l.map { m ->
            when {
                m.mine && !m.sys && m.st == 0 -> deliver(ep, m)
                !m.mine && !m.sys && m.st == 2 -> m.copy(st = 3)
                else -> m
            }
        }
        if (unacked.isNotEmpty()) nm?.sendRead(ep, unacked.map { it.id })
        replace(n, nl)
    }

    private fun markRead(n: String) {
        val l = chats[n] ?: return
        val unr = l.filter { !it.mine && !it.sys && it.st == 0 }
        if (unr.isEmpty()) return
        val ep = online[n]
        val nl = l.map { m -> if (!m.mine && !m.sys && m.st == 0) m.copy(st = if (ep != null) 3 else 2) else m }
        if (ep != null) nm?.sendRead(ep, unr.map { it.id })
        replace(n, nl)
    }

    private fun setSt(n: String, ids: Set<Long>, st: Int) {
        val l = chats[n] ?: return
        if (l.none { it.mine && it.id in ids && it.st < st }) return
        replace(n, l.map { if (it.mine && it.id in ids && it.st < st) it.copy(st = st) else it })
    }

    fun typing(n: String, on: Boolean) {
        val ep = online[n] ?: return
        val now = System.currentTimeMillis()
        if (on) { if (now - lastTypingSent < 2000) return; lastTypingSent = now } else lastTypingSent = 0
        nm?.sendTyping(ep, on)
    }

    fun react(n: String, m: Msg, emoji: String) {
        val e = if (m.react == emoji) "" else emoji
        applyReact(n, m.id, e)
        val ep = online[n]
        if (ep != null) nm?.sendReact(ep, m.id, e)
    }
    private fun applyReact(n: String, mid: Long, emoji: String) {
        val l = chats[n] ?: return
        if (l.none { it.id == mid }) return
        replace(n, l.map { if (it.id == mid) it.copy(react = emoji) else it })
    }

    fun deleteChat(n: String) {
        chats.remove(n); unread.remove(n); store.deleteChat(n)
        if (openChat == n) openChat = null
    }

    fun nudge(n: String) {
        val ep = online[n] ?: return
        nm?.nudge(ep); add(n, Msg(true, "👋 Dürttün", System.currentTimeMillis(), sys = true))
    }

    fun sweep() {
        val now = System.currentTimeMillis()
        chats.keys.toList().forEach { n ->
            val l = chats[n] ?: return@forEach
            if (l.any { it.exp > 0 && it.exp <= now }) replace(n, l.filter { !(it.exp > 0 && it.exp <= now) })
        }
        typingUntil.keys.toList().forEach { k -> if ((typingUntil[k] ?: 0L) < now) typingUntil.remove(k) }
        if (notice != null && now > noticeUntil) notice = null
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val c = appCtx ?: return
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java).defaultVibrator
        else c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pat = longArrayOf(0, 120, 80, 120, 80, 280)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1)) else v.vibrate(pat, -1)
    }

    fun startCall(n: String) {
        val ep = online[n] ?: return
        activeCall = n; callConnected = false; muted = false; nm?.muted = false
        calls.add(0, CallLog(n, true, System.currentTimeMillis())); nm?.ring(ep)
    }
    fun accept() {
        val n = incomingFrom ?: return; val ep = online[n] ?: return
        activeCall = n; incomingFrom = null; callConnected = true; muted = false; nm?.muted = false
        nm?.accept(ep); nm?.startVoice(ep)
    }
    fun endCall() {
        val n = activeCall ?: incomingFrom
        if (n != null) { val ep = online[n]; if (ep != null) nm?.hangup(ep) }
        clearCall()
    }
    fun toggleMute() { muted = !muted; nm?.muted = muted }
    private fun clearCall() { activeCall = null; incomingFrom = null; callConnected = false; nm?.stopVoice() }

    private fun meta(n: String, name: String, mood: Int, color: Int) {
        val isNew = n !in names
        names[n] = name; moods[n] = mood; if (color >= 0) colors[n] = color
        store.saveContact(n, name, color)
        if (isNew) {
            if (requests[n]?.status != 1) requests[n] = ContactRequest(n, name, 0)
            nm?.watch(watchList())
        }
    }

    private fun onText(e: Ev.Text, now: Long) {
        val n = idNum[e.id] ?: return
        val reading = openChat == n
        add(n, Msg(false, e.text, now, id = e.mid, st = if (reading) 3 else 0, exp = if (e.vanish) now + VANISH_MS else 0))
        typingUntil.remove(n)
        nm?.sendAck(e.id, e.mid)
        if (reading) nm?.sendRead(e.id, listOf(e.mid)) else unread[n] = (unread[n] ?: 0) + 1
    }

    private fun onCall(e: Ev.Call, now: Long) {
        val n = idNum[e.id] ?: return
        when (e.kind) {
            1 -> if (activeCall == null && incomingFrom == null) { incomingFrom = n; calls.add(0, CallLog(n, false, now)) }
            2 -> if (activeCall == n) { callConnected = true; nm?.startVoice(e.id) }
            else -> if (activeCall == n || incomingFrom == n) clearCall()
        }
    }

    private fun handle(e: Ev) {
        val now = System.currentTimeMillis()
        when (e) {
            is Ev.Found -> { idNum[e.id] = e.number; meta(e.number, e.name, e.mood, e.color)
                if (e.number !in online) nearby[e.number] = e.name }
            is Ev.Lost -> { val n = idNum[e.id]; if (n != null) nearby.remove(n) }
            is Ev.Connected -> { idNum[e.id] = e.number; online[e.number] = e.id; nearby.remove(e.number)
                meta(e.number, e.name, e.mood, e.color); error = null; flush(e.number)
                if (pendingOpen == e.number) { pendingOpen = null; open(e.number); toast("${e.name} bulundu") } }
            is Ev.Gone -> { val n = idNum[e.id]
                if (n != null) { online.remove(n); typingUntil.remove(n); if (activeCall == n || incomingFrom == n) clearCall() } }
            is Ev.Text -> onText(e, now)
            is Ev.Ack -> { val n = idNum[e.id]; if (n != null) setSt(n, setOf(e.mid), 2) }
            is Ev.Read -> { val n = idNum[e.id]; if (n != null) setSt(n, e.mids.toSet(), 3) }
            is Ev.Typing -> { val n = idNum[e.id]
                if (n != null) { if (e.on) typingUntil[n] = now + 4000 else typingUntil.remove(n) } }
            is Ev.React -> { val n = idNum[e.id]; if (n != null) applyReact(n, e.mid, e.emoji) }
            is Ev.Broadcast -> { val n = idNum[e.id]
                if (n != null) { add(SQUARE, Msg(false, e.text, now, from = nameOf(n)))
                    if (openChat != SQUARE) unread[SQUARE] = (unread[SQUARE] ?: 0) + 1 } }
            is Ev.Nudge -> { val n = idNum[e.id]
                if (n != null) { buzz(); add(n, Msg(false, "👋 ${nameOf(n)} seni dürttü", now, sys = true))
                    if (openChat != n) unread[n] = (unread[n] ?: 0) + 1 } }
            is Ev.Call -> onCall(e, now)
            is Ev.Route -> routes[e.id] = e.route
            is Ev.Absent -> { toast("${e.number} şu an çevrimiçi değil"); if (pendingOpen == e.number) pendingOpen = null }
            is Ev.Taken -> { myNumber = store.regenNumber(); toast("Numara başkasında kullanılıyordu. Yeni numaran: $myNumber"); appCtx?.let { retry(it) } }
            is Ev.Wan -> wanState = e.state
            is Ev.Error -> error = e.text
        }
    }
}
