package com.ngstudio.inchperfect.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Tiny offline synthesiser. Every sound in the game is generated here: nothing is sampled
 * or licensed. Output: 16-bit mono WAV files.
 */
object Synth {
    const val SR = 44100

    class Buf(seconds: Double) {
        val data = FloatArray((seconds * SR).toInt())
        val size get() = data.size
        fun add(i: Int, v: Float) { if (i in data.indices) data[i] += v }
    }

    private var noiseSeed = 0x1234567
    fun noise(): Float { noiseSeed = noiseSeed * 1103515245 + 12345; return ((noiseSeed ushr 8) and 0xffff) / 32768f - 1f }
    fun reseed(s: Int) { noiseSeed = s }

    fun midi(n: Double) = 440.0 * 2.0.pow((n - 69) / 12.0)

    fun env(t: Double, a: Double, d: Double, s: Double, r: Double, len: Double): Double = when {
        t < a -> t / a
        t < a + d -> 1 - (1 - s) * (t - a) / d
        t < len -> s
        t < len + r -> s * (1 - (t - len) / r)
        else -> 0.0
    }

    // oscillators (phase in cycles)
    fun sine(ph: Double) = sin(2 * PI * ph)
    fun saw(ph: Double) = 2 * (ph - kotlin.math.floor(ph + 0.5))
    fun square(ph: Double, pw: Double = 0.5) = if (ph - kotlin.math.floor(ph) < pw) 1.0 else -1.0
    fun tri(ph: Double) = 2 * kotlin.math.abs(saw(ph)) - 1

    /** tone with pitch envelope f(t) */
    fun tone(b: Buf, start: Double, len: Double, amp: Double, wave: (Double) -> Double, freq: (Double) -> Double,
             a: Double = 0.005, d: Double = 0.05, s: Double = 0.6, r: Double = 0.1) {
        var ph = 0.0
        val n = ((len + r) * SR).toInt()
        val i0 = (start * SR).toInt()
        for (k in 0 until n) {
            val t = k.toDouble() / SR
            ph += freq(t) / SR
            b.add(i0 + k, (wave(ph) * amp * env(t, a, d, s, r, len)).toFloat())
        }
    }

    fun noiseBurst(b: Buf, start: Double, len: Double, amp: Double, lpStart: Double, lpEnd: Double, decay: Double = 8.0, hp: Boolean = false) {
        val i0 = (start * SR).toInt(); val n = (len * SR).toInt()
        var lp = 0.0; var prev = 0.0
        for (k in 0 until n) {
            val t = k.toDouble() / n
            val cut = lpStart + (lpEnd - lpStart) * t
            val alpha = (2 * PI * cut / SR).coerceAtMost(0.99)
            val x = noise().toDouble()
            lp += (x - lp) * alpha
            var v = lp
            if (hp) { v = lp - prev; prev = lp }
            b.add(i0 + k, (v * amp * exp(-decay * t * len)).toFloat())
        }
    }

    fun lowpass(b: Buf, cutoff: Double) {
        val a = (2 * PI * cutoff / SR).coerceAtMost(0.99); var y = 0.0
        for (i in b.data.indices) { y += (b.data[i] - y) * a; b.data[i] = y.toFloat() }
    }

    /** simple feedback delay (echo) */
    fun echo(b: Buf, delaySec: Double, feedback: Double, mix: Double) {
        val d = (delaySec * SR).toInt()
        for (i in d until b.size) b.data[i] += (b.data[i - d] * feedback * mix).toFloat()
    }

    fun normalize(b: Buf, peak: Double = 0.89) {
        var m = 1e-6f
        for (v in b.data) m = maxOf(m, kotlin.math.abs(v))
        val g = (peak / m).toFloat()
        for (i in b.data.indices) b.data[i] = (tanh((b.data[i] * g).toDouble() * 1.1) / tanh(1.1)).toFloat() * 0.98f
    }

    fun fadeEdges(b: Buf, ms: Double = 4.0) {
        val n = (ms / 1000 * SR).toInt().coerceAtMost(b.size / 2)
        for (i in 0 until n) { val g = i.toFloat() / n; b.data[i] *= g; b.data[b.size - 1 - i] *= g }
    }

    fun writeWav(b: Buf, file: File) {
        val pcm = ByteBuffer.allocate(b.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in b.data) pcm.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + b.size * 2); header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()); header.putInt(16); header.putShort(1); header.putShort(1)
        header.putInt(SR); header.putInt(SR * 2); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(b.size * 2)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(header.array()); it.write(pcm.array()) }
    }
}
