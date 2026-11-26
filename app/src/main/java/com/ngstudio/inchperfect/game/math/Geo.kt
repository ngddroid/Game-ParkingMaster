package com.ngstudio.inchperfect.game.math

/**
 * Deterministic math helpers. Everything that influences gameplay or level generation goes
 * through StrictMath so a level generated in a JVM unit test is bit-identical to the one
 * generated on the device.
 *
 * World convention: metres, x to the right, y DOWN (screen-like). Heading 0 = +x and
 * positive angles rotate clockwise on screen. A car with heading a moves along (cos a, sin a);
 * its right-hand side is (-sin a, cos a).
 */
object M {
    const val PI = Math.PI
    const val TAU = Math.PI * 2.0
    fun sin(a: Double) = StrictMath.sin(a)
    fun cos(a: Double) = StrictMath.cos(a)
    fun tan(a: Double) = StrictMath.tan(a)
    fun atan2(y: Double, x: Double) = StrictMath.atan2(y, x)
    fun sqrt(v: Double) = StrictMath.sqrt(v)
    fun hypot(x: Double, y: Double) = StrictMath.sqrt(x * x + y * y)
    fun exp(v: Double) = StrictMath.exp(v)
    fun ln(v: Double) = StrictMath.log(v)
    fun pow(a: Double, b: Double) = StrictMath.pow(a, b)
    fun rad(deg: Double) = deg * PI / 180.0
    fun deg(rad: Double) = rad * 180.0 / PI
    fun clamp(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun wrap(a: Double): Double {
        var x = a
        while (x > PI) x -= TAU
        while (x < -PI) x += TAU
        return x
    }
    fun smooth(t: Double): Double { val c = clamp(t, 0.0, 1.0); return c * c * (3 - 2 * c) }
}

data class Pose(val x: Double, val y: Double, val a: Double) {
    val fx get() = M.cos(a)
    val fy get() = M.sin(a)
    /** Local (u forward, w right) to world. */
    fun lx(u: Double, w: Double) = x + M.cos(a) * u - M.sin(a) * w
    fun ly(u: Double, w: Double) = y + M.sin(a) * u + M.cos(a) * w
    fun local(u: Double, w: Double, da: Double = 0.0) = Pose(lx(u, w), ly(u, w), M.wrap(a + da))
}

/** Shapes used for collision. Box = oriented rectangle (half length along heading, half width). */
sealed class Shape {
    abstract val cx: Double
    abstract val cy: Double
    abstract val radius: Double // bounding circle
    abstract fun moved(dx: Double, dy: Double): Shape
}

class Box(override val cx: Double, override val cy: Double, val hl: Double, val hw: Double, val a: Double) : Shape() {
    val c = M.cos(a)
    val s = M.sin(a)
    override val radius = M.hypot(hl, hw)
    override fun moved(dx: Double, dy: Double) = Box(cx + dx, cy + dy, hl, hw, a)
    fun resized(nhl: Double, nhw: Double) = Box(cx, cy, nhl, nhw, a)
    /** corners in order: front-left, front-right, rear-right, rear-left (w = right). */
    fun corners(out: DoubleArray = DoubleArray(8)): DoubleArray {
        val ux = c * hl; val uy = s * hl
        val rx = -s * hw; val ry = c * hw
        out[0] = cx + ux - rx; out[1] = cy + uy - ry
        out[2] = cx + ux + rx; out[3] = cy + uy + ry
        out[4] = cx - ux + rx; out[5] = cy - uy + ry
        out[6] = cx - ux - rx; out[7] = cy - uy - ry
        return out
    }
    fun contains(px: Double, py: Double, slack: Double = 0.0): Boolean {
        val dx = px - cx; val dy = py - cy
        val u = dx * c + dy * s
        val w = -dx * s + dy * c
        return kotlin.math.abs(u) <= hl + slack && kotlin.math.abs(w) <= hw + slack
    }
}

class Disc(override val cx: Double, override val cy: Double, val r: Double) : Shape() {
    override val radius get() = r
    override fun moved(dx: Double, dy: Double) = Disc(cx + dx, cy + dy, r)
}

object Collide {
    private fun projRadius(b: Box, ax: Double, ay: Double): Double =
        kotlin.math.abs(b.c * ax + b.s * ay) * b.hl + kotlin.math.abs(-b.s * ax + b.c * ay) * b.hw

    fun boxBox(a: Box, b: Box): Boolean {
        val dx = b.cx - a.cx; val dy = b.cy - a.cy
        val rr = a.radius + b.radius
        if (dx * dx + dy * dy > rr * rr) return false
        val axes = doubleArrayOf(a.c, a.s, -a.s, a.c, b.c, b.s, -b.s, b.c)
        var i = 0
        while (i < 8) {
            val ax = axes[i]; val ay = axes[i + 1]
            val d = kotlin.math.abs(dx * ax + dy * ay)
            if (d > projRadius(a, ax, ay) + projRadius(b, ax, ay)) return false
            i += 2
        }
        return true
    }

    /** Closest point on box to (px,py), returns squared distance (0 if inside). */
    fun boxPointDist2(b: Box, px: Double, py: Double): Double {
        val dx = px - b.cx; val dy = py - b.cy
        val u = dx * b.c + dy * b.s
        val w = -dx * b.s + dy * b.c
        val cu = M.clamp(u, -b.hl, b.hl)
        val cw = M.clamp(w, -b.hw, b.hw)
        val eu = u - cu; val ew = w - cw
        return eu * eu + ew * ew
    }

    fun boxDisc(b: Box, d: Disc): Boolean = boxPointDist2(b, d.cx, d.cy) <= d.r * d.r

    fun discDisc(a: Disc, b: Disc): Boolean {
        val dx = a.cx - b.cx; val dy = a.cy - b.cy; val r = a.r + b.r
        return dx * dx + dy * dy <= r * r
    }

    fun overlap(a: Shape, b: Shape): Boolean = when (a) {
        is Box -> when (b) { is Box -> boxBox(a, b); is Disc -> boxDisc(a, b) }
        is Disc -> when (b) { is Box -> boxDisc(b, a); is Disc -> discDisc(a, b) }
    }

    private fun segPointDist2(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double): Double {
        val vx = bx - ax; val vy = by - ay
        val wx = px - ax; val wy = py - ay
        val l2 = vx * vx + vy * vy
        val t = if (l2 <= 1e-12) 0.0 else M.clamp((wx * vx + wy * vy) / l2, 0.0, 1.0)
        val ex = ax + vx * t - px; val ey = ay + vy * t - py
        return ex * ex + ey * ey
    }

    /** Separation distance between two boxes (0 when touching/overlapping). */
    fun boxBoxDist(a: Box, b: Box): Double {
        if (boxBox(a, b)) return 0.0
        val pa = a.corners(DoubleArray(8)); val pb = b.corners(DoubleArray(8))
        var best = Double.MAX_VALUE
        for (i in 0 until 4) {
            val px = pa[i * 2]; val py = pa[i * 2 + 1]
            for (j in 0 until 4) {
                val k = (j + 1) % 4
                val d = segPointDist2(pb[j * 2], pb[j * 2 + 1], pb[k * 2], pb[k * 2 + 1], px, py)
                if (d < best) best = d
            }
        }
        for (i in 0 until 4) {
            val px = pb[i * 2]; val py = pb[i * 2 + 1]
            for (j in 0 until 4) {
                val k = (j + 1) % 4
                val d = segPointDist2(pa[j * 2], pa[j * 2 + 1], pa[k * 2], pa[k * 2 + 1], px, py)
                if (d < best) best = d
            }
        }
        return M.sqrt(best)
    }

    fun boxDiscDist(b: Box, d: Disc): Double = maxOf(0.0, M.sqrt(boxPointDist2(b, d.cx, d.cy)) - d.r)

    fun dist(a: Shape, b: Shape): Double = when (a) {
        is Box -> when (b) { is Box -> boxBoxDist(a, b); is Disc -> boxDiscDist(a, b) }
        is Disc -> when (b) {
            is Box -> boxDiscDist(b, a)
            is Disc -> maxOf(0.0, M.hypot(a.cx - b.cx, a.cy - b.cy) - a.r - b.r)
        }
    }
}

/** PCG32 random generator: tiny, fast and identical on every platform. */
class Rng(seed: Long) {
    private var state = 0L
    private val inc = (seed shl 1) or 1L

    init {
        nextInt(); state += seed; nextInt()
    }

    fun nextInt(): Int {
        val old = state
        state = old * 6364136223846793005L + inc
        val xorShifted = (((old ushr 18) xor old) ushr 27).toInt()
        val rot = (old ushr 59).toInt()
        return (xorShifted ushr rot) or (xorShifted shl ((-rot) and 31))
    }

    fun next(): Double = (nextInt().toLong() and 0xffffffffL) / 4294967296.0
    fun range(a: Double, b: Double) = a + (b - a) * next()
    fun int(n: Int): Int = if (n <= 1) 0 else ((nextInt().toLong() and 0xffffffffL) % n).toInt()
    fun int(a: Int, b: Int): Int = a + int(b - a + 1)
    fun chance(p: Double) = next() < p
    fun sign() = if (next() < 0.5) -1.0 else 1.0
    fun <T> pick(list: List<T>): T = list[int(list.size)]
    fun <T> weighted(items: List<Pair<T, Double>>): T {
        val total = items.sumOf { it.second }
        var r = next() * total
        for ((v, w) in items) { r -= w; if (r <= 0) return v }
        return items.last().first
    }

    companion object {
        fun mix(x: Long): Long {
            var z = x + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
