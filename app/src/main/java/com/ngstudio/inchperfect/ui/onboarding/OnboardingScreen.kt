package com.ngstudio.inchperfect.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.Economy
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.game.input.Scheme
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.render.CarLook
import com.ngstudio.inchperfect.game.render.Finish
import com.ngstudio.inchperfect.ui.components.CarPreview
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.CityBackground
import com.ngstudio.inchperfect.ui.components.Segmented
import com.ngstudio.inchperfect.ui.components.drawIcon
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private data class Page(val tag: String, val title: String, val body: String)

private val PAGES = listOf(
    Page("01 · CONTROLS", "Take the wheel", "Steer, shift between D and R, and work the pedals. Real driving, built for your thumbs."),
    Page("02 · PRECISION", "Every inch counts", "Sensors beep as you close in. Park dead centre for an INCH PERFECT bonus."),
    Page("03 · GARAGE", "Earn, collect, customise", "Win coins and stars to unlock 12 cars, paint finishes, wraps and underglow."),
    Page("04 · THE CITY", "10,000 levels · 20 districts", "From sunny suburbs to midnight tunnels. Every level is tested and solvable."),
)

/** First-launch tutorial. There is no skip button: the player taps through all four pages. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val acc = LocalAccent.current
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    Box(Modifier.fillMaxSize()) {
        CityBackground()
        Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxHeight()) { i ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Illustration(i) }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        val p = PAGES[i]
                        Text(p.tag, style = Type.Small.copy(color = acc.primary, letterSpacing = 3.sp, fontSize = 12.sp))
                        Spacer(Modifier.height(6.dp))
                        Text(p.title.uppercase(), style = Type.Hero.copy(color = Palette.Text, fontSize = 32.sp, lineHeight = 36.sp))
                        Spacer(Modifier.height(10.dp))
                        Text(p.body, style = Type.Body.copy(color = Palette.TextDim, fontSize = 17.sp, lineHeight = 24.sp))
                        if (i == 0) {
                            Spacer(Modifier.height(16.dp))
                            val s by GameRepo.settings.collectAsState()
                            Text("CHOOSE YOUR CONTROLS", style = Type.Small.copy(color = Palette.TextDim))
                            Spacer(Modifier.height(6.dp))
                            Segmented(Scheme.entries.map { it to it.title }, s.scheme, { sc -> GameRepo.updateSettings { it.copy(scheme = sc) } })
                        }
                    }
                }
            }
        }
        // bottom bar: dots + next
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in PAGES.indices) {
                    val w by animateDpAsState(if (i == pager.currentPage) 28.dp else 10.dp, label = "dot")
                    Box(Modifier.width(w).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (i == pager.currentPage) acc.primary else Color(0x553A4370)))
                }
            }
            Spacer(Modifier.width(24.dp))
            val last = pager.currentPage == PAGES.size - 1
            ChunkyButton(if (last) "START ENGINE" else "NEXT", Modifier.width(if (last) 240.dp else 180.dp), color = if (last) Palette.Lime else acc.secondary,
                textColor = if (last) Palette.Midnight else Color.White, icon = Ic.NEXT, shimmer = last, sound = if (last) Sfx.UNLOCK else Sfx.CLICK) {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun Illustration(i: Int) {
    val acc = LocalAccent.current
    val inf = rememberInfiniteTransition(label = "ill")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t")
    val swing by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    GlassPanel(Modifier.size(330.dp, 250.dp), padding = 0.dp) {
        when (i) {
            0 -> Canvas(Modifier.fillMaxSize()) {
                // wheel
                val c = Offset(size.width * 0.27f, size.height * 0.58f); val r = size.height * 0.27f
                rotate(swing * 110f, c) {
                    drawCircle(Color(0xFF2A2F3A), r, c, style = Stroke(r * 0.22f))
                    drawCircle(acc.primary, r * 0.84f, c, style = Stroke(3f))
                    drawArc(acc.secondary, -97f, 14f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.22f))
                    for (a in listOf(0f, 180f, 90f)) rotate(a, c) { drawLine(Color(0xFF3A4150), c, Offset(c.x + r * 0.85f, c.y), r * 0.2f, StrokeCap.Round) }
                    drawCircle(Color(0xFF1A1E26), r * 0.3f, c)
                }
                // pedals
                for ((k, col) in listOf(0 to Palette.Red, 1 to Palette.Lime)) {
                    val pressed = if (k == 1) t < 0.5f else t >= 0.5f
                    val x = size.width * (0.62f + k * 0.17f); val h = if (k == 1) size.height * 0.42f else size.height * 0.26f
                    val y = size.height * 0.82f - h
                    drawRoundRect(Color(0xFF2E333D), Offset(x, y + if (pressed) 4f else 0f), Size(size.width * 0.13f, h), CornerRadius(14f))
                    drawRoundRect(col.copy(alpha = if (pressed) 1f else 0.3f), Offset(x, y + if (pressed) 4f else 0f), Size(size.width * 0.13f, h), CornerRadius(14f), style = Stroke(4f))
                }
            }
            1 -> Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val bw = size.width * 0.62f; val bh = size.height * 0.5f
                    val o = Offset((size.width - bw) / 2, (size.height - bh) / 2)
                    drawRoundRect(Palette.Lime.copy(alpha = 0.12f), o, Size(bw, bh), CornerRadius(12f))
                    drawRoundRect(Palette.Lime, o, Size(bw, bh), CornerRadius(12f), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f), -t * 200f)))
                    // sensor arcs
                    val cx = size.width * 0.5f + bw * 0.42f; val cy = size.height / 2
                    for (k in 0 until 3) {
                        val on = (t * 3).toInt() >= k
                        drawArc(listOf(Palette.Lime, Palette.Gold, Palette.Red)[k].copy(alpha = if (on) 1f else 0.2f), -30f, 60f, false,
                            Offset(cx - 30f - k * 16f, cy - 30f - k * 16f), Size(60f + k * 32f, 60f + k * 32f), style = Stroke(6f, cap = StrokeCap.Round))
                    }
                }
                CarPreview(Cars.byId("kestrel").let { CarLook(it.design, 0x19E3FF, Finish.METALLIC, axleR = it.axleRear, axleF = it.axleFront) }, 4.3, 1.85, Modifier.fillMaxSize().padding(40.dp))
                Text("${(90 + (t * 10).toInt()).coerceAtMost(99)}%", style = Type.Title.copy(color = Palette.Gold), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
            2 -> Box(Modifier.fillMaxSize()) {
                val idx = (t * Economy.PAINTS.size).toInt() % Economy.PAINTS.size
                val showcase = listOf("arrowhead", "bruiser", "photon", "glacier", "sundae", "nimbus")
                val sp = Cars.byId(showcase[idx % showcase.size])
                CarPreview(CarLook(sp.design, Economy.PAINTS[idx], Finish.entries[idx % Finish.entries.size], axleR = sp.axleRear, axleF = sp.axleFront), sp.length, sp.width, Modifier.fillMaxSize().padding(30.dp), glow = 0x19E3FF)
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 0 until 6) {
                        val ph = (t * 1.7f + k / 6f) % 1f
                        val x = size.width * (0.1f + 0.8f * ((k * 37) % 10) / 10f); val y = size.height * (1f - ph)
                        translate(x - 14f, y - 14f) { drawIcon(Ic.COIN, Palette.Gold) }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val cols = 5; val rows = 4
                    for (k in 0 until cols * rows) {
                        val x = size.width * (0.12f + 0.76f * (k % cols) / (cols - 1)); val y = size.height * (0.15f + 0.6f * (k / cols) / (rows - 1))
                        val lit = (t * cols * rows).toInt() >= k
                        if (k > 0) {
                            val px = size.width * (0.12f + 0.76f * ((k - 1) % cols) / (cols - 1)); val py = size.height * (0.15f + 0.6f * ((k - 1) / cols) / (rows - 1))
                            drawLine(acc.primary.copy(alpha = if (lit) 0.6f else 0.12f), Offset(px, py), Offset(x, y), 3f)
                        }
                        drawCircle(if (lit) acc.primary else Color(0xFF2A3154), if (k % 5 == 4) 13f else 8f, Offset(x, y))
                    }
                }
                Text("%,d".format((t * 10_000).toInt().coerceAtLeast(1)), style = Type.Hero.copy(color = Palette.Text, fontSize = 34.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
        }
    }
}
