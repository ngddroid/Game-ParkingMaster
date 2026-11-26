package com.ngstudio.inchperfect.game.level

import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.Disc
import com.ngstudio.inchperfect.game.math.Pose
import com.ngstudio.inchperfect.game.math.Shape

/** Obstacle kinds. [solid] = contact means crash; soft ones (cones) tumble and cost stars. */
enum class ObKind(val solid: Boolean, val height: Double) {
    BARRIER(true, 0.9),      // concrete jersey blocks
    WALL(true, 2.4),         // brick/concrete wall
    BUILDING(true, 7.0),     // city block, extruded in 2.5D
    HEDGE(true, 1.3),
    SIDEWALK(true, 0.18),    // raised curb + sidewalk slab
    CAR(true, 1.45),
    VAN(true, 2.0),
    TRUCK(true, 3.4),
    BUS(true, 3.1),
    CONTAINER(true, 2.7),
    PILLAR(true, 3.2),
    PLANTER(true, 0.7),
    DUMPSTER(true, 1.5),
    CRATES(true, 1.1),
    FENCE(true, 1.6),
    BOAT(true, 1.6),
    CONE(false, 0.75),
}

class Obstacle(
    val kind: ObKind,
    val shape: Shape,
    /** stable random variant for rendering */
    val style: Int,
    /** 0xRRGGBB paint (cars etc.), 0 = theme default */
    val color: Int = 0,
)

enum class PropKind { TREE, LAMP, BUSH, MANHOLE, OIL, CRACK, PUDDLE, DRAIN, FLOWERS, SNOWPILE, LEAVES }

class Prop(val kind: PropKind, val x: Double, val y: Double, val a: Double, val size: Double, val variant: Int)

enum class MarkKind { LINE, LINE_YELLOW, DASH, CROSSWALK, STOP, ARROW, HATCH, BAY_OUTLINE, CHEVRON }

/** Painted marking laid inside [box]; LINE/DASH are drawn along the box length. */
class Mark(val kind: MarkKind, val box: Box)

enum class ZoneKind { ROAD, LOT, CONCRETE, GRASS, ICE, SAND, EPOXY, WOOD, PLATE }

class Zone(val kind: ZoneKind, val box: Box)

class TrafficLane(
    val x0: Double, val y0: Double, val x1: Double, val y1: Double,
    val speed: Double,
    val period: Double,
    val phase: Double,
    val carLen: Double = 4.4,
    val carWidth: Double = 1.9,
    val seed: Int,
) {
    val length: Double = kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
    val ux = (x1 - x0) / length
    val uy = (y1 - y0) / length
    val angle: Double = kotlin.math.atan2(uy, ux)

    /** Calls [out] with (index, centreX, centreY) for every car present at time t. */
    inline fun carsAt(t: Double, out: (Int, Double, Double) -> Unit) {
        val travel = length + carLen
        val tt = t + phase
        val firstIdx = kotlin.math.floor((tt - travel / speed) / period).toInt()
        val lastIdx = kotlin.math.floor(tt / period).toInt()
        for (i in firstIdx..lastIdx) {
            val s = (tt - i * period) * speed - carLen / 2
            if (s < -carLen / 2 || s > length + carLen / 2) continue
            out(i, x0 + ux * s, y0 + uy * s)
        }
    }

    fun carBox(cx: Double, cy: Double) = Box(cx, cy, carLen / 2, carWidth / 2, angle)
}

/** A row of bollards that rises and lowers on a timer. Down (passable) for the first openFrac of each period. */
class Gate(val bollards: List<Disc>, val period: Double, val openFrac: Double, val phase: Double, val zone: Box) {
    fun upAmount(t: Double): Double {
        val p = ((t + phase) % period + period) % period / period
        // smooth 0.35 s transitions
        val tr = 0.35 / period
        return when {
            p < openFrac - tr -> 0.0
            p < openFrac -> (p - (openFrac - tr)) / tr
            p > 1.0 - tr -> 1.0 - (p - (1.0 - tr)) / tr
            else -> 1.0
        }
    }
    fun isUp(t: Double) = upAmount(t) > 0.25
}

class Turntable(val cx: Double, val cy: Double, val r: Double, val rotateBy: Double, val duration: Double = 1.6)

enum class Lighting { DAY, GOLDEN, DUSK, NIGHT }
enum class Weather { CLEAR, RAIN, SNOW }

enum class Mech(val title: String, val blurb: String) {
    FWD_BAY("Nose In", "Drive forward into the bay."),
    REV_BAY("Back It In", "Shift to R and reverse into the bay."),
    ANGLED("Angled Bays", "Slide into a slanted bay."),
    CONES("Cones", "Cones won't end your run, but each one costs a star."),
    PARALLEL("Parallel Parking", "Reverse into a gap along the curb."),
    GARAGE("Garages", "Walls on three sides. Take it slow."),
    TRAFFIC("Traffic", "Wait for a gap, then cross."),
    NIGHT("Night Shift", "Your headlights only light what's ahead."),
    TIGHT_TURN("Tight Turns", "Too tight for one sweep. Use reverse to make the corner."),
    DOCK("Loading Docks", "Back in between the big rigs."),
    RAIN("Rain", "Wet roads: less grip, longer stops."),
    GATES("Bollards", "Rising bollards. Time your run."),
    TURNTABLE("Turntables", "Stop on the turntable and press ROTATE."),
    PILLARS("Pillar Garage", "Concrete pillars and tight corners."),
    ICE("Ice", "Ice patches slide. Brake early!"),
    TRAILER("Trailer", "You're towing a trailer. It cuts corners."),
    HAIRPIN("Hairpins", "Full lock, all the way round."),
    SLALOM("Slalom", "Weave through the cones."),
}

enum class Prim { DRIVE, WAIT, ROTATE }

/** A step of the reference (verified) solution. */
class Step(val type: Prim, val gear: Int = 1, val steer: Double = 0.0, val dist: Double = 0.0, val vmax: Double = 2.0, val rot: Double = 0.0, val table: Int = -1)

class Level(
    val number: Int,
    val name: String,
    val district: Int,
    val minX: Double, val minY: Double, val maxX: Double, val maxY: Double,
    val start: Pose,
    val startGear: Int,
    val bay: Box,
    val angleTolDeg: Double,
    val obstacles: List<Obstacle>,
    val props: List<Prop>,
    val marks: List<Mark>,
    val zones: List<Zone>,
    val traffic: List<TrafficLane>,
    val gates: List<Gate>,
    val turntables: List<Turntable>,
    val coins: List<DoubleArray>,
    val lighting: Lighting,
    val weather: Weather,
    val trailer: Boolean,
    val steps: List<Step>,
    /** reference path (car centre) as x0,y0,x1,y1... */
    val refPath: FloatArray,
    val t3: Double,
    val t2: Double,
    val tightness: Double,
    val complexity: Double,
    val mechanics: Set<Mech>,
    val newMechanic: Mech?,
    val tutorial: Boolean,
    val clearance: Double,
    var seed: Long,
    var attempts: Int,
) {
    val isBoss get() = number % 10 == 0
    val isMaster get() = number % 100 == 0
    val width get() = maxX - minX
    val height get() = maxY - minY
}
