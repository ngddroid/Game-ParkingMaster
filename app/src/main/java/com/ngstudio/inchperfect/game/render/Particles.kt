package com.ngstudio.inchperfect.game.render

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/** Pooled particles in world space (metres). */
class Particles(private val max: Int = 700) {
    enum class Kind { SMOKE, DUST, SPARK, DEBRIS, SPARKLE, SPLASH, CONFETTI, EXHAUST, SNOWPUFF }

    private val kind = IntArray(max)
    private val x = FloatArray(max); private val y = FloatArray(max)
    private val vx = FloatArray(max); private val vy = FloatArray(max)
    private val life = FloatArray(max); private val maxLife = FloatArray(max)
    private val size = FloatArray(max); private val rot = FloatArray(max); private val spin = FloatArray(max)
    private val color = IntArray(max)
    private var count = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = java.util.Random(3)

    fun clear() { count = 0 }

    fun emit(k: Kind, px: Double, py: Double, pvx: Double, pvy: Double, lifeS: Float, sz: Float, col: Int) {
        if (count >= max) return
        val i = count++
        kind[i] = k.ordinal; x[i] = px.toFloat(); y[i] = py.toFloat(); vx[i] = pvx.toFloat(); vy[i] = pvy.toFloat()
        life[i] = lifeS; maxLife[i] = lifeS; size[i] = sz; color[i] = col
        rot[i] = rnd.nextFloat() * 6.28f; spin[i] = (rnd.nextFloat() - 0.5f) * 10f
    }

    fun burst(k: Kind, px: Double, py: Double, n: Int, speed: Double, lifeS: Float, sz: Float, col: Int, spread: Double = 6.283, dir: Double = 0.0) {
        repeat(n) {
            val a = dir + (rnd.nextDouble() - 0.5) * spread
            val s = speed * (0.35 + rnd.nextDouble() * 0.9)
            emit(k, px, py, cos(a) * s, sin(a) * s, lifeS * (0.6f + rnd.nextFloat() * 0.6f), sz * (0.6f + rnd.nextFloat() * 0.8f), col)
        }
    }

    fun random() = rnd.nextFloat()

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0) {
                val last = --count
                kind[i] = kind[last]; x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]; rot[i] = rot[last]; spin[i] = spin[last]; color[i] = color[last]
                continue
            }
            val drag = when (Kind.entries[kind[i]]) { Kind.SMOKE, Kind.EXHAUST, Kind.DUST, Kind.SNOWPUFF -> 1.8f; Kind.CONFETTI -> 1.2f; else -> 3.5f }
            val f = (1 - drag * dt).coerceAtLeast(0f)
            vx[i] *= f; vy[i] *= f
            x[i] += vx[i] * dt; y[i] += vy[i] * dt
            rot[i] += spin[i] * dt
            i++
        }
    }

    /** draws in world coordinates (canvas already transformed) */
    fun draw(c: Canvas) {
        for (i in 0 until count) {
            val t = life[i] / maxLife[i] // 1 -> 0
            val k = Kind.entries[kind[i]]
            val col = color[i]
            when (k) {
                Kind.SMOKE, Kind.EXHAUST, Kind.DUST, Kind.SNOWPUFF -> {
                    val grow = size[i] * (1.6f - t * 0.9f)
                    p.color = col; p.alpha = ((col ushr 24) * t * 0.8f).toInt()
                    c.drawCircle(x[i], y[i], grow, p)
                }
                Kind.SPARK -> {
                    p.color = col; p.alpha = (255 * t).toInt(); p.strokeWidth = size[i] * 0.4f
                    c.drawLine(x[i], y[i], x[i] - vx[i] * 0.04f, y[i] - vy[i] * 0.04f, p)
                }
                Kind.DEBRIS, Kind.CONFETTI -> {
                    p.color = col; p.alpha = (255 * minOf(1f, t * 2.5f)).toInt()
                    c.save(); c.translate(x[i], y[i]); c.rotate(rot[i] * 57.3f)
                    val w = size[i]; val h = size[i] * (0.4f + 0.6f * kotlin.math.abs(sin(rot[i] * 2)))
                    c.drawRect(-w / 2, -h / 2, w / 2, h / 2, p)
                    c.restore()
                }
                Kind.SPARKLE -> {
                    p.color = col; p.alpha = (255 * t).toInt(); p.strokeWidth = size[i] * 0.25f
                    val s = size[i] * (0.5f + t)
                    c.drawLine(x[i] - s, y[i], x[i] + s, y[i], p); c.drawLine(x[i], y[i] - s, x[i], y[i] + s, p)
                }
                Kind.SPLASH -> {
                    p.style = Paint.Style.STROKE; p.color = col; p.alpha = (200 * t).toInt(); p.strokeWidth = 0.03f
                    c.drawCircle(x[i], y[i], size[i] * (1.4f - t), p); p.style = Paint.Style.FILL
                }
            }
        }
        p.alpha = 255
    }
}

/** Persistent tyre marks (world space ring buffer). */
class SkidMarks(private val max: Int = 900) {
    private val seg = FloatArray(max * 4)
    private val alpha = FloatArray(max)
    private var head = 0
    private var size = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    var color = 0xFF16171A.toInt()

    fun clear() { size = 0; head = 0 }

    fun add(x1: Double, y1: Double, x2: Double, y2: Double, a: Float) {
        val i = head
        seg[i * 4] = x1.toFloat(); seg[i * 4 + 1] = y1.toFloat(); seg[i * 4 + 2] = x2.toFloat(); seg[i * 4 + 3] = y2.toFloat()
        alpha[i] = a
        head = (head + 1) % max
        if (size < max) size++
    }

    fun draw(c: Canvas, width: Float) {
        p.strokeWidth = width
        p.color = color
        for (k in 0 until size) {
            val i = (head - 1 - k + max) % max
            p.alpha = (alpha[i] * 255).toInt().coerceIn(0, 255)
            c.drawLine(seg[i * 4], seg[i * 4 + 1], seg[i * 4 + 2], seg[i * 4 + 3], p)
        }
    }
}
