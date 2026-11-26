package com.ngstudio.inchperfect.game.sim

import com.ngstudio.inchperfect.game.level.Prim
import com.ngstudio.inchperfect.game.math.M

/**
 * Drives the verified reference solution through the REAL game physics.
 * Used by the unit-test suite (proves every level is completable), by the debug "Auto-solve"
 * button and by the Ghost Guide booster.
 *
 * Strategy: steer only while stopped (dry steering) so each primitive is followed exactly,
 * with a smooth speed profile that brakes to a stop at the end of each primitive.
 */
class Autopilot(private val w: World, startIndex: Int = 0, private val stopAfter: Int = Int.MAX_VALUE) {
    private val steps = w.level.steps
    var idx = startIndex
        private set
    private var phase = 0
    private var odoStart = 0.0
    var waited = 0.0
        private set
    private var waitCheck = 0.0
    private var stepsDone = 0
    /** Steps are authored for the reference car; scale steering so any car follows the same curvature. */
    private val steerScale = com.ngstudio.inchperfect.game.physics.Cars.REF.maxCurv / w.spec.maxCurv

    val done get() = idx >= steps.size || stepsDone >= stopAfter

    fun update(dt: Double) {
        val inp = w.input
        if (done) { inp.throttle = 0.0; inp.brake = 1.0; return }
        val s = steps[idx]
        when (s.type) {
            Prim.DRIVE -> {
                val steer = s.steer * steerScale
                if (phase == 0) {
                    val needStop = w.car.gear != s.gear || kotlin.math.abs(w.car.steer - steer) > 1e-4
                    if (needStop && kotlin.math.abs(w.car.v) > 1e-4) {
                        inp.throttle = 0.0; inp.brake = 1.0; return
                    }
                    inp.gear = s.gear
                    inp.steer = steer
                    inp.throttle = 0.0; inp.brake = 1.0
                    if (kotlin.math.abs(w.car.steer - steer) <= 1e-6 && w.car.gear == s.gear) {
                        phase = 1
                        odoStart = w.car.odometer
                        inp.brake = 0.0
                    } else return
                }
                inp.steer = steer
                inp.gear = s.gear
                val traveled = w.car.odometer - odoStart
                val rem = s.dist - traveled
                val speed = kotlin.math.abs(w.car.v)
                if (rem <= 0.0004) {
                    inp.throttle = 0.0; inp.brake = 1.0
                    if (speed < 1e-4) next()
                    return
                }
                val vt = minOf(s.vmax, M.sqrt(2.0 * 1.3 * rem))
                if (speed < vt) {
                    inp.throttle = M.clamp((vt - speed) * 3.0 + 0.12, 0.0, 1.0); inp.brake = 0.0
                } else {
                    inp.throttle = 0.0; inp.brake = M.clamp((speed - vt) * 2.5, 0.0, 1.0)
                }
            }
            Prim.WAIT -> {
                inp.throttle = 0.0; inp.brake = 1.0
                if (kotlin.math.abs(w.car.v) > 1e-4) return
                waitCheck -= dt
                if (waitCheck > 0) { waited += dt; return }
                waitCheck = 0.1
                if (safeToGo()) next() else waited += dt
            }
            Prim.ROTATE -> {
                if (phase == 0) {
                    inp.throttle = 0.0; inp.brake = 1.0
                    if (kotlin.math.abs(w.car.v) < 1e-4 && w.pressRotate()) phase = 1
                } else if (w.tableActive < 0) next()
            }
        }
    }

    private fun next() { idx++; phase = 0; stepsDone++ }

    /** Look-ahead: simulate the next drive (the crossing) against moving hazards with a margin. */
    private fun safeToGo(): Boolean {
        val probe = w.probe()
        probe.dynamicOnly = true
        probe.dynamicMargin = 0.5
        val ap = Autopilot(probe, idx + 1, stopAfter = 1)
        var t = 0.0
        while (!ap.done && t < 25.0) {
            ap.update(CarPhysicsDt)
            probe.step(CarPhysicsDt)
            if (probe.phase == Phase.CRASHED) return false
            t += CarPhysicsDt
        }
        // keep checking a little after the crossing finished
        var extra = 0.0
        while (extra < 0.6) {
            probe.input.throttle = 0.0; probe.input.brake = 1.0
            probe.step(CarPhysicsDt)
            if (probe.phase == Phase.CRASHED) return false
            extra += CarPhysicsDt
        }
        return true
    }

    companion object {
        const val CarPhysicsDt = 1.0 / 120.0
    }
}
