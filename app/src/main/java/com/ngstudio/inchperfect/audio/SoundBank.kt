package com.ngstudio.inchperfect.audio

import com.ngstudio.inchperfect.audio.Synth.Buf
import com.ngstudio.inchperfect.audio.Synth.midi
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Sfx {
    CLICK, TAP, WHOOSH, COIN, STAR1, STAR2, STAR3, FANFARE, PERFECT, CRASH, CONE, GEAR, BEEP, HORN, SCREECH,
    PURCHASE, UNLOCK, TICK, TABLE, REWIND, OOPS, CHEST, LOCKED, SPIN
}

enum class Track(val bpm: Double) { MENU(96.0), GAME(84.0), NIGHT(100.0) }

/** Renders every effect and music loop to WAV files in the cache (once per version). */
object SoundBank {
    const val VERSION = 3

    fun file(dir: File, s: Sfx) = File(dir, "sfx_${s.name.lowercase()}_v$VERSION.wav")
    fun file(dir: File, t: Track) = File(dir, "music_${t.name.lowercase()}_v$VERSION.wav")

    fun renderAll(dir: File, includeMusic: Boolean = true) {
        for (s in Sfx.entries) {
            val f = file(dir, s)
            if (!f.exists()) { val b = make(s); Synth.fadeEdges(b, 2.0); Synth.writeWav(b, f) }
        }
        if (includeMusic) for (t in Track.entries) renderTrack(dir, t)
    }

    fun renderTrack(dir: File, t: Track) {
        val f = file(dir, t)
        if (!f.exists()) Synth.writeWav(music(t), f)
    }

    // ---------------------------------------------------------------- sfx
    private fun bell(b: Buf, start: Double, f: Double, amp: Double, len: Double = 0.8) {
        val ratios = doubleArrayOf(1.0, 2.0, 2.76, 5.4, 8.93)
        val amps = doubleArrayOf(1.0, 0.45, 0.35, 0.18, 0.08)
        val decays = doubleArrayOf(4.0, 6.0, 8.0, 12.0, 16.0)
        val i0 = (start * Synth.SR).toInt(); val n = (len * Synth.SR).toInt()
        for (k in 0 until n) {
            val t = k.toDouble() / Synth.SR
            var v = 0.0
            for (h in ratios.indices) v += sin(2 * PI * f * ratios[h] * t) * amps[h] * exp(-decays[h] * t)
            b.add(i0 + k, (v * amp * minOf(1.0, t * 400)).toFloat())
        }
    }

    fun make(s: Sfx): Buf {
        Synth.reseed(s.ordinal * 977 + 13)
        return when (s) {
            Sfx.CLICK -> Buf(0.08).also { b ->
                Synth.tone(b, 0.0, 0.03, 0.6, Synth::sine, { t -> 1500 - t * 18000 }, a = 0.001, d = 0.02, s = 0.3, r = 0.03)
                Synth.noiseBurst(b, 0.0, 0.01, 0.25, 6000.0, 3000.0)
            }
            Sfx.TAP -> Buf(0.1).also { b -> Synth.tone(b, 0.0, 0.04, 0.5, Synth::tri, { 880.0 }, a = 0.002, d = 0.03, s = 0.2, r = 0.05) }
            Sfx.WHOOSH -> Buf(0.45).also { b ->
                val n = b.size; var lp = 0.0
                for (k in 0 until n) {
                    val t = k.toDouble() / n
                    val cut = 300 + 3500 * sin(PI * t)
                    lp += (Synth.noise() - lp) * (2 * PI * cut / Synth.SR)
                    b.data[k] = (lp * sin(PI * t) * 1.6).toFloat()
                }
            }
            Sfx.COIN -> Buf(0.45).also { b ->
                Synth.tone(b, 0.0, 0.06, 0.35, { Synth.square(it, 0.25) }, { midi(83.0) }, a = 0.001, d = 0.02, s = 0.7, r = 0.02)
                Synth.tone(b, 0.07, 0.18, 0.35, { Synth.square(it, 0.25) }, { midi(88.0) }, a = 0.001, d = 0.1, s = 0.4, r = 0.15)
                bell(b, 0.07, midi(100.0), 0.15, 0.35)
                Synth.lowpass(b, 7000.0)
            }
            Sfx.STAR1 -> Buf(0.9).also { bell(it, 0.0, midi(84.0), 0.45) }
            Sfx.STAR2 -> Buf(0.9).also { bell(it, 0.0, midi(88.0), 0.45) }
            Sfx.STAR3 -> Buf(1.1).also { bell(it, 0.0, midi(91.0), 0.45); bell(it, 0.0, midi(96.0), 0.2, 1.0) }
            Sfx.FANFARE -> Buf(2.0).also { b ->
                val notes = doubleArrayOf(72.0, 76.0, 79.0, 84.0)
                notes.forEachIndexed { i, n ->
                    Synth.tone(b, i * 0.11, 0.12, 0.22, Synth::saw, { midi(n) }, a = 0.004, d = 0.05, s = 0.6, r = 0.1)
                    Synth.tone(b, i * 0.11, 0.12, 0.16, { Synth.square(it, 0.3) }, { midi(n + 12) }, a = 0.004, d = 0.05, s = 0.5, r = 0.1)
                }
                for (n in doubleArrayOf(72.0, 76.0, 79.0, 84.0)) {
                    Synth.tone(b, 0.46, 0.9, 0.12, Synth::saw, { t -> midi(n) * (1 + 0.004 * sin(t * 30)) }, a = 0.01, d = 0.2, s = 0.7, r = 0.5)
                }
                Synth.lowpass(b, 5200.0)
                bell(b, 0.46, midi(96.0), 0.18, 1.2)
                Synth.echo(b, 0.12, 0.35, 1.0)
            }
            Sfx.PERFECT -> Buf(1.6).also { b ->
                Synth.tone(b, 0.0, 0.6, 0.18, Synth::sine, { t -> 600 + 2600 * t / 0.6 }, a = 0.02, d = 0.1, s = 0.8, r = 0.2)
                for ((i, n) in doubleArrayOf(84.0, 88.0, 91.0, 96.0, 100.0).withIndex()) bell(b, 0.25 + i * 0.07, midi(n), 0.22, 1.1)
                Synth.echo(b, 0.09, 0.4, 1.0)
            }
            Sfx.CRASH -> Buf(1.3).also { b ->
                Synth.tone(b, 0.0, 0.25, 0.9, Synth::sine, { t -> 110 - 200 * t }, a = 0.001, d = 0.1, s = 0.6, r = 0.15)
                Synth.noiseBurst(b, 0.0, 0.7, 0.9, 7000.0, 600.0, decay = 7.0)
                for ((f, a) in listOf(523.0 to 0.25, 1187.0 to 0.2, 1873.0 to 0.16, 2711.0 to 0.12, 3433.0 to 0.08)) {
                    Synth.tone(b, 0.01, 0.05, a, Synth::sine, { f }, a = 0.001, d = 0.3, s = 0.0, r = 0.4)
                }
                val rnd = java.util.Random(7)
                repeat(14) {
                    val st = 0.06 + rnd.nextDouble() * 0.7; val f = 3000 + rnd.nextDouble() * 4000
                    Synth.tone(b, st, 0.01, 0.07, Synth::sine, { f }, a = 0.0005, d = 0.03, s = 0.0, r = 0.06)
                }
            }
            Sfx.CONE -> Buf(0.3).also { b ->
                Synth.tone(b, 0.0, 0.06, 0.6, Synth::sine, { t -> 220 - 900 * t }, a = 0.001, d = 0.05, s = 0.3, r = 0.08)
                Synth.noiseBurst(b, 0.0, 0.09, 0.4, 1500.0, 600.0)
                Synth.tone(b, 0.0, 0.02, 0.2, Synth::tri, { 980.0 }, a = 0.0005, d = 0.02, s = 0.0, r = 0.03)
            }
            Sfx.GEAR -> Buf(0.16).also { b ->
                Synth.noiseBurst(b, 0.0, 0.03, 0.55, 3500.0, 1500.0)
                Synth.tone(b, 0.005, 0.05, 0.6, Synth::sine, { t -> 140 - 300 * t }, a = 0.001, d = 0.04, s = 0.2, r = 0.05)
                Synth.noiseBurst(b, 0.06, 0.02, 0.3, 4000.0, 2000.0)
            }
            Sfx.BEEP -> Buf(0.09).also { b ->
                Synth.tone(b, 0.0, 0.055, 0.45, { Synth.sine(it) + 0.25 * Synth.sine(it * 3) }, { 1760.0 }, a = 0.002, d = 0.01, s = 0.9, r = 0.02)
            }
            Sfx.HORN -> Buf(0.65).also { b ->
                for (f in doubleArrayOf(349.2, 440.0)) Synth.tone(b, 0.0, 0.5, 0.28, { Synth.square(it, 0.45) }, { t -> f * (1 + 0.006 * sin(t * 40)) }, a = 0.01, d = 0.05, s = 0.9, r = 0.08)
                Synth.lowpass(b, 1900.0)
            }
            Sfx.SCREECH -> Buf(0.7).also { b ->
                var lp1 = 0.0; var lp2 = 0.0
                for (k in 0 until b.size) {
                    val t = k.toDouble() / b.size
                    val cen = 2200 + 400 * sin(k * 0.002)
                    val x = Synth.noise().toDouble()
                    lp1 += (x - lp1) * (2 * PI * (cen * 1.2) / Synth.SR); lp2 += (x - lp2) * (2 * PI * (cen * 0.8) / Synth.SR)
                    b.data[k] = ((lp1 - lp2) * 3.0 * sin(PI * t)).toFloat()
                }
            }
            Sfx.PURCHASE -> Buf(0.9).also { b ->
                Synth.tone(b, 0.0, 0.05, 0.3, { Synth.square(it, 0.25) }, { midi(86.0) }, a = 0.001, d = 0.02, s = 0.7, r = 0.02)
                Synth.tone(b, 0.06, 0.05, 0.3, { Synth.square(it, 0.25) }, { midi(91.0) }, a = 0.001, d = 0.02, s = 0.7, r = 0.02)
                bell(b, 0.12, midi(96.0), 0.3, 0.75); bell(b, 0.12, midi(91.0), 0.2, 0.75)
            }
            Sfx.UNLOCK -> Buf(0.9).also { b ->
                for ((i, n) in doubleArrayOf(79.0, 83.0, 86.0, 91.0).withIndex()) Synth.tone(b, i * 0.08, 0.1, 0.28, Synth::tri, { midi(n) }, a = 0.002, d = 0.05, s = 0.6, r = 0.2)
                bell(b, 0.32, midi(98.0), 0.15, 0.55)
                Synth.echo(b, 0.1, 0.35, 1.0)
            }
            Sfx.TICK -> Buf(0.03).also { b -> Synth.noiseBurst(b, 0.0, 0.012, 0.6, 7000.0, 4000.0, hp = true); Synth.tone(b, 0.0, 0.006, 0.4, Synth::sine, { 2400.0 }, a = 0.0005, d = 0.004, s = 0.0, r = 0.01) }
            Sfx.TABLE -> Buf(1.8).also { b ->
                Synth.tone(b, 0.0, 1.5, 0.3, Synth::saw, { t -> 70 + 25 * minOf(1.0, t * 2) }, a = 0.2, d = 0.2, s = 0.8, r = 0.25)
                Synth.lowpass(b, 600.0)
                Synth.noiseBurst(b, 1.55, 0.06, 0.4, 2500.0, 900.0)
            }
            Sfx.REWIND -> Buf(0.6).also { b ->
                Synth.tone(b, 0.0, 0.45, 0.35, { Synth.sine(it) * (0.6 + 0.4 * Synth.square(it * 0.03)) }, { t -> 1700 - 3000 * t }, a = 0.01, d = 0.1, s = 0.8, r = 0.1)
                Synth.noiseBurst(b, 0.0, 0.5, 0.12, 5000.0, 400.0)
            }
            Sfx.OOPS -> Buf(0.8).also { b ->
                for ((i, n) in doubleArrayOf(76.0, 72.0, 69.0).withIndex()) Synth.tone(b, i * 0.14, 0.13, 0.3, Synth::tri, { t -> midi(n) * (1 - t * 0.2) }, a = 0.003, d = 0.05, s = 0.6, r = 0.12)
            }
            Sfx.CHEST -> Buf(1.4).also { b ->
                val rnd = java.util.Random(11)
                repeat(26) { i ->
                    val st = i * 0.035 + rnd.nextDouble() * 0.02; val f = 1200 + i * 110 + rnd.nextDouble() * 300
                    Synth.tone(b, st, 0.05, 0.08, Synth::sine, { f }, a = 0.002, d = 0.05, s = 0.4, r = 0.25)
                }
                for (n in doubleArrayOf(60.0, 64.0, 67.0)) Synth.tone(b, 0.0, 0.9, 0.1, Synth::saw, { midi(n) }, a = 0.4, d = 0.2, s = 0.8, r = 0.4)
                Synth.lowpass(b, 6000.0)
                bell(b, 0.9, midi(96.0), 0.2, 0.5)
            }
            Sfx.LOCKED -> Buf(0.35).also { b ->
                Synth.tone(b, 0.0, 0.09, 0.3, { Synth.square(it, 0.5) }, { 170.0 }, a = 0.002, d = 0.02, s = 0.8, r = 0.02)
                Synth.tone(b, 0.13, 0.09, 0.3, { Synth.square(it, 0.5) }, { 150.0 }, a = 0.002, d = 0.02, s = 0.8, r = 0.04)
                Synth.lowpass(b, 1500.0)
            }
            Sfx.SPIN -> Buf(0.5).also { b -> Synth.noiseBurst(b, 0.0, 0.5, 0.35, 900.0, 2500.0, decay = 2.0) }
        }.also { Synth.normalize(it, if (s == Sfx.BEEP || s == Sfx.TICK || s == Sfx.CLICK) 0.6 else 0.85) }
    }

    // -------------------------------------------------------------- music
    private class Seq(val b: FloatArray) {
        fun add(i: Int, v: Float) { b[Math.floorMod(i, b.size)] += v }
    }

    /** One seamless loop: 8 bars. Every note wraps around the loop end. */
    fun music(track: Track): Buf {
        Synth.reseed(track.ordinal * 31 + 5)
        val beat = 60.0 / track.bpm
        val bars = 8
        val total = bars * 4 * beat
        val out = Buf(total)
        val sq = Seq(out.data)
        val sr = Synth.SR
        val chords: List<IntArray> = when (track) {
            Track.MENU -> listOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62))      // Am F C G
            Track.GAME -> listOf(intArrayOf(50, 53, 57, 60), intArrayOf(55, 59, 62, 65), intArrayOf(48, 52, 55, 59), intArrayOf(57, 60, 64, 67)) // Dm7 G7 Cmaj7 Am7
            Track.NIGHT -> listOf(intArrayOf(52, 55, 59), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62), intArrayOf(50, 54, 57))    // Em C G D
        }

        fun voice(start: Double, len: Double, freq: Double, amp: Double, wave: (Double) -> Double, a: Double, r: Double, lpHz: Double, detune: Double = 0.0) {
            val n = ((len + r) * sr).toInt(); val i0 = (start * sr).toInt()
            var ph = 0.0; var ph2 = 0.17; var lp = 0.0
            val al = (2 * PI * lpHz / sr).coerceAtMost(0.99)
            for (k in 0 until n) {
                val t = k.toDouble() / sr
                ph += freq / sr; ph2 += freq * (1 + detune) / sr
                val e = Synth.env(t, a, 0.0, 1.0, r, len)
                var v = wave(ph)
                if (detune != 0.0) v = (v + wave(ph2)) * 0.5
                lp += (v - lp) * al
                sq.add(i0 + k, (lp * amp * e).toFloat())
            }
        }

        fun kick(t0: Double, amp: Double) {
            val i0 = (t0 * sr).toInt(); var ph = 0.0
            for (k in 0 until (0.32 * sr).toInt()) {
                val t = k.toDouble() / sr
                ph += (48 + 110 * exp(-t * 28)) / sr
                sq.add(i0 + k, (sin(2 * PI * ph) * amp * exp(-t * 9)).toFloat())
            }
        }
        fun snare(t0: Double, amp: Double) {
            val i0 = (t0 * sr).toInt(); var lp = 0.0; var prev = 0.0
            for (k in 0 until (0.22 * sr).toInt()) {
                val t = k.toDouble() / sr
                val x = Synth.noise().toDouble()
                lp += (x - lp) * 0.35; val hp = lp - prev; prev = lp
                sq.add(i0 + k, ((hp * 1.4 * exp(-t * 18)) + sin(2 * PI * 190 * t) * 0.35 * exp(-t * 30)).toFloat() * amp.toFloat())
            }
        }
        fun hat(t0: Double, amp: Double, open: Boolean) {
            val i0 = (t0 * sr).toInt(); var prev = 0.0
            val dec = if (open) 9.0 else 45.0
            for (k in 0 until ((if (open) 0.2 else 0.05) * sr).toInt()) {
                val t = k.toDouble() / sr
                val x = Synth.noise().toDouble(); val hp = x - prev; prev = x
                sq.add(i0 + k, (hp * amp * exp(-t * dec)).toFloat())
            }
        }

        val rnd = java.util.Random(track.ordinal * 101L + 7)
        for (bar in 0 until bars) {
            val ch = chords[bar % chords.size]
            val t0 = bar * 4 * beat
            // pad
            for (n in ch) voice(t0, 4 * beat * 0.95, midi(n.toDouble()), if (track == Track.GAME) 0.06 else 0.07, Synth::saw, 0.25, 0.5, if (track == Track.NIGHT) 1300.0 else 1700.0, 0.006)
            // bass
            val root = ch[0] - 24
            for (q in 0 until 8) {
                val play = when (track) { Track.MENU -> true; Track.GAME -> q % 2 == 0 || q == 7; Track.NIGHT -> q != 3 }
                if (!play) continue
                val note = if (track == Track.MENU && q % 2 == 1) root + 12 else root
                voice(t0 + q * beat / 2, beat * 0.42, midi(note.toDouble()), 0.2, Synth::saw, 0.005, 0.06, 420.0)
            }
            // drums
            for (q in 0 until 4) {
                val tb = t0 + q * beat
                when (track) {
                    Track.MENU -> { kick(tb, 0.55); if (q % 2 == 1) snare(tb, 0.35) }
                    Track.GAME -> { if (q == 0 || q == 2 && bar % 2 == 1) kick(tb, 0.45); if (q == 2) kick(tb + beat * 0.5, 0.3); if (q % 2 == 1) snare(tb, 0.28) }
                    Track.NIGHT -> { if (q % 2 == 0) kick(tb, 0.5); if (q == 1 || q == 3) snare(tb, 0.25); if (q == 3) kick(tb + beat * 0.75, 0.3) }
                }
                for (e in 0 until 2) {
                    val swing = if (track == Track.GAME && e == 1) beat * 0.08 else 0.0
                    hat(tb + e * beat / 2 + swing, if (e == 1) 0.11 else 0.07, track == Track.MENU && e == 1 && q == 3)
                }
            }
            // arpeggio
            val arpAmp = if (track == Track.GAME) 0.05 else 0.065
            for (k in 0 until 16) {
                if (track == Track.GAME && k % 2 == 1) continue
                val n = ch[k % ch.size] + 12 + (if (k / ch.size % 2 == 1) 12 else 0)
                voice(t0 + k * beat / 4, beat / 4 * 0.7, midi(n.toDouble()), arpAmp, { Synth.square(it, 0.3) }, 0.003, 0.08, 2600.0)
            }
            // lead melody on the menu / night themes
            if (track != Track.GAME && bar % 2 == 1) {
                var tt = t0
                val scale = intArrayOf(0, 2, 3, 5, 7, 8, 10)
                while (tt < t0 + 4 * beat - 0.01) {
                    val dur = if (rnd.nextBoolean()) beat else beat / 2
                    val deg = rnd.nextInt(7)
                    val n = ch[0] + 12 + scale[deg]
                    voice(tt, dur * 0.85, midi(n.toDouble()), 0.07, { Synth.tri(it) * 0.7 + Synth.sine(it) * 0.3 }, 0.01, 0.12, 3000.0)
                    tt += dur
                }
            }
        }
        // stereo-ish width via short echo, then master
        val d = (beat * 0.75 * sr).toInt()
        val dry = out.data.copyOf()
        for (i in out.data.indices) out.data[i] += dry[Math.floorMod(i - d, dry.size)] * 0.22f
        Synth.normalize(out, 0.78)
        return out
    }
}
