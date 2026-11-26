package com.ngstudio.inchperfect

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.core.UiThemes
import com.ngstudio.inchperfect.game.input.ActiveInput
import com.ngstudio.inchperfect.game.input.Scheme
import com.ngstudio.inchperfect.ui.AppNav
import com.ngstudio.inchperfect.ui.theme.InchPerfectTheme

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sensors: SensorManager? = null

    /** Landscape game layouts: cap very large system font sizes so text never overflows. */
    override fun attachBaseContext(newBase: android.content.Context) {
        val cfg = android.content.res.Configuration(newBase.resources.configuration)
        if (cfg.fontScale > 1.15f) cfg.fontScale = 1.15f
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        setContent {
            val profile by GameRepo.profile.collectAsState()
            InchPerfectTheme(UiThemes.of(profile.uiTheme)) { AppNav() }
        }
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        Audio.resumeMusic()
        if (GameRepo.settings.value.scheme == Scheme.TILT) sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        Audio.pauseMusic()
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        // landscape: tilt the phone like a steering wheel
        @Suppress("DEPRECATION")
        val rot = windowManager.defaultDisplay.rotation
        val y = e.values[1]
        val roll = Math.toDegrees(Math.atan2(y.toDouble(), 9.81)).toFloat()
        ActiveInput.roll = if (rot == Surface.ROTATION_270) -roll else roll
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val st = ActiveInput.state
        val ctl = ActiveInput.controls
        if (st != null && ctl != null) {
            val down = event.action == KeyEvent.ACTION_DOWN
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> { ctl.keySteer = if (down) -1 else 0; return true }
                    KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> { ctl.keySteer = if (down) 1 else 0; return true }
                    KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> { if (down) st.gear = 1; st.gasHeld = down; st.gasAmount = 1.0; st.touched = true; return true }
                    KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) st.gear = -1; st.gasHeld = down; st.gasAmount = 0.8; st.touched = true; return true }
                    KeyEvent.KEYCODE_SPACE -> { st.brakeHeld = down; return true }
                    KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_E -> { if (down) st.gear = -st.gear; return true }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
