package com.ngstudio.inchperfect.game.physics

/** Visual design of a car (pure data, painted by render.CarPainter). Fractions are of length/width. */
enum class Category(val title: String) {
    CITY("City"), SEDAN("Sedan"), SPORT("Sport"), MUSCLE("Muscle"), SUV("SUV"), OFFROAD("Off-road"), PICKUP("Pickup"), VAN("Van"),
    EV("Electric"), LUXURY("Luxury"), CLASSIC("Classic"), ROADSTER("Roadster"), SUPER("Supercar"), CONCEPT("Concept"), LEGEND("Legend")
}

enum class Roof { SOLID, SUNROOF, PANORAMA, CANOPY, OPEN, TARGA, VINYL }
enum class Hood { PLAIN, SCOOP, VENTS, BULGE, ENGINE_REAR }
enum class Rear { PLAIN, LIP, WING, DUCKTAIL, BED, SPARE, BOXY }
enum class Lamps { SLIM, ROUND, ANGULAR, BAR, QUAD }

data class CarDesign(
    /** centre of the glass cabin, 0 = rear bumper, 1 = nose */
    val cabinCenter: Float = 0.46f,
    /** cabin (rear glass + roof + windshield) length */
    val cabinLen: Float = 0.46f,
    /** shares of the cabin length */
    val windshield: Float = 0.3f,
    val rearGlass: Float = 0.2f,
    /** roof width as fraction of car width */
    val cabinWidth: Float = 0.7f,
    val noseRound: Float = 0.32f,
    val tailRound: Float = 0.26f,
    /** wheel-arch bulge and door pinch (fractions of width) */
    val arch: Float = 0.03f,
    val waist: Float = 0.012f,
    val roof: Roof = Roof.SOLID,
    val hood: Hood = Hood.PLAIN,
    val rear: Rear = Rear.PLAIN,
    val lamps: Lamps = Lamps.SLIM,
    val blackTrim: Boolean = false,
    val roofRails: Boolean = false,
    val flares: Boolean = false,
    val sideIntakes: Boolean = false,
    val taxi: Boolean = false,
    val stripes: Boolean = false,
    val chrome: Boolean = false,
    val lightBar: Boolean = false,
    val roofColor: Int = 0,      // 0 = body colour
    val trimColor: Int = 0x1A1C21,
) {
    companion object {
        // ---- presets per category
        val CITY = CarDesign(0.48f, 0.56f, 0.30f, 0.18f, 0.74f, 0.42f, 0.36f, 0.035f, 0.006f, lamps = Lamps.ROUND)
        val HATCH = CarDesign(0.46f, 0.55f, 0.27f, 0.12f, 0.72f, 0.34f, 0.2f, 0.03f, 0.01f, lamps = Lamps.ANGULAR, rear = Rear.LIP)
        val SEDAN = CarDesign(0.46f, 0.47f, 0.3f, 0.22f, 0.7f, 0.3f, 0.24f, 0.028f, 0.012f)
        val SPORT = CarDesign(0.44f, 0.40f, 0.36f, 0.26f, 0.66f, 0.36f, 0.3f, 0.045f, 0.018f, lamps = Lamps.ANGULAR, rear = Rear.LIP, hood = Hood.VENTS)
        val MUSCLE = CarDesign(0.40f, 0.36f, 0.33f, 0.28f, 0.66f, 0.18f, 0.16f, 0.045f, 0.01f, hood = Hood.SCOOP, rear = Rear.DUCKTAIL, lamps = Lamps.QUAD, stripes = true)
        val SUV = CarDesign(0.46f, 0.60f, 0.2f, 0.1f, 0.78f, 0.2f, 0.16f, 0.025f, 0.004f, lamps = Lamps.ANGULAR, roofRails = true, blackTrim = true)
        val OFFROAD = CarDesign(0.47f, 0.58f, 0.18f, 0.08f, 0.8f, 0.12f, 0.1f, 0.05f, 0.0f, lamps = Lamps.ROUND, roofRails = true, flares = true, blackTrim = true, rear = Rear.SPARE)
        val PICKUP = CarDesign(0.62f, 0.30f, 0.34f, 0.12f, 0.76f, 0.18f, 0.12f, 0.04f, 0.0f, rear = Rear.BED, lamps = Lamps.ANGULAR, blackTrim = true, flares = true)
        val VAN = CarDesign(0.5f, 0.78f, 0.13f, 0.05f, 0.84f, 0.2f, 0.1f, 0.015f, 0.0f, rear = Rear.BOXY, lamps = Lamps.SLIM, roofRails = true)
        val EV = CarDesign(0.47f, 0.54f, 0.34f, 0.24f, 0.72f, 0.42f, 0.34f, 0.03f, 0.01f, roof = Roof.PANORAMA, lamps = Lamps.BAR, lightBar = true)
        val LUXURY = CarDesign(0.45f, 0.48f, 0.27f, 0.2f, 0.7f, 0.26f, 0.22f, 0.025f, 0.01f, roof = Roof.SUNROOF, chrome = true, lamps = Lamps.SLIM)
        val CLASSIC = CarDesign(0.45f, 0.42f, 0.3f, 0.26f, 0.66f, 0.46f, 0.44f, 0.06f, 0.03f, roof = Roof.VINYL, lamps = Lamps.ROUND, chrome = true)
        val ROADSTER = CarDesign(0.40f, 0.30f, 0.32f, 0.0f, 0.66f, 0.38f, 0.32f, 0.05f, 0.02f, roof = Roof.OPEN, lamps = Lamps.ANGULAR, rear = Rear.DUCKTAIL)
        val SUPER = CarDesign(0.52f, 0.34f, 0.46f, 0.12f, 0.62f, 0.4f, 0.26f, 0.06f, 0.025f, hood = Hood.ENGINE_REAR, rear = Rear.WING, lamps = Lamps.ANGULAR, sideIntakes = true)
        val CONCEPT = CarDesign(0.5f, 0.6f, 0.5f, 0.1f, 0.72f, 0.5f, 0.46f, 0.05f, 0.02f, roof = Roof.CANOPY, lamps = Lamps.BAR, lightBar = true)
    }
}
