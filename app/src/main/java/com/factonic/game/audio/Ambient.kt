package com.factonic.game.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/** 16-bit mono WAV writer shared by the sound effects and the music. */
object Wav {
    fun write(f: File, data: FloatArray, rate: Int) {
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in data) pcm.putShort((v.coerceIn(-1f, 1f) * 30000).toInt().toShort())
        val n = data.size * 2
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + n); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(rate); h.putInt(rate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(n)
        FileOutputStream(f).use { it.write(h.array()); it.write(pcm.array()) }
    }
}

/**
 * The Xyra-7 soundtrack, composed procedurally: slow pad chords in E minor with a Phrygian F,
 * glassy bells with a dotted-eighth echo, and filtered alien wind. Everything that rings past the
 * end of the loop wraps around to the start, so the loop is seamless.
 */
object Ambient {
    const val RATE = 22050
    /** Bump when the composition changes so the cached file is regenerated. */
    const val VERSION = 1

    private val CHORDS = arrayOf(
        intArrayOf(40, 47, 54, 55, 62), // Em9      E2 B2 F#3 G3 D4
        intArrayOf(36, 43, 52, 54, 59), // Cmaj7#11 C2 G2 E3 F#3 B3
        intArrayOf(43, 50, 54, 57, 62), // Gmaj9    G2 D3 F#3 A3 D4
        intArrayOf(38, 45, 52, 54, 59), // D6/9     D2 A2 E3 F#3 B3
        intArrayOf(45, 52, 55, 59, 64), // Am9      A2 E3 G3 B3 E4
        intArrayOf(41, 48, 52, 59, 64), // Fmaj7#11 F2 C3 E3 B3 E4 (the alien chord)
        intArrayOf(36, 43, 50, 52, 59), // Cmaj9    C2 G2 D3 E3 B3
        intArrayOf(35, 42, 52, 54, 57), // B7sus    B1 F#2 E3 F#3 A3
    )

    private fun hz(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)
    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x)
    }

    fun render(rate: Int = RATE): FloatArray {
        val beat = 60f / 66f
        val chordLen = (beat * 8 * rate).roundToInt() // two bars per chord
        val n = chordLen * CHORDS.size
        val rnd = Random(7)

        // ---- wavetables: a warm band-limited saw and a pure sine, morphed by a slow "filter" sweep
        val tn = 2048
        val soft = FloatArray(tn)
        val pure = FloatArray(tn)
        var peak = 0f
        for (i in 0 until tn) {
            val ph = 2 * PI * i / tn
            var s = 0.0
            for (k in 1..10) s += sin(k * ph) / k * exp(-k * 0.18)
            soft[i] = s.toFloat(); pure[i] = sin(ph).toFloat(); peak = maxOf(peak, abs(soft[i]))
        }
        for (i in 0 until tn) soft[i] /= peak
        val bright = FloatArray(n) { (0.3f + 0.25f * sin(2 * PI * it / (n / 4.0)).toFloat()) }

        // ---- pads
        val pads = FloatArray(n)
        val att = 2.4f * rate
        val rel = 3.2f * rate
        for ((ci, chord) in CHORDS.withIndex()) {
            val start = ci * chordLen
            val total = (chordLen + rel).toInt()
            for ((ni, note) in chord.withIndex()) {
                val f0 = hz(note)
                val amp = if (ni == 0) .05f else .042f
                for (cents in floatArrayOf(-7f, 0f, 6.5f)) {
                    val inc = f0 * 2f.pow(cents / 1200f) * tn / rate
                    var ph = rnd.nextFloat() * tn
                    for (i in 0 until total) {
                        val env = (if (i < att) smooth(i / att) else 1f) * (if (i > chordLen) 1f - smooth((i - chordLen) / rel) else 1f)
                        val k = ph.toInt()
                        val fr = ph - k
                        val k2 = (k + 1) and (tn - 1)
                        val a = soft[k] + (soft[k2] - soft[k]) * fr
                        val b = pure[k] + (pure[k2] - pure[k]) * fr
                        val pos = (start + i) % n
                        pads[pos] += amp * env * (b + (a - b) * bright[pos])
                        ph += inc
                        if (ph >= tn) ph -= tn
                    }
                }
            }
            // sub-bass an octave under the root
            val sub = hz(chord[0] - 12)
            for (i in 0 until total) {
                val env = (if (i < att) smooth(i / att) else 1f) * (if (i > chordLen) 1f - smooth((i - chordLen) / rel) else 1f)
                pads[(start + i) % n] += .06f * env * sin(2 * PI * sub * i / rate).toFloat()
            }
        }

        // ---- bells on an eighth-note grid, sparse and a little random
        val bells = FloatArray(n)
        val eighth = beat / 2
        val steps = (n / (eighth * rate)).toInt()
        for (s in 0 until steps) {
            val p = when {
                s % 8 == 0 -> .5f; s % 2 == 0 -> .28f; else -> .1f
            }
            if (rnd.nextFloat() > p) continue
            val at = (s * eighth * rate).toInt()
            val chord = CHORDS[min(at / chordLen, CHORDS.size - 1)]
            val note = chord[1 + rnd.nextInt(chord.size - 1)] + 12 + (if (rnd.nextFloat() < .4f) 12 else 0)
            bell(bells, at, hz(note), .06f + rnd.nextFloat() * .035f, n, rate)
        }
        // circular feedback echo: a few passes around the loop let the tails settle seamlessly
        val d = (beat * .75f * rate).toInt()
        val wet = FloatArray(n)
        repeat(6) {
            for (i in 0 until n) {
                val j = (i - d + n) % n
                wet[i] = bells[j] * .45f + wet[j] * .42f
            }
        }

        // ---- wind: twice-filtered noise; the input is periodic so the filter output is too
        val noise = FloatArray(n) { rnd.nextFloat() * 2 - 1 }
        val wind = FloatArray(n)
        var y1 = 0f
        var y2 = 0f
        for (pass in 0..1) for (i in 0 until n) {
            y1 += (noise[i] - y1) * .03f; y2 += (y1 - y2) * .03f
            if (pass == 1) wind[i] = y2
        }
        var rms = 0.0
        for (v in wind) rms += v * v
        val wk = (.028 / sqrt(rms / n + 1e-12)).toFloat()

        // ---- mix and master
        val out = FloatArray(n)
        var pk = 0f
        for (i in 0 until n) {
            val swell = .45f + .55f * (.5f + .5f * sin(2 * PI * i / (n / 3.0)).toFloat())
            val v = pads[i] + bells[i] * .8f + wet[i] + wind[i] * wk * swell
            out[i] = v; pk = maxOf(pk, abs(v))
        }
        val g = .8f / pk
        val drive = 1.6f
        val norm = tanh(drive)
        for (i in 0 until n) out[i] = tanh(out[i] * g * drive) / norm * .9f
        return out
    }

    private fun bell(buf: FloatArray, at: Int, f: Float, amp: Float, n: Int, rate: Int) {
        val len = (2.8f * rate).toInt()
        val w = 2 * PI.toFloat() * f / rate
        for (i in 0 until len) {
            val t = i / rate.toFloat()
            val env = min(1f, t / .004f) * exp(-t * 2.2f)
            val v = sin(w * i) + .28f * sin(w * 2.76f * i) * exp(-t * 4f) + .1f * sin(w * 5.4f * i) * exp(-t * 9f)
            buf[(at + i) % n] += amp * env * v
        }
    }
}
