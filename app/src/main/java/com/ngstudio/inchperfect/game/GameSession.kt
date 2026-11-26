package com.ngstudio.inchperfect.game

import com.ngstudio.inchperfect.game.level.Level
import com.ngstudio.inchperfect.game.level.Weather
import com.ngstudio.inchperfect.game.math.M
import com.ngstudio.inchperfect.game.physics.CarSpec
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.render.Camera
import com.ngstudio.inchperfect.game.render.CarLook
import com.ngstudio.inchperfect.game.render.Particles
import com.ngstudio.inchperfect.game.render.SkidMarks
import com.ngstudio.inchperfect.game.sim.Autopilot
import com.ngstudio.inchperfect.game.sim.Phase
import com.ngstudio.inchperfect.game.sim.World
import com.ngstudio.inchperfect.game.sim.WorldEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue

enum class SessionState { INTRO, PLAYING, PAUSED, CRASHED, PARKED }

/** Snapshot for the Compose HUD (updated ~15 times per second and on events). */
data class Hud(
    val state: SessionState = SessionState.INTRO,
    val time: Double = 0.0,
    val t3: Double = 0.0,
    val t2: Double = 0.0,
    val starsNow: Int = 3,
    val cones: Int = 0,
    val coins: Int = 0,
    val canRotate: Boolean = false,
    val precision: Double = 0.0,
    val stars: Int = 0,
    val gear: Int = 1,
    val speedKmh: Int = 0,
    val parkProgress: Double = 0.0,
    val hint: String? = null,
    val freeRewinds: Boolean = false,
    val rewinding: Boolean = false,
    val ghost: Boolean = false,
    val slowMo: Boolean = false,
    val autoSolve: Boolean = false,
    val crashedIntoTraffic: Boolean = false,
    /** what the car hit (for the crash card) */
    val crashKind: String = "",
    val newMechanicPending: Boolean = false,
    val version: Long = 0,
)

/** Callbacks for sound/haptics (implemented on the Android side). */
interface SessionListener {
    fun onCrash(speed: Double) {}
    fun onCone() {}
    fun onCoin() {}
    fun onParked(stars: Int) {}
    fun onGear(gear: Int) {}
    fun onSensor(level: Int) {}
    fun onTable() {}
    fun onRewind() {}
    fun onIntroEnd() {}
}

class GameSession(
    val level: Level,
    val spec: CarSpec,
    val look: CarLook,
    private val listener: SessionListener,
) : WorldEvents {
    val world = World(level, spec).also { it.events = this }
    val camera = Camera()
    val particles = Particles()
    val skids = SkidMarks()
    val controls = ControlState()
    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    private val _hud = MutableStateFlow(Hud(t3 = level.t3, t2 = level.t2, freeRewinds = level.tutorial))
    val hud: StateFlow<Hud> = _hud

    var state = SessionState.INTRO
        private set
    var clock = 0.0
        private set
    private var acc = 0.0
    private var hudAcc = 0.0
    var slowMoLeft = 0.0
    /** brief freeze on impact (seconds left) */
    private var hitStop = 0.0
    /** smoothed longitudinal body offset in metres (+ = squat back on accel, - = dive forward on brake) */
    var bodyShift = 0.0
        private set
    private var lastV = 0.0
    /** 0xRRGGBB underglow colour (0 = none) */
    var underglow = 0
    @Volatile var fps = 0f
    var peek = false
    var stars = 0
        private set
    private var autoSolve: Autopilot? = null
    private var crashTimer = 0.0
    private var rewindFlash = 0.0
    private var lastSensorLevel = 0
    private var lastGear = level.startGear
    private var tutorialStep = 0
    private var hint: String? = null
    private var version = 0L

    // ghost guide (separate world that replays the verified solution)
    var ghostWorld: World? = null
        private set
    private var ghostPilot: Autopilot? = null
    private var ghostRestart = 0.0

    init {
        world.input.gear = level.startGear
        controls.gear = level.startGear
    }

    fun post(action: () -> Unit) { queue.add(action) }

    // ------------------------------------------------------------ commands
    fun startIntro(viewReady: Boolean) {
        if (viewReady) camera.startIntro(level, world.body().cx, world.body().cy)
    }
    fun skipIntro() = post { camera.skipIntro() }
    fun pause() = post { if (state == SessionState.PLAYING || state == SessionState.INTRO) { state = SessionState.PAUSED; push() } }
    fun resume() = post { if (state == SessionState.PAUSED) { state = if (camera.mode == Camera.Mode.INTRO) SessionState.INTRO else SessionState.PLAYING; push() } }
    fun rotateTable() = post { world.pressRotate() }
    fun peekOn(on: Boolean) = post { peek = on }
    fun useRewind() = post { doRewind(3.0) }
    fun startGhost() = post { if (ghostWorld == null) spawnGhost(); push() }
    fun startSlowMo(seconds: Double) = post { slowMoLeft = seconds; push() }
    /** Debug only: the autopilot drives from the start pose, so it is only available before moving. */
    fun toggleAutoSolve() = post {
        autoSolve = when {
            autoSolve != null -> null
            world.car.odometer < 0.05 && world.tableAngles.all { it == 0.0 } -> Autopilot(world)
            else -> null
        }
        if (state == SessionState.INTRO) { camera.skipIntro(); state = SessionState.PLAYING }
        push()
    }

    val canAutoSolve get() = world.car.odometer < 0.05

    private fun spawnGhost() {
        val g = World(level, Cars.REF)
        g.dynamicOnly = true
        ghostWorld = g
        ghostPilot = Autopilot(g)
    }

    private fun doRewind(sec: Double) {
        if (world.rewind(sec)) {
            state = SessionState.PLAYING
            camera.mode = Camera.Mode.FOLLOW
            rewindFlash = 0.6
            crashTimer = 0.0
            controls.resetPedals()
            controls.gear = world.car.gear
            listener.onRewind()
            push()
        }
    }

    // ------------------------------------------------------------- update
    fun update(frameDt: Double) {
        while (true) { val a = queue.poll() ?: break; a() }
        val dtReal = frameDt.coerceAtMost(0.05)
        clock += dtReal
        rewindFlash = maxOf(0.0, rewindFlash - dtReal)

        if (state == SessionState.PAUSED) return
        if (hitStop > 0) { hitStop -= dtReal; particles.update(dtReal.toFloat() * 0.25f); camera.update(dtReal, level, world.body().cx, world.body().cy, world.car.a, 0.0, peek); return }
        val scale = if (slowMoLeft > 0) 0.5 else 1.0
        if (slowMoLeft > 0) slowMoLeft = maxOf(0.0, slowMoLeft - dtReal)
        val dt = dtReal * scale

        if (state == SessionState.INTRO) {
            camera.update(dtReal, level, world.body().cx, world.body().cy, world.car.a, 0.0, false)
            // world clock runs during intro so traffic moves
            world.time += dt
            if (camera.mode != Camera.Mode.INTRO || controls.anyInput()) {
                camera.skipIntro(); camera.mode = Camera.Mode.FOLLOW
                state = SessionState.PLAYING
                listener.onIntroEnd()
                push()
            }
            particles.update(dtReal.toFloat())
            hudTick(dtReal)
            return
        }

        if (state == SessionState.PLAYING) {
            val ap = autoSolve
            if (ap != null) ap.update(DT) else controls.apply(world.input, dt)
            acc += dt
            var steps = 0
            while (acc >= DT && steps < 8) {
                if (ap != null && steps > 0) ap.update(DT)
                val prevGear = world.car.gear
                world.step(DT)
                if (world.car.gear != prevGear) listener.onGear(world.car.gear)
                acc -= DT; steps++
                if (world.phase != Phase.PLAYING) break
            }
            effects(dt)
            tutorial()
            when (world.phase) {
                Phase.CRASHED -> {
                    state = SessionState.CRASHED
                    camera.mode = Camera.Mode.CRASH
                    camera.shake(1.0)
                    hitStop = 0.09
                    crashTimer = 0.0
                    push()
                }
                Phase.PARKED -> {
                    val t = world.runTime
                    stars = 1 + (if (t <= level.t2 && world.conesHit <= 1) 1 else 0) + (if (t <= level.t3 && world.conesHit == 0) 1 else 0)
                    state = SessionState.PARKED
                    camera.mode = Camera.Mode.PARKED
                    listener.onParked(stars)
                    confetti()
                    push()
                }
                else -> {}
            }
        } else {
            world.step(dt) // cones keep tumbling, traffic keeps moving
            if (state == SessionState.CRASHED) {
                crashTimer += dtReal
                if (level.tutorial && crashTimer > 1.1) doRewind(2.0)
            }
        }

        // ghost
        ghostWorld?.let { g ->
            val gp = ghostPilot!!
            if (gp.done || g.phase != Phase.PLAYING) {
                ghostRestart += dtReal
                if (ghostRestart > 1.2) { ghostRestart = 0.0; spawnGhost() }
            } else {
                var a2 = dtReal
                while (a2 > 0) { gp.update(DT); g.step(DT); a2 -= DT }
            }
        }

        camera.update(dtReal, level, world.body().cx, world.body().cy, world.car.a, world.car.v, peek)
        particles.update(dtReal.toFloat())
        hudTick(dtReal)
    }

    private var exhaustAcc = 0.0
    private fun effects(dt: Double) {
        val c = world.car
        val accel = if (dt > 0) (c.v - lastV) / dt else 0.0
        lastV = c.v
        val target = (-accel * 0.018 * kotlin.math.sign(if (c.v == 0.0) 1.0 else c.v)).coerceIn(-0.09, 0.09)
        bodyShift += (target - bodyShift) * (1 - kotlin.math.exp(-10.0 * dt))
        if (world.input.brake > 0.7 && kotlin.math.abs(c.v) > 3.0 && particles.random() < 0.05) camera.shake(0.18)
        val b = world.body()
        val ca = M.cos(c.a); val sa = M.sin(c.a)
        val speed = kotlin.math.abs(c.v)
        // tyre marks on hard turns, sliding or heavy braking
        val hardBrake = world.input.brake > 0.6 && speed > 2.5
        val sliding = world.lastGrip < 0.6 && kotlin.math.abs(-c.vx * sa + c.vy * ca) > 0.4
        val hardTurn = kotlin.math.abs(c.steer) > 0.85 && speed > 3.8
        if (hardBrake || sliding || hardTurn) {
            val rx = -sa; val ry = ca
            for (side in doubleArrayOf(-1.0, 1.0)) {
                val wx = c.x + rx * side * (spec.width / 2 - 0.25); val wy = c.y + ry * side * (spec.width / 2 - 0.25)
                skids.add(wx, wy, wx - c.vx * dt, wy - c.vy * dt, 0.42f)
            }
            if (particles.random() < 0.35) particles.emit(Particles.Kind.SMOKE, c.x, c.y, -c.vx * 0.2, -c.vy * 0.2, 0.9f, 0.35f, 0x55C8CCD2)
        }
        // exhaust puffs and dust when pulling away
        exhaustAcc += dt
        if (exhaustAcc > 0.12 && spec.engine != com.ngstudio.inchperfect.game.physics.EngineSound.EV) {
            exhaustAcc = 0.0
            val ex = b.cx - ca * (b.hl + 0.05) + (-sa) * 0.45; val ey = b.cy - sa * (b.hl + 0.05) + ca * 0.45
            val th = world.input.throttle
            if (th > 0.1 || particles.random() < 0.2) particles.emit(Particles.Kind.EXHAUST, ex, ey, -ca * (0.4 + th), -sa * (0.4 + th), 0.8f + th.toFloat(), 0.12f + 0.12f * th.toFloat(), 0x30B8BCC4)
        }
        if (world.input.throttle > 0.7 && speed < 2.0 && speed > 0.2 && particles.random() < 0.5) {
            val col = if (level.weather == Weather.SNOW) 0x88FFFFFF.toInt() else 0x44A89F94
            particles.emit(if (level.weather == Weather.SNOW) Particles.Kind.SNOWPUFF else Particles.Kind.DUST,
                c.x + (particles.random() - 0.5) * 1.6, c.y + (particles.random() - 0.5) * 1.6, -ca * 0.6, -sa * 0.6, 0.9f, 0.3f, col)
        }
        if (level.weather == Weather.RAIN && speed > 1.2 && particles.random() < 0.3) {
            particles.emit(Particles.Kind.SPLASH, b.cx + (particles.random() - 0.5) * 3, b.cy + (particles.random() - 0.5) * 2, 0.0, 0.0, 0.5f, 0.25f, 0xAAE0F0FF.toInt())
        }
        // parking sensor level for beeps
        val d = minOf(minOf(world.sensorFront, world.sensorRear), minOf(world.sensorLeft, world.sensorRight) + 0.3)
        val lvl = when { d < 0.3 -> 3; d < 0.7 -> 2; d < 1.3 -> 1; else -> 0 }
        if (lvl != lastSensorLevel) { lastSensorLevel = lvl; listener.onSensor(lvl) }
    }

    private fun confetti() {
        val bx = level.bay.cx; val by = level.bay.cy
        val cols = intArrayOf(0xFF19E3FF.toInt(), 0xFFFF7A1A.toInt(), 0xFF9BFF3C.toInt(), 0xFFFF3DA5.toInt(), 0xFFFFD84A.toInt(), 0xFFFFFFFF.toInt())
        repeat(140) {
            val col = cols[it % cols.size]
            particles.emit(Particles.Kind.CONFETTI, bx + (particles.random() - 0.5) * 2, by + (particles.random() - 0.5) * 2,
                (particles.random() - 0.5) * 16.0, (particles.random() - 0.5) * 16.0, 1.6f + particles.random() * 1.4f, 0.22f, col)
        }
        particles.burst(Particles.Kind.SPARKLE, bx, by, 30, 6.0, 1.0f, 0.3f, 0xFFFFFFFF.toInt())
    }

    // ------------------------------------------------------------- events
    override fun crash(x: Double, y: Double, speed: Double) {
        particles.burst(Particles.Kind.SPARK, x, y, 26, 7.0 + speed * 2, 0.5f, 0.12f, 0xFFFFD27A.toInt())
        particles.burst(Particles.Kind.DEBRIS, x, y, 10, 3.0 + speed, 1.2f, 0.12f, 0xFFDDE2EA.toInt())
        particles.burst(Particles.Kind.SMOKE, x, y, 8, 1.2, 1.2f, 0.4f, 0x66A0A4AA)
        listener.onCrash(speed)
    }

    override fun cone(x: Double, y: Double) {
        camera.shake(0.25)
        particles.burst(Particles.Kind.DEBRIS, x, y, 6, 2.5, 0.8f, 0.08f, 0xFFFF7A1A.toInt())
        listener.onCone(); push()
    }

    override fun coin(x: Double, y: Double) {
        particles.burst(Particles.Kind.SPARKLE, x, y, 14, 3.0, 0.7f, 0.22f, 0xFFFFE27A.toInt())
        listener.onCoin(); push()
    }

    override fun tableStart() { listener.onTable() }

    // ----------------------------------------------------------- tutorial
    private fun tutorial() {
        if (!level.tutorial) { hint = null; return }
        val c = world.car
        val moving = kotlin.math.abs(c.v) > 0.4
        val inBay = world.bayContainsCar()
        hint = when (level.number) {
            1 -> when {
                tutorialStep == 0 && !moving -> "Hold GAS to drive forward"
                inBay -> "Release GAS and tap BRAKE to stop in the bay"
                else -> { tutorialStep = 1; "Stop inside the glowing bay" }
            }
            2 -> if (!moving && world.odometer() < 1) "Turn the wheel, then press GAS" else "Steer into the bay. The arrow shows where the nose must point."
            3 -> if (c.gear > 0) "Tap the gear lever to shift to R (reverse)" else "Hold GAS to reverse into the bay"
            4 -> "Cones won't end your run, but each one costs a star"
            5 -> if (c.gear > 0) "Drive past the bay, then shift to R" else "Reverse in between the cars. From here on, crashes count!"
            else -> null
        }
    }

    private fun hudTick(dt: Double) {
        hudAcc += dt
        if (hudAcc >= 1.0 / 15) { hudAcc = 0.0; push() }
    }

    private fun push() {
        val t = world.runTime
        val starsNow = when {
            state == SessionState.PARKED -> stars
            else -> 1 + (if (t <= level.t2 && world.conesHit <= 1) 1 else 0) + (if (t <= level.t3 && world.conesHit == 0) 1 else 0)
        }
        version++
        _hud.value = Hud(
            state = state, time = t, t3 = level.t3, t2 = level.t2, starsNow = starsNow, cones = world.conesHit,
            coins = world.coinsCollected, canRotate = world.canRotate(), precision = world.precision, stars = stars,
            gear = world.car.gear, speedKmh = (kotlin.math.abs(world.car.v) * 3.6).toInt(), parkProgress = world.parkProgress,
            hint = hint, freeRewinds = level.tutorial, rewinding = rewindFlash > 0, ghost = ghostWorld != null,
            slowMo = slowMoLeft > 0, autoSolve = autoSolve != null, crashedIntoTraffic = world.crashedInto == null, crashKind = crashLabel(), version = version,
        )
    }

    fun rewindFlash() = rewindFlash

    private fun crashLabel(): String {
        if (world.phase != com.ngstudio.inchperfect.game.sim.Phase.CRASHED) return ""
        return when (world.crashedInto) {
            null -> if (level.traffic.isNotEmpty()) "traffic" else "edge"
            com.ngstudio.inchperfect.game.level.ObKind.CAR -> if (level.traffic.isNotEmpty() && world.index.hits(world.body()) == null) "traffic" else "car"
            com.ngstudio.inchperfect.game.level.ObKind.TRUCK, com.ngstudio.inchperfect.game.level.ObKind.BUS, com.ngstudio.inchperfect.game.level.ObKind.VAN -> "truck"
            com.ngstudio.inchperfect.game.level.ObKind.PILLAR -> if (level.gates.isNotEmpty()) "bollard" else "pillar"
            com.ngstudio.inchperfect.game.level.ObKind.SIDEWALK -> "kerb"
            com.ngstudio.inchperfect.game.level.ObKind.HEDGE, com.ngstudio.inchperfect.game.level.ObKind.PLANTER -> "hedge"
            com.ngstudio.inchperfect.game.level.ObKind.CONTAINER -> "container"
            else -> "wall"
        }
    }

    companion object { const val DT = 1.0 / 120.0 }
}

private fun World.odometer() = car.odometer

/** Control state written from the UI thread, read on the game thread. */
class ControlState {
    @Volatile var steerTarget = 0.0      // -1..1 from wheel/tilt/keys
    @Volatile var gasHeld = false
    @Volatile var brakeHeld = false
    @Volatile var gear = 1
    @Volatile var gasAmount = 1.0        // analog pedal depth 0.35..1
    @Volatile var touched = false
    private var throttle = 0.0
    private var brake = 0.0

    fun anyInput() = gasHeld || brakeHeld || kotlin.math.abs(steerTarget) > 0.05 || touched

    fun resetPedals() { gasHeld = false; brakeHeld = false; throttle = 0.0; brake = 0.0 }

    fun apply(inp: com.ngstudio.inchperfect.game.physics.DriveInput, dt: Double) {
        val tTarget = if (gasHeld) gasAmount else 0.0
        throttle = if (tTarget > throttle) minOf(tTarget, throttle + 4.0 * dt) else maxOf(tTarget, throttle - 8.0 * dt)
        val bTarget = if (brakeHeld) 1.0 else 0.0
        brake = if (bTarget > brake) minOf(bTarget, brake + 6.0 * dt) else maxOf(bTarget, brake - 10.0 * dt)
        inp.throttle = throttle
        inp.brake = brake
        inp.steer = steerTarget
        inp.gear = gear
    }
}
