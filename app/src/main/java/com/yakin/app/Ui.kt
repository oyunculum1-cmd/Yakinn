package com.yakin.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

val Bg = Color(0xFF0B1020); val Card = Color(0xFF151B2F); val Ind = Color(0xFF6366F1)
val Teal = Color(0xFF14B8A6); val Mine = Color(0xFF4F46E5); val Theirs = Color(0xFF1E263D)
val Muted = Color(0xFF8B93A7); val Green = Color(0xFF22C55E); val Red = Color(0xFFEF4444)
val Brand = Brush.linearGradient(listOf(Ind, Teal))
val Palette = listOf(Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF06B6D4))

@Composable fun YakinTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Ind, background = Bg, surface = Card, onSurface = Color.White),
    content = content)

private fun hm(ts: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

@Composable fun Root(hub: Hub, onReady: () -> Unit) {
    var splash by remember { mutableStateOf(true) }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = splash
        w.statusBarColor = if (splash) 0xFFEDE9FE.toInt() else 0xFF0B1020.toInt()
        w.navigationBarColor = if (splash) 0xFFDDD6FE.toInt() else 0xFF151B2F.toInt()
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); hub.sweep() } }
    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            hub.myName.isBlank() -> Onboarding(hub, onReady)
            hub.activeCall != null || hub.incomingFrom != null -> CallScreen(hub)
            hub.openChat != null -> ChatScreen(hub, hub.openChat!!)
            else -> Main(hub)
        }
        if (splash) Splash { splash = false }
    }
}

@Composable fun Avatar(name: String, size: Dp = 52.dp, online: Boolean = false, color: Int = -1) {
    val c = if (color in Palette.indices) Palette[color] else Palette[(name.hashCode() and 0x7fffffff) % Palette.size]
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(listOf(c, c.copy(alpha = .5f)))),
            contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White,
                fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
        if (online) Box(Modifier.size(size / 4).align(Alignment.BottomEnd).clip(CircleShape)
            .background(Bg).padding(2.dp).clip(CircleShape).background(Green))
    }
}

@Composable fun Radar(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "p")
    androidx.compose.foundation.Canvas(modifier) {
        val max = size.minDimension / 2
        for (i in 0..2) {
            val q = (p + i / 3f) % 1f
            drawCircle(Ind.copy(alpha = (1 - q) * 0.55f), radius = max * q, center = center, style = Stroke(3f))
        }
        drawCircle(Brand, radius = 26.dp.toPx(), center = center)
    }
}

@Composable fun Onboarding(hub: Hub, onReady: () -> Unit) {
    var t by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Bg)))
        .padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Sensors, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Yakın", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text("İnternetsiz. Sunucusuz. Sadece yakınındakilerle.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Text("Senin numaran", color = Muted, fontSize = 13.sp)
        Text(hub.myNumber, color = Teal, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(18.dp)) {
            BasicTextField(t, { t = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (t.isEmpty()) Text("Adın", color = Muted, fontSize = 18.sp); inner() })
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Brand)
            .alpha(if (t.isBlank()) .4f else 1f)
            .clickable(enabled = t.isNotBlank()) { hub.setName(t.trim()); onReady() }, contentAlignment = Alignment.Center) {
            Text("Başla", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable fun Main(hub: Hub) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ChatsTab(hub) { tab = 2 }
                1 -> CallsTab(hub)
                2 -> NearbyTab(hub)
                else -> ProfileTab(hub)
            }
        }
        NavigationBar(containerColor = Card) {
            val items = listOf("Sohbetler" to Icons.Filled.ChatBubble, "Aramalar" to Icons.Filled.Call,
                "Yakınımda" to Icons.Filled.Sensors, "Profil" to Icons.Filled.Person)
            items.forEachIndexed { i, (l, ic) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {
                    BadgedBox(badge = { if (i == 0 && hub.unread.values.sum() > 0) Badge(containerColor = Teal) }) { Icon(ic, l) } },
                    label = { Text(l, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White,
                        indicatorColor = Ind, unselectedIconColor = Muted, unselectedTextColor = Muted))
            }
        }
    }
}

@Composable fun Header(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TextStyle(brush = Brand, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable fun ChatsTab(hub: Hub, goNearby: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val nums = (hub.chats.keys + hub.online.keys).filter { it != SQUARE }.distinct()
        .filter { q.isBlank() || hub.nameOf(it).contains(q, true) || it.contains(q) }
        .sortedByDescending { hub.chats[it]?.lastOrNull()?.ts ?: 0L }
    Box(Modifier.fillMaxSize()) {
        Column {
            Header("Yakın") {
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Card).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (hub.online.isEmpty()) Muted else Green))
                    Spacer(Modifier.width(6.dp))
                    Text("${hub.online.size} menzilde", color = Muted, fontSize = 12.sp)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card)
                .padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Muted); Spacer(Modifier.width(10.dp))
                BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (q.isEmpty()) Text("Ara", color = Muted); inner() })
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn {
                item { SquareRow(hub) }
                if (nums.isEmpty()) item {
                    Text("Yakınındaki biriyle aynı anda uygulamayı açın. Otomatik bulunur, internet gerekmez.",
                        color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
                items(nums) { n -> ChatRow(hub, n) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp))
            .background(Brand).clickable { goNearby() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Sensors, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
}

@Composable fun Empty(icon: ImageVector, title: String, sub: String) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Ind, modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable fun SquareRow(hub: Hub) {
    val last = hub.chats[SQUARE]?.lastOrNull(); val u = hub.unread[SQUARE] ?: 0
    Row(Modifier.fillMaxWidth().clickable { hub.open(SQUARE) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Campaign, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Meydan", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(last?.let { (if (it.mine) "Sen" else it.from) + ": " + it.text }
                ?: if (hub.online.isEmpty()) "Menzildeki herkesle ortak sohbet" else "${hub.online.size} kişi menzilde · herkese yaz",
                color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (u > 0) Box(Modifier.size(22.dp).clip(CircleShape).background(Teal), contentAlignment = Alignment.Center) {
            Text("$u", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable fun ChatRow(hub: Hub, n: String) {
    val last = hub.chats[n]?.lastOrNull(); val u = hub.unread[n] ?: 0; val name = hub.nameOf(n)
    Row(Modifier.fillMaxWidth().clickable { hub.open(n) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 54.dp, n in hub.online, hub.colors[n] ?: -1); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(last?.text ?: "Yazışmaya başla", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (last != null) Text(hm(last.ts), color = if (u > 0) Teal else Muted, fontSize = 12.sp)
            if (u > 0) Box(Modifier.padding(top = 4.dp).size(22.dp).clip(CircleShape).background(Teal), contentAlignment = Alignment.Center) {
                Text("$u", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable fun NearbyTab(hub: Hub) {
    Column(Modifier.fillMaxSize()) {
        Header("Yakınımda")
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Radar(Modifier.size(200.dp)) }
        Text(if (hub.online.isEmpty() && hub.nearby.isEmpty()) "Çevre taranıyor…" else "Menzildeki kişiler",
            color = Muted, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
        LazyColumn {
            items(hub.online.keys.toList()) { n ->
                PersonRow(hub.nameOf(n), n, Moods[hub.moods[n] ?: 0], true, hub.colors[n] ?: -1) { hub.open(n) } }
            items(hub.nearby.keys.toList()) { n ->
                PersonRow(hub.nameOf(n), n, "Bağlanıyor…", false, hub.colors[n] ?: -1) {} }
        }
    }
}

@Composable fun PersonRow(name: String, num: String, sub: String, on: Boolean, color: Int, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { click() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 48.dp, on, color); Spacer(Modifier.width(14.dp))
        Column { Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("$num · $sub", color = Muted, fontSize = 13.sp) }
    }
}

@Composable fun CallsTab(hub: Hub) {
    Column(Modifier.fillMaxSize()) {
        Header("Aramalar")
        if (hub.calls.isEmpty()) Empty(Icons.Filled.Call, "Arama yok", "Bir sohbetten sesli arama başlat.")
        else LazyColumn { items(hub.calls.toList()) { c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(hub.nameOf(c.number), 48.dp, false, hub.colors[c.number] ?: -1); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(hub.nameOf(c.number), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text((if (c.outgoing) "Giden" else "Gelen") + " · " + hm(c.ts), color = Muted, fontSize = 13.sp) }
                Icon(if (c.outgoing) Icons.Filled.CallMade else Icons.Filled.CallReceived, null, tint = Teal)
            } } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProfileTab(hub: Hub) {
    val ctx = LocalContext.current; val clip = LocalClipboardManager.current
    var name by remember { mutableStateOf(hub.myName) }
    var mood by remember { mutableIntStateOf(hub.myMood) }
    var color by remember { mutableIntStateOf(hub.myColor) }
    var copied by remember { mutableStateOf(false) }
    val changed = name.isNotBlank() && (name.trim() != hub.myName || mood != hub.myMood || color != hub.myColor)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Avatar(name.ifBlank { "?" }, 108.dp, false, color)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Column {
                Text("Adın", color = Muted, fontSize = 12.sp)
                BasicTextField(name, { name = it.take(24) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Ruh halin · yakındakiler görür", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Moods.forEachIndexed { i, m ->
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (mood == i) Ind else Card).clickable { mood = i }
                    .padding(horizontal = 14.dp, vertical = 9.dp)) { Text(m, color = Color.White, fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Avatar rengi", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Palette.forEachIndexed { i, c ->
                Box(Modifier.size(42.dp).clip(CircleShape).background(c).then(if (color == i) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { color = i })
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Brand).alpha(if (changed) 1f else .35f)
            .clickable(enabled = changed) { hub.setProfile(ctx, name.trim(), mood, color) }, contentAlignment = Alignment.Center) {
            Text("Kaydet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Yakın numaran", color = Muted, fontSize = 13.sp)
            Text(hub.myNumber, color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            TextButton({ clip.setText(AnnotatedString(hub.myNumber)); copied = true }) {
                Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, null, tint = Ind, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(if (copied) "Kopyalandı" else "Kopyala", color = Ind) }
        }
        Text("Mesajların sadece bu telefonda saklanır.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    }
}

@Composable fun ChatScreen(hub: Hub, n: String) {
    val sq = n == SQUARE
    val name = if (sq) "Meydan" else hub.nameOf(n)
    val on = if (sq) hub.online.isNotEmpty() else n in hub.online
    val vanish = hub.vanish[n] == true
    val msgs = hub.chats[n] ?: emptyList(); val ls = rememberLazyListState()
    BackHandler { hub.openChat = null }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) ls.animateScrollToItem(msgs.size - 1) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ hub.openChat = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            if (sq) Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Campaign, null, tint = Color.White) }
            else Avatar(name, 40.dp, on, hub.colors[n] ?: -1)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(when { sq -> "${hub.online.size} kişi menzilde"; on -> Moods[hub.moods[n] ?: 0]; else -> "menzil dışı" },
                    color = if (on) Green else Muted, fontSize = 12.sp, maxLines = 1)
            }
            if (!sq) {
                IconButton({ hub.nudge(n) }, enabled = on) { Icon(Icons.Filled.WavingHand, null, tint = if (on) Color(0xFFF59E0B) else Muted) }
                IconButton({ hub.vanish[n] = !vanish }) { Icon(Icons.Filled.Timer, null, tint = if (vanish) Teal else Muted) }
                IconButton({ hub.startCall(n) }, enabled = on) { Icon(Icons.Filled.Call, null, tint = if (on) Teal else Muted) }
            }
        }
        if (vanish) Row(Modifier.fillMaxWidth().background(Color(0xFF12332F)).padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, tint = Teal, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("Kaybolan mesajlar açık · 30 saniye sonra silinir", color = Teal, fontSize = 13.sp) }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp), state = ls, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(msgs) { m ->
                if (m.sys) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                    Text(m.text, color = Muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Card).padding(horizontal = 12.dp, vertical = 5.dp)) }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp, bottomStart = if (m.mine) 18.dp else 4.dp, bottomEnd = if (m.mine) 4.dp else 18.dp))
                        .background(if (m.mine) Mine else Theirs).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (sq && !m.mine) Text(m.from, color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(m.text, color = Color.White, fontSize = 16.sp)
                        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            if (m.exp > 0) { Icon(Icons.Filled.Timer, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
                            Text(hm(m.ts), color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (!on) Text(if (sq) "Menzilde kimse yok." else "Menzil dışı — mesaj göndermek için yaklaşın.", color = Muted, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(6.dp))
        var text by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(Card).padding(horizontal = 18.dp, vertical = 13.dp)) {
                BasicTextField(text, { text = it }, maxLines = 5, textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (text.isEmpty()) Text(if (sq) "Herkese yaz…" else "Mesaj yaz…", color = Muted); inner() })
            }
            Spacer(Modifier.width(8.dp))
            val can = text.isNotBlank() && on
            Box(Modifier.size(50.dp).clip(CircleShape).background(Brand).alpha(if (can) 1f else .4f)
                .clickable(enabled = can) { hub.send(n, text.trim()); text = "" }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White) }
        }
    }
}

@Composable fun CallScreen(hub: Hub) {
    val n = hub.activeCall ?: hub.incomingFrom ?: return
    val incoming = hub.incomingFrom != null && hub.activeCall == null
    var sec by remember { mutableIntStateOf(0) }
    LaunchedEffect(hub.callConnected) { sec = 0; while (hub.callConnected) { delay(1000); sec++ } }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Bg))).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(80.dp))
        Box(contentAlignment = Alignment.Center) {
            if (!hub.callConnected) Radar(Modifier.size(260.dp))
            Avatar(hub.nameOf(n), 130.dp, false, hub.colors[n] ?: -1)
        }
        Spacer(Modifier.height(20.dp))
        Text(hub.nameOf(n), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(when { incoming -> "Gelen sesli arama"; hub.callConnected -> "%02d:%02d".format(sec / 60, sec % 60); else -> "Aranıyor…" },
            color = Muted, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.padding(bottom = 40.dp)) {
            if (incoming) {
                Round(Red, Icons.Filled.CallEnd) { hub.endCall() }
                Round(Green, Icons.Filled.Call) { hub.accept() }
            } else {
                Round(if (hub.muted) Color.White else Card, if (hub.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    if (hub.muted) Bg else Color.White) { hub.toggleMute() }
                Round(Red, Icons.Filled.CallEnd) { hub.endCall() }
            }
        }
    }
}

@Composable fun Round(bg: Color, icon: ImageVector, tint: Color = Color.White, click: () -> Unit) {
    Box(Modifier.size(70.dp).clip(CircleShape).background(bg).clickable { click() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp)) }
}
