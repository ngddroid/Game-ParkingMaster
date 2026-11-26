package com.ngstudio.inchperfect.game.physics

import com.ngstudio.inchperfect.game.math.Box
import com.ngstudio.inchperfect.game.math.M
import com.ngstudio.inchperfect.game.math.Pose

enum class EngineSound { I4, V6, V8, EV, TURBO, HYPER }

/**
 * A drivable car. All cars must fit inside the reference envelope (see [Cars.REF]):
 * rear overhang <= REF, front extent <= REF, width <= REF and turning curvature >= REF.
 * Levels are generated and verified with REF, so every car can complete every level.
 */
data class CarSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val category: Category,
    val design: CarDesign,
    val engine: EngineSound,
    val length: Double,
    val width: Double,
    val wheelbase: Double,
    val rearOverhang: Double,
    val maxSteerDeg: Double,
    val accel: Double,
    val topSpeed: Double,
    val revSpeed: Double,
    val brake: Double,
    val steerRate: Double,
    val price: Int,
    val starPrice: Int = 0,
    val defaultColor: Int,
) {
    val maxCurv: Double = M.tan(M.rad(maxSteerDeg)) / wheelbase
    /** distance from rear axle to body centre along heading */
    val centerOffset: Double = length / 2 - rearOverhang
    val turningRadius: Double = 1.0 / maxCurv
    /** axle positions as fractions of length from the rear bumper (for the painter) */
    val axleRear: Float get() = (rearOverhang / length).toFloat()
    val axleFront: Float get() = ((rearOverhang + wheelbase) / length).toFloat()

    // 0..1 stat bars for the garage UI (relative to the line-up)
    val statSize get() = M.clamp(1.0 - (length * width - 5.0) / (8.55 - 5.0), 0.05, 1.0)
    val statTurning get() = M.clamp((maxCurv - 0.24) / (0.42 - 0.24), 0.05, 1.0)
    val statAccel get() = M.clamp((accel - 2.0) / (6.0 - 2.0), 0.05, 1.0)
    val statSpeed get() = M.clamp((topSpeed - 6.0) / (12.0 - 6.0), 0.05, 1.0)
    val statBrake get() = M.clamp((brake - 5.0) / (9.0 - 5.0), 0.05, 1.0)
}

data class TrailerSpec(
    val length: Double = 4.6,
    val width: Double = 1.9,
    /** hitch distance behind the car's rear axle */
    val hitchBack: Double = 1.25,
    /** hitch to trailer axle */
    val axleDist: Double = 3.3,
    val rearOverhang: Double = 1.0,
)

object Cars {
    private fun c(
        id: String, name: String, tagline: String, cat: Category, d: CarDesign, eng: EngineSound,
        len: Double, wid: Double, wb: Double, ro: Double, steer: Double, accel: Double, top: Double, rev: Double, brake: Double,
        rate: Double, price: Int, color: Int, stars: Int = 0,
    ) = CarSpec(id, name, tagline, cat, d, eng, len, wid, wb, ro, steer, accel, top, rev, brake, rate, price, stars, color)

    val REF = c("rookie", "Rookie", "Reliable first ride", Category.SEDAN, CarDesign.SEDAN, EngineSound.I4,
        4.5, 1.9, 2.7, 0.9, 34.0, 2.6, 7.0, 3.6, 6.0, 2.4, 0, 0xFF7A1A)

    val ALL: List<CarSpec> = listOf(
        REF,
        // ---------------------------------------------------------------- city
        c("minnow", "Minnow", "City micro. Fits anywhere", Category.CITY, CarDesign.CITY, EngineSound.I4, 3.5, 1.65, 2.25, 0.55, 38.0, 2.4, 6.5, 3.4, 6.2, 2.8, 1_500, 0x9BFF3C),
        c("pebble", "Pebble", "Round, cute and nimble", Category.CITY, CarDesign.CITY.copy(roof = Roof.SUNROOF, lamps = Lamps.ROUND), EngineSound.I4, 3.6, 1.68, 2.3, 0.6, 38.0, 2.6, 6.8, 3.4, 6.3, 2.8, 2_500, 0x7FD8F0),
        c("button", "Button", "Two-tone city charmer", Category.CITY, CarDesign.CITY.copy(roofColor = 0xF2F5FF, cabinWidth = 0.76f), EngineSound.I4, 3.7, 1.7, 2.35, 0.62, 37.0, 2.6, 6.8, 3.5, 6.2, 2.7, 3_500, 0xD7263D),
        c("mochi", "Mochi", "Soft curves, big heart", Category.CITY, CarDesign.CITY.copy(noseRound = 0.48f, tailRound = 0.44f, roof = Roof.PANORAMA), EngineSound.EV, 3.6, 1.7, 2.35, 0.6, 39.0, 3.2, 7.0, 3.6, 6.4, 2.9, 4_500, 0xFFB7C5),
        // --------------------------------------------------------------- hatch
        c("zing", "Zing", "Hot hatch with attitude", Category.CITY, CarDesign.HATCH, EngineSound.TURBO, 4.1, 1.8, 2.55, 0.75, 36.0, 3.4, 8.2, 3.8, 6.8, 2.8, 5_500, 0x3A86FF),
        c("sprout", "Sprout", "Eco hatch, smart lines", Category.CITY, CarDesign.HATCH.copy(roof = Roof.PANORAMA, lamps = Lamps.SLIM), EngineSound.EV, 4.0, 1.78, 2.55, 0.72, 37.0, 3.6, 7.6, 3.8, 6.6, 2.8, 6_500, 0x06D6A0),
        c("firecracker", "Firecracker", "Rally-bred hatch", Category.CITY, CarDesign.HATCH.copy(hood = Hood.VENTS, rear = Rear.WING, stripes = true), EngineSound.TURBO, 4.2, 1.82, 2.6, 0.76, 36.5, 4.0, 8.8, 3.8, 7.0, 3.0, 9_000, 0xF2F5FF),
        // --------------------------------------------------------------- sedan
        c("commuter", "Commuter", "Comfy daily driver", Category.SEDAN, CarDesign.SEDAN.copy(lamps = Lamps.ANGULAR), EngineSound.I4, 4.5, 1.85, 2.7, 0.9, 35.0, 2.8, 7.4, 3.7, 6.2, 2.5, 4_000, 0x9AA3AD),
        c("avenue", "Avenue", "Executive calm", Category.SEDAN, CarDesign.SEDAN.copy(roof = Roof.SUNROOF, chrome = true), EngineSound.V6, 4.5, 1.88, 2.7, 0.9, 35.5, 3.2, 8.0, 3.8, 6.6, 2.6, 8_000, 0x1C2A44),
        c("halcyon", "Halcyon", "Quiet hybrid saloon", Category.SEDAN, CarDesign.SEDAN.copy(roof = Roof.PANORAMA, lamps = Lamps.BAR, lightBar = true), EngineSound.EV, 4.45, 1.86, 2.68, 0.86, 36.0, 3.6, 8.2, 3.9, 6.8, 2.7, 12_000, 0xE9EDF2),
        c("corvo", "Corvo", "Sport saloon, sharp suit", Category.SEDAN, CarDesign.SEDAN.copy(hood = Hood.BULGE, rear = Rear.LIP, lamps = Lamps.ANGULAR), EngineSound.TURBO, 4.5, 1.88, 2.7, 0.9, 36.0, 4.0, 9.0, 3.9, 7.0, 2.7, 16_000, 0x2B2F36),
        // ---------------------------------------------------------- taxi/work
        c("hailer", "Hailer", "Taxi with a tight lock", Category.SEDAN, CarDesign.SEDAN.copy(taxi = true), EngineSound.I4, 4.4, 1.85, 2.65, 0.85, 39.0, 3.0, 7.5, 3.8, 6.6, 3.0, 10_000, 0xFFC21A),
        // --------------------------------------------------------------- sport
        c("kestrel", "Kestrel", "Sharp little coupe", Category.SPORT, CarDesign.SPORT, EngineSound.TURBO, 4.3, 1.85, 2.6, 0.82, 36.0, 3.8, 8.8, 3.8, 6.9, 2.9, 7_000, 0x19E3FF),
        c("osprey", "Osprey", "Grand tourer", Category.SPORT, CarDesign.SPORT.copy(roof = Roof.PANORAMA, cabinLen = 0.42f), EngineSound.V6, 4.5, 1.88, 2.7, 0.9, 36.0, 4.2, 9.4, 3.9, 7.2, 2.9, 14_000, 0x8338EC),
        c("shrike", "Shrike", "Track-day weapon", Category.SPORT, CarDesign.SPORT.copy(rear = Rear.WING, hood = Hood.VENTS, stripes = true), EngineSound.TURBO, 4.35, 1.88, 2.62, 0.84, 37.0, 4.8, 10.0, 4.0, 7.6, 3.0, 22_000, 0xFB5607),
        c("peregrine", "Peregrine", "Fastest bird in the lot", Category.SPORT, CarDesign.SPORT.copy(roof = Roof.TARGA, lamps = Lamps.SLIM), EngineSound.V6, 4.4, 1.88, 2.62, 0.84, 37.5, 5.0, 10.4, 4.0, 7.8, 3.1, 30_000, 0x5A6B7D),
        // -------------------------------------------------------------- muscle
        c("bruiser", "Bruiser", "Muscle with manners", Category.MUSCLE, CarDesign.MUSCLE, EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 36.5, 4.8, 9.5, 4.0, 7.2, 2.8, 18_000, 0xD7263D),
        c("stampede", "Stampede", "Twin stripes, twin pipes", Category.MUSCLE, CarDesign.MUSCLE.copy(roof = Roof.VINYL, roofColor = 0x111111), EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 36.0, 5.0, 9.8, 4.0, 7.2, 2.8, 26_000, 0xFFC21A),
        c("thunderclap", "Thunderclap", "All bark, all bite", Category.MUSCLE, CarDesign.MUSCLE.copy(hood = Hood.BULGE, rear = Rear.WING), EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 37.0, 5.4, 10.2, 4.1, 7.4, 2.9, 38_000, 0x2E7D5B),
        c("rumble", "Rumble", "Classic V8 soul", Category.MUSCLE, CarDesign.MUSCLE.copy(chrome = true, lamps = Lamps.ROUND, stripes = false), EngineSound.V8, 4.5, 1.88, 2.7, 0.9, 36.0, 4.6, 9.2, 4.0, 7.0, 2.8, 32_000, 0x3A0CA3),
        // ----------------------------------------------------------------- suv
        c("grizzly", "Grizzly", "Big SUV, light steering", Category.SUV, CarDesign.SUV, EngineSound.V6, 4.5, 1.9, 2.7, 0.9, 35.5, 3.0, 7.5, 3.8, 6.4, 2.6, 9_500, 0x2E7D5B),
        c("glacier", "Glacier", "Premium family hauler", Category.SUV, CarDesign.SUV.copy(roof = Roof.PANORAMA, chrome = true, blackTrim = false), EngineSound.V6, 4.5, 1.9, 2.7, 0.9, 36.0, 3.4, 8.0, 3.9, 6.8, 2.7, 20_000, 0xF2F5FF),
        c("ridgeback", "Ridgeback", "Urban explorer", Category.SUV, CarDesign.SUV.copy(lamps = Lamps.BAR, lightBar = true), EngineSound.EV, 4.45, 1.9, 2.68, 0.88, 37.0, 4.2, 8.4, 4.0, 7.0, 2.8, 28_000, 0x5A6B7D),
        c("basalt", "Basalt", "Blacked-out luxury SUV", Category.SUV, CarDesign.SUV.copy(roofColor = 0x15171B, roof = Roof.SUNROOF), EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 36.0, 4.0, 8.6, 4.0, 7.2, 2.7, 42_000, 0x2B2F36),
        // ------------------------------------------------------------- offroad
        c("mudskipper", "Mudskipper", "Loves the dirty work", Category.OFFROAD, CarDesign.OFFROAD, EngineSound.V6, 4.3, 1.9, 2.6, 0.82, 36.0, 3.2, 7.2, 3.8, 6.6, 2.6, 11_000, 0x6E8B3D),
        c("bushbuck", "Bushbuck", "Expedition ready", Category.OFFROAD, CarDesign.OFFROAD.copy(roof = Roof.SUNROOF, lamps = Lamps.QUAD), EngineSound.V8, 4.45, 1.9, 2.68, 0.88, 36.5, 3.6, 7.6, 3.9, 6.8, 2.7, 24_000, 0xC27C4E),
        c("rockhopper", "Rockhopper", "Short, stubby, unstoppable", Category.OFFROAD, CarDesign.OFFROAD.copy(cabinLen = 0.6f, noseRound = 0.1f), EngineSound.V6, 3.9, 1.88, 2.4, 0.68, 38.0, 3.4, 7.0, 3.8, 6.8, 2.8, 16_000, 0xFF7A1A),
        c("dunefox", "Dune Fox", "Desert racer", Category.OFFROAD, CarDesign.OFFROAD.copy(rear = Rear.WING, stripes = true, roof = Roof.OPEN), EngineSound.TURBO, 4.3, 1.9, 2.6, 0.82, 38.0, 4.6, 9.0, 4.0, 7.0, 3.0, 36_000, 0xF0B35A),
        // -------------------------------------------------------------- pickup
        c("packmule", "Packmule", "Pickup that pivots", Category.PICKUP, CarDesign.PICKUP, EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 38.0, 3.6, 8.0, 4.0, 7.0, 3.0, 13_000, 0x5A6B7D),
        c("toolbox", "Toolbox", "Built for the job site", Category.PICKUP, CarDesign.PICKUP.copy(roofRails = true, lamps = Lamps.SLIM), EngineSound.V6, 4.5, 1.9, 2.7, 0.9, 37.0, 3.2, 7.6, 3.9, 6.8, 2.8, 19_000, 0xF2F5FF),
        c("buckaroo", "Buckaroo", "Lifted ranch truck", Category.PICKUP, CarDesign.PICKUP.copy(lamps = Lamps.ROUND, chrome = true, blackTrim = false), EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 37.0, 3.8, 8.2, 4.0, 7.0, 2.9, 27_000, 0x8E1B1B),
        // ----------------------------------------------------------------- van
        c("parcel", "Parcel", "Delivery hero", Category.VAN, CarDesign.VAN, EngineSound.I4, 4.5, 1.9, 2.7, 0.9, 38.0, 2.8, 7.0, 3.6, 6.4, 2.8, 8_000, 0xF2F5FF),
        c("driftwood", "Driftwood", "Camper with a sunroof", Category.VAN, CarDesign.VAN.copy(roof = Roof.SUNROOF, roofColor = 0xF2F5FF, stripes = true), EngineSound.I4, 4.5, 1.9, 2.7, 0.9, 38.0, 2.8, 7.0, 3.6, 6.4, 2.8, 15_000, 0x00B3A4),
        // ------------------------------------------------------------------ ev
        c("zapper", "Zapper", "Silent instant torque", Category.EV, CarDesign.EV, EngineSound.EV, 4.2, 1.8, 2.6, 0.8, 37.0, 4.4, 8.0, 4.0, 7.0, 3.0, 12_500, 0xF2F5FF),
        c("ohm", "Ohm", "Resistance is futile", Category.EV, CarDesign.EV.copy(cabinLen = 0.58f, noseRound = 0.46f), EngineSound.EV, 4.1, 1.8, 2.55, 0.76, 38.0, 4.6, 8.4, 4.0, 7.2, 3.1, 21_000, 0x19E3FF),
        c("quark", "Quark", "Tiny particle, huge punch", Category.EV, CarDesign.EV.copy(cabinWidth = 0.76f, roof = Roof.CANOPY), EngineSound.EV, 3.8, 1.75, 2.45, 0.68, 40.0, 5.0, 8.6, 4.2, 7.4, 3.2, 26_000, 0x9BFF3C),
        c("photon", "Photon", "Light-speed fastback", Category.EV, CarDesign.EV.copy(rear = Rear.LIP, cabinCenter = 0.44f, lamps = Lamps.BAR), EngineSound.EV, 4.5, 1.88, 2.7, 0.9, 37.0, 5.4, 10.0, 4.2, 7.8, 3.1, 48_000, 0x7B61FF),
        // ------------------------------------------------------------- luxury
        c("midnight", "Midnight", "Luxury with rear-steer feel", Category.LUXURY, CarDesign.LUXURY, EngineSound.V6, 4.5, 1.9, 2.7, 0.9, 41.0, 4.0, 9.0, 4.2, 7.6, 3.2, 40_000, 0x1B1F3B),
        c("regalia", "Regalia", "Rules every lot", Category.LUXURY, CarDesign.LUXURY.copy(roof = Roof.PANORAMA, roofColor = 0x15171B), EngineSound.V8, 4.5, 1.9, 2.7, 0.9, 40.0, 4.4, 9.4, 4.2, 7.8, 3.2, 60_000, 0x7A1F4B),
        c("velvet", "Velvet", "Silk-smooth flagship", Category.LUXURY, CarDesign.LUXURY.copy(lamps = Lamps.BAR, lightBar = true), EngineSound.EV, 4.5, 1.9, 2.7, 0.9, 42.0, 4.8, 9.6, 4.3, 8.0, 3.3, 75_000, 0xE8E2D0),
        // ------------------------------------------------------------- classic
        c("crooner", "Crooner", "Fifties chrome cruiser", Category.CLASSIC, CarDesign.CLASSIC, EngineSound.V8, 4.5, 1.88, 2.7, 0.9, 35.0, 3.0, 7.6, 3.6, 6.2, 2.5, 22_000, 0x7FD8F0),
        c("jitterbug", "Jitterbug", "Retro bubble car", Category.CLASSIC, CarDesign.CLASSIC.copy(cabinLen = 0.5f, noseRound = 0.5f, tailRound = 0.5f), EngineSound.I4, 3.8, 1.7, 2.4, 0.66, 38.0, 2.6, 6.6, 3.4, 6.0, 2.7, 14_000, 0xFFB7C5),
        c("woodie", "Woodie", "Surf wagon", Category.CLASSIC, CarDesign.CLASSIC.copy(cabinLen = 0.56f, rearGlass = 0.08f, roofColor = 0x8A6A4A, roof = Roof.SOLID), EngineSound.V8, 4.5, 1.88, 2.7, 0.9, 35.5, 3.0, 7.4, 3.6, 6.2, 2.6, 28_000, 0xE8E2D0),
        // ------------------------------------------------------------ roadster
        c("sundae", "Sundae", "Top-down sunshine", Category.ROADSTER, CarDesign.ROADSTER, EngineSound.TURBO, 4.0, 1.75, 2.45, 0.7, 38.0, 4.2, 9.0, 3.8, 7.2, 3.1, 17_000, 0xD7263D),
        c("daydream", "Daydream", "Open-air grand tourer", Category.ROADSTER, CarDesign.ROADSTER.copy(cabinLen = 0.34f, chrome = true, lamps = Lamps.ROUND), EngineSound.V6, 4.4, 1.85, 2.62, 0.84, 37.0, 4.4, 9.4, 3.9, 7.4, 3.0, 34_000, 0x0B3D91),
        // ---------------------------------------------------------------- super
        c("arrowhead", "Arrowhead", "Supercar precision", Category.SUPER, CarDesign.SUPER, EngineSound.HYPER, 4.4, 1.9, 2.6, 0.85, 40.0, 5.4, 11.0, 4.2, 8.2, 3.4, 65_000, 0xFF3DA5),
        c("vortex", "Vortex", "Downforce you can see", Category.SUPER, CarDesign.SUPER.copy(stripes = true, lamps = Lamps.SLIM), EngineSound.HYPER, 4.45, 1.9, 2.62, 0.86, 40.0, 5.6, 11.4, 4.2, 8.4, 3.4, 85_000, 0x19E3FF),
        c("stiletto", "Stiletto", "Thin, sharp, deadly fast", Category.SUPER, CarDesign.SUPER.copy(noseRound = 0.26f, cabinWidth = 0.56f), EngineSound.HYPER, 4.4, 1.88, 2.6, 0.85, 41.0, 5.8, 11.6, 4.3, 8.6, 3.5, 110_000, 0xFFC21A),
        c("fang", "Fang", "Bite-sized hypercar", Category.SUPER, CarDesign.SUPER.copy(roof = Roof.TARGA, rear = Rear.DUCKTAIL), EngineSound.HYPER, 4.3, 1.9, 2.55, 0.82, 42.0, 6.0, 11.8, 4.4, 8.8, 3.6, 140_000, 0x2B2F36),
        c("razorback", "Razorback", "Track-only beast", Category.SUPER, CarDesign.SUPER.copy(stripes = true, roofColor = 0x111111), EngineSound.HYPER, 4.45, 1.9, 2.62, 0.86, 42.0, 6.0, 12.0, 4.4, 9.0, 3.6, 175_000, 0x9BFF3C),
        // -------------------------------------------------------------- concept
        c("nimbus", "Nimbus", "Concept that turns on a coin", Category.CONCEPT, CarDesign.CONCEPT, EngineSound.EV, 4.0, 1.8, 2.5, 0.75, 43.0, 4.8, 9.5, 4.4, 8.0, 3.6, 95_000, 0x7B61FF),
        c("halo", "Halo", "Glass bubble of the future", Category.CONCEPT, CarDesign.CONCEPT.copy(cabinWidth = 0.78f, noseRound = 0.5f), EngineSound.EV, 4.2, 1.85, 2.55, 0.78, 43.0, 5.0, 10.0, 4.4, 8.2, 3.6, 130_000, 0xF2F5FF),
        c("quasar", "Quasar", "Pulsing light-bar coupe", Category.CONCEPT, CarDesign.CONCEPT.copy(cabinLen = 0.44f, rear = Rear.WING, sideIntakes = true), EngineSound.HYPER, 4.4, 1.9, 2.6, 0.85, 43.0, 5.8, 11.6, 4.5, 8.8, 3.7, 190_000, 0xFF3DA5),
        c("zenith", "Zenith", "Peak engineering", Category.CONCEPT, CarDesign.CONCEPT.copy(cabinLen = 0.5f, lightBar = true, stripes = true), EngineSound.EV, 4.3, 1.88, 2.58, 0.82, 44.0, 6.0, 12.0, 4.6, 9.0, 3.8, 250_000, 0x06D6A0),
        // --------------------------------------------------------------- legend
        c("legend", "Gilded Legend", "For true Inch Perfect drivers", Category.LEGEND, CarDesign.SUPER.copy(chrome = true, stripes = true), EngineSound.HYPER, 4.2, 1.85, 2.55, 0.8, 45.0, 6.0, 12.0, 4.6, 9.0, 3.8, 0, 0xE8C15A, stars = 6_000),
        c("diamond", "Diamond Ace", "Cut from pure skill", Category.LEGEND, CarDesign.CONCEPT.copy(chrome = true, lamps = Lamps.SLIM), EngineSound.HYPER, 4.3, 1.88, 2.58, 0.82, 45.0, 6.0, 12.0, 4.6, 9.0, 3.8, 0, 0xBFE9FF, stars = 12_000),
        c("solarflare", "Solar Flare", "Reborn from ten thousand bays", Category.LEGEND, CarDesign.SUPER.copy(rear = Rear.WING, lamps = Lamps.BAR, lightBar = true), EngineSound.HYPER, 4.4, 1.9, 2.6, 0.85, 45.0, 6.0, 12.0, 4.6, 9.0, 3.8, 0, 0xFF5E2B, stars = 20_000),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: REF

    /** ordinary cars used for parked and traffic vehicles */
    val CIVILIAN: List<CarSpec> = ALL.filter { it.category in setOf(Category.CITY, Category.SEDAN, Category.SUV, Category.PICKUP, Category.VAN, Category.EV, Category.LUXURY, Category.SPORT, Category.OFFROAD, Category.CLASSIC) }

    val TRAILER = TrailerSpec()
}

/** Driver input. steer -1..1 (positive = right), throttle/brake 0..1, gear +1 drive, -1 reverse. */
class DriveInput {
    var steer = 0.0
    var throttle = 0.0
    var brake = 0.0
    var gear = 1
    fun set(o: DriveInput) { steer = o.steer; throttle = o.throttle; brake = o.brake; gear = o.gear }
    fun clear() { steer = 0.0; throttle = 0.0; brake = 0.0 }
}

/** Mutable car state. Position (x,y) is the REAR AXLE centre. */
class CarState(var x: Double, var y: Double, var a: Double, var gear: Int = 1) {
    var v = 0.0            // signed speed along heading (m/s)
    var steer = 0.0        // actual steering -1..1
    var vx = 0.0           // world velocity (used when sliding)
    var vy = 0.0
    var phi = a            // trailer heading
    var odometer = 0.0
    var reverseDist = 0.0

    fun copy(): CarState = CarState(x, y, a, gear).also {
        it.v = v; it.steer = steer; it.vx = vx; it.vy = vy; it.phi = phi; it.odometer = odometer; it.reverseDist = reverseDist
    }

    fun pose() = Pose(x, y, a)

    companion object { const val TONGUE = 0.7 }

    fun body(spec: CarSpec, inflate: Double = 0.0): Box {
        val c = M.cos(a); val s = M.sin(a)
        return Box(x + c * spec.centerOffset, y + s * spec.centerOffset, spec.length / 2 + inflate, spec.width / 2 + inflate, a)
    }

    fun trailerBody(t: TrailerSpec): Box {
        val hx = x - M.cos(a) * t.hitchBack
        val hy = y - M.sin(a) * t.hitchBack
        val c = M.cos(phi); val s = M.sin(phi)
        val dist = TONGUE + t.length / 2
        return Box(hx - c * dist, hy - s * dist, t.length / 2, t.width / 2, phi)
    }
}

object CarPhysics {
    const val DT = 1.0 / 120.0
    private const val COAST = 1.7

    private fun approach(v: Double, target: Double, step: Double): Double =
        if (v < target) minOf(target, v + step) else maxOf(target, v - step)

    /**
     * Advances the car by dt. grip 1 = dry asphalt, 0.85 rain, ~0.45 ice.
     * Returns the distance moved (signed).
     */
    fun step(s: CarState, spec: CarSpec, inp: DriveInput, grip: Double, dt: Double, trailer: TrailerSpec? = null): Double {
        val target = M.clamp(inp.steer, -1.0, 1.0)
        s.steer = approach(s.steer, target, spec.steerRate * dt)

        s.gear = if (inp.gear < 0) -1 else 1
        val dir = s.gear.toDouble()
        val g = M.clamp(grip, 0.3, 1.0)
        var v = s.v
        if (inp.throttle > 0.01) {
            if (v * dir < -0.02) {
                v = approach(v, 0.0, spec.brake * 0.85 * inp.throttle * g * dt)
            } else {
                val vmax = if (dir > 0) spec.topSpeed else spec.revSpeed
                val sp = v * dir
                val a = spec.accel * g * inp.throttle * maxOf(0.0, 1.0 - sp / vmax)
                v += dir * a * dt
                if (v * dir > vmax) v = dir * vmax
            }
        } else {
            v = approach(v, 0.0, COAST * g * dt)
        }
        if (inp.brake > 0.01) v = approach(v, 0.0, spec.brake * inp.brake * g * dt)
        if (trailer != null) {
            val vmax = if (v >= 0) spec.topSpeed * 0.7 else spec.revSpeed * 0.6
            v = M.clamp(v, -vmax, vmax)
        }
        s.v = v

        val ds = v * dt
        val k = s.steer * spec.maxCurv
        val da = ds * k
        val am = s.a + da / 2
        val fx = M.cos(am); val fy = M.sin(am)
        if (grip >= 0.7) {
            s.vx = v * fx; s.vy = v * fy
        } else {
            val rx = -fy; val ry = fx
            var lat = s.vx * rx + s.vy * ry
            lat *= maxOf(0.0, 1.0 - grip * grip * 14.0 * dt)
            s.vx = v * fx + lat * rx
            s.vy = v * fy + lat * ry
        }
        val prevA = s.a
        s.x += s.vx * dt
        s.y += s.vy * dt
        s.a = M.wrap(s.a + da)
        s.odometer += kotlin.math.abs(ds)
        if (ds < 0) s.reverseDist += -ds

        if (trailer != null) {
            val dTheta = M.wrap(s.a - prevA)
            val rel = M.wrap(prevA - s.phi)
            val dphi = (ds * M.sin(rel) - trailer.hitchBack * dTheta * M.cos(rel)) / trailer.axleDist
            s.phi = M.wrap(s.phi + dphi)
        }
        return ds
    }
}
