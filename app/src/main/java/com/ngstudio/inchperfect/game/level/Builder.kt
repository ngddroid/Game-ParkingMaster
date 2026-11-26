package com.ngstudio.inchperfect.game.level

import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Collide
import com.ngstudio.inchperfect.game.math.Disc
import com.ngstudio.inchperfect.game.math.M
import com.ngstudio.inchperfect.game.math.Pose
import com.ngstudio.inchperfect.game.math.Rng
import com.ngstudio.inchperfect.game.math.Shape
import com.ngstudio.inchperfect.game.physics.CarSpec
import com.ngstudio.inchperfect.game.physics.CarState
import com.ngstudio.inchperfect.game.physics.TrailerSpec

/**
 * Path-first construction kit.
 *
 * 1. Drive the reference car along motion primitives ([drive], [rotate], [waitGap]) — this records the
 *    swept area of the verified solution.
 * 2. Place obstacles with [place]: each obstacle starts overlapping the path and slides away along a
 *    direction until it is exactly the requested clearance away from EVERY sample of the swept area.
 */
class Builder(val rng: Rng, val car: CarSpec, val trailer: TrailerSpec?) {
    var pose = Pose(0.0, 0.0, 0.0)          // rear axle pose
    var phi = 0.0                            // trailer heading
    val steps = ArrayList<Step>()
    val carBoxes = ArrayList<Box>()
    val sweep = ArrayList<Box>()             // car + trailer boxes
    val carPoses = ArrayList<Pose>()         // rear axle poses for each car box
    val segStart = ArrayList<Int>()          // sweep index where each segment starts

    val obstacles = ArrayList<Obstacle>()
    val required = ArrayList<Double>()
    val props = ArrayList<Prop>()
    val marks = ArrayList<Mark>()
    val zones = ArrayList<Zone>()
    val traffic = ArrayList<TrafficLane>()
    val gates = ArrayList<Gate>()
    val tables = ArrayList<Turntable>()
    val coins = ArrayList<DoubleArray>()
    /** deferred placement: runs after the whole path exists so every obstacle sees the full sweep */
    val requests = ArrayList<() -> Unit>()
    val lateRequests = ArrayList<() -> Unit>()
    /** areas where nothing may be placed (e.g. roads with traffic) */
    val keepOut = ArrayList<Box>()
    /** segment index for each car box (for self-proximity checks) */
    val segOf = ArrayList<Int>()
    var seg = 0

    private var grid: HashMap<Long, IntArray>? = null
    private val cell = 3.0
    private var maxBoxR = 0.0

    fun startPose(p: Pose) { pose = p; phi = p.a }

    // ------------------------------------------------------------------ path
    fun bodyAt(p: Pose): Box {
        val c = M.cos(p.a); val s = M.sin(p.a)
        return Box(p.x + c * car.centerOffset, p.y + s * car.centerOffset, car.length / 2, car.width / 2, p.a)
    }

    fun centerOf(p: Pose) = doubleArrayOf(p.x + M.cos(p.a) * car.centerOffset, p.y + M.sin(p.a) * car.centerOffset)

    private fun trailerAt(p: Pose, phiNow: Double): Box {
        val st = CarState(p.x, p.y, p.a).also { it.phi = phiNow }
        return st.trailerBody(trailer!!)
    }

    private fun record() {
        grid = null
        val b = bodyAt(pose)
        carBoxes.add(b); carPoses.add(pose); sweep.add(b); segOf.add(seg)
        if (trailer != null) sweep.add(trailerAt(pose, phi))
    }

    fun beginSegment() { seg++; segStart.add(carBoxes.size) }

    /** Drive [dist] metres with constant steering. gear +1 forward / -1 reverse. */
    fun drive(gear: Int, steer: Double, dist: Double, vmax: Double) {
        if (dist <= 1e-6) return
        if (sweep.isEmpty()) record()
        steps.add(Step(Prim.DRIVE, gear, steer, dist, vmax))
        val n = maxOf(1, kotlin.math.ceil(dist / 0.05).toInt())
        val ds = dist / n * gear
        val k = steer * car.maxCurv
        repeat(n) {
            val da = ds * k
            val am = pose.a + da / 2
            val prevA = pose.a
            pose = Pose(pose.x + ds * M.cos(am), pose.y + ds * M.sin(am), M.wrap(pose.a + da))
            if (trailer != null) {
                val rel = M.wrap(prevA - phi)
                phi = M.wrap(phi + (ds * M.sin(rel) - trailer.hitchBack * da * M.cos(rel)) / trailer.axleDist)
            }
            record()
        }
    }

    /** arc length for turning [angleRad] at steering [steer] */
    fun arcLen(angleRad: Double, steer: Double) = kotlin.math.abs(angleRad) / (kotlin.math.abs(steer) * car.maxCurv)

    fun waitGap() { steps.add(Step(Prim.WAIT)) }

    fun rotate(tableIdx: Int, by: Double) {
        val t = tables[tableIdx]
        steps.add(Step(Prim.ROTATE, rot = by, table = tableIdx))
        val n = maxOf(2, (kotlin.math.abs(by) / M.rad(2.0)).toInt())
        val start = pose
        for (i in 1..n) {
            val ang = by * i / n
            val c = M.cos(ang); val s = M.sin(ang)
            val dx = start.x - t.cx; val dy = start.y - t.cy
            pose = Pose(t.cx + dx * c - dy * s, t.cy + dx * s + dy * c, M.wrap(start.a + ang))
            phi = pose.a
            record()
        }
    }

    // ------------------------------------------------------------- clearance
    private fun key(ix: Int, iy: Int) = (ix.toLong() shl 32) or (iy.toLong() and 0xffffffffL)

    fun freeze() {
        val g = HashMap<Long, ArrayList<Int>>()
        maxBoxR = 0.0
        for (i in sweep.indices) {
            val b = sweep[i]
            if (b.radius > maxBoxR) maxBoxR = b.radius
            val ix = kotlin.math.floor(b.cx / cell).toInt(); val iy = kotlin.math.floor(b.cy / cell).toInt()
            g.getOrPut(key(ix, iy)) { ArrayList() }.add(i)
        }
        grid = HashMap<Long, IntArray>(g.size * 2).also { m -> g.forEach { (k, v) -> m[k] = v.toIntArray() } }
    }

    /** true if [sh] keeps at least [c] metres from every swept sample */
    fun clearOf(sh: Shape, c: Double): Boolean {
        val g = grid ?: run { freeze(); grid!! }
        val reach = sh.radius + maxBoxR + c
        val x0 = kotlin.math.floor((sh.cx - reach) / cell).toInt(); val x1 = kotlin.math.floor((sh.cx + reach) / cell).toInt()
        val y0 = kotlin.math.floor((sh.cy - reach) / cell).toInt(); val y1 = kotlin.math.floor((sh.cy + reach) / cell).toInt()
        for (ix in x0..x1) for (iy in y0..y1) {
            val list = g[key(ix, iy)] ?: continue
            for (i in list) {
                val b = sweep[i]
                val dx = b.cx - sh.cx; val dy = b.cy - sh.cy
                val lim = sh.radius + b.radius + c
                if (dx * dx + dy * dy > lim * lim) continue
                if (Collide.dist(sh, b) < c) return false
            }
        }
        return true
    }

    fun minDist(sh: Shape, cap: Double = 50.0): Double {
        var best = cap
        for (b in sweep) {
            val dx = b.cx - sh.cx; val dy = b.cy - sh.cy
            val lim = sh.radius + b.radius + best
            if (dx * dx + dy * dy > lim * lim) continue
            val d = Collide.dist(sh, b)
            if (d < best) best = d
        }
        return best
    }

    /** Smallest t in [0,maxT] where shapeAt(t) is clear by c. */
    fun slide(c: Double, maxT: Double, stepT: Double = 0.25, shapeAt: (Double) -> Shape): Double? {
        var prev = -1.0
        var t = 0.0
        while (t <= maxT + 1e-9) {
            if (clearOf(shapeAt(t), c)) {
                if (prev < 0) return t
                var lo = prev; var hi = t
                repeat(14) { val mid = (lo + hi) / 2; if (clearOf(shapeAt(mid), c)) hi = mid else lo = mid }
                return hi
            }
            prev = t
            t += stepT
        }
        return null
    }

    private fun isWallLike(k: ObKind) = k == ObKind.BARRIER || k == ObKind.WALL || k == ObKind.BUILDING ||
        k == ObKind.HEDGE || k == ObKind.SIDEWALK || k == ObKind.FENCE || k == ObKind.CONTAINER

    /** Does [sh] of [kind] clash with an already placed obstacle (walls may overlap walls)? */
    fun conflicts(kind: ObKind, sh: Shape, pad: Double = 0.05): Boolean {
        for (k in keepOut) if (Collide.overlap(k, sh)) return true
        for (o in obstacles) {
            if (isWallLike(kind) && isWallLike(o.kind)) continue
            val dx = o.shape.cx - sh.cx; val dy = o.shape.cy - sh.cy
            val lim = o.shape.radius + sh.radius + pad
            if (dx * dx + dy * dy > lim * lim) continue
            if (Collide.dist(o.shape, sh) < pad) return true
        }
        return false
    }

    fun add(kind: ObKind, sh: Shape, c: Double, color: Int = 0): Obstacle {
        val o = Obstacle(kind, sh, rng.nextInt() and 0x7fffffff, color)
        obstacles.add(o); required.add(c)
        return o
    }

    /**
     * Slide-to-clearance placement. Returns the placed obstacle or null when no valid position exists
     * within [maxT] (or it would clash with other obstacles).
     */
    fun place(kind: ObKind, c: Double, maxT: Double, color: Int = 0, jitter: Double = 0.0, shapeAt: (Double) -> Shape): Obstacle? {
        val t0 = slide(c, maxT, shapeAt = shapeAt) ?: return null
        var t = t0 + (if (jitter > 0) rng.range(0.0, jitter) else 0.0)
        var sh = shapeAt(t)
        var tries = 0
        while ((conflicts(kind, sh) || !clearOf(sh, c)) && tries < 20) {
            t += 0.25; tries++
            if (t > maxT) return null
            sh = shapeAt(t)
        }
        if (tries >= 20) return null
        return add(kind, sh, c, color)
    }

    /** Places a whole row as one rigid unit (keeps it aligned). Only [core] members must fit; others are added if clear. */
    fun placeRow(kind: ObKind, c: Double, maxT: Double, shapes: (Double) -> List<Shape>, colors: List<Int>, core: Set<Int>? = null): List<Obstacle> {
        fun ok(t: Double): Boolean { val l = shapes(t); return l.indices.all { i -> (core != null && i !in core) || clearOf(l[i], c) } }
        var t = 0.0
        var found = -1.0
        var prev = -1.0
        while (t <= maxT) {
            if (ok(t)) { found = t; break }
            prev = t; t += 0.25
        }
        if (found < 0) return emptyList()
        if (prev >= 0) {
            var lo = prev; var hi = found
            repeat(12) { val mid = (lo + hi) / 2; if (ok(mid)) hi = mid else lo = mid }
            found = hi
        }
        val out = ArrayList<Obstacle>()
        shapes(found).forEachIndexed { i, sh ->
            if (!conflicts(kind, sh) && clearOf(sh, c)) out.add(add(kind, sh, c, colors.getOrElse(i) { 0 }))
        }
        return out
    }

    /** Largest radius (<= rMax) for a disc at (x,y) that keeps clearance c. */
    fun shrinkDisc(x: Double, y: Double, rMax: Double, c: Double): Double? {
        if (clearOf(Disc(x, y, 0.05), c).not()) return null
        var lo = 0.05; var hi = rMax
        if (clearOf(Disc(x, y, hi), c)) return hi
        repeat(16) { val mid = (lo + hi) / 2; if (clearOf(Disc(x, y, mid), c)) lo = mid else hi = mid }
        return lo
    }

    /**
     * Lines a stretch of the path with wall pieces on one side. side +1 = right, -1 = left.
     * Each piece starts touching the car and slides outward until it is [c] away from the whole sweep.
     */
    fun hugWalls(from: Int, to: Int, side: Int, c: Double, kind: ObKind, pieceLen: Double, thick: Double,
                 maxT: Double = 5.0, colorOf: () -> Int = { 0 }, gap: Double = -0.1, skipChance: Double = 0.0) {
        if (to - from < 2) return
        // split into straight runs (heading within 3 degrees) so walls line up neatly
        val runs = ArrayList<IntArray>()
        var rs = from
        for (i in from + 1 until to) {
            val bi = carBoxes.getOrNull(i) ?: break
            if (kotlin.math.abs(M.wrap(bi.a - carBoxes[rs].a)) > M.rad(3.0)) {
                if (i - 1 > rs) runs.add(intArrayOf(rs, i - 1))
                rs = i
            }
        }
        if (to - 1 > rs) runs.add(intArrayOf(rs, minOf(to - 1, carBoxes.size - 1)))
        val spacing = pieceLen + gap
        for (run in runs) {
            val b0 = carBoxes[run[0]]; val b1 = carBoxes[run[1]]
            val runLen = M.hypot(b1.cx - b0.cx, b1.cy - b0.cy)
            if (runLen < 2.2 && runs.size > 1) continue
            val a = b0.a
            val fx = M.cos(a); val fy = M.sin(a)
            val nx = -M.sin(a) * side; val ny = M.cos(a) * side
            // extend past both ends so corners close up
            val ext = minOf(pieceLen * 0.75, 2.5)
            val total = runLen + ext * 2
            val count = maxOf(1, kotlin.math.ceil(total / spacing).toInt())
            val step = total / count
            val half = minOf(pieceLen, step + 0.05) / 2
            val base = car.width / 2 + thick / 2
            val ts = DoubleArray(count) { -1.0 }
            for (k in 0 until count) {
                val along = -ext + step * (k + 0.5)
                val cx = b0.cx + fx * along; val cy = b0.cy + fy * along
                ts[k] = slide(c, maxT) { t -> Box(cx + nx * (base + t), cy + ny * (base + t), half, thick / 2, a) } ?: -1.0
            }
            val valid = ts.filter { it >= 0 }
            if (valid.isEmpty()) continue
            // align the run when the spread is small (neat straight wall)
            val tAlign = if (valid.max() - valid.min() <= 0.9) valid.max() else -1.0
            for (k in 0 until count) {
                if (ts[k] < 0) continue
                if (skipChance > 0 && rng.chance(skipChance)) continue
                val along = -ext + step * (k + 0.5)
                val cx = b0.cx + fx * along; val cy = b0.cy + fy * along
                val t = if (tAlign >= 0) tAlign else ts[k]
                val sh = Box(cx + nx * (base + t), cy + ny * (base + t), half, thick / 2, a)
                if (!conflicts(kind, sh) && clearOf(sh, c)) add(kind, sh, c, colorOf())
            }
        }
    }

    /** Fills the inside of a turn with one block that slides away from the arc until clear. */
    fun cornerBlock(kind: ObKind, px: Double, py: Double, dirX: Double, dirY: Double, size: Double, a: Double, c: Double) {
        place(kind, c, size * 2 + 6.0) { t -> Box(px + dirX * t, py + dirY * t, size, size, a) }
    }

    /** index range helpers for carBoxes (sweep index differs when towing). */
    fun carIndex(): Int = carBoxes.size

    fun prop(kind: PropKind, x: Double, y: Double, a: Double = 0.0, size: Double = 1.0) {
        props.add(Prop(kind, x, y, a, size, rng.nextInt() and 0x7fffffff))
    }

    /** Is a point far enough from the path and from obstacles to hold decoration of radius r? */
    fun freeSpot(x: Double, y: Double, r: Double, fromPath: Double): Boolean {
        if (!clearOf(Disc(x, y, r), fromPath)) return false
        for (o in obstacles) {
            val dx = o.shape.cx - x; val dy = o.shape.cy - y
            val lim = o.shape.radius + r
            if (dx * dx + dy * dy > lim * lim) continue
            if (Collide.dist(o.shape, Disc(x, y, r)) <= 0.05) return false
        }
        return true
    }
}
