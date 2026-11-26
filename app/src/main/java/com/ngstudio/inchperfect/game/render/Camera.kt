package com.ngstudio.inchperfect.game.render

import android.graphics.Canvas
import android.graphics.RectF
import com.ngstudio.inchperfect.game.level.Level
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * World -> screen camera. Frames content inside [safe] (the part of the screen not covered by
 * controls/HUD) so the bay is never hidden behind the steering wheel.
 */
class Camera {
    var x = 0.0; var y = 0.0
    var zoom = 50.0            // pixels per metre
    var viewW = 1f; var viewH = 1f
    val safe = RectF()
    var baseZoom = 50.0
    var userZoom = 1.0

    enum class Mode { INTRO, FOLLOW, PEEK, CRASH, PARKED }
    var mode = Mode.FOLLOW
    private var introT = 0.0
    var introDuration = 2.4
    private var shake = 0.0
    private var shakeT = 0.0
    var shakeX = 0f; var shakeY = 0f

    // intro path
    private var ix0 = 0.0; private var iy0 = 0.0; private var iz0 = 0.0

    fun setViewport(w: Int, h: Int, safeRect: RectF) {
        viewW = w.toFloat(); viewH = h.toFloat(); safe.set(safeRect)
        baseZoom = h / 12.0 * userZoom
    }

    fun fitZoom(w: Double, h: Double) = min(safe.width() / w, safe.height() / h)

    fun startIntro(level: Level, carX: Double, carY: Double) {
        mode = Mode.INTRO
        introT = 0.0
        ix0 = level.bay.cx; iy0 = level.bay.cy
        val dx = abs(carX - ix0) + 14; val dy = abs(carY - iy0) + 10
        iz0 = min(baseZoom * 1.05, max(baseZoom * 0.45, fitZoom(dx, dy)))
        x = ix0; y = iy0; zoom = baseZoom * 1.15
    }

    fun skipIntro() { if (mode == Mode.INTRO) introT = introDuration }

    val introProgress get() = (introT / introDuration).coerceIn(0.0, 1.0)

    fun shake(amount: Double) { shake = max(shake, amount); shakeT = 0.0 }

    fun update(dt: Double, level: Level, carX: Double, carY: Double, heading: Double, speed: Double, peek: Boolean) {
        shakeT += dt
        shake *= exp(-6.0 * dt)
        shakeX = (shake * kotlin.math.sin(shakeT * 47.0) * zoom * 0.12).toFloat()
        shakeY = (shake * kotlin.math.cos(shakeT * 39.0) * zoom * 0.12).toFloat()

        if (mode == Mode.INTRO) {
            introT += dt
            val t = (introT / introDuration).coerceIn(0.0, 1.0)
            // ease: linger on the bay, sweep, settle on the car
            val e = smooth(((t - 0.25) / 0.75).coerceIn(0.0, 1.0))
            val zT = if (t < 0.25) baseZoom * 1.15 + (iz0 - baseZoom * 1.15) * smooth(t / 0.25) else iz0 + (baseZoom - iz0) * e
            x = ix0 + (carX - ix0) * e
            y = iy0 + (carY - iy0) * e
            zoom = zT
            if (t >= 1.0) mode = Mode.FOLLOW
            return
        }
        var tx: Double; var ty: Double; var tz: Double
        if (peek || mode == Mode.PEEK) {
            tx = (level.minX + level.maxX) / 2; ty = (level.minY + level.maxY) / 2
            tz = fitZoom(level.width + 2, level.height + 2).coerceAtMost(baseZoom)
        } else {
            val look = (speed * 0.55).coerceIn(-2.5, 3.0)
            val cx = carX + kotlin.math.cos(heading) * look
            val cy = carY + kotlin.math.sin(heading) * look
            val bx = level.bay.cx; val by = level.bay.cy
            val d = hypot(bx - carX, by - carY)
            val w = ((1 - d / 24.0).coerceIn(0.0, 1.0)) * 0.45
            tx = cx + (bx - cx) * w; ty = cy + (by - cy) * w
            tz = baseZoom * (1 - 0.1 * (abs(speed) / 7.0).coerceIn(0.0, 1.0))
            if (d < 20) tz = min(tz, max(baseZoom * 0.68, fitZoom(abs(bx - carX) + 9, abs(by - carY) + 7)))
            if (mode == Mode.CRASH) tz = baseZoom * 1.12
            if (mode == Mode.PARKED) { tx = bx; ty = by; tz = baseZoom * 1.1 }
        }
        val k = 1 - exp(-(if (peek) 5.0 else 3.6) * dt)
        x += (tx - x) * k; y += (ty - y) * k
        zoom += (tz - zoom) * (1 - exp(-2.6 * dt))
    }

    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    /** Applies world transform (metres -> pixels) to the canvas. */
    fun apply(c: Canvas) {
        c.translate(safe.centerX() + shakeX, safe.centerY() + shakeY)
        c.scale(zoom.toFloat(), zoom.toFloat())
        c.translate(-x.toFloat(), -y.toFloat())
    }

    fun sx(wx: Double) = (safe.centerX() + (wx - x) * zoom + shakeX).toFloat()
    fun sy(wy: Double) = (safe.centerY() + (wy - y) * zoom + shakeY).toFloat()
    fun wx(sx: Float) = x + (sx - safe.centerX() - shakeX) / zoom
    fun wy(sy: Float) = y + (sy - safe.centerY() - shakeY) / zoom

    /** visible world rect (with margin) for culling */
    fun visible(out: DoubleArray, margin: Double = 2.0) {
        out[0] = wx(0f) - margin; out[1] = wy(0f) - margin
        out[2] = wx(viewW) + margin; out[3] = wy(viewH) + margin
    }
}
