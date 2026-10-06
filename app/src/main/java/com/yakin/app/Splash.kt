package com.yakin.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * Açılış animasyonu (açık mor zemin):
 * 1) mesaj balonu yana kayarak gelir + bildirim noktası + halka
 * 2) balonda "yazıyor…" noktaları
 * 3) noktalar logodaki sinyal simgesine dönüşür (yaylar çizilir)
 * 4) "Yakın" yazısı belirir, ekran uygulamaya solar
 */
@Composable fun Splash(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(3800, easing = LinearEasing)); onDone() }
    val t = p.value
    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    val overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val slide = overshoot.transform(seg(0.04f, 0.26f))
    val typing = seg(0.28f, 0.36f)
    val arcs = FastOutSlowInEasing.transform(seg(0.50f, 0.76f))
    val word = FastOutSlowInEasing.transform(seg(0.74f, 0.90f))
    val fade = seg(0.94f, 1f)
    val ind = Color(0xFF4F46E5)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF5F3FF), Color(0xFFDDD6FE))))
        .alpha(1f - fade), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                val s = size.height / 260f
                val ox = (size.width - 260f * s) / 2f
                translate(left = ox + (1f - slide) * size.width * 1.2f, top = 0f) {
                    drawRoundRect(ind.copy(alpha = .14f), Offset(30f * s, 62f * s), Size(200f * s, 130f * s), CornerRadius(40f * s))
                    drawRoundRect(Color.White, Offset(30f * s, 50f * s), Size(200f * s, 130f * s), CornerRadius(40f * s))
                    val tail = Path().apply { moveTo(74f * s, 170f * s); lineTo(58f * s, 215f * s); lineTo(112f * s, 172f * s); close() }
                    drawPath(tail, Color.White)

                    val ping = seg(0.26f, 0.52f)
                    if (ping > 0f && ping < 1f)
                        drawCircle(ind.copy(alpha = (1f - ping) * .35f), (110f + 90f * ping) * s, Offset(130f * s, 115f * s), style = Stroke(3f * s))

                    val badge = overshoot.transform(seg(0.26f, 0.36f)) * (1f - seg(0.48f, 0.55f))
                    if (badge > 0f) {
                        drawCircle(Color(0xFF14B8A6), 17f * s * badge, Offset(232f * s, 58f * s))
                        drawCircle(Color.White, 6f * s * badge, Offset(232f * s, 58f * s))
                    }

                    if (arcs <= 0f && typing > 0f) for (i in 0..2) {
                        val y = 115f * s - sin(t * 38f - i * 0.9f).coerceAtLeast(0f) * 9f * s
                        drawCircle(ind.copy(alpha = .6f * typing * (1f - seg(0.47f, 0.51f))), 8f * s, Offset((94f + 36f * i) * s, y))
                    }

                    if (arcs > 0f) {
                        val cx = 130f * s; val cy = 138f * s
                        drawCircle(ind, 9f * s * arcs, Offset(cx, cy))
                        for (i in 0..2) {
                            val r = (26f + 20f * i) * s
                            val q = (arcs * 3.2f - i).coerceIn(0f, 1f)
                            drawArc(ind, 270f - 42f * q, 84f * q, false, Offset(cx - r, cy - r), Size(2f * r, 2f * r),
                                style = Stroke(11f * s, cap = StrokeCap.Round))
                        }
                    }
                }
            }
            Text("Yakın", color = Color(0xFF3730A3), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.graphicsLayer { alpha = word; translationY = (1f - word) * 40f })
            Text("Numarayla bul, her yerden konuş.", color = Color(0xFF6D28D9), fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = word; translationY = (1f - word) * 40f })
        }
    }
}
