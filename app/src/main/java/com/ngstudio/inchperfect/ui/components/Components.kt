package com.ngstudio.inchperfect.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.Haptics
import com.ngstudio.inchperfect.game.render.CarLook
import com.ngstudio.inchperfect.game.render.Sprites
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type
import kotlin.math.sin

fun click(s: Sfx = Sfx.CLICK) { Audio.play(s); Haptics.light() }

fun Color.darker(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
fun Color.lighter(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

/** Chunky 3D game button with a lip, gloss and spring press. */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current.secondary,
    textColor: Color = Color.White,
    icon: Ic? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    style: TextStyle = Type.Button,
    shimmer: Boolean = false,
    sound: Sfx = Sfx.CLICK,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press")
    val base = if (enabled) color else Color(0xFF3A4060)
    val lip = 6.dp
    val inf = rememberInfiniteTransition(label = "shine")
    val shine by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "shine")
    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = 1f - 0.03f * press; scaleY = 1f - 0.03f * press }
            .clickable(src, null, enabled = enabled) { click(sound); onClick() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(size.height * 0.32f)
            val lipPx = lip.toPx() * (1f - press * 0.8f)
            // shadow + lip
            drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(0f, lip.toPx() + 3f), Size(size.width, size.height - lip.toPx()), r)
            drawRoundRect(base.darker(0.42f), Offset(0f, lip.toPx()), Size(size.width, size.height - lip.toPx()), r)
            val top = lip.toPx() - lipPx
            drawRoundRect(Brush.verticalGradient(listOf(base.lighter(0.28f), base, base.darker(0.12f)), top, top + size.height - lip.toPx()), Offset(0f, top), Size(size.width, size.height - lip.toPx()), r)
            // gloss
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.04f)), top, top + (size.height - lip.toPx()) * 0.5f),
                Offset(size.height * 0.15f, top + 3f), Size(size.width - size.height * 0.3f, (size.height - lip.toPx()) * 0.42f), CornerRadius(size.height * 0.2f))
            if (shimmer && enabled) {
                val x = size.width * shine
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(x - 60f, 0f), Offset(x + 60f, size.height)),
                    Offset(maxOf(0f, x - 60f), top), Size(minOf(120f, size.width - maxOf(0f, x - 60f)).coerceAtLeast(0f), size.height - lip.toPx()))
            }
        }
        Row(
            Modifier.fillMaxSize().padding(bottom = lip).offset(y = (lip.value * press).dp * 0.8f).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { Icon(icon, textColor, 22.dp); if (text.isNotEmpty()) Spacer(Modifier.width(8.dp)) }
            if (text.isNotEmpty()) Text(text, style = style.copy(color = textColor, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 2f)), maxLines = 1)
        }
    }
}

/** Round icon button (HUD and top bars). */
@Composable
fun RoundButton(ic: Ic, modifier: Modifier = Modifier, size: Dp = 46.dp, tint: Color = Palette.Text, badge: String? = null, accent: Color = LocalAccent.current.primary, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f), label = "rb")
    Box(modifier.size(size).graphicsLayer { scaleX = press; scaleY = press; alpha = if (enabled) 1f else 0.45f }.clickable(src, null, enabled) { click(); onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val rr = drawContext.size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = 0.35f), rr, center + Offset(0f, 4f))
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF2A3360), Color(0xFF141A36))), rr)
            drawCircle(accent.copy(alpha = 0.55f), rr - 1.5f, style = Stroke(2.5f))
        }
        Icon(ic, tint, size * 0.48f)
        if (badge != null) {
            Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).background(Palette.Orange, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(badge, style = Type.Small.copy(color = Color.White, fontSize = 10.sp))
            }
        }
    }
}

/** Translucent glass panel with a soft accent edge. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, glow: Color = LocalAccent.current.primary, corner: Dp = 22.dp, padding: Dp = 14.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.verticalGradient(listOf(Color(0xE61A2248), Color(0xE60E1330))))
            .border(1.dp, Brush.linearGradient(listOf(glow.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f), glow.copy(alpha = 0.2f))), RoundedCornerShape(corner))
            .padding(padding),
        content = content,
    )
}

@Composable
fun CurrencyPill(ic: Ic, value: Int, modifier: Modifier = Modifier, color: Color = Palette.Gold, onPlus: (() -> Unit)? = null) {
    val anim = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xCC0B1026)).border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp)).padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(ic, color, 24.dp) }
        Spacer(Modifier.width(6.dp))
        Text("%,d".format(anim.value.toInt()), style = Type.Number.copy(color = Palette.Text, fontSize = 15.sp))
        if (onPlus != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(22.dp).background(Palette.Lime, CircleShape).clickable { click(); onPlus() }, contentAlignment = Alignment.Center) { Icon(Ic.PLUS, Palette.Midnight, 14.dp) }
        }
    }
}

@Composable
fun StarRow(stars: Int, size: Dp = 16.dp, max: Int = 3, spacing: Dp = 2.dp, dim: Color = Color(0x553A4370)) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (i in 0 until max) Icon(Ic.STAR, if (i < stars) Palette.Gold else dim, size)
    }
}

/** Animated night-city backdrop: gradient sky, twinkling windows, light trails. */
@Composable
fun CityBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val acc = LocalAccent.current
    val inf = rememberInfiniteTransition(label = "bg")
    val t by inf.animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "t")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(acc.bgTop, acc.bgBottom)))
        // glow blobs
        drawCircle(Brush.radialGradient(listOf(acc.primary.copy(alpha = 0.08f * intensity), Color.Transparent), Offset(size.width * 0.18f, size.height * 0.3f), size.height * 0.7f), size.height * 0.7f, Offset(size.width * 0.18f, size.height * 0.3f))
        drawCircle(Brush.radialGradient(listOf(acc.secondary.copy(alpha = 0.06f * intensity), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.75f), size.height * 0.7f), size.height * 0.7f, Offset(size.width * 0.85f, size.height * 0.75f))
        // waterfront / road below the skyline with soft reflections
        val horizon = size.height * 0.62f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0C1124), Color(0xFF070A16)), horizon, size.height), Offset(0f, horizon), Size(size.width, size.height - horizon))
        // skyline
        val rnd = java.util.Random(4)
        var x = -20f
        while (x < size.width) {
            val w = 40f + rnd.nextFloat() * 70f; val h = size.height * (0.12f + rnd.nextFloat() * 0.3f)
            drawRect(Color(0xFF0A0E22).copy(alpha = 0.92f), Offset(x, horizon - h), Size(w, h))
            var wy = horizon - h + 10f
            while (wy < horizon - 8f) {
                var wx = x + 6f
                while (wx < x + w - 8f) {
                    val k = rnd.nextFloat()
                    if (k > 0.55f) {
                        val tw = 0.5f + 0.5f * sin(t * (0.5f + k * 2f) + wx * 0.1f)
                        drawRect(Color(0xFFFFD98A).copy(alpha = (0.15f + 0.45f * tw) * intensity), Offset(wx, wy), Size(5f, 7f))
                    }
                    wx += 12f
                }
                wy += 14f
            }
            x += w + 4f
        }
        // light trails on the road
        for (i in 0 until 6) {
            val ph = ((t * (0.18f + i * 0.03f) + i * 0.37f) % 1f)
            val yy = horizon + 18f + i * 6f
            val xx = -200f + (size.width + 400f) * ph
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, (if (i % 2 == 0) acc.secondary else Palette.Red).copy(alpha = 0.7f * intensity)), xx - 160f, xx), Offset(xx - 160f, yy), Offset(xx, yy), 3f)
        }
    }
}

/** Renders a player car with the game's procedural sprite painter. */
@Composable
fun CarPreview(look: CarLook, lengthM: Double, widthM: Double, modifier: Modifier = Modifier, rotation: Float = 0f, glow: Int = 0, scaleM: Float = 1f) {
    Canvas(modifier) {
        val ppm = minOf(size.width / (lengthM.toFloat() * 1.25f), size.height / (widthM.toFloat() * 2.4f)) * scaleM
        if (glow != 0) {
            val g = Color(glow.toLong() or 0xFF000000)
            drawOval(Brush.radialGradient(listOf(g.copy(alpha = 0.55f), Color.Transparent), center, lengthM.toFloat() * ppm * 0.65f), Offset(center.x - lengthM.toFloat() * ppm * 0.7f, center.y - widthM.toFloat() * ppm * 1.1f), Size(lengthM.toFloat() * ppm * 1.4f, widthM.toFloat() * ppm * 2.2f))
        }
        rotate(rotation) {
            drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(center.x - lengthM.toFloat() * ppm / 2 + 6f, center.y - widthM.toFloat() * ppm / 2 + 10f), Size(lengthM.toFloat() * ppm, widthM.toFloat() * ppm), CornerRadius(widthM.toFloat() * ppm * 0.3f))
            drawContext.canvas.nativeCanvas.apply {
                save(); translate(center.x, center.y)
                Sprites.paintCar(this, look, lengthM.toFloat() * ppm, widthM.toFloat() * ppm)
                restore()
            }
        }
    }
}

@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier, color: Color = LocalAccent.current.primary, height: Dp = 10.dp) {
    val v by animateFloatAsState(value.coerceIn(0f, 1f), tween(600), label = "pb")
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(Color(0xFF0A0E22))) {
        Box(Modifier.fillMaxWidth(v).height(height).clip(RoundedCornerShape(height / 2)).background(Brush.horizontalGradient(listOf(color.darker(0.2f), color.lighter(0.2f)))))
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val acc = LocalAccent.current
    val x by animateFloatAsState(if (on) 1f else 0f, spring(0.6f), label = "tg")
    Box(Modifier.width(54.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) acc.primary.copy(alpha = 0.85f) else Color(0xFF2A3154)).clickable { click(); onChange(!on) }) {
        Box(Modifier.padding(3.dp).offset(x = (24 * x).dp).size(24.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun SliderRow(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    val acc = LocalAccent.current
    var width = 1f
    Box(
        modifier.height(30.dp).pointerInput(range) {
            width = size.width.toFloat()
            detectHorizontalDragGestures { change, _ ->
                val t = (change.position.x / size.width).coerceIn(0f, 1f)
                onChange(range.start + (range.endInclusive - range.start) * t)
            }
        }.drawBehind {
            val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val y = size.height / 2
            drawRoundRect(Color(0xFF0A0E22), Offset(0f, y - 5f), Size(size.width, 10f), CornerRadius(5f))
            drawRoundRect(Brush.horizontalGradient(listOf(acc.primary.darker(0.3f), acc.primary)), Offset(0f, y - 5f), Size(size.width * t, 10f), CornerRadius(5f))
            drawCircle(Color.White, 12f * density / 2.2f + 6f, Offset(size.width * t, y))
            drawCircle(acc.primary, 6f, Offset(size.width * t, y))
        },
    )
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val acc = LocalAccent.current
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A0E22)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((v, label) in options) {
            val sel = v == selected
            Box(
                Modifier.clip(RoundedCornerShape(11.dp)).background(if (sel) acc.primary else Color.Transparent).clickable { click(); onSelect(v) }.padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Type.Small.copy(color = if (sel) Palette.Midnight else Palette.TextDim, fontSize = 12.sp)) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = LocalAccent.current.primary) {
    Text(text.uppercase(), style = Type.Section.copy(color = color, fontSize = 13.sp, letterSpacing = 2.sp), modifier = modifier)
}

/** Full-screen dim + centred panel used by every game dialog. */
@Composable
fun GameDialog(onDismiss: (() -> Unit)?, modifier: Modifier = Modifier, glow: Color = LocalAccent.current.primary, content: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(0.62f, Spring.StiffnessMediumLow)) }
    Box(
        Modifier.fillMaxSize().background(Color(0xB3050816)).clickable(remember { MutableInteractionSource() }, null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier.graphicsLayer { scaleX = 0.7f + 0.3f * appear.value; scaleY = 0.7f + 0.3f * appear.value; alpha = appear.value.coerceIn(0f, 1f) }
            .clickable(remember { MutableInteractionSource() }, null) {}) {
            GlassPanel(glow = glow, padding = 22.dp) { content() }
        }
    }
}

@Composable
fun Label(text: String, style: TextStyle = Type.Body, color: Color = Palette.Text, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, style = style.copy(color = color), modifier = modifier, textAlign = align)
}

@Composable
fun Pulse(content: @Composable (Float) -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val v by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    content(v)
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun ColumnCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }
}
