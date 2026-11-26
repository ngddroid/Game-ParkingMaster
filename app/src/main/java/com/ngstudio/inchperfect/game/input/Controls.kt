package com.ngstudio.inchperfect.game.input

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.MotionEvent
import com.ngstudio.inchperfect.game.ControlState
import kotlin.math.atan2
import kotlin.math.hypot

enum class Scheme(val id: String, val title: String) {
    WHEEL("wheel", "Wheel + Pedals"), ARROWS("arrows", "Arrow Buttons"), TILT("tilt", "Tilt to Steer");
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: WHEEL }
}

data class ControlsConfig(
    val scheme: Scheme = Scheme.WHEEL,
    val leftHanded: Boolean = false,
    val size: Float = 1f,
    val opacity: Float = 0.92f,
    val sensitivity: Float = 1f,
    val autoCenter: Boolean = true,
    val wheel: WheelDesign = Wheels.ALL[0],
)

/** In-engine touch controls (screen space). Touch events arrive on the UI thread, drawing happens on the game thread. */
class Controls(private val st: ControlState, private val display: Typeface, var cfg: ControlsConfig, private val feedback: (Int) -> Unit) {
    // geometry
    @Volatile var wheelCx = 0f; @Volatile var wheelCy = 0f; @Volatile var wheelR = 0f
    val gas = RectF(); val brake = RectF(); val gear = RectF(); val left = RectF(); val right = RectF(); val rev = RectF()
    val safe = RectF()
    private var w = 0; private var h = 0

    // wheel state
    @Volatile var wheelDeg = 0f
    private val maxDeg get() = 420f / cfg.sensitivity
    private var wheelPointer = -1
    private var lastAngle = 0f
    private var gasPointer = -1; private var brakePointer = -1; private var gearPointer = -1
    private var leftPointer = -1; private var rightPointer = -1; private var revPointer = -1
    private var gearDownY = 0f
    @Volatile var tilt = 0f
    @Volatile var keySteer = 0
    private var arrowSteer = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textAlign = Paint.Align.CENTER }
    private val path = Path()
    private var gasAnim = 0f; private var brakeAnim = 0f; private var gearAnim = 0f

    fun layout(width: Int, height: Int, insetL: Int, insetR: Int, insetB: Int) {
        w = width; h = height
        val s = cfg.size
        val pad = height * 0.045f
        val r = height * 0.2f * s
        val flip = cfg.leftHanded
        fun mx(x: Float) = if (flip) width - x else x
        fun mrect(out: RectF, l: Float, t: Float, rr: Float, b: Float) { if (flip) out.set(width - rr, t, width - l, b) else out.set(l, t, rr, b) }
        val bottom = height - pad - insetB * 0.3f
        val leftEdge = pad + insetL
        val rightEdge = width - pad - insetR
        when (cfg.scheme) {
            Scheme.WHEEL, Scheme.TILT -> {
                wheelR = if (cfg.scheme == Scheme.WHEEL) r else 0f
                wheelCx = mx(leftEdge + r); wheelCy = bottom - r
                val gw = height * 0.085f * s; val gh = height * 0.3f * s
                val gx = if (cfg.scheme == Scheme.WHEEL) leftEdge + r * 2 + pad * 0.6f else leftEdge
                mrect(gear, gx, bottom - gh, gx + gw, bottom)
            }
            Scheme.ARROWS -> {
                wheelR = 0f
                val bs = height * 0.2f * s
                mrect(left, leftEdge, bottom - bs, leftEdge + bs, bottom)
                mrect(right, leftEdge + bs + pad * 0.5f, bottom - bs, leftEdge + bs * 2 + pad * 0.5f, bottom)
                gear.setEmpty()
            }
        }
        val pw = height * 0.13f * s; val ph = height * 0.34f * s
        mrect(gas, rightEdge - pw, bottom - ph, rightEdge, bottom)
        val bw = height * 0.2f * s; val bh = height * 0.19f * s
        mrect(brake, rightEdge - pw - pad * 0.5f - bw, bottom - bh, rightEdge - pw - pad * 0.5f, bottom)
        if (cfg.scheme == Scheme.ARROWS) {
            val rw = height * 0.13f * s
            mrect(rev, rightEdge - pw - pad * 0.5f - bw - pad * 0.5f - rw, bottom - ph * 0.8f, rightEdge - pw - pad * 0.5f - bw - pad * 0.5f, bottom)
        } else rev.setEmpty()
        safe.set(width * 0.03f, height * 0.2f, width * 0.97f, height * 0.82f)
    }

    // ------------------------------------------------------------- input
    private fun hitWheel(x: Float, y: Float) = wheelR > 0 && hypot(x - wheelCx, y - wheelCy) < wheelR * 1.3f
    private fun RectF.hit(x: Float, y: Float, slop: Float = 18f) = !isEmpty && x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop

    fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> { for (id in listOf(wheelPointer, gasPointer, brakePointer, gearPointer, leftPointer, rightPointer, revPointer)) if (id >= 0) up(id) }
        }
        st.touched = true
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        when {
            gas.hit(x, y) -> { gasPointer = id; st.gasHeld = true; pedalDepth(y); if (cfg.scheme == Scheme.ARROWS && st.gear != 1) setGear(1); feedback(FB_PEDAL) }
            brake.hit(x, y) -> { brakePointer = id; st.brakeHeld = true; feedback(FB_PEDAL) }
            rev.hit(x, y) -> { revPointer = id; if (st.gear != -1) setGear(-1); st.gasHeld = true; st.gasAmount = 0.8; feedback(FB_PEDAL) }
            gear.hit(x, y, 26f) -> { gearPointer = id; gearDownY = y }
            left.hit(x, y) -> { leftPointer = id; feedback(FB_PEDAL) }
            right.hit(x, y) -> { rightPointer = id; feedback(FB_PEDAL) }
            hitWheel(x, y) -> { wheelPointer = id; lastAngle = atan2(y - wheelCy, x - wheelCx) }
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        when (id) {
            wheelPointer -> {
                val a = atan2(y - wheelCy, x - wheelCx)
                var d = a - lastAngle
                while (d > Math.PI) d -= (Math.PI * 2).toFloat()
                while (d < -Math.PI) d += (Math.PI * 2).toFloat()
                // ignore jitter right at the hub
                if (hypot(x - wheelCx, y - wheelCy) > wheelR * 0.18f) {
                    wheelDeg = (wheelDeg + Math.toDegrees(d.toDouble()).toFloat()).coerceIn(-maxDeg, maxDeg)
                }
                lastAngle = a
                st.steerTarget = (wheelDeg / maxDeg).toDouble()
            }
            gasPointer -> pedalDepth(y)
            gearPointer -> {
                if (y - gearDownY > gear.height() * 0.3f && st.gear != -1) { setGear(-1); gearDownY = y }
                else if (gearDownY - y > gear.height() * 0.3f && st.gear != 1) { setGear(1); gearDownY = y }
            }
        }
    }

    private fun up(id: Int) {
        when (id) {
            wheelPointer -> wheelPointer = -1
            gasPointer -> { gasPointer = -1; st.gasHeld = false }
            brakePointer -> { brakePointer = -1; st.brakeHeld = false }
            revPointer -> { revPointer = -1; st.gasHeld = false }
            gearPointer -> {
                // a tap (no drag) toggles
                if (gearPointer >= 0) {
                    gearPointer = -1
                    if (!gearDragged) setGear(-st.gear)
                }
            }
            leftPointer -> leftPointer = -1
            rightPointer -> rightPointer = -1
        }
        gearDragged = false
    }

    private var gearDragged = false
    private fun setGear(g: Int) { st.gear = g; gearDragged = true; feedback(FB_GEAR) }

    private fun pedalDepth(y: Float) {
        val t = ((y - gas.top) / gas.height()).coerceIn(0f, 1f)
        st.gasAmount = (1.0 - t * 0.6).coerceIn(0.4, 1.0)
    }

    /** Called every frame on the game thread. */
    fun update(dt: Float) {
        when (cfg.scheme) {
            Scheme.WHEEL -> {
                if (wheelPointer < 0 && keySteer == 0 && cfg.autoCenter) {
                    val step = 900f * dt
                    wheelDeg = if (wheelDeg > 0) maxOf(0f, wheelDeg - step) else minOf(0f, wheelDeg + step)
                }
                if (keySteer != 0) wheelDeg = (wheelDeg + keySteer * 700f * dt).coerceIn(-maxDeg, maxDeg)
                st.steerTarget = (wheelDeg / maxDeg).toDouble()
            }
            Scheme.ARROWS -> {
                val target = (if (rightPointer >= 0 || keySteer > 0) 1f else 0f) - (if (leftPointer >= 0 || keySteer < 0) 1f else 0f)
                val rate = 3.2f * cfg.sensitivity * dt
                arrowSteer = if (target > arrowSteer) minOf(target, arrowSteer + rate) else maxOf(target, arrowSteer - rate * 1.4f)
                st.steerTarget = arrowSteer.toDouble()
                wheelDeg = arrowSteer * maxDeg
            }
            Scheme.TILT -> {
                val v = if (keySteer != 0) keySteer.toFloat() else (ActiveInput.roll / (26f / cfg.sensitivity)).coerceIn(-1f, 1f)
                st.steerTarget = v.toDouble(); wheelDeg = v * maxDeg
            }
        }
        val k = (dt * 14f).coerceAtMost(1f)
        gasAnim += ((if (st.gasHeld) 1f else 0f) - gasAnim) * k
        brakeAnim += ((if (st.brakeHeld) 1f else 0f) - brakeAnim) * k
        gearAnim += ((if (st.gear < 0) 1f else 0f) - gearAnim) * (dt * 12f).coerceAtMost(1f)
    }

    fun reset() {
        wheelDeg = 0f; arrowSteer = 0f; st.steerTarget = 0.0; st.gasHeld = false; st.brakeHeld = false
        wheelPointer = -1; gasPointer = -1; brakePointer = -1; gearPointer = -1; leftPointer = -1; rightPointer = -1; revPointer = -1
    }

    // ------------------------------------------------------------- draw
    fun draw(c: Canvas) {
        val a = (cfg.opacity * 255).toInt()
        if (wheelR > 0) drawWheel(c, a)
        if (!gear.isEmpty) drawGear(c, a)
        drawPedal(c, gas, gasAnim, 0xFF9BFF3C.toInt(), a, tall = true)
        drawPedal(c, brake, brakeAnim, 0xFFFF5A3C.toInt(), a, tall = false)
        if (!rev.isEmpty) { drawPedal(c, rev, if (revPointer >= 0) 1f else 0f, 0xFFFF3DA5.toInt(), a, tall = true); label(c, rev, "R", a) }
        if (!left.isEmpty) { drawArrowButton(c, left, -1, leftPointer >= 0, a); drawArrowButton(c, right, 1, rightPointer >= 0, a) }
    }

    private fun label(c: Canvas, r: RectF, t: String, a: Int) {
        tp.color = 0xFFFFFFFF.toInt(); tp.alpha = a; tp.textSize = r.width() * 0.4f
        c.drawText(t, r.centerX(), r.top - r.width() * 0.12f, tp)
    }

    private fun drawWheel(c: Canvas, a: Int) {
        WheelPainter.paint(c, cfg.wheel, wheelCx, wheelCy, wheelR, wheelDeg, a, display, if (st.gear < 0) "R" else "D")
    }

    private fun drawGear(c: Canvas, a: Int) {
        val r = gear
        p.alpha = a
        p.shader = LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(0xFF1A1E26.toInt(), 0xFF2C323E.toInt(), 0xFF1A1E26.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, r.width() / 2, r.width() / 2, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = 0x6619E3FF; p.alpha = (a * 0.5f).toInt()
        c.drawRoundRect(r, r.width() / 2, r.width() / 2, p); p.style = Paint.Style.FILL
        // track
        p.color = 0xFF0B0E14.toInt(); p.alpha = a
        c.drawRoundRect(r.centerX() - r.width() * 0.08f, r.top + r.height() * 0.24f, r.centerX() + r.width() * 0.08f, r.bottom - r.height() * 0.24f, 6f, 6f, p)
        // labels
        tp.textSize = r.width() * 0.42f
        tp.color = if (gearAnim < 0.5f) 0xFF9BFF3C.toInt() else 0x66FFFFFF; tp.alpha = if (gearAnim < 0.5f) a else (a * 0.45f).toInt()
        c.drawText("D", r.centerX(), r.top + r.width() * 0.62f, tp)
        tp.color = if (gearAnim >= 0.5f) 0xFFFF4D4D.toInt() else 0x66FFFFFF; tp.alpha = if (gearAnim >= 0.5f) a else (a * 0.45f).toInt()
        c.drawText("R", r.centerX(), r.bottom - r.width() * 0.25f, tp)
        // knob
        val ky = r.top + r.height() * (0.36f + 0.28f * gearAnim)
        val kr = r.width() * 0.34f
        p.shader = RadialGradient(r.centerX() - kr * 0.3f, ky - kr * 0.3f, kr * 1.2f, intArrayOf(0xFF6A7384.toInt(), 0xFF262B35.toInt()), null, Shader.TileMode.CLAMP)
        p.alpha = a; c.drawCircle(r.centerX(), ky, kr, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        p.color = if (gearAnim >= 0.5f) 0xFFFF4D4D.toInt() else 0xFF9BFF3C.toInt(); p.alpha = a
        c.drawCircle(r.centerX(), ky, kr, p); p.style = Paint.Style.FILL
    }

    private fun drawPedal(c: Canvas, r: RectF, press: Float, accent: Int, a: Int, tall: Boolean) {
        if (r.isEmpty) return
        c.save()
        val sc = 1f - 0.06f * press
        c.scale(sc, sc, r.centerX(), r.bottom)
        p.color = 0x66000000; p.alpha = (a * 0.5f).toInt()
        c.drawRoundRect(r.left + 4, r.top + 8, r.right + 4, r.bottom + 8, 18f, 18f, p)
        p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(0xFF5A6170.toInt(), 0xFF2E333D.toInt(), 0xFF1E2229.toInt()), null, Shader.TileMode.CLAMP)
        p.alpha = a; c.drawRoundRect(r, 18f, 18f, p); p.shader = null
        // rubber ridges
        p.color = 0xFF14171C.toInt(); p.alpha = a
        val inset = r.width() * 0.14f
        val n = if (tall) 9 else 5
        for (k in 0 until n) {
            val yy = r.top + r.height() * (0.12f + 0.76f * k / (n - 1))
            c.drawRoundRect(r.left + inset, yy - r.height() * 0.022f, r.right - inset, yy + r.height() * 0.022f, 5f, 5f, p)
        }
        // accent edge glow
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f + 3f * press; p.color = accent; p.alpha = (a * (0.35f + 0.65f * press)).toInt()
        c.drawRoundRect(r, 18f, 18f, p); p.style = Paint.Style.FILL
        c.restore()
    }

    private fun drawArrowButton(c: Canvas, r: RectF, dir: Int, pressed: Boolean, a: Int) {
        p.shader = RadialGradient(r.centerX(), r.centerY(), r.width() * 0.6f, intArrayOf(0xFF3A4150.toInt(), 0xFF1A1E26.toInt()), null, Shader.TileMode.CLAMP)
        p.alpha = a; c.drawCircle(r.centerX(), r.centerY(), r.width() / 2 * if (pressed) 0.94f else 1f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = if (pressed) 0xFF19E3FF.toInt() else 0x8819E3FF.toInt(); p.alpha = a
        c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, p); p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt(); p.alpha = a
        val s = r.width() * 0.22f * dir
        path.reset(); path.moveTo(r.centerX() + s, r.centerY()); path.lineTo(r.centerX() - s * 0.6f, r.centerY() - r.width() * 0.2f)
        path.lineTo(r.centerX() - s * 0.6f, r.centerY() + r.width() * 0.2f); path.close(); c.drawPath(path, p)
    }

    companion object {
        const val FB_PEDAL = 1
        const val FB_GEAR = 2
    }
}
