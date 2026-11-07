package com.factonic.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.factonic.game.Settings
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tiny procedural sound effects. Every sound is synthesised once at start-up into a WAV file in the
 * cache directory and loaded into a SoundPool - no audio assets needed, nothing to license.
 * Calls are no-ops until [init] runs (e.g. in unit tests).
 */
object Sfx {
    private const val RATE = 22050
    /** Bump when a sound changes so the cached WAVs are rebuilt. */
    private const val VERSION = 2
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val last = HashMap<String, Long>()
    private var prefs: android.content.SharedPreferences? = null

    var enabled = true
        set(v) {
            field = v; prefs?.edit()?.putBoolean("sfx", v)?.apply()
        }

    fun init(ctx: Context) {
        if (pool != null) return
        prefs = ctx.getSharedPreferences("factonic", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean("sfx", true) ?: true
        val p = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = p
        val dir = File(ctx.cacheDir, "sfx_v$VERSION").apply { mkdirs() }
        for ((name, gen) in SOUNDS) {
            val f = File(dir, "$name.wav")
            if (!f.exists() || f.length() < 64) Wav.write(f, gen(), RATE)
            ids[name] = p.load(f.path, 1)
        }
    }

    /** Plays a sound; repeated triggers of the same sound within [gapMs] are merged. */
    fun play(name: String, volume: Float = 1f, gapMs: Long = 45) {
        if (!enabled) return
        val p = pool ?: return
        val id = ids[name] ?: return
        val now = System.currentTimeMillis()
        if (now - (last[name] ?: 0L) < gapMs) return
        last[name] = now
        val v = (volume * .55f * Settings.sfxVolume).coerceIn(0f, 1f)
        if (v <= 0.005f) return
        p.play(id, v, v, 1, 0, 1f + (Random.nextFloat() - .5f) * .06f)
    }

    // ------------------------------------------------------------------------------------ synthesis
    private fun buf(sec: Float) = FloatArray((sec * RATE).toInt())

    private fun tone(out: FloatArray, f0: Float, f1: Float, amp: Float, attack: Float = .005f, decay: Float = 8f, start: Int = 0, harmonics: Float = .25f) {
        var ph = 0.0
        val n = out.size - start
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val f = f0 + (f1 - f0) * (i / n.toFloat())
            ph += 2 * PI * f / RATE
            val env = min(1f, t / attack) * exp(-t * decay)
            out[start + i] += amp * env * (sin(ph).toFloat() + harmonics * sin(2 * ph).toFloat())
        }
    }

    private fun noise(out: FloatArray, amp: Float, decay: Float, lowpass: Float, start: Int = 0, attack: Float = .002f) {
        var y = 0f
        val rnd = Random(out.size)
        for (i in start until out.size) {
            val t = (i - start) / RATE.toFloat()
            y += (rnd.nextFloat() * 2 - 1 - y) * lowpass
            out[i] += amp * y * min(1f, t / attack) * exp(-t * decay)
        }
    }

    private val SOUNDS: List<Pair<String, () -> FloatArray>> = listOf(
        "click" to { buf(.05f).also { tone(it, 1500f, 1100f, .35f, decay = 70f, harmonics = 0f) } },
        "place" to { buf(.16f).also { tone(it, 170f, 70f, .8f, decay = 26f); noise(it, .5f, 40f, .25f) } },
        "rotate" to { buf(.06f).also { tone(it, 900f, 1300f, .3f, decay = 50f, harmonics = 0f) } },
        "remove" to { buf(.16f).also { tone(it, 260f, 620f, .45f, decay = 18f); noise(it, .25f, 30f, .5f) } },
        "mine" to { buf(.09f).also { noise(it, .9f, 45f, .6f); tone(it, 520f, 380f, .3f, decay = 45f) } },
        "craft" to { buf(.22f).also { tone(it, 1318f, 1318f, .3f, decay = 16f, harmonics = .1f); tone(it, 1975f, 1975f, .25f, decay = 16f, start = (RATE * .06f).toInt(), harmonics = .1f) } },
        "research" to {
            buf(.7f).also { b ->
                floatArrayOf(784f, 988f, 1175f, 1568f).forEachIndexed { k, f -> tone(b, f, f, .3f, decay = 5f, start = (RATE * .09f * k).toInt(), harmonics = .15f) }
            }
        },
        "error" to { buf(.2f).also { tone(it, 180f, 170f, .4f, decay = 14f, harmonics = .6f); tone(it, 150f, 140f, .4f, decay = 14f, start = (RATE * .09f).toInt(), harmonics = .6f) } },
        "launch" to { buf(2.2f).also { noise(it, 1f, 1.4f, .05f, attack = .4f); tone(it, 60f, 140f, .5f, attack = .3f, decay = 1.5f, harmonics = .5f) } },
        "open" to { buf(.08f).also { tone(it, 700f, 1000f, .25f, decay = 35f, harmonics = 0f) } },
        "alert" to { buf(.45f).also { tone(it, 988f, 988f, .28f, decay = 9f, harmonics = .2f); tone(it, 740f, 740f, .3f, decay = 8f, start = (RATE * .14f).toInt(), harmonics = .2f) } },
        "objective" to {
            buf(.8f).also { b ->
                floatArrayOf(659f, 880f, 1047f, 1319f).forEachIndexed { k, f -> tone(b, f, f, .26f, decay = 4.5f, start = (RATE * .07f * k).toInt(), harmonics = .12f) }
            }
        },
        "pickup" to { buf(.12f).also { tone(it, 520f, 900f, .3f, decay = 30f, harmonics = .1f) } },
    )

}
