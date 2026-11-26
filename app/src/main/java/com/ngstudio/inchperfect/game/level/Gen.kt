package com.ngstudio.inchperfect.game.level

import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Disc
import com.ngstudio.inchperfect.game.math.M
import com.ngstudio.inchperfect.game.math.Pose
import com.ngstudio.inchperfect.game.math.Rng
import com.ngstudio.inchperfect.game.physics.Cars

enum class Final { OPEN_BAY, FWD_BAY, REV_BAY, ANGLED, PARALLEL, GARAGE, DOCK, PILLARS, TRAILER_PULL }
enum class Seg { STRAIGHT, CORNER, S_BEND, SLALOM, HAIRPIN, TRAFFIC, GATE, TIGHT_TURN, ICE, TURNTABLE }

class Plan(
    val n: Int,
    val tightness: Double,
    val complexity: Double,
    val final: Final,
    val reverseIn: Boolean,
    val segs: List<Seg>,
    val lighting: Lighting,
    val weather: Weather,
    val trailer: Boolean,
    val c: Double,
    val cb: Double,
    val slack: Double,
    val angleTol: Double,
    val walls: List<ObKind>,
    val mechs: Set<Mech>,
    val newMech: Mech?,
    val tutorial: Boolean,
    val name: String,
    val lanes: Int = 1,
    val trafficSpeed: Double = 3.0,
    val steerK: Double = 0.9,
    val rotation: Int = 0,
    val openRows: Boolean = false,
    val startStraight: Double = 2.5,
    val startReverse: Double = 0.0,
    val coneWalls: Boolean = false,
)

/** Builds one level from a [Plan]. Returns null when the layout fails a check (caller re-rolls). */
class Gen(private val p: Plan, private val rng: Rng) {
    private val car = Cars.REF
    private val b = Builder(rng, car, if (p.trailer) Cars.TRAILER else null)
    private val finalReq = ArrayList<() -> Unit>()
    private var bay: Box? = null
    private val roadBands = ArrayList<Box>()
    private val roadSegs = ArrayList<Int>()
    private val vS = M.clamp(1.7 + 1.6 * p.c, 1.8, 3.8)
    private val vA = vS * 0.75
    private val vR = minOf(2.0, vA)
    private val front = car.length - car.rearOverhang

    // ----------------------------------------------------------------- utils
    private val primaryWall: ObKind = rng.pick(p.walls)
    private fun wallKind() = primaryWall

    private fun dims(k: ObKind): Pair<Double, Double> = when (k) { // pieceLen, thickness
        ObKind.BARRIER -> 3.0 to 0.65
        ObKind.WALL -> 4.0 to 0.45
        ObKind.BUILDING -> rng.range(6.0, 11.0) to rng.range(4.0, 8.0)
        ObKind.HEDGE -> 3.2 to 1.1
        ObKind.FENCE -> 3.0 to 0.25
        ObKind.CONTAINER -> 6.06 to 2.44
        ObKind.SIDEWALK -> 6.0 to 3.0
        ObKind.CAR -> 4.4 to 1.85
        else -> 3.0 to 0.6
    }

    private fun carColor(): Int = rng.pick(PARKED_COLORS)

    private fun hug(i0: Int, i1: Int, side: Int, c: Double, kind: ObKind = wallKind()) {
        val (len, thick) = dims(kind)
        if (kind == ObKind.CAR) {
            b.hugWalls(i0, i1, side, c, kind, len, thick, colorOf = { carColor() }, gap = rng.range(0.6, 1.6), skipChance = 0.15)
        } else {
            b.hugWalls(i0, i1, side, c, kind, len, thick)
        }
    }

    private fun bothSides(i0: Int, i1: Int, c: Double) {
        if (p.coneWalls) { b.requests.add { cones(i0, i1, -1, c, 1.7); cones(i0, i1, 1, c, 1.7) }; return }
        val k1 = wallKind()
        b.requests.add { hug(i0, i1, -1, c, k1); hug(i0, i1, 1, c, k1) }
    }

    private fun steerK() = M.clamp(p.steerK + rng.range(-0.05, 0.05), 0.55, 1.0)

    private fun cones(i0: Int, i1: Int, side: Int, c: Double, spacing: Double) {
        var acc = spacing; var lx = Double.NaN; var ly = Double.NaN
        for (i in i0 until i1) {
            val bx = b.carBoxes[i]
            if (!lx.isNaN()) acc += M.hypot(bx.cx - lx, bx.cy - ly)
            lx = bx.cx; ly = bx.cy
            if (acc < spacing) continue
            acc = 0.0
            val nx = -M.sin(bx.a) * side; val ny = M.cos(bx.a) * side
            b.place(ObKind.CONE, c, 4.0) { t -> Disc(bx.cx + nx * (car.width / 2 + 0.25 + t), bx.cy + ny * (car.width / 2 + 0.25 + t), 0.24) }
        }
    }

    // -------------------------------------------------------------- segments
    private fun segment(s: Seg, dir: Int) {
        b.beginSegment()
        when (s) {
            Seg.STRAIGHT -> {
                val i0 = b.carIndex(); b.drive(1, 0.0, rng.range(5.0, 10.0), vS); val i1 = b.carIndex()
                if (Mech.CONES in p.mechs && rng.chance(0.4)) {
                    val side = rng.sign().toInt()
                    b.requests.add { cones(i0, i1, side, p.c, 1.6); hug(i0, i1, -side, p.c) }
                } else if (p.n > 30 && rng.chance(0.25)) {
                    b.requests.add { hug(i0, i1, -1, p.c, ObKind.CAR); hug(i0, i1, 1, p.c) }
                } else bothSides(i0, i1, p.c)
            }
            Seg.CORNER -> {
                val k = steerK()
                val i0 = b.carIndex()
                b.drive(1, 0.0, rng.range(1.5, 3.5), vS)
                val r = 1.0 / (k * car.maxCurv)
                val pz = b.pose
                val tcx = pz.x - M.sin(pz.a) * dir * r; val tcy = pz.y + M.cos(pz.a) * dir * r
                b.drive(1, dir * k, b.arcLen(M.rad(90.0), k), vA)
                val mid = b.carBoxes[(i0 + b.carIndex()) / 2]
                b.drive(1, 0.0, rng.range(2.0, 4.0), vS)
                val i1 = b.carIndex()
                bothSides(i0, i1, p.c)
                if (!p.coneWalls) {
                    val dx = tcx - mid.cx; val dy = tcy - mid.cy; val l = M.hypot(dx, dy)
                    val kind = wallKind()
                    b.requests.add { b.cornerBlock(if (kind == ObKind.CAR) ObKind.HEDGE else kind, tcx - dx / l * 1.5, tcy - dy / l * 1.5, dx / l, dy / l, 2.2, pz.a, p.c) }
                }
            }
            Seg.S_BEND -> {
                val k = steerK(); val th = M.rad(rng.range(20.0, 38.0))
                val i0 = b.carIndex()
                b.drive(1, 0.0, rng.range(1.0, 2.5), vS)
                b.drive(1, dir * k, b.arcLen(th, k), vA)
                b.drive(1, 0.0, rng.range(0.0, 2.0), vA)
                b.drive(1, -dir * k, b.arcLen(th, k), vA)
                b.drive(1, 0.0, rng.range(1.5, 3.0), vS)
                bothSides(i0, b.carIndex(), p.c)
            }
            Seg.SLALOM -> {
                val i0 = b.carIndex()
                b.drive(1, 0.0, 2.5, vS)
                val m = rng.int(3, 5)
                val th = M.rad(rng.range(20.0, 30.0) * (0.85 + 0.3 * p.tightness))
                val k = 0.85
                val peaks = ArrayList<Pair<Int, Int>>()
                var sgn = dir
                b.drive(1, sgn * k, b.arcLen(th, k), vA)
                repeat(m) {
                    sgn = -sgn
                    b.drive(1, sgn * k, b.arcLen(th, k), vA)
                    peaks.add(b.carIndex() - 1 to -sgn)
                    b.drive(1, sgn * k, b.arcLen(th, k), vA)
                }
                b.drive(1, -sgn * k, b.arcLen(th, k), vA)
                b.drive(1, 0.0, 2.5, vS)
                val i1 = b.carIndex()
                b.requests.add {
                    for ((idx, bulge) in peaks) {
                        val bx = b.carBoxes[idx]
                        val nx = -M.sin(bx.a) * -bulge; val ny = M.cos(bx.a) * -bulge
                        b.place(ObKind.CONE, p.c, 5.0) { t -> Disc(bx.cx + nx * t, bx.cy + ny * t, 0.24) }
                    }
                    hug(i0, i1, -1, p.c + 0.9, ObKind.BARRIER); hug(i0, i1, 1, p.c + 0.9, ObKind.BARRIER)
                }
            }
            Seg.HAIRPIN -> {
                val k = steerK()
                val i0 = b.carIndex()
                b.drive(1, 0.0, rng.range(1.5, 3.0), vS)
                val r = 1.0 / (k * car.maxCurv)
                val pz = b.pose
                val cx = pz.x - M.sin(pz.a) * dir * r; val cy = pz.y + M.cos(pz.a) * dir * r
                b.drive(1, dir * k, b.arcLen(M.PI, k), vA)
                b.drive(1, 0.0, rng.range(2.0, 3.5), vS)
                val i1 = b.carIndex()
                b.requests.add {
                    val rr = b.shrinkDisc(cx, cy, r, p.c)
                    if (rr != null && rr > 0.5) b.add(ObKind.PLANTER, Disc(cx, cy, rr), p.c)
                    hug(i0, i1, -dir, p.c)
                }
            }
            Seg.TRAFFIC -> traffic()
            Seg.GATE -> gate()
            Seg.TIGHT_TURN -> {
                val i0 = b.carIndex()
                val start = b.pose
                val a1 = rng.range(2.5, 4.0)
                b.drive(1, 0.0, a1, vS)
                val ta = M.rad(rng.range(34.0, 44.0)); val tb = M.rad(rng.range(16.0, 24.0))
                val tc = M.rad(90.0) - ta - tb
                b.drive(1, dir.toDouble(), b.arcLen(ta, 1.0), vA)
                b.drive(-1, -dir.toDouble(), b.arcLen(tb, 1.0), vR)
                b.drive(1, dir.toDouble(), b.arcLen(tc, 1.0), vA)
                b.drive(1, 0.0, rng.range(2.5, 3.5), vS)
                val i1 = b.carIndex()
                b.requests.add {
                    val hw = car.width / 2 + p.c + 1.0
                    b.place(ObKind.WALL, p.c, 12.0) { t ->
                        Box(start.lx(a1 + front + t + 0.3, -dir * hw * 0.5), start.ly(a1 + front + t + 0.3, -dir * hw * 0.5), 0.3, hw, start.a)
                    }
                    hug(i0, i1, -1, p.c); hug(i0, i1, 1, p.c)
                }
            }
            Seg.ICE -> {
                val i0 = b.carIndex()
                val st = b.pose
                val len = rng.range(10.0, 16.0)
                b.drive(1, 0.0, len, vS * 0.8)
                val ci = p.c + 0.45
                val hw = car.width / 2 + ci + 0.6
                b.zones.add(Zone(ZoneKind.ICE, Box(st.lx(len / 2 + 1.0, 0.0), st.ly(len / 2 + 1.0, 0.0), len / 2 - 1.2, hw, st.a)))
                val i1 = b.carIndex()
                b.requests.add { hug(i0, i1, -1, ci); hug(i0, i1, 1, ci) }
            }
            Seg.TURNTABLE -> {
                val i0 = b.carIndex()
                val st = b.pose
                val a1 = rng.range(3.5, 5.0)
                b.drive(1, 0.0, a1, vS)
                val cc = b.centerOf(b.pose)
                val r = M.hypot(car.length / 2, car.width / 2) + 0.4
                b.tables.add(Turntable(cc[0], cc[1], r, dir * M.PI / 2))
                val iMid = b.carIndex()
                b.rotate(b.tables.size - 1, dir * M.PI / 2)
                val iRot = b.carIndex()
                b.drive(1, 0.0, r - car.centerOffset + rng.range(2.5, 3.5), vS)
                val i1 = b.carIndex()
                b.requests.add {
                    b.place(ObKind.WALL, p.c, 10.0) { t ->
                        Box(st.lx(a1 + car.centerOffset + r + 0.3 + t, 0.0), st.ly(a1 + car.centerOffset + r + 0.3 + t, 0.0), 0.3, r + 1.2, st.a)
                    }
                    hug(i0, iMid, -1, p.c); hug(i0, iMid, 1, p.c)
                    hug(iRot, i1, -1, p.c); hug(iRot, i1, 1, p.c)
                }
            }
        }
    }

    private fun traffic() {
        val i0 = b.carIndex()
        b.drive(1, 0.0, rng.range(2.0, 3.5), vS)
        val iA = b.carIndex()
        b.waitGap()
        val P = b.pose
        val lanes = p.lanes
        val roadStart = front + 0.7
        val w = lanes * 3.4
        val cross = roadStart + w + car.rearOverhang + 0.9
        b.drive(1, 0.0, cross, minOf(3.2, vS))
        val iB = b.carIndex()
        b.drive(1, 0.0, rng.range(2.0, 3.0), vS)
        val i1 = b.carIndex()
        val span = 60.0
        val roadMid = roadStart + w / 2
        val band = Box(P.lx(roadMid, 0.0), P.ly(roadMid, 0.0), w / 2 + 0.4, span, P.a)
        roadBands.add(band)
        roadSegs.add(b.seg)
        b.zones.add(Zone(ZoneKind.ROAD, Box(band.cx, band.cy, w / 2 + 0.3, span, P.a)))
        // crossing time from standstill (a≈2.0, vmax≈3.0)
        val d = cross
        val tCross = if (d < 2.25) M.sqrt(2 * d / 2.0) else 1.5 + (d - 2.25) / 3.0
        val speed = p.trafficSpeed * rng.range(0.9, 1.1)
        for (i in 0 until lanes) {
            val u = roadStart + 3.4 * (i + 0.5)
            val dirLane = if (i % 2 == 0) 1.0 else -1.0
            val period = maxOf(4.5, tCross * 1.7 + 4.4 / speed + 1.2 + rng.range(0.0, 3.0) * (1.0 - p.tightness))
            b.traffic.add(
                TrafficLane(
                    P.lx(u, -span * dirLane), P.ly(u, -span * dirLane), P.lx(u, span * dirLane), P.ly(u, span * dirLane),
                    speed, period, rng.range(0.0, period), seed = rng.nextInt() and 0x7fffffff,
                )
            )
            if (i > 0) b.marks.add(Mark(MarkKind.DASH, Box(P.lx(roadStart + 3.4 * i, 0.0), P.ly(roadStart + 3.4 * i, 0.0), 0.07, span, P.a)))
        }
        b.marks.add(Mark(MarkKind.LINE, Box(P.lx(roadStart + 0.15, 0.0), P.ly(roadStart + 0.15, 0.0), 0.07, span, P.a)))
        b.marks.add(Mark(MarkKind.LINE, Box(P.lx(roadStart + w - 0.15, 0.0), P.ly(roadStart + w - 0.15, 0.0), 0.07, span, P.a)))
        val stopHw = car.width / 2 + p.c
        b.marks.add(Mark(MarkKind.STOP, Box(P.lx(front + 0.25, 0.0), P.ly(front + 0.25, 0.0), 0.18, stopHw, P.a)))
        b.marks.add(Mark(MarkKind.CROSSWALK, Box(P.lx(roadStart - 0.55, 0.0), P.ly(roadStart - 0.55, 0.0), 0.0, stopHw, P.a)))
        b.requests.add {
            hug(i0, iA, -1, p.c, ObKind.SIDEWALK); hug(i0, iA, 1, p.c, ObKind.SIDEWALK)
            // exit side: only samples whose rear already cleared the road
            hug(iB, i1, -1, p.c, ObKind.SIDEWALK); hug(iB, i1, 1, p.c, ObKind.SIDEWALK)
            // sidewalks running along both road edges
            for (side in intArrayOf(-1, 1)) {
                for (edge in doubleArrayOf(roadStart - 2.0, roadStart + w + 2.0)) {
                    b.place(ObKind.SIDEWALK, p.c, 20.0) { t ->
                        val wOff = side * (car.width / 2 + 7.0 + t)
                        Box(P.lx(edge, wOff), P.ly(edge, wOff), 1.5, 6.0, P.a)
                    }
                }
            }
        }
    }

    private fun gate() {
        val i0 = b.carIndex()
        b.drive(1, 0.0, rng.range(2.0, 3.0), vS)
        b.waitGap()
        val P = b.pose
        val gateU = front + 0.9
        b.drive(1, 0.0, gateU + car.rearOverhang + 1.2, minOf(3.0, vS))
        b.drive(1, 0.0, rng.range(1.5, 2.5), vS)
        val i1 = b.carIndex()
        val hwC = car.width / 2 + p.c
        val list = ArrayList<Disc>()
        var w = -hwC + 0.2
        while (w <= hwC - 0.15) { list.add(Disc(P.lx(gateU, w), P.ly(gateU, w), 0.14)); w += 0.8 }
        val dTravel = gateU + car.length
        val tCross = M.sqrt(2 * dTravel / 2.0) + 0.6
        val openFrac = rng.range(0.5, 0.6)
        val period = maxOf(6.5, tCross * 1.8 / openFrac)
        val zone = Box(P.lx(gateU, 0.0), P.ly(gateU, 0.0), 0.5, hwC + 0.3, P.a)
        b.gates.add(Gate(list, period, openFrac, rng.range(0.0, period), zone))
        b.marks.add(Mark(MarkKind.HATCH, zone))
        b.keepOut.add(Box(zone.cx, zone.cy, 0.4, hwC - 0.05, P.a))
        b.requests.add { hug(i0, i1, -1, p.c); hug(i0, i1, 1, p.c) }
    }

    // ---------------------------------------------------------------- finals
    private fun rowNeighbors(target: Box, axisX: Double, axisY: Double, count: Int, c: Double, kind: ObKind = ObKind.CAR,
                             shapeOf: (Double, Double) -> Box = { x, y -> Box(x, y, target.hl, target.hw, target.a) }): List<Box> {
        val placed = ArrayList<Box>()
        placed.add(target)
        for (side in intArrayOf(-1, 1)) {
            var baseX = target.cx; var baseY = target.cy
            var prevHalf = target.hw
            for (i in 0 until count) {
                val skip = p.openRows && i > 0 && rng.chance(0.35)
                val ob: Box?
                if (i == 0) {
                    val o = b.place(kind, c, 14.0, if (kind == ObKind.CAR) carColor() else 0) { t ->
                        shapeOf(baseX + axisX * side * t, baseY + axisY * side * t)
                    } ?: break
                    ob = o.shape as Box
                } else {
                    val gap = rng.range(0.55, 1.1)
                    val tmp = shapeOf(0.0, 0.0)
                    val off = prevHalf + gap + tmp.hw
                    val sh = shapeOf(baseX + axisX * side * off, baseY + axisY * side * off)
                    if (skip || b.conflicts(kind, sh) || !b.clearOf(sh, c)) { baseX += axisX * side * (off); baseY += axisY * side * off; prevHalf = tmp.hw; continue }
                    ob = b.add(kind, sh, c, if (kind == ObKind.CAR) carColor() else 0).shape as Box
                }
                placed.add(ob)
                baseX = ob.cx; baseY = ob.cy; prevHalf = ob.hw
            }
        }
        return placed
    }

    /** bay lines between boxes in a row (row axis given), each line runs along the box heading */
    private fun rowLines(boxes: List<Box>, axisX: Double, axisY: Double) {
        if (boxes.isEmpty()) return
        val sorted = boxes.sortedBy { it.cx * axisX + it.cy * axisY }
        val ref = sorted[0]
        for (i in 0..sorted.size) {
            val (x, y) = when (i) {
                0 -> (sorted[0].cx - axisX * (sorted[0].hw + 0.45)) to (sorted[0].cy - axisY * (sorted[0].hw + 0.45))
                sorted.size -> (sorted.last().cx + axisX * (sorted.last().hw + 0.45)) to (sorted.last().cy + axisY * (sorted.last().hw + 0.45))
                else -> {
                    val a = sorted[i - 1]; val c = sorted[i]
                    val pa = a.cx * axisX + a.cy * axisY + a.hw; val pc = c.cx * axisX + c.cy * axisY - c.hw
                    if (pc - pa > 4.5) continue
                    val m = (pa + pc) / 2
                    val along = (a.cx * axisX + a.cy * axisY)
                    (a.cx + axisX * (m - along)) to (a.cy + axisY * (m - along))
                }
            }
            b.marks.add(Mark(MarkKind.LINE, Box(x, y, ref.hl + 0.3, 0.06, ref.a)))
        }
    }

    private fun lotZone(center: Box, extraU: Double) {
        b.zones.add(Zone(ZoneKind.LOT, Box(center.cx, center.cy, center.hl + extraU, center.hw + extraU, center.a)))
    }

    private fun finalBay() {
        b.beginSegment()
        val k = steerK()
        when (p.final) {
            Final.OPEN_BAY -> openBay()
            Final.FWD_BAY, Final.GARAGE, Final.PILLARS, Final.DOCK, Final.REV_BAY -> perpendicular(k)
            Final.ANGLED -> angled(k)
            Final.PARALLEL -> parallel(k)
            Final.TRAILER_PULL -> trailerPull(k)
        }
    }

    private fun openBay() {
        // handcrafted levels describe their own path before calling this; just mark the bay
        val fin = b.bodyAt(b.pose)
        bay = Box(fin.cx, fin.cy, fin.hl + p.slack / 2, fin.hw + 0.7, fin.a)
        finalReq.add {
            val d = 1.6 + p.cb
            for (s in intArrayOf(-1, 1)) {
                val ax = -M.sin(fin.a) * s; val ay = M.cos(fin.a) * s
                if (rng.chance(0.7)) b.place(ObKind.CAR, p.cb, 8.0, carColor()) { t -> Box(fin.cx + ax * (fin.hw * 2 + d + t), fin.cy + ay * (fin.hw * 2 + d + t), fin.hl, fin.hw, fin.a) }
            }
            b.marks.add(Mark(MarkKind.LINE, Box(fin.cx - M.sin(fin.a) * (bay!!.hw + 0.06), fin.cy + M.cos(fin.a) * (bay!!.hw + 0.06), bay!!.hl, 0.06, fin.a)))
            b.marks.add(Mark(MarkKind.LINE, Box(fin.cx + M.sin(fin.a) * (bay!!.hw + 0.06), fin.cy - M.cos(fin.a) * (bay!!.hw + 0.06), bay!!.hl, 0.06, fin.a)))
        }
    }

    private fun perpendicular(k: Double) {
        val side = rng.sign().toInt()
        val reverse = p.reverseIn
        val aisle = b.pose
        val ax = M.cos(aisle.a); val ay = M.sin(aisle.a)
        val i0 = b.carIndex()
        if (reverse) {
            b.drive(1, 0.0, rng.range(3.0, 6.0) + p.startStraight, vS)
            b.drive(-1, side * k, b.arcLen(M.rad(90.0), k), vR)
            b.drive(-1, 0.0, rng.range(0.6, 1.8), vR * 0.8)
        } else {
            b.drive(1, 0.0, rng.range(2.0, 5.0) + p.startStraight, vS)
            b.drive(1, side * k, b.arcLen(M.rad(90.0), k), vA)
            b.drive(1, 0.0, rng.range(0.8, 2.0), vA * 0.8)
        }
        val i1 = b.carIndex()
        val fin = b.bodyAt(b.pose)
        val inward = if (reverse) -1.0 else 1.0 // direction (along car heading) pointing deeper into the bay
        val dx = M.cos(fin.a) * inward; val dy = M.sin(fin.a) * inward
        val latSlack = minOf(p.cb * 0.85, 0.55)
        bay = Box(fin.cx + dx * 0.05, fin.cy + dy * 0.05, fin.hl + p.slack / 2, fin.hw + latSlack, fin.a)
        val backC = maxOf(p.cb, p.slack / 2 + 0.05)
        val count = 3 + rng.int(0, 2)
        finalReq.add {
            when (p.final) {
                Final.GARAGE -> {
                    for (s in intArrayOf(-1, 1)) b.place(ObKind.WALL, p.cb, 6.0) { t ->
                        val off = fin.hw + 0.15 + t
                        Box(fin.cx + ax * s * off + dx * 0.4, fin.cy + ay * s * off + dy * 0.4, fin.hl + 0.6, 0.15, fin.a)
                    }
                    b.place(ObKind.WALL, backC, 6.0) { t -> Box(fin.cx + dx * (fin.hl + 0.2 + t), fin.cy + dy * (fin.hl + 0.2 + t), 0.2, fin.hw + 1.2, fin.a) }
                    b.zones.add(Zone(ZoneKind.EPOXY, Box(fin.cx + dx * 0.3, fin.cy + dy * 0.3, fin.hl + 0.4, fin.hw + p.cb, fin.a)))
                    // neighbouring garages / facade
                    for (s in intArrayOf(-1, 1)) b.place(ObKind.BUILDING, p.cb + 0.3, 12.0) { t ->
                        val off = fin.hw + p.cb + 3.5 + t
                        Box(fin.cx + ax * s * off + dx * 1.0, fin.cy + ay * s * off + dy * 1.0, fin.hl + 1.2, 3.0, fin.a)
                    }
                }
                Final.PILLARS -> {
                    for (s in intArrayOf(-1, 1)) for (end in doubleArrayOf(-0.6, 0.6)) {
                        b.place(ObKind.PILLAR, p.cb, 6.0) { t ->
                            val off = fin.hw + 0.35 + t
                            Box(fin.cx + ax * s * off + dx * end * fin.hl, fin.cy + ay * s * off + dy * end * fin.hl, 0.32, 0.32, fin.a)
                        }
                    }
                    val row = rowNeighbors(fin, ax, ay, count, p.cb + 0.7)
                    rowLines(row, ax, ay)
                    b.place(ObKind.WALL, backC, 6.0) { t -> Box(fin.cx + dx * (fin.hl + 0.3 + t), fin.cy + dy * (fin.hl + 0.3 + t), 0.3, 14.0, fin.a) }
                }
                Final.DOCK -> {
                    val tl = rng.range(4.6, 6.0)
                    val row = rowNeighbors(fin, ax, ay, 2, p.cb, ObKind.TRUCK) { x, y ->
                        Box(x + dx * (tl - fin.hl), y + dy * (tl - fin.hl), tl, 1.25, fin.a)
                    }
                    rowLines(row.filter { it !== fin }, ax, ay)
                    b.place(ObKind.BUILDING, backC, 8.0) { t -> Box(fin.cx + dx * (fin.hl + 3.0 + t), fin.cy + dy * (fin.hl + 3.0 + t), 2.8, 16.0, fin.a) }
                }
                else -> {
                    val row = rowNeighbors(fin, ax, ay, count, p.cb)
                    rowLines(row, ax, ay)
                    val backKind = rng.pick(listOf(ObKind.HEDGE, ObKind.SIDEWALK, ObKind.WALL, ObKind.BARRIER))
                    b.place(backKind, backC, 6.0) { t -> Box(fin.cx + dx * (fin.hl + 0.6 + t), fin.cy + dy * (fin.hl + 0.6 + t), 0.6, 15.0, fin.a) }
                }
            }
            // opposite row across the aisle (rigid unit)
            val oppKind = if (p.final == Final.DOCK) ObKind.CONTAINER else ObKind.CAR
            val pitch = if (oppKind == ObKind.CAR) car.width + rng.range(0.8, 1.2) else 2.6
            val nOpp = 6
            val startU = rng.range(-2.0, 2.0)
            val colors = List(nOpp) { carColor() }
            val awayX = -M.sin(aisle.a) * -side; val awayY = M.cos(aisle.a) * -side
            val oppHl = if (oppKind == ObKind.CAR) car.length / 2 else 3.03
            val oppHw = if (oppKind == ObKind.CAR) car.width / 2 else 1.22
            val baseX = fin.cx; val baseY = fin.cy
            val opp = b.placeRow(oppKind, p.c, 18.0, { t ->
                (0 until nOpp).mapNotNull { j ->
                    if (p.openRows && rng.chance(0.0)) null else {
                        val u = startU + (j - nOpp / 2) * pitch
                        val cx = baseX + ax * u + awayX * (t + oppHl)
                        val cy = baseY + ay * u + awayY * (t + oppHl)
                        Box(cx, cy, oppHl, oppHw, M.wrap(aisle.a + side * M.PI / 2))
                    }
                }
            }, colors, core = setOf(nOpp / 2 - 1, nOpp / 2, nOpp / 2 + 1))
            rowLines(opp.map { it.shape as Box }, ax, ay)
            lotZone(Box(fin.cx, fin.cy, 6.0, 6.0, aisle.a), 6.0)
        }
    }

    private fun angled(k: Double) {
        val side = rng.sign().toInt()
        val aisle = b.pose
        val ax = M.cos(aisle.a); val ay = M.sin(aisle.a)
        val ang = M.rad(if (rng.chance(0.5)) 45.0 else 60.0)
        val i0 = b.carIndex()
        b.drive(1, 0.0, rng.range(2.0, 5.0) + p.startStraight, vS)
        b.drive(1, side * k, b.arcLen(ang, k), vA)
        b.drive(1, 0.0, rng.range(1.0, 2.0), vA * 0.8)
        val i1 = b.carIndex()
        val fin = b.bodyAt(b.pose)
        val dx = M.cos(fin.a); val dy = M.sin(fin.a)
        bay = Box(fin.cx, fin.cy, fin.hl + p.slack / 2, fin.hw + minOf(p.cb * 0.85, 0.55), fin.a)
        finalReq.add {
            val row = rowNeighbors(fin, ax, ay, 3, p.cb)
            for (r in row) b.marks.add(Mark(MarkKind.LINE, Box(r.cx - ax * (r.hw / M.sin(ang) + 0.3), r.cy - ay * (r.hw / M.sin(ang) + 0.3), r.hl + 0.3, 0.06, r.a)))
            row.maxByOrNull { it.cx * ax + it.cy * ay }?.let { r ->
                b.marks.add(Mark(MarkKind.LINE, Box(r.cx + ax * (r.hw / M.sin(ang) + 0.3), r.cy + ay * (r.hw / M.sin(ang) + 0.3), r.hl + 0.3, 0.06, r.a)))
            }
            val back = Box(fin.cx + dx * (fin.hl + 0.8), fin.cy + dy * (fin.hl + 0.8), 0.6, 16.0, aisle.a)
            val nx = -M.sin(aisle.a) * side; val ny = M.cos(aisle.a) * side
            b.place(ObKind.HEDGE, maxOf(p.cb, p.slack / 2), 8.0) { t -> back.moved(nx * t, ny * t) }
            // far side boundary
            b.place(wallKind(), p.c, 18.0) { t -> Box(fin.cx - nx * (2.0 + t), fin.cy - ny * (2.0 + t), 14.0, 0.6, aisle.a) }
        }
    }

    private fun parallel(k: Double) {
        val side = rng.sign().toInt()
        val road = b.pose
        val ax = M.cos(road.a); val ay = M.sin(road.a)
        val i0 = b.carIndex()
        val th = M.rad(rng.range(32.0, 42.0))
        val gapLat = M.lerp(1.4, 0.35, p.tightness) + rng.range(0.0, 0.3)
        val r = 1.0 / (k * car.maxCurv)
        val arcShift = 2 * r * (1 - M.cos(th))
        val dStraight = maxOf(0.0, (car.width + gapLat - arcShift) / M.sin(th))
        b.drive(1, 0.0, rng.range(4.0, 7.0) + p.startStraight, vS)
        b.drive(-1, side * k, b.arcLen(th, k), vR)
        b.drive(-1, 0.0, dStraight, vR)
        b.drive(-1, -side * k, b.arcLen(th, k), vR)
        val i1 = b.carIndex()
        val fin = b.bodyAt(b.pose)
        bay = Box(fin.cx, fin.cy, fin.hl + p.slack / 2, fin.hw + 0.3, fin.a)
        finalReq.add {
            val longC = maxOf(p.cb, 0.15)
            val f = b.place(ObKind.CAR, longC, 12.0, carColor()) { t -> fin.moved(ax * (fin.hl * 2 + 0.05 + t), ay * (fin.hl * 2 + 0.05 + t)) }
            val r0 = b.place(ObKind.CAR, longC, 12.0, carColor()) { t -> fin.moved(-ax * (fin.hl * 2 + 0.05 + t), -ay * (fin.hl * 2 + 0.05 + t)) }
            // more cars along the curb
            for ((anchor, s) in listOf(f to 1.0, r0 to -1.0)) {
                var a = anchor?.shape as? Box ?: continue
                repeat(2) {
                    val off = a.hl * 2 + rng.range(1.0, 2.2)
                    val sh = a.moved(ax * s * off, ay * s * off)
                    if (!b.conflicts(ObKind.CAR, sh) && b.clearOf(sh, p.c)) { b.add(ObKind.CAR, sh, p.c, carColor()); a = sh }
                }
            }
            val nx = -M.sin(road.a) * side; val ny = M.cos(road.a) * side
            val curbC = M.lerp(0.45, 0.12, p.tightness)
            b.place(ObKind.SIDEWALK, curbC, 6.0) { t -> Box(fin.cx + nx * (fin.hw + 1.5 + t), fin.cy + ny * (fin.hw + 1.5 + t), 24.0, 1.5, road.a) }
            // painted parking lane ticks
            b.marks.add(Mark(MarkKind.LINE, Box(fin.cx - nx * (fin.hw + 0.25), fin.cy - ny * (fin.hw + 0.25), 24.0, 0.05, road.a)))
            // far side of the street
            val farKind = rng.pick(listOf(ObKind.SIDEWALK, ObKind.CAR))
            if (farKind == ObKind.CAR) {
                val colors = List(5) { carColor() }
                b.placeRow(ObKind.CAR, p.c, 16.0, { t ->
                    (0 until 5).map { j ->
                        val u = (j - 2) * (car.length + 1.4)
                        Box(fin.cx + ax * u - nx * (t + 2.0), fin.cy + ay * u - ny * (t + 2.0), car.length / 2, car.width / 2, road.a)
                    }
                }, colors)
            } else b.place(ObKind.SIDEWALK, p.c, 16.0) { t -> Box(fin.cx - nx * (1.5 + t), fin.cy - ny * (1.5 + t), 24.0, 1.5, road.a) }
            b.zones.add(Zone(ZoneKind.ROAD, Box(fin.cx, fin.cy, 26.0, 5.0, road.a)))
            hug(i0, i1, -side, p.c)
            // street furniture on the sidewalk
            for (j in -2..2) {
                val px = fin.cx + ax * j * 7.0 + nx * (fin.hw + 2.6 + curbC); val py = fin.cy + ay * j * 7.0 + ny * (fin.hw + 2.6 + curbC)
                if (rng.chance(0.6)) b.prop(if (rng.chance(0.5)) PropKind.TREE else PropKind.LAMP, px, py, road.a, rng.range(0.9, 1.2))
            }
        }
    }

    private fun trailerPull(k: Double) {
        val side = rng.sign().toInt()
        val road = b.pose
        val i0 = b.carIndex()
        val th = M.rad(rng.range(18.0, 28.0))
        b.drive(1, 0.0, rng.range(3.0, 6.0) + p.startStraight, vS * 0.8)
        b.drive(1, side * k * 0.8, b.arcLen(th, k * 0.8), vA * 0.7)
        b.drive(1, 0.0, rng.range(0.5, 1.5), vA * 0.7)
        b.drive(1, -side * k * 0.8, b.arcLen(th, k * 0.8), vA * 0.7)
        b.drive(1, 0.0, rng.range(6.0, 8.0), vA * 0.6)
        val i1 = b.carIndex()
        val fin = b.bodyAt(b.pose)
        val tr = b.sweep.last()
        val fx = fin.cx + M.cos(fin.a) * fin.hl; val fy = fin.cy + M.sin(fin.a) * fin.hl
        val rx = tr.cx - M.cos(tr.a) * tr.hl; val ry = tr.cy - M.sin(tr.a) * tr.hl
        val len = M.hypot(fx - rx, fy - ry)
        bay = Box((fx + rx) / 2, (fy + ry) / 2, len / 2 + p.slack / 2, fin.hw + 0.4, fin.a)
        finalReq.add {
            val bb = bay!!
            val nx = -M.sin(bb.a); val ny = M.cos(bb.a)
            for (s in intArrayOf(-1, 1)) {
                b.place(ObKind.BOAT, p.cb, 10.0) { t -> Box(bb.cx + nx * s * (bb.hw * 2 + 0.3 + t), bb.cy + ny * s * (bb.hw * 2 + 0.3 + t), bb.hl - 0.4, 1.1, bb.a) }
            }
            b.place(ObKind.BARRIER, maxOf(p.cb, p.slack / 2), 8.0) { t -> Box(bb.cx + M.cos(bb.a) * (bb.hl + 0.5 + t), bb.cy + M.sin(bb.a) * (bb.hl + 0.5 + t), 0.4, 6.0, bb.a) }
            b.zones.add(Zone(ZoneKind.PLATE, Box(bb.cx, bb.cy, bb.hl + 1.0, 8.0, bb.a)))
            hug(i0, i1, -side, p.c + 0.3)
        }
    }

    // ------------------------------------------------------------- assembly
    private fun pathSelfClear(): Boolean {
        val boxes = b.carBoxes
        val segs = b.segOf
        val minSep = 2 * p.c + 1.4
        val stride = 3
        var i = 0
        while (i < boxes.size) {
            val a = boxes[i]
            var j = i + stride
            while (j < boxes.size) {
                if (segs[j] >= segs[i] + 2) {
                    val c = boxes[j]
                    val dx = a.cx - c.cx; val dy = a.cy - c.cy
                    val lim = a.radius + c.radius + minSep
                    if (dx * dx + dy * dy < lim * lim) {
                        if (com.ngstudio.inchperfect.game.math.Collide.boxBoxDist(a, c) < minSep) return false
                    }
                }
                j += stride
            }
            i += stride
        }
        // traffic roads must not cross any other part of the path
        roadBands.forEachIndexed { bi, band ->
            val wide = Box(band.cx, band.cy, band.hl + 1.5, band.hw, band.a)
            for (k in boxes.indices step 2) {
                if (segs[k] == roadSegs[bi]) continue
                if (com.ngstudio.inchperfect.game.math.Collide.boxBox(wide, boxes[k])) return false
            }
        }
        return true
    }

    fun build(): Level? {
        b.startPose(Pose(0.0, 0.0, 0.0))
        val i0 = b.carIndex()
        if (p.startReverse > 0) b.drive(-1, 0.0, p.startReverse, vR) else b.drive(1, 0.0, p.startStraight, vS)
        val iS = b.carIndex()
        b.requests.add { bothSidesNow(i0, iS) }
        var heading = 0.0
        for (s in p.segs) {
            val prefer = if (heading > 0.3) -1 else if (heading < -0.3) 1 else rng.sign().toInt()
            val dir = if (rng.chance(0.8)) prefer else -prefer
            segment(s, dir)
            heading = M.wrap(b.pose.a)
            if (kotlin.math.abs(heading) > M.rad(135.0) && s != Seg.HAIRPIN) return null
        }
        finalBay()
        if (!pathSelfClear()) return null
        b.freeze()
        for (band in roadBands) b.keepOut.add(band)
        finalReq.forEach { it() }
        b.requests.forEach { it() }
        decorate()
        b.lateRequests.forEach { it() }
        return assemble()
    }

    private fun bothSidesNow(i0: Int, i1: Int) {
        val k = wallKind()
        hug(i0, i1, -1, p.c, k); hug(i0, i1, 1, p.c, k)
    }

    private fun decorate() {
        // trees & lamps in free spots near the path but outside walls
        val boxes = b.carBoxes
        var acc = 0.0
        for (i in 1 until boxes.size step 4) {
            acc += M.hypot(boxes[i].cx - boxes[i - 1].cx, boxes[i].cy - boxes[i - 1].cy) * 4
            if (acc < 7.0) continue
            acc = 0.0
            val bx = boxes[i]
            val side = rng.sign()
            val dist = car.width / 2 + p.c + rng.range(2.6, 5.0)
            val x = bx.cx - M.sin(bx.a) * side * dist; val y = bx.cy + M.cos(bx.a) * side * dist
            val kind = when {
                p.lighting == Lighting.NIGHT || p.lighting == Lighting.DUSK -> if (rng.chance(0.55)) PropKind.LAMP else PropKind.TREE
                p.weather == Weather.SNOW -> if (rng.chance(0.5)) PropKind.SNOWPILE else PropKind.TREE
                else -> if (rng.chance(0.75)) PropKind.TREE else PropKind.BUSH
            }
            if (b.freeSpot(x, y, 0.6, 1.4)) b.prop(kind, x, y, rng.range(0.0, M.TAU), rng.range(0.8, 1.25))
        }
        // ground decals on the driving surface
        for (i in boxes.indices step 37) {
            val bx = boxes[i]
            val kind = rng.pick(if (p.weather == Weather.RAIN) listOf(PropKind.PUDDLE, PropKind.PUDDLE, PropKind.OIL, PropKind.MANHOLE) else listOf(PropKind.CRACK, PropKind.OIL, PropKind.MANHOLE, PropKind.DRAIN, PropKind.LEAVES))
            b.prop(kind, bx.cx + rng.range(-1.5, 1.5), bx.cy + rng.range(-1.5, 1.5), rng.range(0.0, M.TAU), rng.range(0.7, 1.3))
        }
        // coins along the verified path
        if (!p.tutorial || p.n >= 5) {
            val count = if (p.n % 10 == 0) 3 else rng.int(0, 2) + if (p.n > 10) 1 else 0
            val total = boxes.size
            for (j in 0 until count) {
                val idx = ((0.22 + 0.5 * (j + rng.next() * 0.6) / maxOf(1, count)) * total).toInt().coerceIn(1, total - 1)
                val bx = boxes[idx]
                b.coins.add(doubleArrayOf(bx.cx, bx.cy))
            }
        }
    }

    private fun assemble(): Level? {
        val bayBox = bay ?: return null
        // bounds
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        fun inc(x: Double, y: Double, r: Double) {
            if (x - r < minX) minX = x - r; if (y - r < minY) minY = y - r
            if (x + r > maxX) maxX = x + r; if (y + r > maxY) maxY = y + r
        }
        for (bx in b.sweep) inc(bx.cx, bx.cy, bx.radius + 3.0)
        val margin = maxOf(4.0, p.c + 3.0)
        minX -= margin - 3.0; minY -= margin - 3.0; maxX += margin - 3.0; maxY += margin - 3.0
        // drop obstacles entirely outside, clip nothing else
        val obs = ArrayList<Obstacle>()
        for (o in b.obstacles) {
            val s = o.shape
            if (s.cx + s.radius < minX - 1 || s.cx - s.radius > maxX + 1 || s.cy + s.radius < minY - 1 || s.cy - s.radius > maxY + 1) continue
            obs.add(o)
        }
        // boundary walls (solid), split around traffic roads
        val border = rng.pick(p.walls.filter { it != ObKind.CAR && it != ObKind.CONTAINER }.ifEmpty { listOf(ObKind.BARRIER) })
        val t = 0.6
        val edges = listOf(
            doubleArrayOf(minX, minY - t, maxX, minY - t), doubleArrayOf(minX, maxY + t, maxX, maxY + t),
            doubleArrayOf(minX - t, minY, minX - t, maxY), doubleArrayOf(maxX + t, minY, maxX + t, maxY),
        )
        for (e in edges) {
            val horizontal = e[1] == e[3]
            val lo = if (horizontal) e[0] - t else e[1] - t
            val hi = if (horizontal) e[2] + t else e[3] + t
            val cuts = ArrayList<DoubleArray>()
            for (band in roadBands) {
                // intersect band centre line with this edge line
                val ux = -band.s; val uy = band.c // band runs along its width axis
                val along = if (horizontal) {
                    if (kotlin.math.abs(uy) < 1e-6) continue
                    val tt = (e[1] - band.cy) / uy; band.cx + ux * tt
                } else {
                    if (kotlin.math.abs(ux) < 1e-6) continue
                    val tt = (e[0] - band.cx) / ux; band.cy + uy * tt
                }
                cuts.add(doubleArrayOf(along - band.hl - 1.0, along + band.hl + 1.0))
            }
            cuts.sortBy { it[0] }
            var cur = lo
            val pieces = ArrayList<DoubleArray>()
            for (cIv in cuts) { if (cIv[0] > cur) pieces.add(doubleArrayOf(cur, cIv[0])); cur = maxOf(cur, cIv[1]) }
            if (cur < hi) pieces.add(doubleArrayOf(cur, hi))
            for (pc in pieces) {
                val len = pc[1] - pc[0]; if (len < 0.5) continue
                val mid = (pc[0] + pc[1]) / 2
                val sh = if (horizontal) Box(mid, e[1], len / 2, t, 0.0) else Box(e[0], mid, len / 2, t, M.PI / 2)
                obs.add(Obstacle(border, sh, rng.nextInt() and 0x7fffffff))
            }
        }
        // reference path for ghost / hint
        val ref = ArrayList<Float>()
        var lx = Double.NaN; var ly = Double.NaN
        for (bx in b.carBoxes) {
            if (!lx.isNaN() && M.hypot(bx.cx - lx, bx.cy - ly) < 0.3) continue
            ref.add(bx.cx.toFloat()); ref.add(bx.cy.toFloat()); lx = bx.cx; ly = bx.cy
        }
        val refTime = referenceTime(b.steps, p.trailer)
        val factor = if (p.tutorial) 2.6 else Difficulty.starFactor(p.tightness)
        val t3 = refTime * factor + 2.0
        val lvl = Level(
            number = p.n, name = p.name, district = (p.n - 1) / LEVELS_PER_DISTRICT,
            minX = minX, minY = minY, maxX = maxX, maxY = maxY,
            start = Pose(0.0, 0.0, 0.0), startGear = 1, bay = bayBox, angleTolDeg = p.angleTol,
            obstacles = obs, props = b.props, marks = b.marks, zones = b.zones, traffic = b.traffic, gates = b.gates,
            turntables = b.tables, coins = b.coins, lighting = p.lighting, weather = p.weather, trailer = p.trailer,
            steps = b.steps, refPath = ref.toFloatArray(), t3 = t3, t2 = t3 * 1.5,
            tightness = p.tightness, complexity = p.complexity, mechanics = p.mechs, newMechanic = p.newMech,
            tutorial = p.tutorial, clearance = p.c, seed = 0L, attempts = 0,
        )
        return if (p.rotation == 0) lvl else Transform.rotate(lvl, p.rotation)
    }

    companion object {
        val PARKED_COLORS = listOf(
            0xE9EDF2, 0xC8CED6, 0x2B2F36, 0x1C2A44, 0x8E1B1B, 0xB33A3A, 0x2F5D8A, 0x3C7DC4, 0x5E7D3A,
            0xD9C9A3, 0x6B4E3D, 0x9AA3AD, 0x40464F, 0xF2E3C6, 0x7A1F4B, 0x1E5E5A,
        )

        /** Human-like reference time: steers while rolling, accelerates/decelerates smoothly. */
        fun referenceTime(steps: List<Step>, trailer: Boolean): Double {
            val acc = 1.8; val dec = 2.2
            var t = 0.0
            var i = 0
            while (i < steps.size) {
                val s = steps[i]
                when (s.type) {
                    Prim.WAIT -> t += 2.5
                    Prim.ROTATE -> t += 1.6 + 0.8
                    Prim.DRIVE -> {
                        // merge a run of same-gear drives
                        var len = 0.0; var vmin = Double.MAX_VALUE; var steerTime = 0.0
                        var lastSteer = s.steer
                        var j = i
                        while (j < steps.size && steps[j].type == Prim.DRIVE && steps[j].gear == s.gear) {
                            len += steps[j].dist
                            vmin = minOf(vmin, steps[j].vmax)
                            steerTime += kotlin.math.abs(steps[j].steer - lastSteer) / 2.4 * 0.4
                            lastSteer = steps[j].steer
                            j++
                        }
                        val v = if (trailer) vmin * 0.8 else vmin
                        val dAcc = v * v / (2 * acc); val dDec = v * v / (2 * dec)
                        t += if (len >= dAcc + dDec) v / acc + v / dec + (len - dAcc - dDec) / v
                        else 2 * M.sqrt(len / (acc + dec) * 2)
                        t += steerTime + 0.6 // gear change / reaction
                        i = j
                        continue
                    }
                }
                i++
            }
            return t
        }
    }
}
