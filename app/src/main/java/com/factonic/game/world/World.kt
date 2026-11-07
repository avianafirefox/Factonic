package com.factonic.game.world

import com.factonic.game.data.Item
import com.factonic.game.data.Items
import com.factonic.game.sim.Building
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object Res {
    const val NONE = 0
    const val FERRITE = 1
    const val CUPRITE = 2
    const val LUMITE = 3
    const val CARBON = 4
    const val AETHER = 5
    const val XENITE = 6
    const val SAP = 7
    const val VENT = 8
    const val SOLANITE = 9
    const val TERRAVITE = 10
    const val VITAE = 11

    fun item(r: Int): Item? = when (r) {
        FERRITE -> Items.FERRITE_ORE
        CUPRITE -> Items.CUPRITE_ORE
        LUMITE -> Items.LUMITE
        CARBON -> Items.CARBON
        AETHER -> Items.AETHER
        XENITE -> Items.XENITE
        SAP -> Items.SAP
        SOLANITE -> Items.SOLANITE_ORE
        TERRAVITE -> Items.TERRAVITE_ORE
        VITAE -> Items.VITAE_OOZE
        else -> null
    }

    fun isOre(r: Int) = r in FERRITE..XENITE
    fun isRare(r: Int) = r == SOLANITE || r == TERRAVITE
    fun infinite(r: Int) = r == SAP || r == VITAE
    fun sprite(r: Int) = when (r) {
        FERRITE -> "ore_ferrite_ore"; CUPRITE -> "ore_cuprite_ore"; LUMITE -> "ore_lumite"
        CARBON -> "ore_carbon"; AETHER -> "ore_aether_crystal"; XENITE -> "ore_xenite_ore"
        SOLANITE -> "ore_solanite_ore"; TERRAVITE -> "ore_terravite_ore"
        else -> ""
    }
}

object Ground {
    const val REGOLITH = 0
    const val MOSS = 1
    const val BASALT = 2
    const val DUNES = 3
    const val LAKE = 4
    val names = arrayOf("regolith", "moss", "basalt", "dunes")
}

object Flora {
    const val NONE = 0
    const val MUSHROOM = 1
    const val SPIRE = 2
    const val TENDRIL = 3
    const val TREE = 4
    const val ROCK = 5
    val sprites = arrayOf("", "flora_mushroom", "flora_spire", "flora_tendril", "flora_tree", "flora_rock")
}

/** Smooth value noise, seeded. */
class Noise(seed: Long) {
    private val perm = IntArray(512)

    init {
        val r = Random(seed)
        val p = (0 until 256).shuffled(r)
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun h(x: Int, y: Int) = perm[(perm[x and 255] + y) and 511] / 255f
    private fun fade(t: Float) = t * t * (3 - 2 * t)

    fun value(x: Float, y: Float): Float {
        val xi = floor(x).toInt();
        val yi = floor(y).toInt()
        val xf = fade(x - xi);
        val yf = fade(y - yi)
        val a = h(xi, yi);
        val b = h(xi + 1, yi)
        val c = h(xi, yi + 1);
        val d = h(xi + 1, yi + 1)
        return (a + (b - a) * xf) + ((c + (d - c) * xf) - (a + (b - a) * xf)) * yf
    }

    fun fbm(x: Float, y: Float, oct: Int = 4): Float {
        var sum = 0f;
        var amp = 1f;
        var f = 1f;
        var norm = 0f
        repeat(oct) { sum += value(x * f, y * f) * amp; norm += amp; amp *= .5f; f *= 2f }
        return sum / norm
    }
}

class World(val seed: Long, val w: Int = 512, val h: Int = 512) {
    val ground = ByteArray(w * h)
    val variant = ByteArray(w * h)
    val res = ByteArray(w * h)
    val amount = IntArray(w * h)
    val flora = ByteArray(w * h)
    val bld = arrayOfNulls<Building>(w * h)

    val cx get() = w / 2
    val cy get() = h / 2

    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun idx(x: Int, y: Int) = y * w + x
    fun at(x: Int, y: Int): Building? = if (inside(x, y)) bld[y * w + x] else null
    fun resAt(x: Int, y: Int) = if (inside(x, y)) res[idx(x, y)].toInt() else 0
    fun groundAt(x: Int, y: Int) = if (inside(x, y)) ground[idx(x, y)].toInt() else Ground.LAKE

    fun generate() {
        val n1 = Noise(seed)
        val n2 = Noise(seed * 31 + 7)
        val n3 = Noise(seed * 17 + 3)
        val rnd = Random(seed)
        for (y in 0 until h) for (x in 0 until w) {
            val i = idx(x, y)
            val a = n1.fbm(x / 28f, y / 28f)
            val b = n2.fbm(x / 40f, y / 40f)
            val g = when {
                a > .58f -> Ground.MOSS
                b > .6f -> Ground.DUNES
                a < .38f -> Ground.BASALT
                else -> Ground.REGOLITH
            }
            ground[i] = g.toByte()
            variant[i] = rnd.nextInt(4).toByte()
            val lake = n3.fbm(x / 22f, y / 22f, 3)
            val d = hypot((x - cx).toFloat(), (y - cy).toFloat())
            if (lake > .68f && d > 16) ground[i] = Ground.LAKE.toByte()
            // border lakes so the map has a natural edge
            val edge = minOf(x, y, w - 1 - x, h - 1 - y)
            if (edge < 3 + (n3.value(x / 6f, y / 6f) * 4).toInt()) ground[i] = Ground.LAKE.toByte()
        }

        // guaranteed starter patches around the landing site
        val starter = listOf(Res.FERRITE, Res.CUPRITE, Res.LUMITE, Res.CARBON)
        val baseAng = rnd.nextFloat() * 6.28f
        starter.forEachIndexed { k, r ->
            val ang = baseAng + k * 1.5708f + rnd.nextFloat() * .5f
            val dist = 11f + rnd.nextFloat() * 6f
            patch(r, cx + (cos(ang) * dist).toInt(), cy + (sin(ang) * dist).toInt(), 5.5f + rnd.nextFloat() * 1.5f, 1400, n1)
        }
        // sap pools and a vent within reach
        val sapAng = rnd.nextFloat() * 6.28f
        pool(Res.SAP, cx + (cos(sapAng) * 26).toInt(), cy + (sin(sapAng) * 26).toInt(), 2.6f, rnd)
        pool(Res.SAP, cx + (cos(sapAng + 2.6f) * 34).toInt(), cy + (sin(sapAng + 2.6f) * 34).toInt(), 2.2f, rnd)
        // aether + xenite a bit further out
        val aAng = rnd.nextFloat() * 6.28f
        patch(Res.AETHER, cx + (cos(aAng) * 38).toInt(), cy + (sin(aAng) * 38).toInt(), 5f, 1500, n2)
        patch(Res.XENITE, cx + (cos(aAng + 3f) * 46).toInt(), cy + (sin(aAng + 3f) * 46).toInt(), 5f, 1500, n2)

        // scattered patches across the map, richer with distance
        val area = (w * h) / 65536f
        repeat((70 * area).toInt()) {
            val x = 8 + rnd.nextInt(w - 16)
            val y = 8 + rnd.nextInt(h - 16)
            val d = hypot((x - cx).toFloat(), (y - cy).toFloat())
            if (d < 30) return@repeat
            val r = when (rnd.nextInt(100)) {
                in 0..21 -> Res.FERRITE
                in 22..41 -> Res.CUPRITE
                in 42..55 -> Res.LUMITE
                in 56..71 -> Res.CARBON
                in 72..83 -> Res.AETHER
                else -> Res.XENITE
            }
            patch(r, x, y, 4f + rnd.nextFloat() * 5f, (1500 + d * 40).toInt(), n1)
        }
        repeat((14 * area).toInt()) {
            val x = 10 + rnd.nextInt(w - 20);
            val y = 10 + rnd.nextInt(h - 20)
            if (hypot((x - cx).toFloat(), (y - cy).toFloat()) > 40) pool(Res.SAP, x, y, 2f + rnd.nextFloat(), rnd)
        }
        // geothermal vents (each a 2x2 crater)
        var vents = 0
        var tries = 0
        val maxR = minOf(w, h) / 2f - 12f
        while (vents < (18 * area).toInt() && tries < 3000) {
            tries++
            val ang = rnd.nextFloat() * 6.28f
            val dist = if (vents < 2) 30f + rnd.nextFloat() * 10f else 30f + rnd.nextFloat() * (maxR - 30f)
            val x = cx + (cos(ang) * dist).toInt();
            val y = cy + (sin(ang) * dist).toInt()
            if (!inside(x, y) || !inside(x + 1, y + 1)) continue
            if ((0..1).any { dx -> (0..1).any { dy -> ground[idx(x + dx, y + dy)].toInt() == Ground.LAKE || res[idx(x + dx, y + dy)].toInt() != 0 } }) continue
            res[idx(x, y)] = Res.VENT.toByte()
            vents++
        }

        // the three Xyra elements: rich but rare, far from the landing site
        val rareR = maxR.coerceAtLeast(60f)
        val rAng = rnd.nextFloat() * 6.28f
        val rare = listOf(Res.SOLANITE, Res.TERRAVITE, Res.VITAE)
        repeat(if (area >= 2f) 7 else 2) { k ->
            rare.forEachIndexed { j, r ->
                // first ring of each is guaranteed at a reachable distance in distinct directions
                val ang = if (k == 0) rAng + j * 2.094f else rnd.nextFloat() * 6.28f
                val dist = if (k == 0) rareR * .5f + rnd.nextFloat() * 10f else rareR * (.55f + rnd.nextFloat() * .45f)
                val x = cx + (cos(ang) * dist).toInt(); val y = cy + (sin(ang) * dist).toInt()
                if (r == Res.VITAE) {
                    clearLake(x, y, 5)
                    pool(Res.VITAE, x, y, 2.4f + rnd.nextFloat() * .6f, rnd)
                } else {
                    clearLake(x, y, 7)
                    patch(r, x, y, 3.5f + rnd.nextFloat() * 1.5f, 6000 + rnd.nextInt(3000), n3, overwrite = true)
                }
            }
        }

        // flora
        for (y in 0 until h) for (x in 0 until w) {
            val i = idx(x, y)
            if (ground[i].toInt() == Ground.LAKE || res[i].toInt() != 0) continue
            val d = hypot((x - cx).toFloat(), (y - cy).toFloat())
            if (d < 7) continue
            val dens = when (ground[i].toInt()) {
                Ground.MOSS -> .11f; Ground.REGOLITH -> .05f; Ground.BASALT -> .035f; else -> .025f
            }
            if (rnd.nextFloat() < dens) {
                flora[i] = when (rnd.nextInt(20)) {
                    in 0..5 -> Flora.MUSHROOM
                    in 6..9 -> Flora.SPIRE
                    in 10..15 -> Flora.TENDRIL
                    16 -> Flora.TREE
                    else -> Flora.ROCK
                }.toByte()
            }
        }
    }

    private fun clearLake(px: Int, py: Int, rad: Int) {
        for (y in py - rad..py + rad) for (x in px - rad..px + rad) {
            if (!inside(x, y) || minOf(x, y, w - 1 - x, h - 1 - y) < 8) continue
            if (hypot((x - px).toFloat(), (y - py).toFloat()) <= rad && ground[idx(x, y)].toInt() == Ground.LAKE) ground[idx(x, y)] = Ground.BASALT.toByte()
        }
    }

    private fun patch(r: Int, px: Int, py: Int, rad: Float, rich: Int, n: Noise, overwrite: Boolean = false) {
        val ri = rad.toInt() + 3
        for (y in py - ri..py + ri) for (x in px - ri..px + ri) {
            if (!inside(x, y)) continue
            val i = idx(x, y)
            if (ground[i].toInt() == Ground.LAKE) continue
            val d = hypot((x - px).toFloat(), (y - py).toFloat())
            val wobble = (n.value(x / 3f + r * 13, y / 3f) - .5f) * 3.2f
            if (d < rad + wobble) {
                if (res[i].toInt() != 0 && res[i].toInt() != r && !overwrite) continue
                if (overwrite && res[i].toInt() == Res.VENT) continue
                res[i] = r.toByte()
                amount[i] = (rich * (1.2f - d / (rad + 2f))).toInt().coerceAtLeast(150)
            }
        }
    }

    private fun pool(r: Int, px: Int, py: Int, rad: Float, rnd: Random) {
        val ri = rad.toInt() + 1
        for (y in py - ri..py + ri) for (x in px - ri..px + ri) {
            if (!inside(x, y)) continue
            val i = idx(x, y)
            if (ground[i].toInt() == Ground.LAKE) continue
            if (hypot((x - px).toFloat(), (y - py).toFloat()) < rad + rnd.nextFloat() * .6f) {
                res[i] = r.toByte(); amount[i] = 1
            }
        }
    }

    fun clearArea(x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0..y1) for (x in x0..x1) if (inside(x, y)) {
            flora[idx(x, y)] = 0
            if (abs(x - cx) < 3 && abs(y - cy) < 3) {
                if (ground[idx(x, y)].toInt() == Ground.LAKE) ground[idx(x, y)] = Ground.REGOLITH.toByte()
            }
        }
    }
}
