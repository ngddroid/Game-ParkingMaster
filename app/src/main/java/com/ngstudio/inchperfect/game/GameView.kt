package com.ngstudio.inchperfect.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ngstudio.inchperfect.game.input.Controls
import com.ngstudio.inchperfect.game.render.Quality
import com.ngstudio.inchperfect.game.render.Renderer

/**
 * Hosts the game loop: a dedicated thread steps the simulation (fixed 120 Hz inside the session)
 * and renders to a hardware-accelerated canvas.
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val session: GameSession,
    private val renderer: Renderer,
    val controls: Controls,
    private val targetFps: Int,
    private val autoQuality: Boolean,
    private val onQualityDrop: (Quality) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {

    @Volatile private var running = false
    private var thread: Thread? = null
    private var introStarted = false
    @Volatile var fps = 0f
        private set
    private var workMs = 0.0

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (Build.VERSION.SDK_INT >= 30) {
            try { holder.surface.setFrameRate(targetFps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) } catch (_: Throwable) {}
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val insets = rootWindowInsets
        val l = insets?.systemWindowInsetLeft ?: 0
        val r = insets?.systemWindowInsetRight ?: 0
        val b = insets?.systemWindowInsetBottom ?: 0
        controls.layout(width, height, l, r, b)
        session.camera.setViewport(width, height, controls.safe)
        if (!introStarted) { introStarted = true; session.startIntro(true) }
        start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "inch-perfect-game").apply { priority = Thread.MAX_PRIORITY - 1; start() }
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    private fun loop() {
        val t0 = System.nanoTime()
        var first = true
        var last = System.nanoTime()
        val frameNs = 1_000_000_000L / targetFps
        var fpsAcc = 0.0; var fpsFrames = 0
        var slowFrames = 0; var sampled = 0
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9).coerceIn(0.0, 0.05)
            last = now
            session.update(dt)
            controls.update(dt.toFloat())
            val c: Canvas? = try {
                if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
            } catch (e: Exception) { null }
            if (c != null) {
                try {
                    renderer.draw(c, session, dt.toFloat())
                    controls.draw(c)
                } finally {
                    try { holder.unlockCanvasAndPost(c) } catch (_: Exception) {}
                }
                if (first) { first = false; android.util.Log.i("IPPerf", "first frame after %.0f ms".format((System.nanoTime() - t0) / 1e6)) }
            }
            // fps + automatic quality
            fpsAcc += dt; fpsFrames++
            if (fpsAcc >= 1.0) {
                fps = (fpsFrames / fpsAcc).toFloat(); fpsAcc = 0.0; fpsFrames = 0
                session.fps = fps
                if (com.ngstudio.inchperfect.BuildConfig.DEBUG) android.util.Log.i("IPPerf", "L${session.level.number} fps=%.1f q=${renderer.quality} work=%.1fms".format(fps, workMs))
            }
            if (autoQuality && session.clock > 2.5 && sampled < 360) {
                sampled++
                if (dt > 1.0 / 42) slowFrames++
                if (sampled == 360 && slowFrames > 120 && renderer.quality != Quality.LOW) {
                    val q = Quality.entries[renderer.quality.ordinal - 1]
                    renderer.quality = q
                    onQualityDrop(q)
                    sampled = 0; slowFrames = 0
                }
            }
            val spent = System.nanoTime() - now
            workMs = workMs * 0.9 + (spent / 1e6) * 0.1
            if (spent < frameNs - 2_000_000) {
                try { Thread.sleep((frameNs - spent) / 1_000_000 - 1) } catch (_: InterruptedException) {}
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (session.state == SessionState.INTRO && event.actionMasked == MotionEvent.ACTION_DOWN) session.skipIntro()
        return controls.onTouch(event)
    }
}
