package com.factonic.game

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Player preferences, persisted in SharedPreferences. Values are Compose state, so any screen that reads
 * them refreshes as soon as they change. Defaults apply until [load] runs (e.g. in unit tests).
 */
object Settings {
    private var prefs: SharedPreferences? = null

    private val _music = mutableStateOf(true)
    private val _musicVolume = mutableFloatStateOf(.6f)
    private val _sfxVolume = mutableFloatStateOf(.8f)
    private val _haptics = mutableStateOf(true)
    private val _pointer = mutableStateOf(true)
    private val _hints = mutableStateOf(true)
    private val _keepAwake = mutableStateOf(true)

    var music: Boolean
        get() = _music.value
        set(v) { _music.value = v; prefs?.edit()?.putBoolean("music", v)?.apply() }
    var musicVolume: Float
        get() = _musicVolume.floatValue
        set(v) { _musicVolume.floatValue = v.coerceIn(0f, 1f); prefs?.edit()?.putFloat("musicVolume", _musicVolume.floatValue)?.apply() }
    var sfxVolume: Float
        get() = _sfxVolume.floatValue
        set(v) { _sfxVolume.floatValue = v.coerceIn(0f, 1f); prefs?.edit()?.putFloat("sfxVolume", _sfxVolume.floatValue)?.apply() }
    var haptics: Boolean
        get() = _haptics.value
        set(v) { _haptics.value = v; prefs?.edit()?.putBoolean("haptics", v)?.apply() }
    /** The arrow at the screen edge that leads to the current objective's resource. */
    var objectivePointer: Boolean
        get() = _pointer.value
        set(v) { _pointer.value = v; prefs?.edit()?.putBoolean("pointer", v)?.apply() }
    /** The one-line control reminders above the toolbar. */
    var hints: Boolean
        get() = _hints.value
        set(v) { _hints.value = v; prefs?.edit()?.putBoolean("hints", v)?.apply() }
    /** Keep the screen on while playing. */
    var keepAwake: Boolean
        get() = _keepAwake.value
        set(v) { _keepAwake.value = v; prefs?.edit()?.putBoolean("keepAwake", v)?.apply() }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("factonic", Context.MODE_PRIVATE)
        prefs = p
        _music.value = p.getBoolean("music", true)
        _musicVolume.floatValue = p.getFloat("musicVolume", .6f)
        _sfxVolume.floatValue = p.getFloat("sfxVolume", .8f)
        _haptics.value = p.getBoolean("haptics", true)
        _pointer.value = p.getBoolean("pointer", true)
        _hints.value = p.getBoolean("hints", true)
        _keepAwake.value = p.getBoolean("keepAwake", true)
    }
}
