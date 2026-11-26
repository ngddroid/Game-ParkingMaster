package com.ngstudio.inchperfect.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ic { GARAGE, STORE, PLAY, LEVELS, REWARDS, SETTINGS, BACK, LOCK, STAR, COIN, PAUSE, RESTART, EYE, REWIND, GHOST, SLOW, SOUND, MUSIC, VIBRATE, CLOSE, CHECK, GIFT, CROWN, TROPHY, CHART, CAR, PALETTE, SPARK, DOC, SHIELD, ROTATE, HORN, CHEST, TARGET, CALENDAR, WHEEL, PLUS, HOME, NEXT }

/** Hand-drawn vector icon set (24x24 grid) so the UI has its own consistent look. */
@Composable
fun Icon(ic: Ic, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIcon(ic, tint) }
}

fun DrawScope.drawIcon(ic: Ic, tint: Color) {
    val s = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    val stroke = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(block: Path.() -> Unit) = Path().apply(block)
    fun Path.m(x: Float, y: Float) = moveTo(x * s, y * s)
    fun Path.l(x: Float, y: Float) = lineTo(x * s, y * s)
    fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticTo(x1 * s, y1 * s, x * s, y * s)
    when (ic) {
        Ic.PLAY, Ic.NEXT -> drawPath(path { m(7f, 4.5f); l(19.5f, 12f); l(7f, 19.5f); close() }, tint)
        Ic.PAUSE -> { drawRoundRect(tint, o(6f, 4.5f), Size(4f * s, 15f * s), CornerRadius(1.5f * s)); drawRoundRect(tint, o(14f, 4.5f), Size(4f * s, 15f * s), CornerRadius(1.5f * s)) }
        Ic.RESTART -> {
            drawArc(tint, -60f, 290f, false, o(4.5f, 4.5f), Size(15f * s, 15f * s), style = stroke)
            drawPath(path { m(17.5f, 2.5f); l(18.5f, 8.5f); l(12.8f, 7.2f); close() }, tint)
        }
        Ic.BACK -> drawPath(path { m(15f, 4.5f); l(7.5f, 12f); l(15f, 19.5f) }, tint, style = stroke)
        Ic.CLOSE -> { drawLine(tint, o(6f, 6f), o(18f, 18f), stroke.width, StrokeCap.Round); drawLine(tint, o(18f, 6f), o(6f, 18f), stroke.width, StrokeCap.Round) }
        Ic.CHECK -> drawPath(path { m(5f, 12.5f); l(10f, 17.5f); l(19f, 6.5f) }, tint, style = stroke)
        Ic.PLUS -> { drawLine(tint, o(12f, 5f), o(12f, 19f), stroke.width, StrokeCap.Round); drawLine(tint, o(5f, 12f), o(19f, 12f), stroke.width, StrokeCap.Round) }
        Ic.LOCK -> {
            drawRoundRect(tint, o(5f, 10.5f), Size(14f * s, 10f * s), CornerRadius(2.5f * s))
            drawArc(tint, 180f, 180f, false, o(8f, 4f), Size(8f * s, 10f * s), style = stroke)
            drawCircle(Color.Black.copy(alpha = 0.45f), 1.6f * s, o(12f, 15.5f))
        }
        Ic.STAR -> {
            val p = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 10f else 4.4f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                val x = 12f + r * kotlin.math.cos(a).toFloat(); val y = 12.5f + r * kotlin.math.sin(a).toFloat()
                if (i == 0) p.moveTo(x * s, y * s) else p.lineTo(x * s, y * s)
            }
            p.close(); drawPath(p, tint)
        }
        Ic.COIN -> {
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFF1A6), Color(0xFFFFC21A), Color(0xFFB87A00)), o(4f, 4f), o(20f, 20f)), 9.5f * s, o(12f, 12f))
            drawCircle(Color(0xFFFFE27A), 6.4f * s, o(12f, 12f))
            drawCircle(Color(0xFFB87A00), 6.4f * s, o(12f, 12f), style = Stroke(1.2f * s))
            drawRoundRect(Color(0xFFB87A00), o(10.8f, 8f), Size(2.4f * s, 8f * s), CornerRadius(1f * s))
        }
        Ic.GARAGE, Ic.HOME -> {
            drawPath(path { m(3f, 11f); l(12f, 4f); l(21f, 11f); l(21f, 20f); l(3f, 20f); close() }, tint, style = stroke)
            drawRoundRect(tint, o(7.5f, 13f), Size(9f * s, 7f * s), CornerRadius(1f * s), style = Stroke(1.6f * s))
            drawLine(tint, o(7.5f, 15.5f), o(16.5f, 15.5f), 1.4f * s); drawLine(tint, o(7.5f, 18f), o(16.5f, 18f), 1.4f * s)
        }
        Ic.STORE -> {
            drawRoundRect(tint, o(4f, 8f), Size(16f * s, 12.5f * s), CornerRadius(2.5f * s), style = stroke)
            drawArc(tint, 180f, 180f, false, o(8f, 3.5f), Size(8f * s, 9f * s), style = stroke)
        }
        Ic.LEVELS -> {
            drawPath(path { m(4f, 20f); l(4f, 4f) }, tint, style = stroke)
            drawPath(path { m(4f, 4.5f); l(18f, 4.5f); l(15f, 8.5f); l(18f, 12.5f); l(4f, 12.5f); close() }, tint)
        }
        Ic.REWARDS, Ic.GIFT -> {
            drawRoundRect(tint, o(3.5f, 9f), Size(17f * s, 11.5f * s), CornerRadius(2f * s), style = stroke)
            drawLine(tint, o(12f, 9f), o(12f, 20.5f), stroke.width); drawLine(tint, o(3.5f, 13f), o(20.5f, 13f), stroke.width)
            drawPath(path { m(12f, 9f); q(6f, 2f, 7f, 7f); q(9f, 9f, 12f, 9f); q(15f, 9f, 17f, 7f); q(18f, 2f, 12f, 9f) }, tint, style = Stroke(1.8f * s))
        }
        Ic.SETTINGS -> {
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                drawLine(tint, o(12f + 6f * kotlin.math.cos(a).toFloat(), 12f + 6f * kotlin.math.sin(a).toFloat()), o(12f + 9.5f * kotlin.math.cos(a).toFloat(), 12f + 9.5f * kotlin.math.sin(a).toFloat()), 3.2f * s, StrokeCap.Round)
            }
            drawCircle(tint, 6.6f * s, o(12f, 12f), style = stroke)
            drawCircle(tint, 2.2f * s, o(12f, 12f))
        }
        Ic.EYE -> {
            drawPath(path { m(2f, 12f); q(12f, 2f, 22f, 12f); q(12f, 22f, 2f, 12f) }, tint, style = stroke)
            drawCircle(tint, 3.3f * s, o(12f, 12f))
        }
        Ic.REWIND -> {
            drawPath(path { m(11.5f, 6f); l(3.5f, 12f); l(11.5f, 18f); close() }, tint)
            drawPath(path { m(20.5f, 6f); l(12.5f, 12f); l(20.5f, 18f); close() }, tint)
        }
        Ic.GHOST -> {
            drawPath(path { m(5f, 20f); l(5f, 10f); q(5f, 3f, 12f, 3f); q(19f, 3f, 19f, 10f); l(19f, 20f); l(16.5f, 18f); l(14f, 20f); l(12f, 18f); l(10f, 20f); l(7.5f, 18f); close() }, tint)
            drawCircle(Color.Black.copy(alpha = 0.6f), 1.6f * s, o(9.5f, 10f)); drawCircle(Color.Black.copy(alpha = 0.6f), 1.6f * s, o(14.5f, 10f))
        }
        Ic.SLOW -> {
            drawCircle(tint, 9f * s, o(12f, 12.5f), style = stroke)
            drawLine(tint, o(12f, 12.5f), o(12f, 7.5f), stroke.width, StrokeCap.Round); drawLine(tint, o(12f, 12.5f), o(15.5f, 14.5f), stroke.width, StrokeCap.Round)
        }
        Ic.SOUND -> {
            drawPath(path { m(3f, 9f); l(7f, 9f); l(12f, 4.5f); l(12f, 19.5f); l(7f, 15f); l(3f, 15f); close() }, tint)
            drawArc(tint, -45f, 90f, false, o(10f, 7f), Size(8f * s, 10f * s), style = stroke)
            drawArc(tint, -50f, 100f, false, o(9f, 3.5f), Size(13f * s, 17f * s), style = stroke)
        }
        Ic.MUSIC -> {
            drawLine(tint, o(9f, 18f), o(9f, 5f), stroke.width); drawLine(tint, o(19f, 15f), o(19f, 3.5f), stroke.width)
            drawLine(tint, o(9f, 5f), o(19f, 3.5f), stroke.width * 1.2f)
            drawCircle(tint, 3f * s, o(6.5f, 18f)); drawCircle(tint, 3f * s, o(16.5f, 15f))
        }
        Ic.VIBRATE -> {
            drawRoundRect(tint, o(8f, 3.5f), Size(8f * s, 17f * s), CornerRadius(2f * s), style = stroke)
            drawLine(tint, o(4.5f, 8f), o(4.5f, 16f), stroke.width, StrokeCap.Round); drawLine(tint, o(19.5f, 8f), o(19.5f, 16f), stroke.width, StrokeCap.Round)
        }
        Ic.CROWN -> drawPath(path { m(3f, 18f); l(4.5f, 7f); l(9f, 11.5f); l(12f, 5f); l(15f, 11.5f); l(19.5f, 7f); l(21f, 18f); close() }, tint)
        Ic.TROPHY -> {
            drawPath(path { m(7f, 4f); l(17f, 4f); l(17f, 9f); q(17f, 14f, 12f, 14f); q(7f, 14f, 7f, 9f); close() }, tint)
            drawLine(tint, o(12f, 14f), o(12f, 18f), stroke.width); drawRoundRect(tint, o(7.5f, 18f), Size(9f * s, 3f * s), CornerRadius(1f * s))
            drawArc(tint, 90f, 180f, false, o(3f, 5f), Size(6f * s, 6f * s), style = Stroke(1.8f * s))
            drawArc(tint, -90f, 180f, false, o(15f, 5f), Size(6f * s, 6f * s), style = Stroke(1.8f * s))
        }
        Ic.CHART -> {
            drawRoundRect(tint, o(4f, 12f), Size(4f * s, 8f * s), CornerRadius(1f * s)); drawRoundRect(tint, o(10f, 7f), Size(4f * s, 13f * s), CornerRadius(1f * s))
            drawRoundRect(tint, o(16f, 3.5f), Size(4f * s, 16.5f * s), CornerRadius(1f * s))
        }
        Ic.CAR -> {
            drawRoundRect(tint, o(2.5f, 10f), Size(19f * s, 7f * s), CornerRadius(3f * s))
            drawPath(path { m(6f, 10.5f); l(8f, 5.5f); l(16f, 5.5f); l(18f, 10.5f) }, tint, style = stroke)
            drawCircle(Color.Black.copy(alpha = 0.5f), 2.2f * s, o(7f, 17f)); drawCircle(Color.Black.copy(alpha = 0.5f), 2.2f * s, o(17f, 17f))
        }
        Ic.PALETTE -> {
            drawCircle(tint, 9.5f * s, o(12f, 12f), style = stroke)
            for ((x, y) in listOf(8f to 8.5f, 13f to 6.5f, 16.5f to 10f, 7f to 13.5f)) drawCircle(tint, 1.8f * s, o(x, y))
        }
        Ic.SPARK -> drawPath(path { m(12f, 2f); l(14f, 10f); l(22f, 12f); l(14f, 14f); l(12f, 22f); l(10f, 14f); l(2f, 12f); l(10f, 10f); close() }, tint)
        Ic.DOC -> {
            drawRoundRect(tint, o(5f, 3f), Size(14f * s, 18f * s), CornerRadius(2f * s), style = stroke)
            for (y in listOf(8f, 12f, 16f)) drawLine(tint, o(8.5f, y), o(15.5f, y), 1.6f * s, StrokeCap.Round)
        }
        Ic.SHIELD -> {
            drawPath(path { m(12f, 2.5f); l(20f, 5.5f); l(20f, 11f); q(20f, 18f, 12f, 21.5f); q(4f, 18f, 4f, 11f); l(4f, 5.5f); close() }, tint, style = stroke)
            drawPath(path { m(8.5f, 12f); l(11f, 14.5f); l(15.5f, 9.5f) }, tint, style = stroke)
        }
        Ic.ROTATE -> {
            drawArc(tint, 20f, 300f, false, o(4f, 4f), Size(16f * s, 16f * s), style = stroke)
            drawPath(path { m(20.5f, 7f); l(20f, 13.5f); l(14.5f, 10.5f); close() }, tint)
        }
        Ic.HORN -> {
            drawPath(path { m(3f, 10f); l(9f, 10f); l(19f, 4f); l(19f, 20f); l(9f, 14f); l(3f, 14f); close() }, tint)
        }
        Ic.CHEST -> {
            drawRoundRect(tint, o(3f, 9f), Size(18f * s, 11f * s), CornerRadius(1.8f * s))
            drawPath(path { m(3f, 10f); q(3f, 4f, 12f, 4f); q(21f, 4f, 21f, 10f); close() }, tint.copy(alpha = 0.8f))
            drawRoundRect(Color.Black.copy(alpha = 0.45f), o(10.5f, 10f), Size(3f * s, 4.5f * s), CornerRadius(0.8f * s))
        }
        Ic.TARGET -> {
            drawCircle(tint, 9.5f * s, o(12f, 12f), style = stroke); drawCircle(tint, 5.5f * s, o(12f, 12f), style = stroke); drawCircle(tint, 1.8f * s, o(12f, 12f))
        }
        Ic.CALENDAR -> {
            drawRoundRect(tint, o(3.5f, 5f), Size(17f * s, 15.5f * s), CornerRadius(2f * s), style = stroke)
            drawLine(tint, o(3.5f, 9.5f), o(20.5f, 9.5f), stroke.width); drawLine(tint, o(8f, 3f), o(8f, 6.5f), stroke.width, StrokeCap.Round); drawLine(tint, o(16f, 3f), o(16f, 6.5f), stroke.width, StrokeCap.Round)
            drawCircle(tint, 1.6f * s, o(12f, 15f))
        }
        Ic.WHEEL -> {
            drawCircle(tint, 9.5f * s, o(12f, 12f), style = stroke)
            drawCircle(tint, 2.8f * s, o(12f, 12f))
            drawLine(tint, o(3f, 12f), o(9f, 12f), stroke.width); drawLine(tint, o(15f, 12f), o(21f, 12f), stroke.width); drawLine(tint, o(12f, 15f), o(12f, 21f), stroke.width)
        }
    }
}
