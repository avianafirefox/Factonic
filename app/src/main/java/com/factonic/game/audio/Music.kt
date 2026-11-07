package com.factonic.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.factonic.game.Settings
import java.io.File

/**
 * Background music. The track is synthesised by [Ambient] on a background thread the first time the
 * game runs and cached as a WAV file, then looped with a MediaPlayer. All player calls happen on the
 * main thread.
 */
object Music {
    private var player: MediaPlayer? = null
    private var track: File? = null
    private var main: Handler? = null
    private var started = false
    private var foreground = false

    fun init(ctx: Context) {
        if (started) return
        started = true
        val h = Handler(Looper.getMainLooper())
        main = h
        val dir = File(ctx.cacheDir, "music").apply { mkdirs() }
        val f = File(dir, "xyra_ambient_v${Ambient.VERSION}.wav")
        Thread({
            try {
                if (!f.exists() || f.length() < 100_000) {
                    dir.listFiles()?.forEach { it.delete() }
                    val tmp = File(dir, f.name + ".tmp")
                    Wav.write(tmp, Ambient.render(Ambient.RATE), Ambient.RATE)
                    tmp.renameTo(f)
                }
                h.post { track = f; refresh() }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }, "music-gen").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    fun onResume() {
        foreground = true; refresh()
    }

    fun onPause() {
        foreground = false; refresh()
    }

    /** Start, pause or re-level the music to match the settings and app state. Main thread only. */
    fun refresh() {
        val f = track ?: return
        try {
            if (foreground && Settings.music && Settings.musicVolume > 0.01f) {
                val p = player ?: create(f) ?: return
                val v = Settings.musicVolume * .7f
                p.setVolume(v, v)
                if (!p.isPlaying) p.start()
            } else {
                player?.let { if (it.isPlaying) it.pause() }
            }
        } catch (e: Exception) {
            // the player hit an error state: drop it and try again next time
            e.printStackTrace()
            release()
        }
    }

    private fun create(f: File): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(f.path)
            isLooping = true
            setOnErrorListener { _, _, _ -> main?.post { release() }; true }
            prepare()
        }.also { player = it }
    } catch (e: Exception) {
        e.printStackTrace(); null
    }

    fun release() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
