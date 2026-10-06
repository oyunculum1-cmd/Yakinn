package com.yakin.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

fun routeLabel(r: Int?) = if (r == 2) "internetten" else "aynı ağda"
private fun hm(ts: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
private fun dayKey(ts: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))
private fun dayLabel(ts: Long): String {
    val today = dayKey(System.currentTimeMillis()); val yest = dayKey(System.currentTimeMillis() - 86_400_000L)
    val k = dayKey(ts)
    return if (k == today) "Bugün" else if (k == yest) "Dün" else SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date(ts))
}

/** Basınca yaylanan tıklama animasyonu. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "bounce")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** İçeriği aşağıdan süzülerek ve belirerek getirir. */
@Composable fun Appear(delayMs: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Box(modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }) { content() }
}

@OptIn(ExperimentalAnimationApi::class)
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
    val key = when {
        hub.myName.isBlank() -> "onb"
        hub.activeCall != null || hub.incomingFrom != null -> "call"
        hub.openChat != null -> "chat:" + hub.openChat
        else -> "main"
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        AnimatedContent(targetState = key, label = "root", transitionSpec = {
            (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 10 }) togetherWith fadeOut(tween(140))
        }) { k ->
            when {
                k == "onb" -> Onboarding(hub, onReady)
                k == "call" -> CallScreen(hub)
                k.startsWith("chat:") -> ChatScreen(hub, k.removePrefix("chat:"))
                else -> Main(hub)
            }
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
        .verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Appear(0) { Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Sensors, null, tint = Color.White, modifier = Modifier.size(52.dp)) } }
        Spacer(Modifier.height(20.dp))
        Appear(120) { Text("Yakın", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold) }
        Appear(200) { Text("Aynı ağda internetsiz, uzakta internetle. Numarayla bul.", color = Muted, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(28.dp))
        Appear(320) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Senin numaran", color = Muted, fontSize = 13.sp)
            Text(hub.myNumber, color = Teal, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) } }
        Spacer(Modifier.height(20.dp))
        Appear(440, Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(18.dp)) {
                BasicTextField(t, { t = it.take(24) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (t.isEmpty()) Text("Adın", color = Muted, fontSize = 18.sp); inner() })
            } }
        Spacer(Modifier.height(16.dp))
        Appear(540, Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Brand)
                .alpha(if (t.isBlank()) .4f else 1f)
                .bounceClick(enabled = t.isNotBlank()) { hub.setName(t.trim()); onReady() }, contentAlignment = Alignment.Center) {
                Text("Başla", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }
    }
}

@Composable fun Main(hub: Hub) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = hub.error != null) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF3B1D1D)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(hub.error ?: "", color = Color(0xFFFCA5A5), fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton({ hub.retry(ctx) }) { Text("Tekrar dene", color = Color.White) }
            }
        }
        AnimatedVisibility(visible = hub.notice != null) {
            Text(hub.notice ?: "", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Ind).padding(10.dp))
        }
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = hub.tab, label = "tab") { t ->
                when (t) {
                    0 -> ChatsTab(hub) { hub.tab = 2 }
                    1 -> CallsTab(hub)
                    2 -> NearbyTab(hub)
                    else -> ProfileTab(hub)
                }
            }
        }
        NavigationBar(containerColor = Card) {
            val items = listOf("Sohbetler" to Icons.Filled.ChatBubble, "Aramalar" to Icons.Filled.Call,
                "Yakınımda" to Icons.Filled.Sensors, "Profil" to Icons.Filled.Person)
            items.forEachIndexed { i, (l, ic) ->
                NavigationBarItem(selected = hub.tab == i, onClick = { hub.tab = i }, icon = {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ChatsTab(hub: Hub, goNearby: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var del by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
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
                    Text("${hub.online.size} kişi bağlı", color = Muted, fontSize = 12.sp)
                }
                IconButton({ add = true }) { Icon(Icons.Filled.PersonAdd, null, tint = Teal) }
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
                item(key = "square") { SquareRow(hub) }
                if (nums.isEmpty()) item(key = "hint") {
                    Text("Aynı Wi-Fi ya da hotspot'taki biri otomatik görünür. Uzaktakini sağ üstten numarayla bul. Eski konuşmalar burada saklanır.",
                        color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
                items(nums, key = { it }) { n -> ChatRow(hub, n, Modifier.animateItemPlacement()) { del = n } }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp))
            .background(Brand).bounceClick { goNearby() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Sensors, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
    }
    if (add) AddDialog(hub) { add = false }
    del?.let { n ->
        AlertDialog(onDismissRequest = { del = null }, title = { Text("Sohbeti sil") },
            text = { Text("${hub.nameOf(n)} ile tüm konuşma bu telefondan silinir.") },
            confirmButton = { TextButton({ hub.deleteChat(n); del = null }) { Text("Sil", color = Red) } },
            dismissButton = { TextButton({ del = null }) { Text("Vazgeç") } })
    }
}

@Composable fun Empty(icon: ImageVector, title: String, sub: String) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Ind, modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable fun Badge21(u: Int) {
    AnimatedVisibility(visible = u > 0, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Teal), contentAlignment = Alignment.Center) {
            Text("$u", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                ?: if (hub.online.isEmpty()) "Bağlı herkesle ortak sohbet" else "${hub.online.size} kişi bağlı · herkese yaz",
                color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Badge21(u)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ChatRow(hub: Hub, n: String, modifier: Modifier = Modifier, onLong: () -> Unit) {
    val last = hub.chats[n]?.lastOrNull(); val u = hub.unread[n] ?: 0; val name = hub.nameOf(n)
    val typing = hub.typingUntil[n] != null
    Row(modifier.fillMaxWidth().combinedClickable(onClick = { hub.open(n) }, onLongClick = onLong)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 54.dp, n in hub.online, hub.colors[n] ?: -1); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (typing) Text("yazıyor…", color = Teal, maxLines = 1)
            else Text(last?.text ?: "Yazışmaya başla", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (last != null) Text(hm(last.ts), color = if (u > 0) Teal else Muted, fontSize = 12.sp)
            Badge21(u)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun NearbyTab(hub: Hub) {
    Column(Modifier.fillMaxSize()) {
        Header("Yakınımda")
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Radar(Modifier.size(200.dp)) }
        Text(if (hub.online.isEmpty() && hub.nearby.isEmpty()) "Ağ taranıyor…" else "Bağlı kişiler",
            color = Muted, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
        LazyColumn {
            items(hub.online.keys.toList(), key = { "o$it" }) { n ->
                PersonRow(hub.nameOf(n), n, Moods[hub.moods[n] ?: 0] + " · " + routeLabel(hub.routes[n]), true, hub.colors[n] ?: -1, Modifier.animateItemPlacement()) { hub.open(n) } }
            items(hub.nearby.keys.toList(), key = { "n$it" }) { n ->
                PersonRow(hub.nameOf(n), n, "Bağlanıyor…", false, hub.colors[n] ?: -1, Modifier.animateItemPlacement()) {} }
        }
    }
}

@Composable fun PersonRow(name: String, num: String, sub: String, on: Boolean, color: Int, modifier: Modifier = Modifier, click: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable { click() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
    var server by remember { mutableStateOf(hub.server) }
    val changed = name.isNotBlank() && (name.trim() != hub.myName || mood != hub.myMood || color != hub.myColor)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Appear(0) { Avatar(name.ifBlank { "?" }, 108.dp, false, color) }
        Spacer(Modifier.height(20.dp))
        Appear(80, Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Column {
                    Text("Adın", color = Muted, fontSize = 12.sp)
                    BasicTextField(name, { name = it.take(24) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth())
                }
            } }
        Spacer(Modifier.height(16.dp))
        Appear(160, Modifier.fillMaxWidth()) { Column {
            Text("Ruh halin · yakındakiler görür", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Moods.forEachIndexed { i, m ->
                    val bgc by animateColorAsState(if (mood == i) Ind else Card, label = "chip")
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bgc).bounceClick { mood = i }
                        .padding(horizontal = 14.dp, vertical = 9.dp)) { Text(m, color = Color.White, fontSize = 14.sp) }
                }
            } } }
        Spacer(Modifier.height(18.dp))
        Appear(240, Modifier.fillMaxWidth()) { Column {
            Text("Avatar rengi", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Palette.forEachIndexed { i, c ->
                    val sc by animateFloatAsState(if (color == i) 1.18f else 1f, spring(dampingRatio = 0.5f), label = "pal")
                    Box(Modifier.size(42.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(c)
                        .then(if (color == i) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .bounceClick { color = i })
                }
            } } }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Brand).alpha(if (changed) 1f else .35f)
            .bounceClick(enabled = changed) { hub.setProfile(ctx, name.trim(), mood, color) }, contentAlignment = Alignment.Center) {
            Text("Kaydet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)) {
            Text("Sunucu adresi (internet için)", color = Muted, fontSize = 12.sp)
            BasicTextField(server, { server = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                decorationBox = { inner -> if (server.isEmpty()) Text("wss://ornek.onrender.com", color = Muted, fontSize = 16.sp); inner() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(when (hub.wanState) { 2 -> Green; 1 -> Color(0xFFF59E0B); else -> Muted }))
                Spacer(Modifier.width(8.dp))
                Text(when (hub.wanState) { 2 -> "İnternet: bağlı"; 1 -> "İnternet: bağlanıyor…"; else -> "İnternet: kapalı" },
                    color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton({ hub.setServer(ctx, server) }) { Text("Bağlan", color = Ind) }
            }
            Text("Boş bırakırsan sadece aynı Wi-Fi / hotspot üzerinden çalışır.", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        Appear(320, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Yakın numaran", color = Muted, fontSize = 13.sp)
                Text(hub.myNumber, color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                TextButton({ clip.setText(AnnotatedString(hub.myNumber)); copied = true }) {
                    Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, null, tint = Ind, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (copied) "Kopyalandı" else "Kopyala", color = Ind) }
            } }
        Text("Mesajların sadece bu telefonda saklanır.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    }
}

@Composable fun Ticks(st: Int) {
    Crossfade(targetState = st, label = "tick") { s ->
        when (s) {
            0 -> Icon(Icons.Filled.Schedule, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(13.dp))
            1 -> Icon(Icons.Filled.Done, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(14.dp))
            2 -> Icon(Icons.Filled.DoneAll, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(14.dp))
            else -> Icon(Icons.Filled.DoneAll, null, tint = Color(0xFF5EEAD4), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "ty")
    Row(Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(Theirs).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0..2) {
            val y by t.animateFloat(0f, -6f, infiniteRepeatable(tween(420, delayMillis = i * 140, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(8.dp).graphicsLayer { translationY = y * density }.clip(CircleShape).background(Muted))
        }
    }
}

@Composable fun Chip(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        Text(text, color = Muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Card).padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Bubble(hub: Hub, n: String, m: Msg, sq: Boolean, lastMine: Boolean) {
    val fresh = System.currentTimeMillis() - m.ts < 1500
    val vis = remember { MutableTransitionState(!fresh).apply { targetState = true } }
    var menu by remember { mutableStateOf(false) }
    val canReact = !sq && m.id != 0L
    AnimatedVisibility(visibleState = vis, enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 2 } +
        scaleIn(tween(220), initialScale = 0.85f, transformOrigin = TransformOrigin(if (m.mine) 1f else 0f, 1f))) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
            Box {
                Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                    bottomEnd = if (m.mine) 4.dp else 18.dp, bottomStart = if (m.mine) 18.dp else 4.dp))
                    .background(if (m.mine) Mine else Theirs)
                    .combinedClickable(onClick = {}, onLongClick = { if (canReact) menu = true })
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (sq && !m.mine) Text(m.from, color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(m.text, color = Color.White, fontSize = 16.sp)
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (m.react.isNotEmpty()) { Text(m.react, fontSize = 13.sp); Spacer(Modifier.width(4.dp)) }
                        if (m.exp > 0 || m.van) { Icon(Icons.Filled.Timer, null, tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
                        Text(hm(m.ts), color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                        if (m.mine && !sq) { Spacer(Modifier.width(4.dp)); Ticks(m.st) }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        listOf("❤️", "👍", "😂", "😮", "🙏").forEach { e ->
                            Text(e, fontSize = 26.sp, modifier = Modifier.clickable { hub.react(n, m, e); menu = false }.padding(6.dp))
                        }
                    }
                }
            }
            if (lastMine && m.st == 3 && !sq) Text("Görüldü", color = Teal, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp, top = 2.dp))
            if (lastMine && m.st == 0 && !sq) Text("Bekliyor · bağlanınca gönderilir", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp, top = 2.dp))
        }
    }
}

@Composable fun ChatScreen(hub: Hub, n: String) {
    val sq = n == SQUARE
    val name = if (sq) "Meydan" else hub.nameOf(n)
    val on = if (sq) hub.online.isNotEmpty() else n in hub.online
    val vanish = hub.vanish[n] == true
    val typing = !sq && hub.typingUntil[n] != null
    val msgs = hub.chats[n] ?: emptyList()
    val ls = rememberLazyListState()
    val lastMine = msgs.indexOfLast { it.mine && !it.sys }
    BackHandler { hub.openChat = null }
    LaunchedEffect(msgs.size, typing) { ls.animateScrollToItem(msgs.size) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Card).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ hub.openChat = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            if (sq) Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Campaign, null, tint = Color.White) }
            else Avatar(name, 40.dp, on, hub.colors[n] ?: -1)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val status = when { sq -> "${hub.online.size} kişi bağlı"; typing -> "yazıyor…"
                    on -> Moods[hub.moods[n] ?: 0] + " · " + routeLabel(hub.routes[n]); else -> "çevrimdışı · geçmiş görünür" }
                val stc = if (typing) Teal else if (on) Green else Muted
                Crossfade(targetState = status, label = "status") { s -> Text(s, color = stc, fontSize = 12.sp, maxLines = 1) }
            }
            if (!sq) {
                IconButton({ hub.nudge(n) }, enabled = on) { Icon(Icons.Filled.TouchApp, null, tint = if (on) Color(0xFFF59E0B) else Muted) }
                IconButton({ hub.vanish[n] = !vanish }) { Icon(Icons.Filled.Timer, null, tint = if (vanish) Teal else Muted) }
                IconButton({ hub.startCall(n) }, enabled = on) { Icon(Icons.Filled.Call, null, tint = if (on) Teal else Muted) }
            }
        }
        AnimatedVisibility(visible = vanish) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF12332F)).padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = Teal, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text("Kaybolan mesajlar açık · 30 saniye sonra silinir", color = Teal, fontSize = 13.sp) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp), state = ls, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(msgs) { i, m ->
                Column {
                    if (i == 0 || dayKey(msgs[i - 1].ts) != dayKey(m.ts)) Chip(dayLabel(m.ts))
                    if (m.sys) Chip(m.text) else Bubble(hub, n, m, sq, i == lastMine)
                }
            }
            item {
                AnimatedVisibility(visible = typing, enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(0f, 1f)), exit = fadeOut()) { TypingBubble() }
            }
        }
        AnimatedVisibility(visible = !on) {
            Text(if (sq) "Şu an kimse bağlı değil." else "Çevrimdışı — yazdıkların karşı taraf bağlanınca otomatik gönderilir.", color = Muted, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(6.dp))
        }
        var text by remember { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(Card).padding(horizontal = 18.dp, vertical = 13.dp)) {
                BasicTextField(text, { text = it; if (!sq) hub.typing(n, it.isNotEmpty()) }, maxLines = 5,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (text.isEmpty()) Text(if (sq) "Herkese yaz…" else "Mesaj yaz…", color = Muted); inner() })
            }
            Spacer(Modifier.width(8.dp))
            val can = text.isNotBlank() && (!sq || on)
            Box(Modifier.size(50.dp).clip(CircleShape).background(Brand).alpha(if (can) 1f else .4f)
                .bounceClick(enabled = can) { hub.send(n, text.trim()); text = "" }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White) }
        }
    }
}

@Composable fun SoundBars() {
    val t = rememberInfiniteTransition(label = "bars")
    Row(Modifier.height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..4) {
            val h by t.animateFloat(6f, 26f, infiniteRepeatable(tween(480 + i * 90, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(4.dp).height(h.dp).clip(CircleShape).background(Teal))
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
        Spacer(Modifier.height(14.dp))
        AnimatedVisibility(visible = hub.callConnected && !hub.muted) { SoundBars() }
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
    Box(Modifier.size(70.dp).clip(CircleShape).background(bg).bounceClick { click() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp)) }
}

private fun fmtNumber(v: String): String = v.filter { it.isDigit() }.take(9).chunked(3).joinToString(" ")

@Composable fun AddDialog(hub: Hub, close: () -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Numara ile bul") },
        text = { Column {
            Text("Arkadaşının Yakın numarasını yaz. Aynı Wi-Fi'deyseniz ya da sunucu bağlıysa bulunur.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bg).padding(14.dp)) {
                BasicTextField(t, { v -> t = fmtNumber(v) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Teal, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Teal), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (t.isEmpty()) Text("000 000 000", color = Muted, fontSize = 22.sp, fontFamily = FontFamily.Monospace); inner() })
            }
        } },
        confirmButton = { TextButton({ if (t.length == 11) { hub.find(t); close() } }) { Text("Bul") } },
        dismissButton = { TextButton(close) { Text("Vazgeç") } })
}

@Composable fun StickerPicker(stickers: StickerPack, onSelect: (Sticker) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton({ expanded = !expanded }) { Icon(Icons.Filled.SmileFilled, null, tint = Teal) }
        if (expanded) {
            AlertDialog(onDismissRequest = { expanded = false },
                title = { Text("Sticker") },
                text = {
                    LazyColumn {
                        items(stickers.stickers.size) { i ->
                            val s = stickers.stickers[i]
                            Row(Modifier.fillMaxWidth().clickable { onSelect(s); expanded = false }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (s.emoji.isNotEmpty()) Text(s.emoji, fontSize = 40.sp)
                                else if (s.base64.isNotEmpty()) {
                                    val bm = BitmapFactory.decodeByteArray(Base64.getDecoder().decode(s.base64), 0, s.base64.length)
                                    Image(bm.asImageBitmap(), null, Modifier.size(60.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ expanded = false }) { Text("Kapat") } }
            )
        }
    }
}

@Composable fun MessageBubble(msg: Msg, onReact: (String) -> Unit, onMenu: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Column(Modifier.animateItemPlacement()) {
        Box(Modifier.clickable { onMenu() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { showMenu = !showMenu })
            }
        ) {
            if (msg.stickerBase64.isNotEmpty()) {
                val bm = BitmapFactory.decodeByteArray(Base64.getDecoder().decode(msg.stickerBase64), 0, msg.stickerBase64.length)
                Image(bm.asImageBitmap(), null, Modifier.size(100.dp).clip(RoundedCornerShape(18.dp))
                    .animateContentSize()
                    .graphicsLayer { scaleX = 0.9f; scaleY = 0.9f }
                )
            } else if (msg.gifBase64.isNotEmpty()) {
                val bm = BitmapFactory.decodeByteArray(Base64.getDecoder().decode(msg.gifBase64), 0, msg.gifBase64.length)
                Image(bm.asImageBitmap(), null, Modifier.size(140.dp).clip(RoundedCornerShape(16.dp))
                    .animateContentSize()
                )
            } else {
                Text(msg.t, color = Color.White, fontSize = 14.sp)
            }
            if (showMenu) {
                Row(Modifier.padding(top = 4.dp)) {
                    for (e in listOf("❤️", "👍", "😂")) {
                        Text(e, Modifier.clickable { onReact(e); showMenu = false }.padding(2.dp))
                    }
                }
            }
        }
        if (msg.r.isNotEmpty()) Text(msg.r, fontSize = 28.sp)
    }
}


@Composable fun ContactRequestDialog(hub: Hub, contact: ContactRequest, onAccept: () -> Unit, onBlock: () -> Unit) {
    AlertDialog(onDismissRequest = {  },
        title = { Text("Yeni kişi") },
        text = { Text(contact.name + " (${contact.number}) seni buldu. Kabul?") },
        confirmButton = { TextButton({ onAccept() }) { Text("Kabul") } },
        dismissButton = { TextButton({ onBlock() }) { Text("Engelle") } }
    )
}
