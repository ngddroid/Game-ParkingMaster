package com.ngstudio.inchperfect.game.level

import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Disc
import com.ngstudio.inchperfect.game.math.M
import com.ngstudio.inchperfect.game.math.Pose
import com.ngstudio.inchperfect.game.math.Rng
import com.ngstudio.inchperfect.game.math.Shape

/**
 * Deterministic level factory: level n always produces the identical layout on every device.
 * Levels 1–10 are handcrafted tutorials; 11–10,000 come from the path-first generator.
 */
object LevelGenerator {
    private const val MAX_ATTEMPTS = 48

    private val cache = object : LinkedHashMap<Int, Level>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Level>?) = size > 6
    }

    @Synchronized
    fun level(n: Int): Level = cache.getOrPut(n) { generate(n) }

    fun seedFor(n: Int, attempt: Int): Long =
        Rng.mix(n * 1_000_003L + attempt * 7_919L + GENERATOR_VERSION * 1_000_000_007L)

    fun generate(n: Int): Level {
        require(n in 1..LEVEL_COUNT) { "level out of range: $n" }
        for (attempt in 0 until MAX_ATTEMPTS) {
            val seed = seedFor(n, attempt)
            val rng = Rng(seed)
            val plan = if (n <= TUTORIAL_LEVELS) tutorialPlan(n, rng) else plan(n, rng, attempt)
            val lvl = try { Gen(plan, rng).build() } catch (e: IllegalStateException) { null } ?: continue
            if (!Validator.quick(lvl)) continue
            lvl.seed = seed; lvl.attempts = attempt
            return lvl
        }
        // Fallback: a plain bay (always succeeds)
        val rng = Rng(seedFor(n, 999))
        val base = plan(n, rng, 999)
        val simple = Plan(
            n, base.tightness, base.complexity, Final.FWD_BAY, false, emptyList(), base.lighting, Weather.CLEAR, false,
            base.c, base.cb, base.slack, base.angleTol, base.walls, setOf(Mech.FWD_BAY), null, false, base.name,
        )
        return Gen(simple, rng).build()!!.also { it.seed = seedFor(n, 999); it.attempts = MAX_ATTEMPTS }
    }

    // ------------------------------------------------------------------ plans
    private val WALLS: List<List<ObKind>> = listOf(
        listOf(ObKind.HEDGE, ObKind.BARRIER, ObKind.FENCE),
        listOf(ObKind.BUILDING, ObKind.BARRIER, ObKind.SIDEWALK),
        listOf(ObKind.CONTAINER, ObKind.BARRIER),
        listOf(ObKind.WALL, ObKind.BARRIER),
        listOf(ObKind.BUILDING, ObKind.BARRIER),
        listOf(ObKind.BUILDING, ObKind.HEDGE, ObKind.SIDEWALK),
        listOf(ObKind.BARRIER, ObKind.WALL),
        listOf(ObKind.WALL, ObKind.FENCE),
        listOf(ObKind.BARRIER, ObKind.HEDGE),
        listOf(ObKind.BARRIER, ObKind.FENCE),
        listOf(ObKind.BUILDING, ObKind.WALL),
        listOf(ObKind.CONTAINER, ObKind.WALL),
        listOf(ObKind.WALL, ObKind.BARRIER),
        listOf(ObKind.BARRIER, ObKind.WALL),
        listOf(ObKind.FENCE, ObKind.HEDGE),
        listOf(ObKind.BARRIER, ObKind.FENCE),
        listOf(ObKind.HEDGE),
        listOf(ObKind.BARRIER, ObKind.WALL),
        listOf(ObKind.WALL),
        listOf(ObKind.BUILDING, ObKind.BARRIER, ObKind.HEDGE, ObKind.CONTAINER, ObKind.WALL),
    )

    private val NAMES: Map<Final, List<String>> = mapOf(
        Final.REV_BAY to listOf("Back It Up", "Reverse Gear", "Rear View", "Tail First", "Backstage", "Reverse Psychology", "Mirror Check", "Backflip"),
        Final.FWD_BAY to listOf("Nose Dive", "Straight In", "Front Row", "Head First", "Pull In", "Nose to Nose", "Clean Entry"),
        Final.ANGLED to listOf("Slant Bay", "On the Angle", "Diagonal Duty", "Herringbone", "Lean In", "Tilted"),
        Final.PARALLEL to listOf("Curb Kiss", "Side Street", "Squeeze Play", "Kerbside", "Gap Hunter", "Tight Gap", "Street Smart"),
        Final.GARAGE to listOf("Garage Door", "Lock-Up", "Home Base", "Box Room", "Wall to Wall", "Snug Fit"),
        Final.DOCK to listOf("Dock Duty", "Big Rigs", "Loading Bay", "Freight Line", "Between Giants"),
        Final.PILLARS to listOf("Pillar Talk", "Concrete Jungle", "Underground", "Column Dodge", "Level B2"),
        Final.TRAILER_PULL to listOf("Tow Pro", "Long Load", "Hitch Hiker", "Boat Day", "Trailer Trail"),
        Final.OPEN_BAY to listOf("Open Lot"),
    )

    fun plan(n: Int, rng: Rng, attempt: Int): Plan {
        val d0 = Difficulty.tightness(n)
        val cx = Difficulty.complexity(n)
        val district = (n - 1) / LEVELS_PER_DISTRICT
        val avail = Difficulty.available(n)
        val intro = Difficulty.introducedAt(n)
        val boss = n % 10 == 0

        val trailer = Mech.TRAILER in avail && (intro == Mech.TRAILER || rng.chance(if (district == 11) 0.45 else 0.06))

        // ---- final archetype
        val finalForced = when (intro) {
            Mech.FWD_BAY -> Final.FWD_BAY; Mech.REV_BAY -> Final.REV_BAY; Mech.ANGLED -> Final.ANGLED
            Mech.PARALLEL -> Final.PARALLEL; Mech.GARAGE -> Final.GARAGE; Mech.DOCK -> Final.DOCK
            Mech.PILLARS -> Final.PILLARS; Mech.TRAILER -> Final.TRAILER_PULL
            else -> null
        }
        val final = finalForced ?: if (trailer) Final.TRAILER_PULL else {
            val pool = ArrayList<Pair<Final, Double>>()
            pool.add(Final.FWD_BAY to 1.0)
            if (Mech.REV_BAY in avail) pool.add(Final.REV_BAY to 1.5)
            if (Mech.ANGLED in avail) pool.add(Final.ANGLED to 0.7)
            if (Mech.PARALLEL in avail) pool.add(Final.PARALLEL to if (district == 1) 3.0 else 0.9)
            if (Mech.GARAGE in avail) pool.add(Final.GARAGE to if (district == 3 || district == 16) 1.8 else 0.6)
            if (Mech.DOCK in avail) pool.add(Final.DOCK to if (district == 2 || district == 11) 2.5 else 0.5)
            if (Mech.PILLARS in avail) pool.add(Final.PILLARS to if (district == 3 || district == 18) 2.5 else 0.5)
            rng.weighted(pool)
        }
        val reverseIn = when (final) {
            Final.REV_BAY, Final.DOCK -> true
            Final.FWD_BAY -> false
            Final.GARAGE, Final.PILLARS -> rng.chance(0.65)
            else -> false
        }

        // ---- lighting & weather
        val nightAvail = Mech.NIGHT in avail
        val lighting = when {
            intro == Mech.NIGHT -> Lighting.NIGHT
            !nightAvail -> if (rng.chance(0.2)) Lighting.GOLDEN else Lighting.DAY
            district == 4 || district == 18 -> if (rng.chance(0.8)) Lighting.NIGHT else Lighting.DUSK
            else -> rng.weighted(listOf(Lighting.DAY to 0.55, Lighting.GOLDEN to 0.15, Lighting.DUSK to 0.15, Lighting.NIGHT to 0.15))
        }
        val weather = when {
            intro == Mech.RAIN -> Weather.RAIN
            Mech.RAIN !in avail -> Weather.CLEAR
            district == 8 -> if (rng.chance(0.8)) Weather.SNOW else Weather.CLEAR
            district == 5 -> if (rng.chance(0.7)) Weather.RAIN else Weather.CLEAR
            n > 500 && rng.chance(0.05) -> Weather.SNOW
            rng.chance(0.12) -> Weather.RAIN
            else -> Weather.CLEAR
        }

        // ---- lead-in segments
        val segIntro = when (intro) {
            Mech.SLALOM -> Seg.SLALOM; Mech.HAIRPIN -> Seg.HAIRPIN; Mech.TRAFFIC -> Seg.TRAFFIC; Mech.GATES -> Seg.GATE
            Mech.TIGHT_TURN -> Seg.TIGHT_TURN; Mech.ICE -> Seg.ICE; Mech.TURNTABLE -> Seg.TURNTABLE
            Mech.CONES -> Seg.STRAIGHT
            else -> null
        }
        var count = kotlin.math.floor(cx * 4.2).toInt() + (if (rng.chance(0.45)) 1 else 0) + (if (boss) 1 else 0)
        count = count.coerceAtMost(if (n % 100 == 0) 5 else 4)
        if (attempt > 16) count = maxOf(0, count - 1)
        if (attempt > 32) count = 0
        val pool = ArrayList<Pair<Seg, Double>>()
        pool.add(Seg.STRAIGHT to 1.0); pool.add(Seg.CORNER to 1.7)
        if (n >= 25) pool.add(Seg.S_BEND to 1.0)
        if (!trailer) {
            if (Mech.SLALOM in avail) pool.add(Seg.SLALOM to 0.55)
            if (Mech.HAIRPIN in avail) pool.add(Seg.HAIRPIN to 0.45)
            if (Mech.TRAFFIC in avail) pool.add(Seg.TRAFFIC to if (district == 9 || district == 1) 1.6 else 0.6)
            if (Mech.GATES in avail) pool.add(Seg.GATE to if (district == 13 || district == 15) 1.5 else 0.45)
            if (Mech.TIGHT_TURN in avail) pool.add(Seg.TIGHT_TURN to 0.5)
            if (Mech.ICE in avail) pool.add(Seg.ICE to if (district == 8 || weather == Weather.SNOW) 1.6 else 0.25)
            if (Mech.TURNTABLE in avail) pool.add(Seg.TURNTABLE to 0.4)
        }
        val segs = ArrayList<Seg>()
        if (segIntro != null) segs.add(segIntro)
        while (segs.size < count) {
            val s = rng.weighted(pool)
            if (segs.isNotEmpty() && segs.last() == s && s != Seg.CORNER) continue
            segs.add(s)
        }
        if (segIntro != null && segs.size > 1) { segs.remove(segIntro); segs.add(segs.size / 2, segIntro) }

        // ---- mechanics in play & effective difficulty
        val mechs = LinkedHashSet<Mech>()
        mechs.add(when (final) {
            Final.FWD_BAY -> Mech.FWD_BAY; Final.REV_BAY -> Mech.REV_BAY; Final.ANGLED -> Mech.ANGLED
            Final.PARALLEL -> Mech.PARALLEL; Final.GARAGE -> Mech.GARAGE; Final.DOCK -> Mech.DOCK
            Final.PILLARS -> Mech.PILLARS; Final.TRAILER_PULL -> Mech.TRAILER; Final.OPEN_BAY -> Mech.FWD_BAY
        })
        if (final == Final.GARAGE || final == Final.PILLARS) mechs.add(if (reverseIn) Mech.REV_BAY else Mech.FWD_BAY)
        for (s in segs) when (s) {
            Seg.SLALOM -> mechs.add(Mech.SLALOM); Seg.HAIRPIN -> mechs.add(Mech.HAIRPIN); Seg.TRAFFIC -> mechs.add(Mech.TRAFFIC)
            Seg.GATE -> mechs.add(Mech.GATES); Seg.TIGHT_TURN -> mechs.add(Mech.TIGHT_TURN); Seg.ICE -> mechs.add(Mech.ICE)
            Seg.TURNTABLE -> mechs.add(Mech.TURNTABLE); else -> {}
        }
        if (Mech.CONES in avail && (intro == Mech.CONES || rng.chance(0.3))) mechs.add(Mech.CONES)
        if (lighting == Lighting.NIGHT) mechs.add(Mech.NIGHT)
        if (weather == Weather.RAIN) mechs.add(Mech.RAIN)
        var d = d0 - mechs.sumOf { Difficulty.penalty(it) }
        if (intro != null) d *= 0.72
        d = M.clamp(d, 0.10, 1.0)

        val name = rng.pick(NAMES.getValue(final))
        return Plan(
            n = n, tightness = d, complexity = cx, final = final, reverseIn = reverseIn, segs = segs,
            lighting = lighting, weather = weather, trailer = trailer,
            c = Difficulty.corridorClearance(d), cb = Difficulty.bayClearance(d), slack = Difficulty.bayLengthSlack(d),
            angleTol = Difficulty.angleTolDeg(d), walls = WALLS[district.coerceIn(0, WALLS.size - 1)],
            mechs = mechs, newMech = intro, tutorial = false, name = name,
            lanes = (1 + (if (cx > 0.35 && rng.chance(0.6)) 1 else 0) + (if (cx > 0.7 && rng.chance(0.5)) 1 else 0)),
            trafficSpeed = Difficulty.trafficSpeed(d), steerK = M.lerp(0.72, 1.0, d),
            rotation = rng.weighted(listOf(0 to 0.5, 2 to 0.2, 1 to 0.15, 3 to 0.15)),
            openRows = d < 0.33,
            startStraight = rng.range(2.0, 4.0),
        )
    }

    /** Handcrafted tutorial levels: big spaces, crashes rewind for free. */
    fun tutorialPlan(n: Int, rng: Rng): Plan {
        val walls = listOf(ObKind.HEDGE, ObKind.BARRIER, ObKind.FENCE)
        fun tp(
            name: String, final: Final, segs: List<Seg> = emptyList(), c: Double = 2.0, cb: Double = 1.6,
            reverse: Boolean = false, lighting: Lighting = Lighting.DAY, mech: Set<Mech>, startStraight: Double = 3.0,
            startReverse: Double = 0.0, cones: Boolean = false, rotation: Int = 0,
        ) = Plan(
            n, 0.02 * n, 0.0, final, reverse, segs, lighting, Weather.CLEAR, false, c, cb, 1.8, 14.0, walls, mech,
            null, true, name, lanes = 1, trafficSpeed = 2.4, steerK = 0.7, rotation = rotation, openRows = true,
            startStraight = startStraight, startReverse = startReverse, coneWalls = cones,
        )
        return when (n) {
            1 -> tp("First Gear", Final.OPEN_BAY, startStraight = 14.0, mech = setOf(Mech.FWD_BAY))
            2 -> tp("Easy Turn", Final.OPEN_BAY, listOf(Seg.CORNER), startStraight = 5.0, mech = setOf(Mech.FWD_BAY))
            3 -> tp("Back It Up", Final.OPEN_BAY, startReverse = 9.0, mech = setOf(Mech.REV_BAY))
            4 -> tp("Cone Alley", Final.OPEN_BAY, listOf(Seg.S_BEND), c = 1.5, cones = true, startStraight = 4.0, mech = setOf(Mech.CONES))
            else -> tp("Rookie Finale", Final.REV_BAY, listOf(Seg.STRAIGHT), c = 1.6, cb = 1.15, reverse = true, mech = setOf(Mech.REV_BAY))
        }
    }
}

/** Light checks run on device after generation. The full proof (autopilot) runs in unit tests. */
object Validator {
    fun quick(l: Level): Boolean {
        val bay = l.bay
        if (bay.cx < l.minX || bay.cx > l.maxX || bay.cy < l.minY || bay.cy > l.maxY) return false
        if (l.width > 260 || l.height > 260) return false
        if (l.obstacles.size > 420) return false
        // the bay must be free of solid obstacles
        for (o in l.obstacles) {
            if (!o.kind.solid) continue
            if (com.ngstudio.inchperfect.game.math.Collide.overlap(o.shape, Box(bay.cx, bay.cy, bay.hl - 0.3, bay.hw - 0.2, bay.a))) return false
        }
        return true
    }
}

/** Rigid rotation of a whole level by quarter turns about the origin (adds layout variety). */
object Transform {
    fun rotate(l: Level, quarter: Int): Level {
        val th = quarter * M.PI / 2
        val c = M.cos(th); val s = M.sin(th)
        fun rx(x: Double, y: Double) = x * c - y * s
        fun ry(x: Double, y: Double) = x * s + y * c
        fun box(b: Box) = Box(rx(b.cx, b.cy), ry(b.cx, b.cy), b.hl, b.hw, M.wrap(b.a + th))
        fun shape(sh: Shape): Shape = when (sh) { is Box -> box(sh); is Disc -> Disc(rx(sh.cx, sh.cy), ry(sh.cx, sh.cy), sh.r) }
        val xs = doubleArrayOf(rx(l.minX, l.minY), rx(l.maxX, l.minY), rx(l.minX, l.maxY), rx(l.maxX, l.maxY))
        val ys = doubleArrayOf(ry(l.minX, l.minY), ry(l.maxX, l.minY), ry(l.minX, l.maxY), ry(l.maxX, l.maxY))
        val ref = FloatArray(l.refPath.size)
        for (i in 0 until l.refPath.size / 2) {
            val x = l.refPath[i * 2].toDouble(); val y = l.refPath[i * 2 + 1].toDouble()
            ref[i * 2] = rx(x, y).toFloat(); ref[i * 2 + 1] = ry(x, y).toFloat()
        }
        return Level(
            l.number, l.name, l.district, xs.min(), ys.min(), xs.max(), ys.max(),
            Pose(rx(l.start.x, l.start.y), ry(l.start.x, l.start.y), M.wrap(l.start.a + th)), l.startGear,
            box(l.bay), l.angleTolDeg,
            l.obstacles.map { Obstacle(it.kind, shape(it.shape), it.style, it.color) },
            l.props.map { Prop(it.kind, rx(it.x, it.y), ry(it.x, it.y), it.a + th, it.size, it.variant) },
            l.marks.map { Mark(it.kind, box(it.box)) },
            l.zones.map { Zone(it.kind, box(it.box)) },
            l.traffic.map { TrafficLane(rx(it.x0, it.y0), ry(it.x0, it.y0), rx(it.x1, it.y1), ry(it.x1, it.y1), it.speed, it.period, it.phase, it.carLen, it.carWidth, it.seed) },
            l.gates.map { g -> Gate(g.bollards.map { Disc(rx(it.cx, it.cy), ry(it.cx, it.cy), it.r) }, g.period, g.openFrac, g.phase, box(g.zone)) },
            l.turntables.map { Turntable(rx(it.cx, it.cy), ry(it.cx, it.cy), it.r, it.rotateBy, it.duration) },
            l.coins.map { doubleArrayOf(rx(it[0], it[1]), ry(it[0], it[1])) },
            l.lighting, l.weather, l.trailer, l.steps, ref, l.t3, l.t2, l.tightness, l.complexity, l.mechanics,
            l.newMechanic, l.tutorial, l.clearance, l.seed, l.attempts,
        )
    }
}
