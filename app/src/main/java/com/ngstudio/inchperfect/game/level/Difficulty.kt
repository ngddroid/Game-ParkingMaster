package com.ngstudio.inchperfect.game.level

import com.ngstudio.inchperfect.game.math.M

const val LEVEL_COUNT = 10_000
const val LEVELS_PER_DISTRICT = 500
const val GENERATOR_VERSION = 2
/** levels 1..TUTORIAL_LEVELS are handcrafted and forgiving */
const val TUTORIAL_LEVELS = 5

/**
 * Two independent difficulty dials:
 *  - tightness D(n): clearances, angle tolerance, star times (reaches the physical limit at 10,000)
 *  - complexity C(n): number of segments, gear changes, mechanics stacking
 */
object Difficulty {
    private val SAW = doubleArrayOf(0.80, 0.84, 0.88, 0.91, 0.94, 0.86, 0.92, 0.95, 0.97, 1.00)
    private val LN_TOTAL = M.ln(1.0 + (LEVEL_COUNT - TUTORIAL_LEVELS) / 140.0)

    fun envelope(n: Int): Double =
        if (n <= TUTORIAL_LEVELS) 0.02 * n else 0.20 + 0.80 * M.ln(1.0 + (n - TUTORIAL_LEVELS) / 140.0) / LN_TOTAL

    fun tightness(n: Int): Double = if (n <= TUTORIAL_LEVELS) 0.02 * n else envelope(n) * SAW[(n - 1) % 10]

    fun complexity(n: Int): Double = M.ln(1.0 + n / 400.0) / M.ln(26.0)

    /** free space per side between the car and neighbours in the target row (m) */
    fun bayClearance(d: Double) = M.lerp(0.90, 0.10, M.pow(M.clamp(d, 0.0, 1.0), 0.8))

    /** free space per side for corridors and aisles (m) */
    fun corridorClearance(d: Double) = M.lerp(1.60, 0.14, M.pow(M.clamp(d, 0.0, 1.0), 0.9))

    /** extra length of the target bay beyond the car (m, total) */
    fun bayLengthSlack(d: Double) = M.lerp(1.4, 0.45, M.clamp(d, 0.0, 1.0))

    fun angleTolDeg(d: Double) = M.lerp(12.0, 3.0, M.clamp(d, 0.0, 1.0))

    fun starFactor(d: Double) = M.lerp(2.2, 1.30, M.clamp(d, 0.0, 1.0))

    fun trafficSpeed(d: Double) = M.lerp(2.5, 7.0, M.clamp(d, 0.0, 1.0))

    /** Level at which each mechanic is first introduced (with a NEW! card). */
    val INTRO: Map<Mech, Int> = linkedMapOf(
        Mech.FWD_BAY to 6, Mech.REV_BAY to 7, Mech.ANGLED to 9, Mech.CONES to 11, Mech.SLALOM to 14,
        Mech.PARALLEL to 17, Mech.GARAGE to 22, Mech.TRAFFIC to 27, Mech.NIGHT to 33, Mech.TIGHT_TURN to 41,
        Mech.DOCK to 51, Mech.HAIRPIN to 61, Mech.RAIN to 81, Mech.GATES to 121, Mech.TURNTABLE to 161,
        Mech.PILLARS to 251, Mech.ICE to 401, Mech.TRAILER to 601,
    )

    fun introducedAt(n: Int): Mech? = INTRO.entries.firstOrNull { it.value == n }?.key

    fun available(n: Int): Set<Mech> = INTRO.filter { it.value <= n }.keys

    /** Difficulty discount for mechanics that are hard on their own. */
    fun penalty(m: Mech): Double = when (m) {
        Mech.NIGHT -> 0.05; Mech.RAIN -> 0.05; Mech.ICE -> 0.12; Mech.TRAILER -> 0.25
        Mech.TRAFFIC -> 0.04; Mech.GATES -> 0.04; Mech.TIGHT_TURN -> 0.06; Mech.PARALLEL -> 0.03
        else -> 0.0
    }
}

data class District(val index: Int, val name: String, val signature: String, val tint: Int)

object Districts {
    val ALL = listOf(
        District(0, "Sunny Suburbs", "Basics & cones", 0x7FD36B),
        District(1, "Downtown Grid", "Curbside parallel", 0x5AA9FF),
        District(2, "Harbor Docks", "Lorries & containers", 0x3EC1C9),
        District(3, "Mall Underground", "Pillars & ramps", 0xB0B8C8),
        District(4, "Night Market", "After dark", 0xFF7A1A),
        District(5, "Rainy Boulevard", "Wet & slippery", 0x6C8BFF),
        District(6, "Airport Valet", "Buses & baggage", 0xF2F5FF),
        District(7, "Desert Motel", "Dusty lots", 0xF0B35A),
        District(8, "Snow Peak Resort", "Black ice", 0xBFE9FF),
        District(9, "Stadium Rush", "Heavy traffic", 0x9BFF3C),
        District(10, "Old Town Alleys", "Cobblestone hairpins", 0xC98A5B),
        District(11, "Industrial Yard", "Trailers", 0xFFB020),
        District(12, "Skyline Rooftops", "Edges everywhere", 0x8FD3FF),
        District(13, "Tech Park", "Moving bollards", 0x8FA3B8),
        District(14, "Beach Boardwalk", "Sun & sand", 0xFFE28A),
        District(15, "Construction Zone", "Barriers & gates", 0xFF7A1A),
        District(16, "Royal Gardens", "Fountains & hedges", 0x4CD18A),
        District(17, "Pit Lane", "Timing is everything", 0xFF4D4D),
        District(18, "Midnight Tunnels", "Total darkness", 0x7B61FF),
        District(19, "Legends Plaza", "Everything at once", 0xE8C15A),
    )

    fun of(level: Int) = ALL[((level - 1) / LEVELS_PER_DISTRICT).coerceIn(0, ALL.size - 1)]
}
