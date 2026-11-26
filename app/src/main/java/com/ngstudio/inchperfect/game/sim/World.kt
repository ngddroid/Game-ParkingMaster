package com.ngstudio.inchperfect.game.sim

import com.ngstudio.inchperfect.game.level.Level
import com.ngstudio.inchperfect.game.level.ObKind
import com.ngstudio.inchperfect.game.level.Obstacle
import com.ngstudio.inchperfect.game.level.Weather
import com.ngstudio.inchperfect.game.level.ZoneKind
import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Collide
import com.ngstudio.inchperfect.game.math.Disc
import com.ngstudio.inchperfect.game.math.M
import com.ngstudio.inchperfect.game.math.Shape
import com.ngstudio.inchperfect.game.physics.CarPhysics
import com.ngstudio.inchperfect.game.physics.CarSpec
import com.ngstudio.inchperfect.game.physics.CarState
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.physics.DriveInput
import com.ngstudio.inchperfect.game.physics.TrailerSpec

enum class Phase { PLAYING, CRASHED, PARKED }

interface WorldEvents {
    fun crash(x: Double, y: Double, speed: Double) {}
    fun cone(x: Double, y: Double) {}
    fun coin(x: Double, y: Double) {}
    fun parked() {}
    fun tableStart() {}
    fun tableEnd() {}
    fun gateMove(up: Boolean) {}
}

class ConeState(val x0: Double, val y0: Double, val r: Double) {
    var x = x0; var y = y0; var vx = 0.0; var vy = 0.0; var ang = 0.0; var spin = 0.0; var knocked = false
    fun copy() = ConeState(x0, y0, r).also { it.x = x; it.y = y; it.vx = vx; it.vy = vy; it.ang = ang; it.spin = spin; it.knocked = knocked }
}

/** Static broadphase shared between the live world and probe worlds. */
class StaticIndex(level: Level) {
    val solids: List<Obstacle> = level.obstacles.filter { it.kind.solid }
    private val cell = 4.0
    private val grid = HashMap<Long, IntArray>()

    init {
        val tmp = HashMap<Long, ArrayList<Int>>()
        solids.forEachIndexed { i, o ->
            val s = o.shape
            val x0 = kotlin.math.floor((s.cx - s.radius) / cell).toInt(); val x1 = kotlin.math.floor((s.cx + s.radius) / cell).toInt()
            val y0 = kotlin.math.floor((s.cy - s.radius) / cell).toInt(); val y1 = kotlin.math.floor((s.cy + s.radius) / cell).toInt()
            for (ix in x0..x1) for (iy in y0..y1) tmp.getOrPut(key(ix, iy)) { ArrayList() }.add(i)
        }
        tmp.forEach { (k, v) -> grid[k] = v.toIntArray() }
    }

    private fun key(ix: Int, iy: Int) = (ix.toLong() shl 32) or (iy.toLong() and 0xffffffffL)

    inline fun forNear(x: Double, y: Double, r: Double, fn: (Obstacle) -> Unit) = near(x, y, r).forEach { fn(solids[it]) }

    private val seen = HashSet<Int>()
    fun near(x: Double, y: Double, r: Double): Set<Int> {
        seen.clear()
        val x0 = kotlin.math.floor((x - r) / cell).toInt(); val x1 = kotlin.math.floor((x + r) / cell).toInt()
        val y0 = kotlin.math.floor((y - r) / cell).toInt(); val y1 = kotlin.math.floor((y + r) / cell).toInt()
        for (ix in x0..x1) for (iy in y0..y1) grid[key(ix, iy)]?.let { for (i in it) seen.add(i) }
        return seen
    }

    fun hits(sh: Shape): Obstacle? {
        for (i in near(sh.cx, sh.cy, sh.radius)) {
            val o = solids[i]
            if (Collide.overlap(o.shape, sh)) return o
        }
        return null
    }
}

class World(val level: Level, val spec: CarSpec, val index: StaticIndex = StaticIndex(level)) {
    val trailer: TrailerSpec? = if (level.trailer) Cars.TRAILER else null
    val car = CarState(level.start.x, level.start.y, level.start.a, level.startGear)
    val input = DriveInput().also { it.gear = level.startGear }
    var phase = Phase.PLAYING
    var time = 0.0          // world clock (traffic, gates)
    var runTime = 0.0       // player's timer, starts on first input
    var timerStarted = false
    var events: WorldEvents? = null
    var crashX = 0.0; var crashY = 0.0
    var crashedInto: ObKind? = null
    var conesHit = 0
    var parkHold = 0.0
    val parkHoldNeeded = 0.5
    var precision = 0.0
    val cones: List<ConeState> = level.obstacles.filter { it.kind == ObKind.CONE }.map { val d = it.shape as Disc; ConeState(d.cx, d.cy, d.r) }
    val coinsTaken = BooleanArray(level.coins.size)
    var coinsCollected = 0

    // turntable
    var tableActive = -1
    var tableProgress = 0.0
    private var tableStartA = 0.0
    private var tableStartX = 0.0
    private var tableStartY = 0.0
    private var tableBase = 0.0
    val tableAngles = DoubleArray(level.turntables.size)

    /** probe worlds: only check moving hazards, with an extra safety margin */
    var dynamicOnly = false
    var dynamicMargin = 0.0

    // sensors: distance to nearest solid ahead / behind (m), for HUD arcs and beeps
    var sensorFront = 9.0
    var sensorRear = 9.0
    var sensorLeft = 9.0
    var sensorRight = 9.0

    var lastGrip = 1.0
    private val baseGrip = when (level.weather) { Weather.RAIN -> 0.85; Weather.SNOW -> 0.8; else -> 1.0 }
    private val iceZones = level.zones.filter { it.kind == ZoneKind.ICE }.map { it.box }

    // rewind snapshots
    private class Snap(val car: CarState, val time: Double, val run: Double, val cones: List<ConeState>, val coins: BooleanArray, val conesHit: Int, val tables: DoubleArray)
    private val snaps = ArrayDeque<Snap>()
    private var snapAcc = 0.0

    fun body(): Box = car.body(spec)
    fun trailerBody(): Box? = trailer?.let { car.trailerBody(it) }

    fun gripAt(x: Double, y: Double): Double {
        for (z in iceZones) if (z.contains(x, y)) return 0.45
        return baseGrip
    }

    fun canRotate(): Boolean {
        if (phase != Phase.PLAYING || tableActive >= 0 || kotlin.math.abs(car.v) > 0.05) return false
        return tableUnderCar() >= 0
    }

    private fun tableUnderCar(): Int {
        val pts = body().corners()
        level.turntables.forEachIndexed { i, t ->
            var inside = true
            for (k in 0 until 4) {
                val dx = pts[k * 2] - t.cx; val dy = pts[k * 2 + 1] - t.cy
                if (dx * dx + dy * dy > t.r * t.r) { inside = false; break }
            }
            if (inside) return i
        }
        return -1
    }

    fun pressRotate(): Boolean {
        if (!canRotate()) return false
        tableActive = tableUnderCar()
        tableProgress = 0.0
        tableStartA = car.a; tableStartX = car.x; tableStartY = car.y
        tableBase = tableAngles[tableActive]
        car.v = 0.0
        events?.tableStart()
        startTimer()
        return true
    }

    fun startTimer() { timerStarted = true }

    fun step(dt: Double) {
        time += dt
        if (phase != Phase.PLAYING) { updateCones(dt); return }
        if (!timerStarted && (input.throttle > 0.02 || input.brake > 0.02 || kotlin.math.abs(input.steer) > 0.05 || input.gear != level.startGear)) startTimer()
        if (timerStarted) runTime += dt

        snapAcc += dt
        if (snapAcc >= 0.1) { snapAcc = 0.0; pushSnap() }

        if (tableActive >= 0) {
            val t = level.turntables[tableActive]
            tableProgress = minOf(1.0, tableProgress + dt / t.duration)
            val e = M.smooth(tableProgress)
            val ang = t.rotateBy * e
            val c = M.cos(ang); val s = M.sin(ang)
            val dx = tableStartX - t.cx; val dy = tableStartY - t.cy
            car.x = t.cx + dx * c - dy * s
            car.y = t.cy + dx * s + dy * c
            car.a = M.wrap(tableStartA + ang)
            car.phi = car.a
            car.vx = 0.0; car.vy = 0.0
            tableAngles[tableActive] = tableBase + ang
            if (tableProgress >= 1.0) {
                tableAngles[tableActive] = M.wrap(tableAngles[tableActive])
                tableActive = -1
                events?.tableEnd()
            }
        } else {
            lastGrip = gripAt(car.x, car.y)
            CarPhysics.step(car, spec, input, lastGrip, dt, trailer)
        }

        checkCollisions()
        if (phase != Phase.PLAYING) return
        updateCones(dt)
        collectCoins()
        updateSensors()
        checkParked(dt)
    }

    private fun crash(x: Double, y: Double, kind: ObKind?) {
        if (phase != Phase.PLAYING) return
        phase = Phase.CRASHED
        crashX = x; crashY = y; crashedInto = kind
        events?.crash(x, y, kotlin.math.abs(car.v))
        car.v = 0.0
    }

    private fun contactPoint(a: Box, sh: Shape): DoubleArray {
        // approximate: closest corner of the car to the obstacle centre
        val pts = a.corners()
        var best = 0; var bd = Double.MAX_VALUE
        for (k in 0 until 4) {
            val dx = pts[k * 2] - sh.cx; val dy = pts[k * 2 + 1] - sh.cy
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = k }
        }
        return doubleArrayOf((pts[best * 2] * 2 + sh.cx) / 3, (pts[best * 2 + 1] * 2 + sh.cy) / 3)
    }

    private fun checkCollisions() {
        val bodies = ArrayList<Box>(2)
        bodies.add(body())
        trailerBody()?.let { bodies.add(it) }
        if (trailer != null && kotlin.math.abs(M.wrap(car.a - car.phi)) > M.rad(78.0)) {
            crash(car.x, car.y, null); return
        }
        for (b0 in bodies) {
            val bx = if (dynamicMargin > 0) Box(b0.cx, b0.cy, b0.hl + dynamicMargin, b0.hw + dynamicMargin * 0.5, b0.a) else b0
            if (!dynamicOnly) {
            // world bounds
            if (bx.cx < level.minX - 0.5 || bx.cx > level.maxX + 0.5 || bx.cy < level.minY - 0.5 || bx.cy > level.maxY + 0.5) { crash(bx.cx, bx.cy, null); return }
            index.hits(bx)?.let { o -> val p = contactPoint(bx, o.shape); crash(p[0], p[1], o.kind); return }
            }
            for (g in level.gates) {
                if (!g.isUp(time)) continue
                for (d in g.bollards) if (Collide.boxDisc(bx, d)) { crash(d.cx, d.cy, ObKind.PILLAR); return }
            }
            for (lane in level.traffic) {
                var hit: DoubleArray? = null
                lane.carsAt(time) { _, x, y -> if (hit == null && Collide.boxBox(bx, lane.carBox(x, y))) hit = doubleArrayOf(x, y) }
                hit?.let { val p = contactPoint(bx, lane.carBox(it[0], it[1])); crash(p[0], p[1], ObKind.CAR); return }
            }
        }
    }

    private fun updateCones(dt: Double) {
        val bodies = if (phase == Phase.PLAYING) listOfNotNull(body(), trailerBody()) else emptyList()
        for (c in cones) {
            if (!c.knocked) {
                for (bx in bodies) {
                    if (Collide.boxDisc(bx, Disc(c.x, c.y, c.r))) {
                        c.knocked = true
                        val sp = maxOf(1.2, kotlin.math.abs(car.v) * 1.4)
                        var dx = c.x - bx.cx; var dy = c.y - bx.cy
                        val l = maxOf(0.01, M.hypot(dx, dy)); dx /= l; dy /= l
                        c.vx = dx * sp * 0.6 + car.vx * 0.8
                        c.vy = dy * sp * 0.6 + car.vy * 0.8
                        c.spin = (if ((c.x0 * 7 + c.y0 * 13).toInt() % 2 == 0) 1 else -1) * (4.0 + sp * 3)
                        conesHit++
                        events?.cone(c.x, c.y)
                        break
                    }
                }
            } else {
                c.x += c.vx * dt; c.y += c.vy * dt; c.ang += c.spin * dt
                val f = maxOf(0.0, 1.0 - 2.6 * dt)
                c.vx *= f; c.vy *= f; c.spin *= f
            }
        }
    }

    private fun collectCoins() {
        val bx = body()
        level.coins.forEachIndexed { i, p ->
            if (!coinsTaken[i] && Collide.boxPointDist2(bx, p[0], p[1]) < 0.45 * 0.45) {
                coinsTaken[i] = true; coinsCollected++; events?.coin(p[0], p[1])
            }
        }
    }

    private fun updateSensors() {
        val bx = body()
        val c = bx.c; val s = bx.s
        var f = 9.0; var r = 9.0; var lft = 9.0; var rgt = 9.0
        val reach = 2.2
        index.forNear(bx.cx, bx.cy, bx.radius + reach) { o ->
            val d = Collide.dist(o.shape, bx)
            if (d > reach) return@forNear
            // classify by direction from car centre to the obstacle's closest region
            val dx = o.shape.cx - bx.cx; val dy = o.shape.cy - bx.cy
            val u = dx * c + dy * s
            val w = -dx * s + dy * c
            val probeU = M.clamp(u, -bx.hl, bx.hl)
            when {
                probeU >= bx.hl * 0.55 -> f = minOf(f, d)
                probeU <= -bx.hl * 0.55 -> r = minOf(r, d)
                w < 0 -> lft = minOf(lft, d)
                else -> rgt = minOf(rgt, d)
            }
        }
        sensorFront = f; sensorRear = r; sensorLeft = lft; sensorRight = rgt
    }

    fun bayContainsCar(): Boolean {
        val bay = level.bay
        val pts = body().corners()
        for (k in 0 until 4) if (!bay.contains(pts[k * 2], pts[k * 2 + 1])) return false
        trailerBody()?.let { t ->
            val tp = t.corners()
            for (k in 0 until 4) if (!bay.contains(tp[k * 2], tp[k * 2 + 1])) return false
        }
        return true
    }

    fun headingError(): Double = kotlin.math.abs(M.wrap(car.a - level.bay.a))

    fun headingOk() = headingError() <= M.rad(level.angleTolDeg)

    /** 0..1 progress of the "parking" fill ring */
    val parkProgress get() = M.clamp(parkHold / parkHoldNeeded, 0.0, 1.0)

    private fun checkParked(dt: Double) {
        if (tableActive >= 0) { parkHold = 0.0; return }
        val inside = bayContainsCar() && headingOk()
        parkHold = if (inside && kotlin.math.abs(car.v) < 0.08) parkHold + dt else 0.0
        if (parkHold >= parkHoldNeeded) {
            phase = Phase.PARKED
            precision = computePrecision()
            events?.parked()
        }
    }

    fun computePrecision(): Double {
        val bay = level.bay
        val bx = body()
        val dx = bx.cx - bay.cx; val dy = bx.cy - bay.cy
        val lon = kotlin.math.abs(dx * bay.c + dy * bay.s)
        val lat = kotlin.math.abs(-dx * bay.s + dy * bay.c)
        val maxLat = maxOf(0.05, bay.hw - bx.hw)
        val maxLon = maxOf(0.05, bay.hl - bx.hl)
        val angTol = M.rad(level.angleTolDeg)
        val score = 1.0 - 0.45 * M.clamp(lat / maxLat, 0.0, 1.0) - 0.35 * M.clamp(headingError() / angTol, 0.0, 1.0) -
            0.20 * M.clamp(lon / maxLon, 0.0, 1.0)
        return M.clamp(score, 0.0, 1.0) * 100.0
    }

    // ------------------------------------------------------------- rewind
    private fun pushSnap() {
        snaps.addLast(Snap(car.copy(), time, runTime, cones.map { it.copy() }, coinsTaken.copyOf(), conesHit, tableAngles.copyOf()))
        while (snaps.size > 80) snaps.removeFirst()
    }

    fun canRewind() = snaps.size >= 3

    /** Restores the state from ~[seconds] ago. World time keeps running so traffic stays consistent. */
    fun rewind(seconds: Double): Boolean {
        if (snaps.isEmpty()) return false
        val target = snaps.last().time - seconds
        var s = snaps.last()
        while (snaps.size > 1 && snaps.last().time > target) s = snaps.removeLast()
        if (snaps.isNotEmpty() && snaps.last().time <= target) s = snaps.last()
        val c = s.car
        car.x = c.x; car.y = c.y; car.a = c.a; car.v = 0.0; car.steer = c.steer; car.vx = 0.0; car.vy = 0.0; car.phi = c.phi; car.gear = c.gear
        runTime = s.run
        s.cones.forEachIndexed { i, cs -> val d = cones[i]; d.x = cs.x; d.y = cs.y; d.vx = 0.0; d.vy = 0.0; d.ang = cs.ang; d.spin = 0.0; d.knocked = cs.knocked }
        for (i in coinsTaken.indices) coinsTaken[i] = s.coins[i]
        coinsCollected = coinsTaken.count { it }
        conesHit = s.conesHit
        for (i in tableAngles.indices) tableAngles[i] = s.tables[i]
        tableActive = -1
        phase = Phase.PLAYING
        parkHold = 0.0
        input.clear(); input.gear = car.gear
        // keep traffic from immediately re-hitting: nudge clock forward if a traffic car overlaps
        var guard = 0
        while (guard < 200 && overlapsDynamic()) { time += 0.1; guard++ }
        return true
    }

    private fun overlapsDynamic(): Boolean {
        val bx = body().let { Box(it.cx, it.cy, it.hl + 0.6, it.hw + 0.6, it.a) }
        for (lane in level.traffic) {
            var hit = false
            lane.carsAt(time) { _, x, y -> if (Collide.boxBox(bx, lane.carBox(x, y))) hit = true }
            if (hit) return true
        }
        for (g in level.gates) if (g.isUp(time)) for (d in g.bollards) if (Collide.boxDisc(bx, d)) return true
        return false
    }

    /** Lightweight copy for look-ahead (autopilot traffic checks). */
    fun probe(): World {
        val w = World(level, spec, index)
        w.car.x = car.x; w.car.y = car.y; w.car.a = car.a; w.car.v = car.v; w.car.steer = car.steer; w.car.gear = car.gear; w.car.phi = car.phi
        w.input.gear = car.gear
        w.time = time
        w.timerStarted = true
        return w
    }
}
