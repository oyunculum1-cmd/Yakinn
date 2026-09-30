package com.yakin.app

import android.content.Context
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

data class Msg(val mine: Boolean, val text: String, val ts: Long, val from: String = "",
               val exp: Long = 0, val sys: Boolean = false)
data class CallLog(val number: String, val outgoing: Boolean, val ts: Long)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("yakin", Context.MODE_PRIVATE)
    val number: String = p.getString("number", null) ?: "%03d %03d %03d".format(
        (100..999).random(), (100..999).random(), (100..999).random()).also { p.edit().putString("number", it).apply() }
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
        val o = JSONObject(p.getString("contacts", "{}")!!)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }
    fun saveContact(num: String, name: String, color: Int) {
        val o = JSONObject(p.getString("contacts", "{}")!!); o.put(num, "$name|$color")
        p.edit().putString("contacts", o.toString()).apply()
    }
    fun chatNumbers(): List<String> {
        val a = JSONArray(p.getString("chats", "[]")!!); return (0 until a.length()).map { a.getString(it) }
    }
    fun loadChat(num: String): List<Msg> {
        val a = JSONArray(p.getString("chat_$num", "[]")!!)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Msg(o.getBoolean("m"), o.getString("t"), o.getLong("s"), o.optString("f", ""), o.optLong("e", 0), o.optBoolean("y", false))
        }
    }
    fun saveChat(num: String, list: List<Msg>) {
        val a = JSONArray()
        list.takeLast(500).forEach {
            a.put(JSONObject().put("m", it.mine).put("t", it.text).put("s", it.ts).put("f", it.from).put("e", it.exp).put("y", it.sys))
        }
        val nums = chatNumbers().toMutableList(); if (num !in nums) nums.add(num)
        p.edit().putString("chat_$num", a.toString()).putString("chats", JSONArray(nums).toString()).apply()
    }
}

class Hub(private val store: Store) {
    val myNumber = store.number
    var myName by mutableStateOf(store.name)
    var myMood by mutableIntStateOf(store.mood)
    var myColor by mutableIntStateOf(store.color)
    val names = mutableStateMapOf<String, String>()
    val colors = mutableStateMapOf<String, Int>()
    val moods = mutableStateMapOf<String, Int>()
    val online = mutableStateMapOf<String, String>()      // numara -> endpointId
    val nearby = mutableStateMapOf<String, String>()      // bulunan ama henüz bağlı olmayan
    val chats = mutableStateMapOf<String, List<Msg>>()
    val unread = mutableStateMapOf<String, Int>()
    val vanish = mutableStateMapOf<String, Boolean>()     // kaybolan mesaj modu (sohbet başına)
    val calls = mutableStateListOf<CallLog>()
    var openChat by mutableStateOf<String?>(null)
    var incomingFrom by mutableStateOf<String?>(null)
    var activeCall by mutableStateOf<String?>(null)
    var callConnected by mutableStateOf(false)
    var muted by mutableStateOf(false)
    private val idNum = HashMap<String, String>()
    private var nm: NearbyManager? = null
    private var appCtx: Context? = null

    init {
        store.contacts().forEach { (n, v) ->
            val a = v.split("|"); names[n] = a[0]; a.getOrNull(1)?.toIntOrNull()?.let { if (it >= 0) colors[n] = it }
        }
        store.chatNumbers().forEach { chats[it] = store.loadChat(it) }
    }

    fun nameOf(n: String) = names[n]?.takeIf { it.isNotBlank() } ?: n

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (nm != null || myName.isBlank()) return
        nm = NearbyManager(ctx, myNumber, myName, myMood, myColor) { handle(it) }.also { it.start() }
    }

    fun setProfile(ctx: Context, name: String, mood: Int, color: Int) {
        myName = name; myMood = mood; myColor = color
        store.name = name; store.mood = mood; store.color = color
        nm?.stop(); nm = null; online.clear(); nearby.clear(); idNum.clear()
        start(ctx)
    }

    fun setName(n: String) { myName = n; store.name = n }

    fun open(n: String) { openChat = n; unread.remove(n) }

    fun send(n: String, text: String) {
        val now = System.currentTimeMillis()
        if (n == SQUARE) {
            online.values.forEach { nm?.sendBroadcast(it, text) }
            add(SQUARE, Msg(true, text, now, from = myName)); return
        }
        val id = online[n] ?: return
        val d = vanish[n] == true
        nm?.sendText(id, text, d)
        add(n, Msg(true, text, now, exp = if (d) now + VANISH_MS else 0))
    }

    fun nudge(n: String) {
        val id = online[n] ?: return
        nm?.nudge(id); add(n, Msg(true, "👋 Dürttün", System.currentTimeMillis(), sys = true))
    }

    private fun add(n: String, m: Msg) { val l = (chats[n] ?: emptyList()) + m; chats[n] = l; store.saveChat(n, l) }

    fun sweep() {
        val now = System.currentTimeMillis()
        chats.keys.toList().forEach { n ->
            val l = chats[n] ?: return@forEach
            if (l.any { it.exp > 0 && it.exp <= now }) {
                val nl = l.filter { !(it.exp > 0 && it.exp <= now) }; chats[n] = nl; store.saveChat(n, nl)
            }
        }
    }

    private fun buzz() {
        val c = appCtx ?: return
        val v = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 280), -1))
    }

    fun startCall(n: String) {
        val id = online[n] ?: return
        activeCall = n; callConnected = false; muted = false; nm?.muted = false
        calls.add(0, CallLog(n, true, System.currentTimeMillis())); nm?.ring(id)
    }
    fun accept() {
        val n = incomingFrom ?: return; val id = online[n] ?: return
        activeCall = n; incomingFrom = null; callConnected = true; muted = false; nm?.muted = false
        nm?.accept(id); nm?.startVoice(id)
    }
    fun endCall() {
        val n = activeCall ?: incomingFrom
        n?.let { online[it]?.let { id -> nm?.hangup(id) } }
        clearCall()
    }
    fun toggleMute() { muted = !muted; nm?.muted = muted }
    private fun clearCall() { activeCall = null; incomingFrom = null; callConnected = false; nm?.stopVoice() }

    private fun meta(n: String, name: String, mood: Int, color: Int) {
        names[n] = name; moods[n] = mood; if (color >= 0) colors[n] = color
        store.saveContact(n, name, color)
    }

    private fun handle(e: Ev) {
        val now = System.currentTimeMillis()
        when (e) {
            is Ev.Found -> { idNum[e.id] = e.number; meta(e.number, e.name, e.mood, e.color)
                if (e.number !in online) nearby[e.number] = e.name }
            is Ev.Lost -> idNum[e.id]?.let { nearby.remove(it) }
            is Ev.Connected -> { idNum[e.id] = e.number; online[e.number] = e.id; nearby.remove(e.number)
                meta(e.number, e.name, e.mood, e.color) }
            is Ev.Gone -> idNum[e.id]?.let { online.remove(it); if (activeCall == it || incomingFrom == it) clearCall() }
            is Ev.Text -> idNum[e.id]?.let { n ->
                add(n, Msg(false, e.text, now, exp = if (e.vanish) now + VANISH_MS else 0))
                if (openChat != n) unread[n] = (unread[n] ?: 0) + 1 }
            is Ev.Broadcast -> idNum[e.id]?.let { n ->
                add(SQUARE, Msg(false, e.text, now, from = nameOf(n)))
                if (openChat != SQUARE) unread[SQUARE] = (unread[SQUARE] ?: 0) + 1 }
            is Ev.Nudge -> idNum[e.id]?.let { n ->
                buzz(); add(n, Msg(false, "👋 ${nameOf(n)} seni dürttü", now, sys = true))
                if (openChat != n) unread[n] = (unread[n] ?: 0) + 1 }
            is Ev.Call -> idNum[e.id]?.let { n ->
                when (e.kind) {
                    1 -> if (activeCall == null && incomingFrom == null) { incomingFrom = n; calls.add(0, CallLog(n, false, now)) }
                    2 -> if (activeCall == n) { callConnected = true; nm?.startVoice(e.id) }
                    else -> if (activeCall == n || incomingFrom == n) clearCall()
                }
            }
        }
    }
}
