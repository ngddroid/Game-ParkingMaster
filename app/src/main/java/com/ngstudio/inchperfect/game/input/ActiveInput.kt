package com.ngstudio.inchperfect.game.input

import com.ngstudio.inchperfect.game.ControlState

/** Bridges hardware input (keyboard / tilt sensor) from the Activity to the running level. */
object ActiveInput {
    @Volatile var controls: Controls? = null
    @Volatile var state: ControlState? = null
    @Volatile var roll = 0f
}
