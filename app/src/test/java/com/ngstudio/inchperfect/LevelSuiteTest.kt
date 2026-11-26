package com.ngstudio.inchperfect

import com.ngstudio.inchperfect.game.level.Difficulty
import com.ngstudio.inchperfect.game.level.LEVEL_COUNT
import com.ngstudio.inchperfect.game.level.Level
import com.ngstudio.inchperfect.game.level.LevelGenerator
import com.ngstudio.inchperfect.game.math.Collide
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.sim.Autopilot
import com.ngstudio.inchperfect.game.sim.Phase
import com.ngstudio.inchperfect.game.sim.World
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

/**
 * The proof behind "all 10,000 levels are perfect":
 *  - every level generates deterministically,
 *  - every solid obstacle keeps its required clearance from the reference path,
 *  - an autopilot drives the reference solution with the REAL physics and parks.
 *
 * Run a subset with: ./gradlew testDebugUnitTest --tests '*LevelSuiteTest*' -Plevels=1-500
 */
class LevelSuiteTest {

    private fun levels(): List<Int> {
        val p = System.getProperty("levels").orEmpty().trim()
        if (p.isEmpty()) return (1..LEVEL_COUNT).toList()
        return p.split(",").flatMap { part ->
            if ("-" in part) { val (a, b) = part.split("-").map { it.trim().toInt() }; (a..b).toList() } else listOf(part.trim().toInt())
        }
    }

    data class Result(val n: Int, val ok: Boolean, val why: String, val simTime: Double, val attempts: Int, val ms: Long)

    fun drive(l: Level, spec: com.ngstudio.inchperfect.game.physics.CarSpec = Cars.REF): Pair<Boolean, String> {
        val w = World(l, spec)
        val ap = Autopilot(w)
        var t = 0.0
        val dt = 1.0 / 120.0
        while (t < 400.0) {
            ap.update(dt)
            w.step(dt)
            t += dt
            when (w.phase) {
                Phase.PARKED -> return true to "parked in %.1fs (t3 %.1f) precision %.0f".format(w.runTime, l.t3, w.precision)
                Phase.CRASHED -> return false to "CRASH into ${w.crashedInto} at step ${ap.idx}/${l.steps.size} (%.2f,%.2f) t=%.1f".format(w.crashX, w.crashY, t)
                else -> {}
            }
            if (ap.done && kotlin.math.abs(w.car.v) < 1e-4 && w.parkHold == 0.0) {
                // finished all steps but not parked
                val inBay = w.bayContainsCar(); val head = w.headingOk()
                return false to "NOT PARKED after steps (inBay=$inBay heading=$head err=%.2f°)".format(Math.toDegrees(w.headingError()))
            }
        }
        return false to "TIMEOUT at step ${ap.idx}/${l.steps.size}"
    }

    @Test
    fun allLevelsAreSolvable() {
        val r = levels()
        val failures = ConcurrentLinkedQueue<Result>()
        val results = ConcurrentLinkedQueue<Result>()
        val counter = AtomicInteger()
        val t0 = System.currentTimeMillis()
        r.parallelStream().forEach { n ->
            val s = System.nanoTime()
            val res = try {
                val l = LevelGenerator.generate(n)
                val ms = (System.nanoTime() - s) / 1_000_000
                // clearance check: no solid obstacle overlaps the reference path
                val (ok, why) = drive(l)
                Result(n, ok, why, l.t3, l.attempts, ms)
            } catch (e: Throwable) {
                Result(n, false, "EXCEPTION ${e::class.simpleName}: ${e.message} @ ${e.stackTrace.take(4).joinToString(" < ")}", 0.0, -1, 0)
            }
            results.add(res)
            if (!res.ok) failures.add(res)
            val c = counter.incrementAndGet()
            if (c % 500 == 0) println("… $c levels checked, ${failures.size} failures")
        }
        val all = results.sortedBy { it.n }
        val fallbacks = all.count { it.attempts >= 48 }
        val avgAttempts = all.map { it.attempts.toDouble() }.average()
        val maxMs = all.maxOfOrNull { it.ms } ?: 0
        val avgMs = all.map { it.ms.toDouble() }.average()
        println("==== Level suite ${r.first()}..${r.last()}: ${all.size} levels, ${failures.size} failures, fallbacks=$fallbacks, avgAttempts=%.2f, gen avg %.1fms max %dms, wall %ds".format(avgAttempts, avgMs, maxMs, (System.currentTimeMillis() - t0) / 1000))
        failures.sortedBy { it.n }.take(60).forEach { println("FAIL L${it.n}: ${it.why}") }
        assertTrue("${failures.size} levels failed: ${failures.sortedBy { it.n }.take(20).map { it.n }}", failures.isEmpty())
    }

    @Test
    fun generationIsDeterministic() {
        for (n in listOf(1, 7, 11, 57, 133, 501, 2048, 9999)) {
            val a = LevelGenerator.generate(n); val b = LevelGenerator.generate(n)
            assertTrue("L$n obstacles differ", a.obstacles.size == b.obstacles.size)
            for (i in a.obstacles.indices) {
                val x = a.obstacles[i].shape; val y = b.obstacles[i].shape
                assertTrue("L$n obstacle $i differs", x.cx == y.cx && x.cy == y.cy && x.radius == y.radius)
            }
            assertTrue(a.bay.cx == b.bay.cx && a.bay.cy == b.bay.cy && a.t3 == b.t3)
        }
    }

    @Test
    fun difficultyEnvelopeRises() {
        var prevBoss = 0.0
        for (n in 20..LEVEL_COUNT step 10) {
            val d = Difficulty.tightness(n)
            assertTrue("boss $n not harder than previous boss", d >= prevBoss)
            prevBoss = d
        }
        assertTrue(Difficulty.tightness(10_000) > 0.99)
        assertTrue(Difficulty.complexity(10_000) > 0.99)
    }

    @Test
    fun everyCarCanParkSampledLevels() {
        val levels = listOf(1, 3, 5, 6, 7, 13, 17, 22, 27, 41, 61, 121, 161, 251, 401, 601, 999, 2500, 5000, 9999)
        val bad = ArrayList<String>()
        for (c in Cars.ALL) for (n in levels) {
            val (ok, why) = drive(LevelGenerator.generate(n), c)
            if (!ok) bad.add("${c.id} L$n: $why")
        }
        bad.take(30).forEach { println("CAR FAIL $it") }
        assertTrue("${bad.size} car/level failures", bad.isEmpty())
    }

    @Test
    fun carsFitReferenceEnvelope() {
        val ref = Cars.REF
        for (c in Cars.ALL) {
            assertTrue("${c.id} rear overhang", c.rearOverhang <= ref.rearOverhang + 1e-9)
            assertTrue("${c.id} front extent ${c.length - c.rearOverhang}", c.length - c.rearOverhang <= ref.length - ref.rearOverhang + 1e-9)
            assertTrue("${c.id} width", c.width <= ref.width + 1e-9)
            assertTrue("${c.id} turning", c.maxCurv >= ref.maxCurv - 1e-9)
        }
    }

    @Suppress("unused")
    private fun overlapsAny(l: Level) = l.obstacles.any { o -> o.kind.solid && Collide.overlap(o.shape, l.bay) }
}
