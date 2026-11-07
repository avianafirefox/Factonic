package com.factonic.game.render

import android.graphics.Bitmap
import com.factonic.game.world.Ground
import com.factonic.game.world.Noise
import com.factonic.game.world.Res
import com.factonic.game.world.World
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.floor

/**
 * Smooth, seamless terrain. The world is cut into 16x16-tile chunks that are painted into bitmaps
 * off the UI thread. Each pixel blends the tileable biome textures with weights interpolated between
 * tile centres and perturbed by noise, so biome borders and lake shores are organic curves instead of
 * square tiles. Two levels of detail: 64 px/tile when zoomed in, 32 px/tile when zoomed out.
 */
class Terrain(private val world: World) {
    companion object {
        const val CHUNK = 16
        private const val TEX = 256
        /** Robolectric screenshot tests paint chunks synchronously. */
        var synchronous = false
        private val FALLBACK = intArrayOf(0xFF4A4458.toInt(), 0xFF2F5A55.toInt(), 0xFF2C2D44.toInt(), 0xFF6A4E6E.toInt(), 0xFF0E5A5C.toInt())
        private const val BUDGET_BYTES = 56L * 1024 * 1024
        /** One painter pool for the whole app; a new world reuses it instead of starting more threads. */
        private val pool by lazy {
            Executors.newFixedThreadPool(2) { r -> Thread(r, "terrain").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
        }
    }

    private val tex: Array<IntArray?> = arrayOfNulls(5)
    private val noise = Noise(world.seed * 7 + 11)
    private val cache = object : LinkedHashMap<Long, Bitmap>(64, .75f, true) {}
    private var cacheBytes = 0L
    private val pending = ConcurrentHashMap.newKeySet<Long>()
    private val done = ConcurrentHashMap<Long, Bitmap>()

    init {
        val names = arrayOf("terrain_regolith", "terrain_moss", "terrain_basalt", "terrain_dunes", "terrain_lake")
        for (k in names.indices) {
            val b = Sprites[names[k]] ?: continue
            val s = if (b.width == TEX && b.height == TEX) b else Bitmap.createScaledBitmap(b, TEX, TEX, true)
            val px = IntArray(TEX * TEX); s.getPixels(px, 0, TEX, 0, 0, TEX, TEX); tex[k] = px
        }
    }

    private fun key(lod: Int, cx: Int, cy: Int) = (lod.toLong() shl 40) or (cx.toLong() shl 20) or cy.toLong()

    /** Returns the chunk bitmap, the other LOD as a stand-in, or null while it's being painted. */
    fun chunk(cx: Int, cy: Int, lod: Int): Bitmap? {
        drain()
        val k = key(lod, cx, cy)
        synchronized(cache) { cache[k] }?.let { return it }
        if (synchronous) {
            val b = paint(cx, cy, lod); put(k, b); return b
        }
        if (pending.add(k)) pool.execute {
            try { done[k] = paint(cx, cy, lod) } finally { pending.remove(k) }
        }
        return synchronized(cache) { cache[key(1 - lod, cx, cy)] }
    }

    private fun drain() {
        if (done.isEmpty()) return
        val it = done.entries.iterator()
        while (it.hasNext()) { val e = it.next(); put(e.key, e.value); it.remove() }
    }

    private fun put(k: Long, b: Bitmap) = synchronized(cache) {
        cache.put(k, b)?.let { cacheBytes -= it.byteCount }
        cacheBytes += b.byteCount
        val it = cache.entries.iterator()
        while (cacheBytes > BUDGET_BYTES && it.hasNext()) {
            val e = it.next(); if (e.key == k) continue
            cacheBytes -= e.value.byteCount; it.remove()
        }
    }

    /** Forget every painted chunk (e.g. after loading a different world). */
    fun clear() = synchronized(cache) { cache.clear(); cacheBytes = 0 }

    // ------------------------------------------------------------------------------------------- painting
    private fun cls(x: Int, y: Int): Int = if (!world.inside(x, y)) Ground.LAKE else world.ground[world.idx(x, y)].toInt()

    private fun stainColor(x: Int, y: Int): Int {
        if (!world.inside(x, y)) return 0
        return when (world.res[world.idx(x, y)].toInt()) {
            Res.FERRITE -> 0xFF3A6FD8.toInt(); Res.CUPRITE -> 0xFFE07A3A.toInt(); Res.LUMITE -> 0xFFE8E2C8.toInt()
            Res.CARBON -> 0xFF101018.toInt(); Res.AETHER -> 0xFFA86BFF.toInt(); Res.XENITE -> 0xFF5BE05A.toInt()
            Res.SAP -> 0xFFFF9A20.toInt(); Res.SOLANITE -> 0xFFFFB238.toInt(); Res.TERRAVITE -> 0xFF8FB89A.toInt()
            Res.VITAE -> 0xFFFF5FA8.toInt()
            else -> 0
        }
    }

    private fun texel(c: Int, x: Int, y: Int): Int {
        val t = tex[c] ?: return FALLBACK[c]
        return t[(y and 255) * TEX + (x and 255)]
    }

    private fun ss(e0: Float, e1: Float, v: Float): Float {
        val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * t).toInt()
        val g = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * t).toInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun scale(a: Int, k: Float): Int {
        val r = ((a shr 16 and 255) * k).toInt().coerceIn(0, 255)
        val g = ((a shr 8 and 255) * k).toInt().coerceIn(0, 255)
        val b = ((a and 255) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun paint(cx: Int, cy: Int, lod: Int): Bitmap {
        val ppt = if (lod == 0) 64 else 32
        val texStep = 64 / ppt // keep the texture's world scale constant across LODs
        val n = CHUNK * ppt
        val out = IntArray(n * n)
        val wgt = FloatArray(5)
        val gs = IntArray(4)
        val ws = FloatArray(4)
        val st = IntArray(4)
        val x0 = cx * CHUNK
        val y0 = cy * CHUNK
        for (py in 0 until n) {
            val wy = y0 + (py + .5f) / ppt
            for (px in 0 until n) {
                val wx = x0 + (px + .5f) / ppt
                // domain warp: biome borders follow organic curves instead of the tile grid
                val dx = (noise.value(wx * .55f + 31f, wy * .55f) - .5f) * .9f + (noise.value(wx * 1.9f, wy * 1.9f + 17f) - .5f) * .35f
                val dy = (noise.value(wx * .55f, wy * .55f + 63f) - .5f) * .9f + (noise.value(wx * 1.9f + 41f, wy * 1.9f) - .5f) * .35f
                val u = wx + dx - .5f
                val v = wy + dy - .5f
                val iu = floor(u).toInt()
                val fu = u - iu
                val iv = floor(v).toInt()
                val fv = v - iv
                gs[0] = cls(iu, iv); gs[1] = cls(iu + 1, iv); gs[2] = cls(iu, iv + 1); gs[3] = cls(iu + 1, iv + 1)
                ws[0] = (1 - fu) * (1 - fv); ws[1] = fu * (1 - fv); ws[2] = (1 - fu) * fv; ws[3] = fu * fv
                val tx = px * texStep
                val ty = py * texStep
                var col: Int
                if (gs[0] == gs[1] && gs[0] == gs[2] && gs[0] == gs[3]) {
                    col = texel(gs[0], tx, ty)
                } else {
                    for (k in 0 until 5) wgt[k] = 0f
                    for (k in 0 until 4) wgt[gs[k]] += ws[k]
                    // two strongest classes after a per-class noise nudge
                    var a = -1; var sa = -9f; var b = -1; var sb = -9f
                    for (c in 0 until 5) {
                        if (wgt[c] <= 0f) continue
                        val nz = noise.value(wx * 1.6f + c * 19.3f, wy * 1.6f + c * 7.1f) * .65f + noise.value(wx * 4.3f + c * 3.7f, wy * 4.3f) * .35f
                        val s = wgt[c] + (nz - .5f) * .55f
                        if (s > sa) { b = a; sb = sa; a = c; sa = s } else if (s > sb) { b = c; sb = s }
                    }
                    if (b < 0) col = texel(a, tx, ty)
                    else {
                        val lakeEdge = a == Ground.LAKE || b == Ground.LAKE
                        val d = sa - sb
                        val t = if (lakeEdge) ss(-.04f, .04f, d) else ss(-.14f, .14f, d)
                        col = mix(texel(b, tx, ty), texel(a, tx, ty), t)
                        if (lakeEdge) {
                            if (a == Ground.LAKE) {
                                // shallow water near the shore glows brighter
                                col = mix(col, 0xFF2FD8C4.toInt(), .32f * (1 - ss(0f, .3f, d)))
                            } else {
                                // wet, darker ground around lakes plus a thin bright rim
                                col = scale(col, .62f + .38f * ss(0f, .3f, d))
                                if (d < .05f) col = mix(col, 0xFF5CF5DF.toInt(), .45f * (1 - d / .05f))
                            }
                        }
                    }
                }
                // large-scale tonal variation hides texture repetition
                val big = noise.value(wx / 7f + 100f, wy / 7f) * .7f + noise.value(wx / 2.3f, wy / 2.3f + 50f) * .3f
                col = scale(col, .86f + .26f * big)
                // mineral stains under ore patches and pools
                st[0] = stainColor(iu, iv); st[1] = stainColor(iu + 1, iv); st[2] = stainColor(iu, iv + 1); st[3] = stainColor(iu + 1, iv + 1)
                if (st[0] != 0 || st[1] != 0 || st[2] != 0 || st[3] != 0) {
                    var m = 0f; var best = 0; var bw = -1f
                    for (k in 0 until 4) if (st[k] != 0) { m += ws[k]; if (ws[k] > bw) { bw = ws[k]; best = st[k] } }
                    val s = ss(.2f, .7f, m + (noise.value(wx * 2.2f, wy * 2.2f + 9f) - .5f) * .5f)
                    if (s > 0f) col = mix(scale(col, 1f - .22f * s), best, .16f * s)
                }
                out[py * n + px] = col
            }
        }
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.RGB_565)
        bmp.setPixels(out, 0, n, 0, 0, n, n)
        return bmp
    }
}
