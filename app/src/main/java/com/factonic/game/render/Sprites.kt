package com.factonic.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.factonic.game.R

/** Loads every PNG in drawable-nodpi once and keeps it for both the renderer and the UI. */
object Sprites {
    private val map = HashMap<String, Bitmap>()
    lateinit var light: Bitmap
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        for (f in R.drawable::class.java.fields) {
            if (f.name.startsWith("title") || f.name.startsWith("ic_")) continue
            val id = f.getInt(null)
            val bmp = try { BitmapFactory.decodeResource(ctx.resources, id, opts) } catch (e: Exception) { null } ?: continue
            map[f.name] = bmp
        }
        light = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(64f, 64f, 64f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(64f, 64f, 64f, p)
        }
        loaded = true
    }

    operator fun get(name: String): Bitmap? = map[name]
    fun has(name: String) = map.containsKey(name)
}
