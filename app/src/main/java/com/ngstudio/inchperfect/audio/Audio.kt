package com.ngstudio.inchperfect.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.ngstudio.inchperfect.game.physics.EngineSound
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Sound effects (SoundPool), looping music (MediaPlayer) and a live engine synth (AudioTrack). */
object Audio {
    private var pool: SoundPool? = null
    private val ids = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()
    private lateinit var dir: File
    @Volatile var sfxVolume = 0.9f
    @Volatile var musicVolume = 0.55f
    @Volatile var engineVolume = 0.8f
    private var player: MediaPlayer? = null
    private var currentTrack: Track? = null
    private var wantedTrack: Track? = null
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile var ready = false
        private set
    private var app: Context? = null

    fun init(ctx: Context, onReady: () -> Unit = {}) {
        if (app != null) return
        app = ctx.applicationContext
        dir = File(ctx.cacheDir, "audio")
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs).build().also { sp ->
            sp.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded.add(id) } }
        }
        worker.execute {
            // effects first (fast), then the menu loop so it can start, then the rest
            SoundBank.renderAll(dir, includeMusic = false)
            for (s in Sfx.entries) ids[s] = pool!!.load(SoundBank.file(dir, s).absolutePath, 1)
            ready = true
            main.post(onReady)
            SoundBank.renderTrack(dir, Track.MENU)
            main.post { wantedTrack?.let { playMusic(it) } }
            SoundBank.renderTrack(dir, Track.GAME)
            SoundBank.renderTrack(dir, Track.NIGHT)
            main.post { wantedTrack?.let { if (currentTrack != it) playMusic(it) } }
        }
    }

    fun play(s: Sfx, vol: Float = 1f, rate: Float = 1f) {
        val sp = pool ?: return
        val id = ids[s] ?: return
        if (synchronized(loaded) { id !in loaded }) return
        val v = (vol * sfxVolume).coerceIn(0f, 1f)
        if (v <= 0.001f) return
        sp.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    // ---------------------------------------------------------- music
    fun playMusic(t: Track) {
        wantedTrack = t
        if (currentTrack == t && player != null) return
        val f = SoundBank.file(dir, t)
        if (!f.exists()) return
        val old = player
        val mp = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(f.absolutePath); isLooping = true; prepare(); setVolume(0f, 0f); start()
            }
        } catch (e: Exception) { null } ?: return
        player = mp; currentTrack = t
        fade(mp, 0f, musicVolume, 700) {}
        if (old != null) fade(old, musicVolume, 0f, 500) { try { old.release() } catch (_: Exception) {} }
    }

    fun stopMusic() { wantedTrack = null; player?.let { p -> fade(p, musicVolume, 0f, 400) { try { p.release() } catch (_: Exception) {} } }; player = null; currentTrack = null }

    fun pauseMusic() { try { player?.pause() } catch (_: Exception) {} }
    fun resumeMusic() { try { player?.let { if (!it.isPlaying) it.start() } } catch (_: Exception) {} }

    fun applyMusicVolume() { try { player?.setVolume(musicVolume, musicVolume) } catch (_: Exception) {} }

    private fun fade(mp: MediaPlayer, from: Float, to: Float, ms: Long, done: () -> Unit) {
        val steps = 14
        for (i in 1..steps) main.postDelayed({
            val v = from + (to - from) * i / steps
            try { mp.setVolume(v, v) } catch (_: Exception) {}
            if (i == steps) done()
        }, ms * i / steps)
    }

    // ---------------------------------------------------------- engine
    private var engineThread: Thread? = null
    @Volatile private var engineOn = false
    @Volatile var rpm = 800.0
    @Volatile var throttle = 0.0
    @Volatile var speed = 0.0
    @Volatile var profile = EngineSound.I4

    fun startEngine(p: EngineSound) {
        profile = p
        if (engineOn) return
        engineOn = true
        engineThread = Thread({ engineLoop() }, "engine-audio").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stopEngine() { engineOn = false; engineThread = null }

    private fun engineLoop() {
        val sr = 22050
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, 4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
        } catch (e: Exception) { return }
        track.play()
        val buf = ShortArray(512)
        var ph = 0.0; var ph2 = 0.0; var lp = 0.0; var lp2 = 0.0; var noiseS = 12345
        var curRpm = rpm; var curTh = 0.0; var gain = 0.0
        while (engineOn) {
            val tRpm = rpm; val tTh = throttle
            val cyl = when (profile) { EngineSound.I4 -> 4; EngineSound.V6 -> 6; EngineSound.V8 -> 8; EngineSound.TURBO -> 4; EngineSound.HYPER -> 10; EngineSound.EV -> 0 }
            val ev = profile == EngineSound.EV
            for (i in buf.indices) {
                curRpm += (tRpm - curRpm) * 0.0009
                curTh += (tTh - curTh) * 0.0012
                val targetGain = (0.16 + 0.34 * curTh) * engineVolume * sfxVolume
                gain += (targetGain - gain) * 0.0008
                var v: Double
                if (ev) {
                    val f = 70.0 + abs(speed) * 38 + curTh * 30
                    ph += f / sr; ph2 += f * 6.03 / sr
                    v = sin(2 * PI * ph) * 0.5 + sin(4 * PI * ph) * 0.18 + sin(2 * PI * ph2) * (0.05 + 0.08 * curTh)
                    v *= 0.55
                } else {
                    val fire = curRpm / 60.0 * cyl / 2.0
                    ph += fire / sr
                    val x = ph - kotlin.math.floor(ph)
                    // pulse train with harmonics: each firing is a short decaying burst
                    val pulse = kotlin.math.exp(-x * (5.0 + 3 * curTh)) * 2 - 0.6
                    noiseS = noiseS * 1103515245 + 12345
                    val n = ((noiseS ushr 8) and 0xffff) / 32768.0 - 1.0
                    v = pulse * 0.7 + sin(2 * PI * ph * 0.5) * 0.35 + n * (0.08 + 0.25 * curTh) * kotlin.math.exp(-x * 3)
                    if (profile == EngineSound.TURBO || profile == EngineSound.HYPER) {
                        ph2 += (2400 + curRpm * 0.6) / sr
                        v += sin(2 * PI * ph2) * 0.04 * curTh
                    }
                }
                val cut = 380 + 2600 * curTh + curRpm * 0.15
                val a = (2 * PI * cut / sr).coerceAtMost(0.95)
                lp += (v - lp) * a
                lp2 += (lp - lp2) * a
                buf[i] = (kotlin.math.tanh(lp2 * 1.4) * gain * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
            track.write(buf, 0, buf.size)
        }
        try { track.stop(); track.release() } catch (_: Exception) {}
    }
}
