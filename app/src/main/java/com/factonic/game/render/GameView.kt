package com.factonic.game.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.graphics.DashPathEffect
import com.factonic.game.Settings
import com.factonic.game.data.BKind
import com.factonic.game.data.BType
import com.factonic.game.data.Cat
import com.factonic.game.data.Item
import com.factonic.game.data.Recipe
import com.factonic.game.data.Tunnels
import com.factonic.game.sim.Belt
import com.factonic.game.sim.Building
import com.factonic.game.sim.Crafter
import com.factonic.game.sim.DX
import com.factonic.game.sim.DY
import com.factonic.game.sim.Drill
import com.factonic.game.sim.Game
import com.factonic.game.sim.Generator
import com.factonic.game.sim.Inserter
import com.factonic.game.sim.Lab
import com.factonic.game.sim.Lamp
import com.factonic.game.sim.Splitter
import com.factonic.game.sim.Tool
import com.factonic.game.sim.Tunnel
import com.factonic.game.world.Flora
import com.factonic.game.world.Ground
import com.factonic.game.world.Res
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface GameViewListener {
    fun onSelect(b: Building?)
    fun onToast(msg: String)
    fun onToolCleared() {}
    fun onToolPicked(id: String) {}
}

@SuppressLint("ViewConstructor")
class GameView(ctx: Context, var game: Game) : View(ctx) {
    var listener: GameViewListener? = null
    /** The app is in the background. */
    var appPaused = false
    /** A menu that stops the clock is open. */
    var menuPaused = false
    val paused get() = appPaused || menuPaused
    var selected: Building? = null
    /** False while the HUD is hidden for screenshots: the objective pointer hides too. */
    var overlays = true

    // camera (tile units, centre of the screen)
    var camX = game.world.cx.toFloat() + .5f
    var camY = game.world.cy.toFloat() + .5f
    var zoom = 64f * resources.displayMetrics.density / 2.2f

    private val minZoom get() = 20f * resources.displayMetrics.density / 2.2f * 1.1f
    private val maxZoom get() = 170f * resources.displayMetrics.density / 2.2f

    private val STEP = 1f / 60f
    private var acc = 0f
    private var lastNanos = 0L
    private var t = 0f

    // paints
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val lightPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 150 }
    private val badPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 150; colorFilter = PorterDuffColorFilter(0xAAFF3355.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }
    private val rf = RectF()
    private val path = Path()
    private val filters = HashMap<Int, LightingColorFilter>()

    private var terrain = Terrain(game.world)
    private var terrainWorld = game.world
    private val density = resources.displayMetrics.density

    // touch
    private var downX = 0f;
    private var downY = 0f;
    private var lastX = 0f;
    private var lastY = 0f
    private var moved = false
    private var multi = false
    private var touching = false
    private var downTime = 0L
    private var holdMined = false
    private var holdTimer = 0f
    private var lastPlaceTile = -1
    var hoverX = Int.MIN_VALUE;
    var hoverY = 0
    private var lastTool: Tool = Tool.None
    /** The touch started on the placement ghost: dragging moves it (or lays a line of belts). */
    private var ghostDrag = false
    /** The touch started on a belt ghost's arrow handle: dragging stretches a line of belts. */
    private var lineDrag = false
    private var lineDir = 0
    private var lineLen = 0
    /** A long-press copied a building into the tool slot; swallow the release. */
    private var pipetted = false
    private var grabDX = 0f
    private var grabDY = 0f
    private val slop = 10f * resources.displayMetrics.density
    /** The drag started on a building with the Remove tool: everything the finger crosses is picked up. */
    private var removeDrag = false
    private var lastRemoveTile = -1

    // pipette clipboard: settings copied from the building that was long-pressed
    private var clipType: BType? = null
    private var clipRecipe: Recipe? = null
    private var clipFilter: Item? = null

    // camera flights
    private var flyFromX = 0f
    private var flyFromY = 0f
    private var flyToX = 0f
    private var flyToY = 0f
    private var flyT = 1f

    // objective pointer
    private var pointer: FloatArray? = null
    private var pointerRes = 0
    private var pointerClock = 0f
    private val pointerPos = FloatArray(2)
    private var pointerShown = false

    private class Pulse(val x: Float, val y: Float, val size: Float, var life: Float)
    private val pulses = ArrayList<Pulse>()
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(6f * resources.displayMetrics.density, 6f * resources.displayMetrics.density), 0f)
    }

    private val spores = Array(46) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 6.28f, .5f + Random.nextFloat()) }
    private class Puff(var x: Float, var y: Float, var life: Float, val dark: Boolean)
    private val puffs = ArrayList<Puff>()

    private val scaleDet = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val fx = d.focusX;
            val fy = d.focusY
            val wx = sx2w(fx);
            val wy = sy2w(fy)
            zoom = (zoom * d.scaleFactor).coerceIn(minZoom, maxZoom)
            camX = wx - (fx - width / 2f) / zoom
            camY = wy - (fy - height / 2f) / zoom
            clampCam()
            return true
        }
    })

    private fun sx2w(sx: Float) = camX + (sx - width / 2f) / zoom
    private fun sy2w(sy: Float) = camY + (sy - height / 2f) / zoom
    private fun wx2s(wx: Float) = (wx - camX) * zoom + width / 2f
    private fun wy2s(wy: Float) = (wy - camY) * zoom + height / 2f

    fun centerOn(x: Float, y: Float) {
        flyT = 1f; camX = x; camY = y; clampCam()
    }

    /** Glide the camera to a point instead of jumping. */
    fun flyTo(x: Float, y: Float) {
        flyFromX = camX; flyFromY = camY; flyToX = x; flyToY = y; flyT = 0f
    }

    private fun tickFlight(dt: Float) {
        if (flyT >= 1f) return
        flyT = min(1f, flyT + dt / .65f)
        val e = flyT * flyT * (3 - 2 * flyT)
        camX = flyFromX + (flyToX - flyFromX) * e
        camY = flyFromY + (flyToY - flyFromY) * e
        clampCam()
        if (game.tool is Tool.Place && !ghostDrag && !lineDrag) updateHover(width / 2f, height / 2f)
    }

    private fun buzz(kind: Int) {
        if (Settings.haptics) performHapticFeedback(kind)
    }

    private fun clampCam() {
        camX = camX.coerceIn(0f, game.world.w.toFloat())
        camY = camY.coerceIn(0f, game.world.h.toFloat())
    }

    private fun lf(color: Int) = filters.getOrPut(color) { LightingColorFilter(color, 0) }

    // ------------------------------------------------------------------------------------------- loop
    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        var dt = if (lastNanos == 0L) 0f else (now - lastNanos) / 1e9f
        lastNanos = now
        dt = min(dt, .1f)
        if (!paused) {
            t += dt
            acc += dt
            var steps = 0
            while (acc >= STEP && steps < 4) {
                game.tick(STEP); acc -= STEP; steps++
            }
            if (steps == 4) acc = 0f
            handleHold(dt)
            game.messages.forEach { listener?.onToast(it) }
            game.messages.clear()
            if (game.sfx.isNotEmpty()) {
                game.sfx.forEach { com.factonic.game.audio.Sfx.play(it) }
                game.sfx.clear()
            }
            if (selected != null && !game.buildings.contains(selected)) {
                selected = null; listener?.onSelect(null)
            }
        }
        syncGhost()
        tickFlight(dt)
        if ((ghostDrag || lineDrag || removeDrag) && touching && moved) edgeScroll(dt)
        updatePointer(dt)
        render(canvas, dt)
        postInvalidateOnAnimation()
    }

    /** When a building tool is picked, the ghost appears in the middle of the screen. */
    private fun syncGhost() {
        val tool = game.tool
        if (tool == lastTool) return
        val prev = lastTool
        lastTool = tool
        clipType = null; clipRecipe = null; clipFilter = null
        if (tool is Tool.Place) {
            if (prev is Tool.Place && hoverX != Int.MIN_VALUE) {
                // keep the same centre when swapping building types
                val c = hoverX + prev.type.size / 2f
                val d = hoverY + prev.type.size / 2f
                hoverX = floor(c - tool.type.size / 2f + .5f).toInt(); hoverY = floor(d - tool.type.size / 2f + .5f).toInt()
            } else updateHover(width / 2f, height / 2f)
        } else {
            hoverX = Int.MIN_VALUE; ghostDrag = false; lineDrag = false
        }
    }

    /** While dragging the ghost close to an edge, the map scrolls along. */
    private fun edgeScroll(dt: Float) {
        val m = 36f * density
        var vx = 0f; var vy = 0f
        if (lastX < m) vx = -1f else if (lastX > width - m) vx = 1f
        if (lastY < m) vy = -1f else if (lastY > height - m) vy = 1f
        if (vx == 0f && vy == 0f) return
        camX += vx * dt * 9f; camY += vy * dt * 9f; clampCam()
        when {
            lineDrag -> updateLine(lastX, lastY)
            ghostDrag -> moveGhost(lastX, lastY)
            removeDrag -> swipeRemove(lastX, lastY, lastX, lastY)
        }
    }

    private fun ghostRect(out: RectF): Boolean {
        val tool = game.tool as? Tool.Place ?: return false
        if (hoverX == Int.MIN_VALUE) return false
        val s = tool.type.size
        val pad = max(zoom * .3f, 14f * density)
        out.set(wx2s(hoverX.toFloat()) - pad, wy2s(hoverY.toFloat()) - pad, wx2s((hoverX + s).toFloat()) + pad, wy2s((hoverY + s).toFloat()) + pad)
        return true
    }

    private val hitRect = RectF()
    private fun onGhost(sx: Float, sy: Float) = ghostRect(hitRect) && hitRect.contains(sx, sy)

    private fun unequip() {
        game.tool = Tool.None
        syncGhost()
        listener?.onToolCleared()
    }

    private fun handleHold(dt: Float) {
        if (!touching || moved || multi || game.tool != Tool.None || pipetted) return
        if ((System.nanoTime() - downTime) / 1e6 < 350) return
        val tx = floor(sx2w(downX)).toInt();
        val ty = floor(sy2w(downY)).toInt()
        val held = game.world.at(tx, ty)
        if (held != null) {
            if ((System.nanoTime() - downTime) / 1e6 >= 480) pipette(held)
            return
        }
        if (game.world.at(tx, ty) != null || !(Res.isOre(game.world.resAt(tx, ty)) || Res.isRare(game.world.resAt(tx, ty)))) return
        holdTimer -= dt
        if (holdTimer <= 0f) {
            holdTimer = .3f; holdMined = true
            game.handMine(tx, ty)
        }
    }

    /** Long-press on a building: pick the same building (and direction) as the active tool. */
    private fun pipette(b: Building) {
        pipetted = true
        if (b.type.kind == BKind.CORE) return
        if (game.inventory[itemOf(b.type.id)] <= 0) {
            listener?.onToast("No ${b.type.name} in your inventory - craft more"); com.factonic.game.audio.Sfx.play("error"); return
        }
        game.tool = Tool.Place(b.type)
        if (b.type.rotatable) game.placeDir = b.dir
        lastTool = game.tool
        clipType = b.type
        clipRecipe = (b as? Crafter)?.takeIf { !it.isLauncher && !it.cat.auto }?.recipe
        clipFilter = (b as? Splitter)?.filter
        // the ghost waits next to the original: in front of a belt, otherwise to its right
        if (b.type.kind == BKind.BELT) { hoverX = b.x + DX[b.dir]; hoverY = b.y + DY[b.dir] }
        else { hoverX = b.x + b.size; hoverY = b.y }
        selected = null; listener?.onSelect(null)
        listener?.onToolPicked(b.type.id)
        val extra = clipRecipe?.name ?: clipFilter?.let { "filter: ${it.name}" }
        listener?.onToast("Copied ${b.type.name}" + (extra?.let { " ($it)" } ?: ""))
        buzz(android.view.HapticFeedbackConstants.LONG_PRESS)
        com.factonic.game.audio.Sfx.play("rotate")
    }

    /** Fly the camera back to the Lander Core. */
    /** Test hook: show a belt line preview of [len] tiles in direction [dir]. */
    fun previewLine(dir: Int, len: Int) { lineDrag = true; moved = true; lineDir = dir; lineLen = len; game.placeDir = dir }

    fun goHome() {
        val core = game.buildings.firstOrNull { it.type.kind == BKind.CORE }
        if (core != null) flyTo(core.x + core.size / 2f, core.y + core.size / 2f)
        else flyTo(game.world.cx + .5f, game.world.cy + .5f)
        zoom = 64f * density / 2.2f
    }

    // ------------------------------------------------------------------------------------------- belt line handle
    private fun beltTool(): BType? = (game.tool as? Tool.Place)?.type?.takeIf { it.kind == BKind.BELT }
    private fun handleRadius() = max(zoom * .3f, 17f * density)
    private val hpos = FloatArray(2)

    /** Screen position of the round arrow handle in front of a belt ghost. */
    private fun handlePos(out: FloatArray): Boolean {
        if (beltTool() == null || hoverX == Int.MIN_VALUE) return false
        if (lineDrag && moved) { out[0] = lastX; out[1] = lastY; return true }
        val d = game.placeDir
        val gap = max(.95f, .5f + handleRadius() / zoom + .12f)
        out[0] = wx2s(hoverX + .5f + DX[d] * gap); out[1] = wy2s(hoverY + .5f + DY[d] * gap)
        return true
    }

    private fun onHandle(sx: Float, sy: Float): Boolean {
        if (!handlePos(hpos)) return false
        val dx = sx - hpos[0]; val dy = sy - hpos[1]
        val r = handleRadius() * 1.35f
        return dx * dx + dy * dy <= r * r
    }

    /** Line direction = the dominant axis of the drag; length = tiles from the ghost to the finger. */
    private fun updateLine(sx: Float, sy: Float) {
        val wx = sx2w(sx) - (hoverX + .5f)
        val wy = sy2w(sy) - (hoverY + .5f)
        val dist: Float
        if (abs(wx) >= abs(wy)) { lineDir = if (wx > 0) 1 else 3; dist = abs(wx) } else { lineDir = if (wy > 0) 2 else 0; dist = abs(wy) }
        val n = max(1, floor(dist + .5f).toInt()).coerceAtMost(120)
        if (n != lineLen) { lineLen = n; com.factonic.game.audio.Sfx.play("rotate", .35f, 30) }
        game.placeDir = lineDir
    }

    private fun buildLine() {
        val tool = game.tool as? Tool.Place ?: return
        var placed = 0; var blocked = 0; var ranOut = false
        for (i in 0 until lineLen) {
            val x = hoverX + DX[lineDir] * i; val y = hoverY + DY[lineDir] * i
            val ex = game.world.at(x, y)
            if (ex != null) {
                if (ex.type.kind == BKind.BELT) game.setDir(ex, lineDir) else blocked++
                continue
            }
            if (game.inventory[itemOf(tool.type.id)] <= 0) { ranOut = true; break }
            if (game.canPlace(tool.type, x, y) != null) { blocked++; continue }
            if (game.place(tool.type, x, y, lineDir) != null) placed++
        }
        game.placeDir = lineDir
        hoverX += DX[lineDir] * lineLen; hoverY += DY[lineDir] * lineLen
        if (placed > 0) {
            com.factonic.game.audio.Sfx.play("place")
            buzz(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        when {
            ranOut -> { listener?.onToast("Out of ${tool.type.name} - placed $placed"); com.factonic.game.audio.Sfx.play("error") }
            blocked > 0 -> listener?.onToast("Placed $placed belts - skipped $blocked blocked tiles")
        }
        listener?.onToolCleared()
    }

    private fun drawLinePreview(c: Canvas) {
        val tool = game.tool as? Tool.Place ?: return
        var stock = game.inventory[itemOf(tool.type.id)]
        stroke.strokeWidth = max(2f, zoom * .07f)
        for (i in 0 until lineLen) {
            val x = hoverX + DX[lineDir] * i; val y = hoverY + DY[lineDir] * i
            val ex = game.world.at(x, y)
            val ok = if (ex != null) ex.type.kind == BKind.BELT else (stock > 0 && game.canPlace(tool.type, x, y) == null)
            if (ex == null && ok) stock--
            if (ex == null) drawSprite(c, tool.type.id, x.toFloat(), y.toFloat(), 1f, 0f, if (ok) ghostPaint else badPaint)
            // chevron
            val cx = x + .5f; val cy = y + .5f
            val lx = -DY[lineDir]; val ly = DX[lineDir]
            stroke.color = if (ok) 0xFF3FF2E0.toInt() else 0xFFFF4A5A.toInt()
            path.reset()
            path.moveTo(wx2s(cx - DX[lineDir] * .12f + lx * .2f), wy2s(cy - DY[lineDir] * .12f + ly * .2f))
            path.lineTo(wx2s(cx + DX[lineDir] * .14f), wy2s(cy + DY[lineDir] * .14f))
            path.lineTo(wx2s(cx - DX[lineDir] * .12f - lx * .2f), wy2s(cy - DY[lineDir] * .12f - ly * .2f))
            c.drawPath(path, stroke)
        }
        // outline of the whole run
        val x0 = min(hoverX, hoverX + DX[lineDir] * (lineLen - 1)).toFloat(); val y0 = min(hoverY, hoverY + DY[lineDir] * (lineLen - 1)).toFloat()
        val x1 = max(hoverX, hoverX + DX[lineDir] * (lineLen - 1)) + 1f; val y1 = max(hoverY, hoverY + DY[lineDir] * (lineLen - 1)) + 1f
        rf.set(wx2s(x0), wy2s(y0), wx2s(x1), wy2s(y1))
        stroke.color = 0xCC3FF2E0.toInt(); stroke.strokeWidth = 2.5f * density
        c.drawRoundRect(rf, zoom * .15f, zoom * .15f, stroke)
    }

    private fun drawHandle(c: Canvas) {
        if (!handlePos(hpos)) return
        val r = handleRadius()
        val d = if (lineDrag && moved) lineDir else game.placeDir
        val pulse = (sin(t * 4f) + 1f) * .5f
        fill.color = 0x553FF2E0
        c.drawCircle(hpos[0], hpos[1], r * (1.15f + .12f * pulse), fill)
        fill.color = 0xF0141026.toInt()
        c.drawCircle(hpos[0], hpos[1], r, fill)
        stroke.color = 0xFF3FF2E0.toInt(); stroke.strokeWidth = 2.5f * density
        c.drawCircle(hpos[0], hpos[1], r, stroke)
        // arrow
        val lx = -DY[d].toFloat(); val ly = DX[d].toFloat()
        val ax = DX[d].toFloat(); val ay = DY[d].toFloat()
        path.reset()
        path.moveTo(hpos[0] + ax * r * .55f, hpos[1] + ay * r * .55f)
        path.lineTo(hpos[0] - ax * r * .35f + lx * r * .45f, hpos[1] - ay * r * .35f + ly * r * .45f)
        path.lineTo(hpos[0] - ax * r * .1f, hpos[1] - ay * r * .1f)
        path.lineTo(hpos[0] - ax * r * .35f - lx * r * .45f, hpos[1] - ay * r * .35f - ly * r * .45f)
        path.close()
        fill.color = 0xFF3FF2E0.toInt()
        c.drawPath(path, fill)
        if (lineDrag && moved && lineLen > 0) {
            textPaint.textSize = 13f * density
            val label = "×$lineLen"
            fill.color = 0xE6141026.toInt()
            val tw = textPaint.measureText(label)
            rf.set(hpos[0] - tw / 2 - 6 * density, hpos[1] - r - 26 * density, hpos[0] + tw / 2 + 6 * density, hpos[1] - r - 6 * density)
            c.drawRoundRect(rf, 6 * density, 6 * density, fill)
            c.drawText(label, hpos[0], hpos[1] - r - 11 * density, textPaint)
        }
    }

    // ------------------------------------------------------------------------------------------- render
    private fun render(c: Canvas, dt: Float) {
        val w = game.world
        val z = zoom
        val x0 = max(0, floor(sx2w(0f)).toInt() - 1)
        val y0 = max(0, floor(sy2w(0f)).toInt() - 1)
        val x1 = min(w.w - 1, floor(sx2w(width.toFloat())).toInt() + 1)
        val y1 = min(w.h - 1, floor(sy2w(height.toFloat())).toInt() + 1)
        c.drawColor(0xFF0B0A18.toInt())

        // smooth terrain chunks
        if (terrainWorld !== w) { terrain = Terrain(w); terrainWorld = w }
        val lod = if (z > 40f) 0 else 1
        val ch = Terrain.CHUNK
        for (cy in max(0, y0 / ch)..min(w.h / ch - 1, y1 / ch)) for (cx in max(0, x0 / ch)..min(w.w / ch - 1, x1 / ch)) {
            val sx = wx2s((cx * ch).toFloat())
            val sy = wy2s((cy * ch).toFloat())
            val bmp = terrain.chunk(cx, cy, lod)
            if (bmp != null) {
                rf.set(sx, sy, sx + ch * z + 1f, sy + ch * z + 1f)
                c.drawBitmap(bmp, null, rf, bmpPaint)
            } else {
                // placeholder while the chunk is painted in the background
                for (ty in cy * ch until cy * ch + ch) for (tx in cx * ch until cx * ch + ch) {
                    fill.color = PLACEHOLDER[w.ground[w.idx(tx, ty)].toInt()]
                    val px = wx2s(tx.toFloat()); val py = wy2s(ty.toFloat())
                    c.drawRect(px, py, px + z + 1f, py + z + 1f, fill)
                }
            }
        }

        // resources + lake shimmer
        for (ty in y0..y1) for (tx in x0..x1) {
            val i = w.idx(tx, ty)
            val sx = wx2s(tx.toFloat());
            val sy = wy2s(ty.toFloat())
            val g = w.ground[i].toInt()
            if (g == Ground.LAKE) {
                val h = (tx * 31 + ty * 17) and 0x7fffffff
                if (h % 9 == 0) {
                    val ph = t * .8f + (h % 97) * .37f
                    val a = (sin(ph) + 1f) * .5f
                    lightPaint.colorFilter = lf(0xFF6AFFE8.toInt()); lightPaint.alpha = (a * a * 90).toInt()
                    val r = z * (.25f + .15f * a)
                    val bx = sx + z * (.3f + .4f * ((h shr 3) % 7) / 7f); val by = sy + z * (.5f + .12f * sin(ph * .7f))
                    rf.set(bx - r, by - r * .5f, bx + r, by + r * .5f); c.drawBitmap(Sprites.light, null, rf, lightPaint)
                } else if (h % 13 == 0) {
                    fill.color = 0x553FF2E0
                    c.drawCircle(sx + z * .5f, sy + z * (.5f + .1f * sin(t + tx)), z * .06f, fill)
                }
                continue
            }
            val r = w.res[i].toInt()
            if (r != 0) {
                when {
                    Res.isOre(r) || Res.isRare(r) -> {
                        val v = ((tx * 7 + ty * 13) and 0x7fffffff) % 3
                        Sprites["${Res.sprite(r)}_$v"]?.let { bmp ->
                            val k = if (w.amount[i] < 100) .75f else 1.12f
                            rf.set(sx + z * (1 - k) / 2, sy + z * (1 - k) / 2, sx + z * (1 + k) / 2, sy + z * (1 + k) / 2)
                            c.drawBitmap(bmp, null, rf, bmpPaint)
                        }
                    }

                    r == Res.SAP -> Sprites["sap_pool_${(tx + ty) % 2}"]?.let { rf.set(sx - z * .05f, sy - z * .05f, sx + z * 1.05f, sy + z * 1.05f); c.drawBitmap(it, null, rf, bmpPaint) }
                    r == Res.VITAE -> Sprites["vitae_pool_${(tx + ty) % 2}"]?.let {
                        val k = .04f * sin(t * 2.2f + tx * 1.3f + ty)
                        rf.set(sx - z * (.08f + k), sy - z * (.08f + k), sx + z * (1.08f + k), sy + z * (1.08f + k)); c.drawBitmap(it, null, rf, bmpPaint)
                    }
                    r == Res.VENT -> Sprites["vent"]?.let { rf.set(sx, sy, sx + z * 2, sy + z * 2); c.drawBitmap(it, null, rf, bmpPaint) }
                }
            }
        }
        // flora
        for (ty in y0..y1) for (tx in x0..x1) {
            val i = w.idx(tx, ty)
            val f = w.flora[i].toInt()
            if (f == 0 || w.bld[i] != null) continue
            val bmp = Sprites[Flora.sprites[f]] ?: continue
            val sx = wx2s(tx.toFloat());
            val sy = wy2s(ty.toFloat())
            val sway = sin(t * 1.2f + tx * .9f + ty) * z * .02f
            if (f == Flora.TREE) rf.set(sx - z * .5f + sway, sy - z * 1.1f, sx + z * 1.5f + sway, sy + z * .9f)
            else rf.set(sx + sway, sy, sx + z + sway, sy + z)
            c.drawBitmap(bmp, null, rf, bmpPaint)
        }

        // grid while building
        if (game.tool != Tool.None && z > 26f) {
            stroke.color = 0x22FFFFFF; stroke.strokeWidth = 1f
            for (tx in x0..x1 + 1) {
                val sx = wx2s(tx.toFloat()); c.drawLine(sx, 0f, sx, height.toFloat(), stroke)
            }
            for (ty in y0..y1 + 1) {
                val sy = wy2s(ty.toFloat()); c.drawLine(0f, sy, width.toFloat(), sy, stroke)
            }
        }

        // buildings: belts first, then items, then structures, then inserters
        val bx0 = max(0, x0 - 2);
        val by0 = max(0, y0 - 2)
        for (pass in 0..3) {
            for (ty in by0..y1) for (tx in bx0..x1) {
                val b = w.bld[w.idx(tx, ty)] ?: continue
                if (b.x != tx || b.y != ty) continue
                when (pass) {
                    0 -> if (b is Belt) drawBelt(c, b)
                    1 -> if (b is Belt) drawBeltItems(c, b)
                    2 -> if (b is Tunnel) drawTunnel(c, b) else if (b !is Belt && b !is Inserter) drawStructure(c, b)
                    3 -> if (b is Inserter) drawInserter(c, b)
                }
            }
        }
        drawTunnelLinks(c, bx0, by0, x1, y1)

        // placement pulses
        val pu = pulses.iterator()
        while (pu.hasNext()) {
            val p = pu.next()
            p.life -= dt
            if (p.life <= 0f) {
                pu.remove(); continue
            }
            val k = 1f - p.life / .45f
            stroke.color = Color.argb((p.life / .45f * 200).toInt(), 63, 242, 224)
            stroke.strokeWidth = (3f - 2f * k) * density
            val r = p.size * zoom * (.5f + .35f * k)
            c.drawCircle(wx2s(p.x), wy2s(p.y), r, stroke)
        }

        // smoke puffs
        val pit = puffs.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt; p.y -= dt * .5f; p.x += dt * .15f
            if (p.life <= 0) {
                pit.remove(); continue
            }
            fill.color = if (p.dark) Color.argb((p.life * 60).toInt(), 40, 36, 50) else Color.argb((p.life * 50).toInt(), 230, 230, 240)
            c.drawCircle(wx2s(p.x), wy2s(p.y), z * (.25f + (2f - p.life) * .2f), fill)
        }

        // selection
        selected?.let { b -> drawSelection(c, b.x.toFloat(), b.y.toFloat(), b.size.toFloat(), 0xFF3FF2E0.toInt()) }

        drawGhost(c)
        drawNight(c, x0, y0, x1, y1)
        drawPointer(c)

        // floating text
        textPaint.textSize = max(12f, z * .28f)
        for (f in game.floats) {
            textPaint.alpha = (min(1f, f.life) * 255).toInt()
            c.drawText(f.text, wx2s(f.x), wy2s(f.y), textPaint)
        }
        textPaint.alpha = 255
    }

    /** Pulsing outline + corner brackets so the selected thing is unmistakable. */
    private fun drawSelection(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        val pulse = (sin(t * 5f) + 1f) * .5f
        val pad = zoom * (.06f + .05f * pulse)
        rf.set(wx2s(x) - pad, wy2s(y) - pad, wx2s(x + s) + pad, wy2s(y + s) + pad)
        fill.color = (color and 0x00FFFFFF) or ((28 + 30 * pulse).toInt() shl 24)
        c.drawRoundRect(rf, zoom * .18f, zoom * .18f, fill)
        stroke.color = (color and 0x00FFFFFF) or ((150 + 105 * pulse).toInt() shl 24)
        stroke.strokeWidth = (2.5f + 1.5f * pulse) * density
        c.drawRoundRect(rf, zoom * .18f, zoom * .18f, stroke)
        // corner brackets
        val L = min(rf.width(), rf.height()) * .28f
        val o = 4f * density + 3f * density * pulse
        stroke.color = color; stroke.strokeWidth = 3.5f * density
        val l = rf.left - o; val tp = rf.top - o; val r = rf.right + o; val bt = rf.bottom + o
        c.drawLine(l, tp, l + L, tp, stroke); c.drawLine(l, tp, l, tp + L, stroke)
        c.drawLine(r, tp, r - L, tp, stroke); c.drawLine(r, tp, r, tp + L, stroke)
        c.drawLine(l, bt, l + L, bt, stroke); c.drawLine(l, bt, l, bt - L, stroke)
        c.drawLine(r, bt, r - L, bt, stroke); c.drawLine(r, bt, r, bt - L, stroke)
    }

    private fun beltColor(id: String) = when (id) {
        "fast_belt" -> 0xFF3FF2E0.toInt(); "express_belt" -> 0xFFFF5AD8.toInt(); "tunnel_belt" -> TUNNEL_COLOR; else -> 0xFFFFB84A.toInt()
    }

    private fun drawBelt(c: Canvas, b: Belt) = drawBeltAt(c, b.x, b.y, b.dir, b.curve, b.type.speed, beltColor(b.type.id))

    private fun drawBeltAt(c: Canvas, x: Int, y: Int, dir: Int, curve: Int, speed: Float, col: Int) {
        val z = zoom
        c.save()
        c.translate(wx2s(x + .5f), wy2s(y + .5f))
        c.rotate(dir * 90f)
        c.scale(z, z)
        val off = (t * speed) % .5f
        stroke.strokeWidth = .085f
        if (curve == 0) {
            fill.color = 0xFF232536.toInt(); c.drawRect(-.42f, -.5f, .42f, .5f, fill)
            fill.color = 0xFF2E3146.toInt(); c.drawRect(-.34f, -.5f, .34f, .5f, fill)
            fill.color = darken(col, .35f)
            c.drawRect(-.48f, -.5f, -.36f, .5f, fill); c.drawRect(.36f, -.5f, .48f, .5f, fill)
            fill.color = col
            c.drawRect(-.48f, -.5f, -.42f, .5f, fill); c.drawRect(.42f, -.5f, .48f, .5f, fill)
            if (zoom > 18) {
                stroke.color = (col and 0x00FFFFFF) or 0x99000000.toInt()
                var k = -1
                while (k < 2) {
                    val y = .5f - (k * .5f + off) - .25f
                    if (y > -.55f && y < .55f) {
                        path.reset(); path.moveTo(-.18f, y + .1f); path.lineTo(0f, y - .07f); path.lineTo(.18f, y + .1f)
                        c.drawPath(path, stroke)
                    }
                    k++
                }
            }
        } else {
            if (curve == 1) c.scale(-1f, 1f)
            // quarter annulus around corner (-.5,-.5)
            fun ring(r0: Float, r1: Float, color: Int) {
                fill.color = color
                path.reset()
                rf.set(-.5f - r1, -.5f - r1, -.5f + r1, -.5f + r1); path.arcTo(rf, 0f, 90f, true)
                rf.set(-.5f - r0, -.5f - r0, -.5f + r0, -.5f + r0); path.arcTo(rf, 90f, -90f, false)
                path.close(); c.drawPath(path, fill)
            }
            ring(.08f, .92f, 0xFF232536.toInt())
            ring(.16f, .84f, 0xFF2E3146.toInt())
            ring(.02f, .14f, darken(col, .35f)); ring(.86f, .98f, darken(col, .35f))
            ring(.02f, .08f, col); ring(.92f, .98f, col)
            if (zoom > 18) {
                stroke.color = (col and 0x00FFFFFF) or 0x99000000.toInt()
                var k = 0
                while (k < 2) {
                    val p = (k * .5f + off + .25f) % 1f
                    val a = Math.toRadians(90.0 * (1 - p)).toFloat()
                    c.save()
                    c.translate(-.5f + cos(a) * .5f, -.5f + sin(a) * .5f)
                    c.rotate(Math.toDegrees(a.toDouble()).toFloat())
                    path.reset(); path.moveTo(-.18f, .1f); path.lineTo(0f, -.07f); path.lineTo(.18f, .1f)
                    c.drawPath(path, stroke)
                    c.restore()
                    k++
                }
            }
        }
        c.restore()
    }

    /** The hood of a tunnel end, drawn over its belt and the items on it. */
    private fun drawTunnel(c: Canvas, b: Tunnel) {
        drawTunnelHood(c, b.x, b.y, b.dir, b.entry, b.partner != null)
        if (game.alerts.contains(b) && (t * 2).toInt() % 2 == 0) badge(c, b, 0xFFFFB84A.toInt(), "!")
    }

    private fun drawTunnelHood(c: Canvas, x: Int, y: Int, dir: Int, entry: Boolean, paired: Boolean) {
        c.save()
        c.translate(wx2s(x + .5f), wy2s(y + .5f))
        c.rotate(dir * 90f)
        c.scale(zoom, zoom)
        // local frame: forward is -y. An entrance hoods its front half, an exit (mirrored) its back half.
        c.save()
        if (!entry) c.scale(1f, -1f)
        // soft shadow spread evenly, so it looks the same whichever way the tunnel faces
        fill.color = 0x55000000
        rf.set(-.53f, -.53f, .53f, .16f); c.drawRoundRect(rf, .16f, .16f, fill)
        fill.color = 0xFF3A3F5A.toInt()
        rf.set(-.48f, -.5f, .48f, .1f); c.drawRoundRect(rf, .12f, .12f, fill)
        fill.color = 0xFF555D7E.toInt()
        rf.set(-.38f, -.44f, .38f, -.06f); c.drawRoundRect(rf, .08f, .08f, fill)
        fill.color = TUNNEL_COLOR
        c.drawRect(-.48f, -.42f, -.4f, .1f, fill); c.drawRect(.4f, -.42f, .48f, .1f, fill)
        // the mouth
        fill.color = if (paired) 0xFF0B0914.toInt() else 0xFF3A2410.toInt()
        rf.set(-.32f, -.04f, .32f, .14f); c.drawRoundRect(rf, .08f, .08f, fill)
        stroke.color = if (paired) darken(TUNNEL_COLOR, .2f) else 0xFFFFB84A.toInt(); stroke.strokeWidth = .04f
        c.drawRoundRect(rf, .08f, .08f, stroke)
        c.restore()
        // a chevron on the roof always points the way items flow
        val cy = if (entry) -.24f else .26f
        stroke.color = TUNNEL_COLOR; stroke.strokeWidth = .07f
        path.reset(); path.moveTo(-.16f, cy + .07f); path.lineTo(0f, cy - .08f); path.lineTo(.16f, cy + .07f)
        c.drawPath(path, stroke)
        c.restore()
    }

    private fun drawTunnelLinks(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int) {
        val all = (game.tool as? Tool.Place)?.type?.kind == BKind.TUNNEL
        val sel = selected as? Tunnel
        if (!all && sel == null) return
        dash.strokeWidth = max(2f, zoom * .06f)
        dash.color = 0xCC3FF2E0.toInt()
        fun link(a: Tunnel) {
            val p = a.partner ?: return
            c.drawLine(wx2s(a.x + .5f), wy2s(a.y + .5f), wx2s(p.x + .5f), wy2s(p.y + .5f), dash)
        }
        if (sel != null) link(sel)
        if (all) for (b in game.buildings) {
            if (b is Tunnel && b.entry && b.x in x0 - 8..x1 + 8 && b.y in y0 - 8..y1 + 8) link(b)
        }
    }

    private fun drawSplitter(c: Canvas, b: Splitter) {
        drawSplitterAt(c, b.x, b.y, b.dir, b.filter, if (b.working) t * 4f else 0f, b.lastSide, b.flash)
    }

    private fun drawSplitterAt(c: Canvas, x: Int, y: Int, dir: Int, filter: Item?, spin: Float, lastSide: Int, flash: Float) {
        c.save()
        c.translate(wx2s(x + .5f), wy2s(y + .5f))
        c.scale(zoom, zoom)
        // a short belt stub on every side
        for (sd in 0 until 4) {
            c.save(); c.rotate(sd * 90f)
            fill.color = 0xFF232536.toInt(); c.drawRect(-.3f, -.5f, .3f, -.3f, fill)
            fill.color = 0xFFFFB84A.toInt(); c.drawRect(-.3f, -.5f, -.25f, -.3f, fill); c.drawRect(.25f, -.5f, .3f, -.3f, fill)
            if (sd == lastSide && flash < .3f) {
                fill.color = Color.argb(((1f - flash / .3f) * 220).toInt(), 63, 242, 224)
                c.drawRect(-.24f, -.5f, .24f, -.34f, fill)
            }
            c.restore()
        }
        fill.color = 0x66000000
        rf.set(-.38f, -.36f, .44f, .46f); c.drawRoundRect(rf, .14f, .14f, fill)
        fill.color = 0xFF3A3F5A.toInt()
        rf.set(-.4f, -.4f, .4f, .4f); c.drawRoundRect(rf, .14f, .14f, fill)
        stroke.color = 0xFFFFB84A.toInt(); stroke.strokeWidth = .045f
        c.drawRoundRect(rf, .14f, .14f, stroke)
        fill.color = 0xFF1A1C28.toInt(); c.drawCircle(0f, 0f, .25f, fill)
        if (filter == null) {
            stroke.color = 0xFFFFB84A.toInt(); stroke.strokeWidth = .06f
            for (k in 0 until 3) {
                val a = spin + k * 2.094f
                c.drawLine(0f, 0f, cos(a) * .19f, sin(a) * .19f, stroke)
            }
            fill.color = 0xFFC7CEDA.toInt(); c.drawCircle(0f, 0f, .06f, fill)
        }
        // the front (where filtered items go)
        c.rotate(dir * 90f)
        fill.color = if (filter != null) 0xFFFF5AD8.toInt() else 0xFF3FF2E0.toInt()
        path.reset(); path.moveTo(0f, -.48f); path.lineTo(-.13f, -.33f); path.lineTo(.13f, -.33f); path.close()
        c.drawPath(path, fill)
        c.restore()
        if (filter != null) drawCentered(c, Sprites[filter.id], x + .5f, y + .5f, .44f)
    }

    private val tmp = FloatArray(2)
    private fun beltPos(b: Belt, pos: Float, out: FloatArray) {
        var lx: Float;
        var ly: Float
        if (b.curve == 0) {
            lx = 0f; ly = .5f - pos
        } else {
            val a = Math.toRadians(90.0 * (1 - pos)).toFloat()
            lx = -.5f + cos(a) * .5f; ly = -.5f + sin(a) * .5f
            if (b.curve == 1) lx = -lx
        }
        val (rx, ry) = when (b.dir) {
            0 -> lx to ly; 1 -> -ly to lx; 2 -> -lx to -ly; else -> ly to -lx
        }
        out[0] = b.x + .5f + rx; out[1] = b.y + .5f + ry
    }

    private fun drawBeltItems(c: Canvas, b: Belt) {
        val s = zoom * .26f
        for (it in b.items) {
            beltPos(b, it.pos, tmp)
            val bmp = Sprites[it.item.id] ?: continue
            val sx = wx2s(tmp[0]);
            val sy = wy2s(tmp[1])
            rf.set(sx - s, sy - s, sx + s, sy + s)
            c.drawBitmap(bmp, null, rf, bmpPaint)
        }
    }

    private fun drawSprite(c: Canvas, name: String, x: Float, y: Float, size: Float, rot: Float = 0f, paint: Paint = bmpPaint) {
        val bmp = Sprites[name] ?: return
        val sx = wx2s(x);
        val sy = wy2s(y)
        val hs = size * zoom / 2
        if (rot != 0f) {
            c.save(); c.rotate(rot, sx + hs, sy + hs)
        }
        rf.set(sx, sy, sx + hs * 2, sy + hs * 2)
        c.drawBitmap(bmp, null, rf, paint)
        if (rot != 0f) c.restore()
    }

    private fun drawCentered(c: Canvas, bmp: Bitmap?, wx: Float, wy: Float, size: Float, rot: Float = 0f, paint: Paint = bmpPaint) {
        bmp ?: return
        val sx = wx2s(wx);
        val sy = wy2s(wy)
        val hs = size * zoom / 2
        c.save(); c.translate(sx, sy); if (rot != 0f) c.rotate(rot)
        rf.set(-hs, -hs, hs, hs)
        c.drawBitmap(bmp, null, rf, paint)
        c.restore()
    }

    private fun drawStructure(c: Canvas, b: Building) {
        val s = b.size.toFloat()
        val cx = b.x + s / 2;
        val cy = b.y + s / 2
        when (b) {
            is Drill -> {
                drawSprite(c, b.type.sprite, b.x.toFloat(), b.y.toFloat(), s, b.dir * 90f)
                when (b.type.kind) {
                    BKind.DRILL -> drawCentered(c, Sprites["drill_head"], cx, cy, s * .58f, b.anim * 57f)
                    BKind.RARE_DRILL -> {
                        drawCentered(c, Sprites["resonance_head"] ?: Sprites["drill_head"], cx, cy, s * .62f, b.anim * 90f)
                        if (b.working) glowAt(c, cx, cy, .8f + .3f * sin(t * 14), 0xFFFFC040.toInt(), .55f)
                    }
                    BKind.BIO_PUMP -> if (b.working) glowAt(c, cx, cy, .9f + .2f * sin(t * 3), 0xFFFF5FA8.toInt(), .6f)
                    else -> if (b.working) glowAt(c, cx, cy, .9f + .15f * sin(t * 4), 0xFFFFB030.toInt(), .5f)
                }
            }

            is Crafter -> {
                drawSprite(c, b.type.sprite, b.x.toFloat(), b.y.toFloat(), s)
                when (b.type.id) {
                    "assembler", "assembler2" -> {
                        b.recipe?.let { drawCentered(c, Sprites[it.main.id], cx, cy + .05f, .8f) }
                        drawCentered(c, Sprites["assembler_arm"], cx, cy, 1.1f, if (b.working) sin(b.anim * 5f) * 70f else 0f)
                    }

                    "smelter", "arc_furnace", "chem_plant" -> {
                        if (b.working) {
                            val col = when (b.type.id) {
                                "smelter" -> 0xFFFF9A2A.toInt(); "arc_furnace" -> 0xFF9FE8FF.toInt(); else -> 0xFF7DFF9A.toInt()
                            }
                            glowAt(c, cx, cy, .9f + .12f * sin(t * 9 + b.x), col, .55f)
                            if (b.type.id == "smelter" && Random.nextFloat() < .03f && puffs.size < 160) puffs += Puff(b.x + .4f, b.y + .3f, 2f, true)
                        }
                        b.recipe?.let { if (zoom > 30) drawCentered(c, Sprites[it.main.id], b.x + s - .35f, b.y + s - .35f, .5f) }
                    }

                    "launcher" -> {
                        drawRocket(c, b, cx, cy)
                        if (b.cargoMode && !b.crafting && zoom > 22) {
                            val f = (b.cargo.total() / game.cargoCapacity.toFloat()).coerceIn(0f, 1f)
                            fill.color = 0xCC14101F.toInt(); rf.set(wx2s(b.x + .3f), wy2s(b.y + s - .32f), wx2s(b.x + s - .3f), wy2s(b.y + s - .16f)); c.drawRoundRect(rf, 4f, 4f, fill)
                            fill.color = 0xFFFFC46B.toInt(); rf.right = rf.left + (rf.width()) * f; c.drawRoundRect(rf, 4f, 4f, fill)
                        }
                    }

                    "ingot_forge", "liquefier", "pulverizer", "curing_kiln", "bio_centrifuge", "gene_sequencer", "vitae_synth", "cargo_packer" -> drawExotic(c, b, cx, cy, s)
                }
                if (b.recipe == null && !b.cat.auto) badge(c, b, 0xFFFFCC4D.toInt(), "?")
            }

            is Lab -> {
                drawSprite(c, "lab", b.x.toFloat(), b.y.toFloat(), s)
                if (b.working) {
                    glowAt(c, cx, cy, 1.1f + .2f * sin(t * 3 + b.x), 0xFF3FF2E0.toInt(), .6f)
                    fill.color = 0xCCFFFFFF.toInt()
                    for (k in 0 until 3) {
                        val a = b.anim * 3f + k * 2.09f
                        c.drawCircle(wx2s(cx + cos(a) * .55f), wy2s(cy + sin(a) * .55f), zoom * .05f, fill)
                    }
                }
            }

            is Generator -> {
                drawSprite(c, b.type.sprite, b.x.toFloat(), b.y.toFloat(), s)
                when (b.type.kind) {
                    BKind.GEOTHERMAL -> drawCentered(c, Sprites["fan"], cx, cy, 1.25f, b.anim * 200f)
                    BKind.COMBUSTION -> if (b.output > 1f) {
                        glowAt(c, b.x + 1.5f, cy + .1f, .6f + .1f * sin(t * 11), 0xFFFF7A2A.toInt(), .6f)
                        if (Random.nextFloat() < .05f && puffs.size < 160) puffs += Puff(b.x + .4f + Random.nextFloat() * .4f, b.y + .2f, 2f, true)
                    }

                    BKind.CORE -> glowAt(c, cx + .05f, cy + .05f, .9f + .1f * sin(t * 2), 0xFF7FFFF2.toInt(), .5f)
                    BKind.ACCUMULATOR -> {
                        val f = b.energy / 5000f
                        fill.color = 0xFF26C26A.toInt()
                        rf.set(wx2s(b.x + .12f), wy2s(b.y + .86f), wx2s(b.x + .12f + .76f * f), wy2s(b.y + .94f))
                        c.drawRect(rf, fill)
                    }

                    else -> {}
                }
            }

            is Lamp -> drawSprite(c, "lamp", b.x.toFloat(), b.y.toFloat(), 1f)
            is Splitter -> drawSplitter(c, b)
            else -> drawSprite(c, b.type.sprite, b.x.toFloat(), b.y.toFloat(), s)
        }
        if (b.demand > 0 && game.powerSat < .5f && (t * 2).toInt() % 2 == 0) badge(c, b, 0xFFFF4A5A.toInt(), "⚡")
        else if (game.alerts.contains(b) && !(b is Crafter && b.recipe == null) && (t * 2).toInt() % 2 == 0) badge(c, b, 0xFFFFB84A.toInt(), "!")
    }

    private fun drawExotic(c: Canvas, b: Crafter, cx: Float, cy: Float, s: Float) {
        val on = b.working
        when (b.type.id) {
            "ingot_forge" -> if (on) {
                glowAt(c, cx, cy + .1f, 1.1f + .15f * sin(t * 10 + b.x), 0xFFFFD27A.toInt(), .7f)
                if (Random.nextFloat() < .04f && puffs.size < 160) puffs += Puff(b.x + .6f, b.y + .4f, 2f, true)
            }
            "liquefier" -> if (on) glowAt(c, cx, cy, 1f + .2f * sin(t * 3 + b.y), 0xFFFFB238.toInt(), .6f)
            "pulverizer" -> {
                drawCentered(c, Sprites["fan"], cx - .55f, cy, .9f, if (on) b.anim * 300f else 0f)
                drawCentered(c, Sprites["fan"], cx + .55f, cy, .9f, if (on) -b.anim * 300f else 0f)
                if (on && Random.nextFloat() < .05f && puffs.size < 160) puffs += Puff(cx + (Random.nextFloat() - .5f), cy, 1.5f, false)
            }
            "curing_kiln" -> if (on) glowAt(c, cx, cy, 1.2f + .15f * sin(t * 5), 0xFFB57BFF.toInt(), .6f)
            "bio_centrifuge" -> {
                drawCentered(c, Sprites["fan"], cx, cy, 1.5f, if (on) b.anim * 720f else 0f)
                if (on) glowAt(c, cx, cy, 1f, 0xFFFF5FA8.toInt(), .45f)
            }
            "gene_sequencer" -> if (on) {
                glowAt(c, cx, cy, 1.1f + .2f * sin(t * 3), 0xFFFF7FC8.toInt(), .5f)
                for (k in 0 until 6) {
                    val a = b.anim * 2.5f + k * 1.047f
                    fill.color = if (k % 2 == 0) 0xEE7DFFE0.toInt() else 0xEEFF7FC8.toInt()
                    c.drawCircle(wx2s(cx + cos(a) * .35f), wy2s(cy + (k - 2.5f) * .16f), zoom * .05f, fill)
                }
            }
            "vitae_synth" -> {
                val p = .5f + .5f * sin(t * 2.4f)
                glowAt(c, cx, cy, (if (on) 1.5f else .8f) + .3f * p, 0xFFFF5FA8.toInt(), if (on) .8f else .3f)
                if (on) {
                    glowAt(c, cx, cy, .6f, 0xFFFFFFFF.toInt(), .5f * p)
                    fill.color = 0xDDFFE0F0.toInt()
                    for (k in 0 until 4) {
                        val a = b.anim * 1.8f + k * 1.571f
                        c.drawCircle(wx2s(cx + cos(a) * 1f), wy2s(cy + sin(a) * 1f), zoom * .06f, fill)
                    }
                }
            }
            "cargo_packer" -> drawCentered(c, Sprites["assembler_arm"], cx, cy - .1f, 1.2f, if (on) sin(b.anim * 4f) * 80f else 0f)
        }
        b.recipe?.let { if (zoom > 26) drawCentered(c, Sprites[it.main.id], b.x + s - .42f, b.y + s - .42f, .62f) }
    }

    private fun drawRocket(c: Canvas, b: Crafter, cx: Float, cy: Float) {
        if (!b.crafting) return
        val p = b.progress
        val bmp = Sprites["rocket"] ?: return
        if (p < .85f) {
            ghostPaint.alpha = (min(1f, p / .3f) * 255).toInt()
            drawCentered(c, bmp, cx, cy, 1.6f, 0f, ghostPaint)
            ghostPaint.alpha = 150
        } else {
            val k = (p - .85f) / .15f
            val rise = k * k * 14f
            glowAt(c, cx, cy + .8f - rise, 1.6f, 0xFFFFA040.toInt(), .9f)
            drawCentered(c, bmp, cx, cy - rise, 1.6f)
            if (puffs.size < 200) puffs += Puff(cx + (Random.nextFloat() - .5f), cy + .8f - rise, 2f, false)
        }
    }

    private fun badge(c: Canvas, b: Building, color: Int, s: String) {
        val bx = wx2s(b.x + b.size - .25f);
        val by = wy2s(b.y + .25f)
        fill.color = 0xDD14101F.toInt(); c.drawCircle(bx, by, zoom * .2f, fill)
        textPaint.color = color; textPaint.textSize = zoom * .26f
        c.drawText(s, bx, by + zoom * .09f, textPaint)
        textPaint.color = Color.WHITE
    }

    private fun drawInserter(c: Canvas, b: Inserter) {
        drawSprite(c, "inserter_base", b.x.toFloat(), b.y.toFloat(), 1f)
        val cx = b.x + .5f;
        val cy = b.y + .5f
        // swing from source (behind) to target (front) through the "left" side
        val srcAng = Math.atan2(-DY[b.dir].toDouble(), -DX[b.dir].toDouble()).toFloat()
        val ang = srcAng + b.arm * Math.PI.toFloat()
        val reach = .62f
        val hx = cx + cos(ang) * reach;
        val hy = cy + sin(ang) * reach
        val ex = cx + cos(ang - .5f) * reach * .55f;
        val ey = cy + sin(ang - .5f) * reach * .55f
        val col = if (b.type.id == "fast_inserter") 0xFF4AA8FF.toInt() else 0xFFFFCC4D.toInt()
        stroke.color = 0xFF14101F.toInt(); stroke.strokeWidth = zoom * .16f
        path.reset(); path.moveTo(wx2s(cx), wy2s(cy)); path.lineTo(wx2s(ex), wy2s(ey)); path.lineTo(wx2s(hx), wy2s(hy))
        c.drawPath(path, stroke)
        stroke.color = col; stroke.strokeWidth = zoom * .1f
        c.drawPath(path, stroke)
        fill.color = 0xFFC7CEDA.toInt()
        c.drawCircle(wx2s(cx), wy2s(cy), zoom * .09f, fill)
        c.drawCircle(wx2s(ex), wy2s(ey), zoom * .06f, fill)
        b.held?.let { drawCentered(c, Sprites[it.id], hx, hy, .4f) }
        // direction pip
        if (zoom > 30) {
            fill.color = 0xAA3FF2E0.toInt()
            c.drawCircle(wx2s(cx + DX[b.dir] * .38f), wy2s(cy + DY[b.dir] * .38f), zoom * .05f, fill)
        }
    }

    private fun drawGhost(c: Canvas) {
        val tool = game.tool as? Tool.Place ?: return
        if (hoverX == Int.MIN_VALUE) return
        if (lineDrag && moved) { drawLinePreview(c); drawHandle(c); return }
        drawGhostBody(c, tool)
        drawHandle(c)
    }

    private fun drawGhostBody(c: Canvas, tool: Tool.Place) {
        val t2 = tool.type
        val ok = game.canPlace(t2, hoverX, hoverY) == null
        if (t2.kind == BKind.TUNNEL || t2.kind == BKind.SPLITTER) {
            drawLogisticsGhost(c, t2, ok); return
        }
        val p = if (ok) ghostPaint else badPaint
        val s = t2.size.toFloat()
        val rot = if (t2.rotatable && t2.kind != BKind.BELT && t2.kind != BKind.INSERTER) game.placeDir * 90f else 0f
        val name = if (t2.kind == BKind.BELT) t2.id else if (t2.kind == BKind.INSERTER) "inserter_base" else t2.sprite
        drawSprite(c, name, hoverX.toFloat(), hoverY.toFloat(), s, rot, p)
        if (t2.rotatable) {
            // arrow showing direction
            val cx = hoverX + s / 2;
            val cy = hoverY + s / 2
            val d = game.placeDir
            stroke.color = if (ok) 0xFF3FF2E0.toInt() else 0xFFFF4A5A.toInt(); stroke.strokeWidth = zoom * .1f
            val tipX = cx + DX[d] * (s / 2 + .1f);
            val tipY = cy + DY[d] * (s / 2 + .1f)
            c.drawLine(wx2s(cx), wy2s(cy), wx2s(tipX), wy2s(tipY), stroke)
            val lx = -DY[d];
            val ly = DX[d]
            path.reset()
            path.moveTo(wx2s(tipX - DX[d] * .25f + lx * .2f), wy2s(tipY - DY[d] * .25f + ly * .2f))
            path.lineTo(wx2s(tipX), wy2s(tipY))
            path.lineTo(wx2s(tipX - DX[d] * .25f - lx * .2f), wy2s(tipY - DY[d] * .25f - ly * .2f))
            c.drawPath(path, stroke)
        }
        drawSelection(c, hoverX.toFloat(), hoverY.toFloat(), s, if (ok) 0xFF3FF2E0.toInt() else 0xFFFF4A5A.toInt())
    }

    /** Tunnel and splitter ghosts are drawn like the real thing, see-through, plus where a tunnel would connect. */
    private fun drawLogisticsGhost(c: Canvas, t2: BType, ok: Boolean) {
        val d = game.placeDir
        val hx = hoverX
        val hy = hoverY
        rf.set(wx2s(hx - .3f), wy2s(hy - .3f), wx2s(hx + 1.3f), wy2s(hy + 1.3f))
        val layer = c.saveLayerAlpha(rf, if (ok) 175 else 120)
        if (t2.kind == BKind.TUNNEL) {
            val link = game.tunnelPartnerFor(hx, hy, d)
            drawBeltAt(c, hx, hy, d, 0, t2.speed, TUNNEL_COLOR)
            drawTunnelHood(c, hx, hy, d, link?.second != true, link != null)
        } else drawSplitterAt(c, hx, hy, d, null, t * 2f, -1, 1f)
        c.restoreToCount(layer)
        if (!ok) {
            fill.color = 0x55FF3355; rf.set(wx2s(hx.toFloat()), wy2s(hy.toFloat()), wx2s(hx + 1f), wy2s(hy + 1f)); c.drawRoundRect(rf, zoom * .15f, zoom * .15f, fill)
        }
        if (t2.kind == BKind.TUNNEL) {
            val link = game.tunnelPartnerFor(hx, hy, d)
            dash.strokeWidth = max(2f, zoom * .06f)
            if (link != null) {
                dash.color = 0xEE3FF2E0.toInt()
                c.drawLine(wx2s(hx + .5f), wy2s(hy + .5f), wx2s(link.first.x + .5f), wy2s(link.first.y + .5f), dash)
            } else {
                // show how far the exit may go
                for (k in 1..Tunnels.SPAN + 1) {
                    fill.color = if (k == Tunnels.SPAN + 1) 0xCC3FF2E0.toInt() else 0x883FF2E0.toInt()
                    c.drawCircle(wx2s(hx + .5f + DX[d] * k), wy2s(hy + .5f + DY[d] * k), zoom * (if (k == Tunnels.SPAN + 1) .09f else .05f), fill)
                }
            }
        }
        drawSelection(c, hx.toFloat(), hy.toFloat(), 1f, if (ok) 0xFF3FF2E0.toInt() else 0xFFFF4A5A.toInt())
    }

    // ------------------------------------------------------------------------------------------- objective pointer
    private fun updatePointer(dt: Float) {
        pointerClock -= dt
        if (pointerClock > 0f) return
        pointerClock = .5f
        val m = game.mission
        val target = m?.target
        if (!overlays || !Settings.objectivePointer || target == null || target.isEmpty()) {
            pointer = null; return
        }
        val p = game.nearestDeposit(target, camX, camY)
        pointer = p
        if (p != null) {
            val r = game.world.resAt(floor(p[0]).toInt(), floor(p[1]).toInt())
            pointerRes = if (r != 0 && target.contains(r)) r else target[0]
        }
    }

    private fun drawPointer(c: Canvas) {
        pointerShown = false
        val p = pointer ?: return
        val d = density
        val sx = wx2s(p[0])
        val sy = wy2s(p[1])
        val left = 46 * d
        val top = 92 * d
        val right = width - 46 * d
        val bottom = height - 118 * d
        if (right <= left || bottom <= top) return
        if (sx in left..right && sy in top..bottom) {
            // on screen: a bobbing marker above the deposit
            val bob = sin(t * 4f) * 4 * d
            val r = 9 * d
            val tipY = sy - 4 * d + bob
            path.reset(); path.moveTo(sx, tipY); path.lineTo(sx - r, tipY - r * 1.4f); path.lineTo(sx + r, tipY - r * 1.4f); path.close()
            fill.color = 0xEE3FF2E0.toInt(); c.drawPath(path, fill)
            stroke.color = 0xFF14101F.toInt(); stroke.strokeWidth = 1.5f * d; c.drawPath(path, stroke)
            return
        }
        // off screen: a badge on the edge of the safe area, pointing at the deposit
        val cx = width / 2f
        val cy = height / 2f
        val dx = sx - cx
        val dy = sy - cy
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val kx = if (dx > 0) (right - cx) / dx else if (dx < 0) (left - cx) / dx else Float.MAX_VALUE
        val ky = if (dy > 0) (bottom - cy) / dy else if (dy < 0) (top - cy) / dy else Float.MAX_VALUE
        val k = min(kx, ky)
        val px = cx + dx * k
        val py = cy + dy * k
        pointerPos[0] = px; pointerPos[1] = py; pointerShown = true
        val r = 22 * d
        val ux = dx / len
        val uy = dy / len
        val pulse = (sin(t * 3f) + 1f) * .5f
        path.reset()
        path.moveTo(px + ux * (r + 12 * d), py + uy * (r + 12 * d))
        path.lineTo(px + ux * (r - 2 * d) - uy * 9 * d, py + uy * (r - 2 * d) + ux * 9 * d)
        path.lineTo(px + ux * (r - 2 * d) + uy * 9 * d, py + uy * (r - 2 * d) - ux * 9 * d)
        path.close()
        fill.color = 0xFF3FF2E0.toInt(); c.drawPath(path, fill)
        fill.color = 0x553FF2E0; c.drawCircle(px, py, r * (1.12f + .12f * pulse), fill)
        fill.color = 0xF0141026.toInt(); c.drawCircle(px, py, r, fill)
        stroke.color = 0xFF3FF2E0.toInt(); stroke.strokeWidth = 2.5f * d; c.drawCircle(px, py, r, stroke)
        Res.item(pointerRes)?.let { item ->
            Sprites[item.id]?.let { bmp -> rf.set(px - r * .7f, py - r * .7f, px + r * .7f, py + r * .7f); c.drawBitmap(bmp, null, rf, bmpPaint) }
        }
        val dist = sqrt((p[0] - camX) * (p[0] - camX) + (p[1] - camY) * (p[1] - camY)).toInt()
        val label = "$dist m"
        textPaint.textSize = 11f * d
        textPaint.color = Color.WHITE
        val tw = textPaint.measureText(label)
        val ly = if (py > cy) py - r - 9 * d else py + r + 16 * d
        fill.color = 0xE6141026.toInt()
        rf.set(px - tw / 2 - 5 * d, ly - 12 * d, px + tw / 2 + 5 * d, ly + 4 * d)
        c.drawRoundRect(rf, 5 * d, 5 * d, fill)
        c.drawText(label, px, ly, textPaint)
    }

    private fun glowAt(c: Canvas, wx: Float, wy: Float, radius: Float, color: Int, strength: Float) {
        lightPaint.colorFilter = lf(color)
        lightPaint.alpha = (strength * 255).toInt().coerceIn(0, 255)
        val sx = wx2s(wx);
        val sy = wy2s(wy);
        val r = radius * zoom
        rf.set(sx - r, sy - r, sx + r, sy + r)
        c.drawBitmap(Sprites.light, null, rf, lightPaint)
    }

    private fun drawNight(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int) {
        val dark = 1f - game.daylight
        // spores drift always, brighter at night
        val sporeA = (.25f + dark * .6f)
        for (s in spores) {
            s[0] = (s[0] + .004f * s[3] * .016f * 60f / 60f + sin(t * .3f + s[2]) * .0004f) % 1f
            s[1] = (s[1] - .0006f * s[3] + 1f) % 1f
            val px = s[0] * width;
            val py = s[1] * height
            lightPaint.colorFilter = lf(if (s[2] > 3f) 0xFF7DFFE0.toInt() else 0xFFE07DFF.toInt())
            lightPaint.alpha = (sporeA * 120 * (.6f + .4f * sin(t * 2 + s[2]))).toInt().coerceIn(0, 255)
            val r = 7f * s[3] * resources.displayMetrics.density
            rf.set(px - r, py - r, px + r, py + r)
            c.drawBitmap(Sprites.light, null, rf, lightPaint)
        }
        if (dark < .02f) return
        fill.color = Color.argb((dark * 170).toInt(), 6, 4, 26)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val w = game.world
        val k = dark
        for (ty in y0..y1) for (tx in x0..x1) {
            val i = w.idx(tx, ty)
            val r = w.res[i].toInt()
            when (r) {
                Res.SAP -> if ((tx + ty) % 2 == 0) glowAt(c, tx + .5f, ty + .5f, 1.3f, 0xFFFFA030.toInt(), .45f * k)
                Res.VENT -> glowAt(c, tx + 1f, ty + 1f, 1.8f, 0xFFFF6A1A.toInt(), .6f * k)
                Res.XENITE -> if ((tx * 3 + ty) % 3 == 0) glowAt(c, tx + .5f, ty + .5f, 1.2f, 0xFF7DFF6A.toInt(), .35f * k)
                Res.AETHER -> if ((tx * 3 + ty) % 3 == 0) glowAt(c, tx + .5f, ty + .5f, 1.2f, 0xFFB57BFF.toInt(), .35f * k)
                Res.SOLANITE -> if ((tx + ty * 2) % 2 == 0) glowAt(c, tx + .5f, ty + .5f, 1.4f, 0xFFFFB238.toInt(), .5f * k)
                Res.TERRAVITE -> if ((tx * 3 + ty) % 3 == 0) glowAt(c, tx + .5f, ty + .5f, 1.1f, 0xFFCFFFE0.toInt(), .28f * k)
                Res.VITAE -> if ((tx + ty) % 2 == 0) glowAt(c, tx + .5f, ty + .5f, 1.5f, 0xFFFF5FA8.toInt(), (.45f + .1f * sin(t * 2 + tx)) * k)
            }
            if (w.bld[i] == null) when (w.flora[i].toInt()) {
                Flora.MUSHROOM -> glowAt(c, tx + .5f, ty + .5f, .9f, 0xFFFF5AD8.toInt(), .5f * k)
                Flora.SPIRE -> glowAt(c, tx + .5f, ty + .5f, 1f, 0xFF3FF2E0.toInt(), .5f * k)
                Flora.TENDRIL -> glowAt(c, tx + .5f, ty + .3f, .8f, 0xFF7DFFE0.toInt(), .45f * k)
                Flora.TREE -> glowAt(c, tx + .5f, ty - .1f, 1.8f, 0xFFB45AFF.toInt(), .5f * k)
            }
            val b = w.bld[i] ?: continue
            if (b.x != tx || b.y != ty) continue
            val cx = b.centerX;
            val cy = b.centerY
            when (b) {
                is Lamp -> if (b.lit > .01f) glowAt(c, cx, cy, 6f, 0xFFD8FFF8.toInt(), .75f * b.lit * k)
                is Generator -> if (b.type.kind == BKind.CORE) glowAt(c, cx, cy, 5f, 0xFF7FFFF2.toInt(), .55f * k)
                else if (b.output > 1f) glowAt(c, cx, cy, 2f, 0xFFFF8A3A.toInt(), .4f * k)
                is Lab -> glowAt(c, cx, cy, if (b.working) 3f else 1.6f, 0xFF3FF2E0.toInt(), .5f * k)
                is Crafter -> if (b.working) glowAt(c, cx, cy, 2.4f, when (b.cat) {
                    Cat.SMELT, Cat.FORGE -> 0xFFFF9A2A.toInt(); Cat.CENTRIFUGE, Cat.SEQUENCE, Cat.SYNTH -> 0xFFFF5FA8.toInt()
                    Cat.CURE -> 0xFFB57BFF.toInt(); Cat.LIQUEFY -> 0xFFFFB238.toInt(); else -> 0xFFFFE0B0.toInt()
                }, .45f * k)
                else glowAt(c, cx, cy, 1.2f, 0xFFFFE0B0.toInt(), .2f * k)

                is Drill -> glowAt(c, cx, cy, 1.4f, 0xFF3FF2E0.toInt(), (if (b.working) .4f else .15f) * k)
                else -> {}
            }
        }
    }

    // ------------------------------------------------------------------------------------------- input
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDet.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                moved = false; multi = false; touching = true; holdMined = false; holdTimer = 0f
                downTime = System.nanoTime(); lastPlaceTile = -1; pipetted = false
                flyT = 1f
                syncGhost()
                removeDrag = game.tool == Tool.Remove && game.world.at(floor(sx2w(e.x)).toInt(), floor(sy2w(e.y)).toInt()) != null
                lastRemoveTile = -1
                lineDrag = onHandle(e.x, e.y)
                if (lineDrag) lineLen = 1
                ghostDrag = !lineDrag && onGhost(e.x, e.y)
                if (ghostDrag) {
                    grabDX = sx2w(e.x) - hoverX; grabDY = sy2w(e.y) - hoverY
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true; ghostDrag = false; lineDrag = false; removeDrag = false
                lastX = centroidX(e); lastY = centroidY(e)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // continue panning with remaining fingers without a jump
                val idx = e.actionIndex
                var sx = 0f;
                var sy = 0f;
                var n = 0
                for (k in 0 until e.pointerCount) if (k != idx) {
                    sx += e.getX(k); sy += e.getY(k); n++
                }
                if (n > 0) {
                    lastX = sx / n; lastY = sy / n
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2 || multi) {
                    val cx = centroidX(e);
                    val cy = centroidY(e)
                    camX -= (cx - lastX) / zoom; camY -= (cy - lastY) / zoom
                    lastX = cx; lastY = cy; clampCam()
                    moved = true
                    return true
                }
                if (!moved && (abs(e.x - downX) > slop || abs(e.y - downY) > slop)) moved = true
                if (moved) {
                    if (lineDrag) updateLine(e.x, e.y)
                    else if (ghostDrag) moveGhost(e.x, e.y)
                    else if (removeDrag) swipeRemove(lastX, lastY, e.x, e.y)
                    else {
                        // any other drag pans the map; the selected tool stays equipped
                        camX -= (e.x - lastX) / zoom; camY -= (e.y - lastY) / zoom; clampCam()
                    }
                }
                lastX = e.x; lastY = e.y
            }

            MotionEvent.ACTION_UP -> {
                touching = false
                if (lineDrag && !multi) {
                    if (moved) buildLine()
                    else { game.placeDir = (game.placeDir + 1) % 4; com.factonic.game.audio.Sfx.play("rotate") }
                } else if (!multi && !moved && !pipetted) tap(e.x, e.y)
                ghostDrag = false; lineDrag = false; pipetted = false; removeDrag = false
            }

            MotionEvent.ACTION_CANCEL -> { touching = false; ghostDrag = false; lineDrag = false; removeDrag = false }
        }
        return true
    }

    private fun moveGhost(sx: Float, sy: Float) {
        if (game.tool !is Tool.Place) return
        hoverX = floor(sx2w(sx) - grabDX + .5f).toInt()
        hoverY = floor(sy2w(sy) - grabDY + .5f).toInt()
    }

    private fun centroidX(e: MotionEvent): Float {
        var s = 0f; for (k in 0 until e.pointerCount) s += e.getX(k); return s / e.pointerCount
    }

    private fun centroidY(e: MotionEvent): Float {
        var s = 0f; for (k in 0 until e.pointerCount) s += e.getY(k); return s / e.pointerCount
    }

    private fun updateHover(sx: Float, sy: Float) {
        val tool = game.tool as? Tool.Place
        val size = tool?.type?.size ?: 1
        hoverX = floor(sx2w(sx) - size / 2f + .5f).toInt()
        hoverY = floor(sy2w(sy) - size / 2f + .5f).toInt()
    }

    /** Remove-tool swipe: pick up every building along the finger's path. */
    private fun swipeRemove(ax: Float, ay: Float, bx: Float, by: Float) {
        val steps = max(1, (sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay)) / (zoom * .4f)).toInt())
        for (k in 1..steps) {
            val sx = ax + (bx - ax) * k / steps
            val sy = ay + (by - ay) * k / steps
            val tx = floor(sx2w(sx)).toInt()
            val ty = floor(sy2w(sy)).toInt()
            if (!game.world.inside(tx, ty)) continue
            val tile = game.world.idx(tx, ty)
            if (tile == lastRemoveTile) continue
            lastRemoveTile = tile
            val b = game.world.at(tx, ty) ?: continue
            if (b.type.kind != BKind.CORE) removeAt(sx, sy)
        }
    }

    private fun tap(sx: Float, sy: Float) {
        val tx = floor(sx2w(sx)).toInt();
        val ty = floor(sy2w(sy)).toInt()
        if (pointerShown) {
            val p = pointer
            val dx = sx - pointerPos[0]
            val dy = sy - pointerPos[1]
            val r = 32 * density
            if (p != null && dx * dx + dy * dy <= r * r) {
                flyTo(p[0], p[1]); com.factonic.game.audio.Sfx.play("open"); return
            }
        }
        when (val tool = game.tool) {
            Tool.None -> {
                val b = game.world.at(tx, ty)
                val r = game.world.resAt(tx, ty)
                if (b != null) {
                    selected = b; listener?.onSelect(b)
                } else if (Res.isOre(r) || Res.isRare(r)) {
                    if (!holdMined) game.handMine(tx, ty)
                } else {
                    selected = null; listener?.onSelect(null)
                }
            }

            is Tool.Place -> {
                if (onGhost(sx, sy)) {
                    val existing = game.world.at(hoverX, hoverY)
                    if (existing != null && existing.type == tool.type && existing.x == hoverX && existing.y == hoverY) {
                        // tapping the ghost again rotates what you just built
                        if (tool.type.rotatable) { game.rotate(existing); game.placeDir = existing.dir; com.factonic.game.audio.Sfx.play("rotate") }
                    } else {
                        val b = placeAtHover()
                        if (b is Tunnel) {
                            // after an entrance the ghost jumps ahead to where an exit would cross one belt
                            val step = if (b.entry && b.partner == null) 2 else 1
                            hoverX += DX[b.dir] * step; hoverY += DY[b.dir] * step
                        } else if (b != null && tool.type.kind == BKind.BELT) {
                            // belts: the ghost steps forward so repeated taps lay a line
                            hoverX += DX[game.placeDir]; hoverY += DY[game.placeDir]
                        }
                    }
                } else {
                    // tapping anywhere else puts the tool away (and selects a building if there is one)
                    unequip()
                    val b = game.world.at(tx, ty)
                    selected = b; listener?.onSelect(b)
                }
            }

            Tool.Remove -> {
                if (game.world.at(tx, ty) != null) removeAt(sx, sy) else unequip()
            }
        }
    }

    private fun placeAtHover(): Building? {
        val tool = game.tool as? Tool.Place ?: return null
        val err = game.canPlace(tool.type, hoverX, hoverY)
        if (err != null) {
            listener?.onToast(err); com.factonic.game.audio.Sfx.play("error"); return null
        }
        if (game.inventory[itemOf(tool.type.id)] <= 0) {
            listener?.onToast("No ${tool.type.name} left - craft more"); com.factonic.game.audio.Sfx.play("error"); return null
        }
        val b = game.place(tool.type, hoverX, hoverY, game.placeDir) ?: return null
        applyClip(b)
        pulses += Pulse(b.x + b.size / 2f, b.y + b.size / 2f, b.size.toFloat(), .45f)
        com.factonic.game.audio.Sfx.play("place")
        buzz(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        listener?.onToolCleared()
        return b
    }

    /** A building placed from a pipette copy gets the original's recipe or filter. */
    private fun applyClip(b: Building) {
        if (b.type != clipType) return
        val r = clipRecipe
        if (b is Crafter && r != null && game.unlocked.contains(r.id)) b.setRecipe(game, r)
        if (b is Splitter) b.filter = clipFilter
    }

    /** Called by the UI to drop the ghost at the screen centre (e.g. after re-selecting the same tool). */
    fun recenterGhost() {
        if (game.tool is Tool.Place) updateHover(width / 2f, height / 2f)
    }

    private fun itemOf(id: String): Item = com.factonic.game.data.Items[id]

    private fun placeOne(tool: Tool.Place, x: Int, y: Int) {
        val existing = game.world.at(x, y)
        if (existing != null) {
            if (existing.type.kind == BKind.BELT && tool.type.kind == BKind.BELT) game.setDir(existing, game.placeDir)
            return
        }
        if (game.inventory[itemOf(tool.type.id)] <= 0) {
            listener?.onToast("No ${tool.type.name} left"); return
        }
        game.place(tool.type, x, y, game.placeDir)
    }

    private fun removeAt(sx: Float, sy: Float) {
        val tx = floor(sx2w(sx)).toInt();
        val ty = floor(sy2w(sy)).toInt()
        val b = game.world.at(tx, ty) ?: return
        if (game.remove(b)) {
            game.floats += com.factonic.game.sim.FloatText("+1 ${b.type.name}", tx + .5f, ty + .3f)
            com.factonic.game.audio.Sfx.play("remove")
            listener?.onToolCleared()
            if (selected === b) {
                selected = null; listener?.onSelect(null)
            }
        }
    }

    companion object {
        private val TUNNEL_COLOR = 0xFF9B87FF.toInt()
        private val PLACEHOLDER = intArrayOf(0xFF4A4458.toInt(), 0xFF2F5A55.toInt(), 0xFF2C2D44.toInt(), 0xFF6A4E6E.toInt(), 0xFF0E5A5C.toInt())
        fun lerpColor(a: Int, b: Int, t: Float): Int {
            val ar = (a shr 16) and 255;
            val ag = (a shr 8) and 255;
            val ab = a and 255
            val br = (b shr 16) and 255;
            val bg = (b shr 8) and 255;
            val bb = b and 255
            return Color.argb(255, (ar + (br - ar) * t).toInt(), (ag + (bg - ag) * t).toInt(), (ab + (bb - ab) * t).toInt())
        }

        fun darken(c: Int, k: Float): Int = Color.argb(
            Color.alpha(c), (Color.red(c) * (1 - k)).toInt(), (Color.green(c) * (1 - k)).toInt(), (Color.blue(c) * (1 - k)).toInt()
        )
    }
}

@Suppress("unused")
private fun sqrtF(v: Float) = sqrt(v)
