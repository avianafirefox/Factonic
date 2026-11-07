package com.factonic.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.factonic.game.data.*
import com.factonic.game.render.GameView
import com.factonic.game.render.Terrain
import com.factonic.game.render.Sprites
import com.factonic.game.sim.*
import com.factonic.game.ui.*
import com.factonic.game.world.Res
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h380dp-land-xhdpi", sdk = [34])
class ShotTest {
    private val out = File(System.getProperty("user.dir"), "../shots").apply { mkdirs() }

    private fun demo(): Game {
        val g = Game.newGame(1234L)
        val w = g.world
        Recipes.all.forEach { g.unlocked += it.id }
        listOf("ferrite_plate" to 200, "gear" to 50, "circuit" to 50, "steel" to 50, "drill" to 10, "assembler" to 4, "lab" to 4, "solar" to 4, "lamp" to 4, "belt" to 100)
            .forEach { (id, n) -> g.inventory.add(Items[id], n) }
        var spot: Pair<Int, Int>? = null
        loop@ for (r in 2..60) for (y in w.cy - r..w.cy + r) for (x in w.cx - r..w.cx + r) {
            if ((0..1).all { dy -> (0..1).all { dx -> w.resAt(x + dx, y + dy) == Res.FERRITE } } &&
                g.canPlace(Buildings["drill"], x, y) == null &&
                (2..9).all { dx -> (-3..1).all { dy -> w.at(x + dx, y + dy) == null && g.canPlace(Buildings["chest"], x + dx, y + dy) == null } }
            ) { spot = x to y; break@loop }
        }
        val (x, y) = spot!!
        g.place(Buildings["drill"], x, y, 1)
        g.place(Buildings["belt"], x + 2, y, 1); g.place(Buildings["belt"], x + 3, y, 1)
        g.place(Buildings["inserter"], x + 4, y, 1)
        g.place(Buildings["smelter"], x + 5, y, 0)
        g.place(Buildings["inserter"], x + 7, y, 1)
        g.place(Buildings["chest"], x + 8, y, 0)
        g.place(Buildings["lab"], x + 5, y - 3, 0)
        g.place(Buildings["solar"], x + 2, y - 3, 0)
        g.place(Buildings["lamp"], x + 8, y - 2, 0)
        g.startResearch(Techs["automation"])
        repeat(60 * 40) { g.tick(1f / 60f) }
        g.time = Game.DAY_LENGTH * 0.15f
        return g
    }

    private fun snap(a: ComponentActivity, name: String) {
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
        val v: View = a.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** An endgame outpost on a Solanite deposit with the exotic machines and a cargo rocket lifting off. */
    private fun exotic(): Game {
        val g = Game.newGame(1234L)
        val w = g.world
        Recipes.all.forEach { g.unlocked += it.id }
        Techs.all.forEach { g.researched += it.id }
        listOf("resonance_drill" to 4, "ingot_forge" to 2, "liquefier" to 2, "curing_kiln" to 2, "pulverizer" to 1, "cargo_packer" to 2, "launcher" to 1,
            "geothermal" to 2, "belt" to 60, "inserter" to 20, "vitae_synth" to 1, "gene_sequencer" to 1, "bio_centrifuge" to 1)
            .forEach { (id, n) -> g.inventory.add(Items[id], n) }
        var spot: Pair<Int, Int>? = null
        loop@ for (y in 20 until w.h - 20) for (x in 20 until w.w - 20) {
            if ((0..1).all { dy -> (0..1).all { dx -> w.resAt(x + dx, y + dy) == Res.SOLANITE } } && g.canPlace(Buildings["resonance_drill"], x, y) == null) { spot = x to y; break@loop }
        }
        val (x, y) = spot!!
        g.place(Buildings["resonance_drill"], x, y, 1)
        listOf(x - 2 to y + 2, x + 2 to y - 2).forEach { (a, b) -> if (g.canPlace(Buildings["resonance_drill"], a, b) == null) g.place(Buildings["resonance_drill"], a, b, 2) }
        for (k in 0..5) g.place(Buildings["belt"], x + 2 + k, y, 1)
        fun near(id: String, cx: Int, cy: Int): Building? {
            val t = Buildings[id]
            for (r in 0..14) for (yy in cy - r..cy + r) for (xx in cx - r..cx + r) if (g.canPlace(t, xx, yy) == null) return g.place(t, xx, yy, 0)
            return null
        }
        val forge = near("ingot_forge", x + 8, y - 1) as Crafter
        val liq = near("liquefier", x + 12, y - 1) as Crafter
        liq.setRecipe(g, Recipes["liquid_solanite"])
        val kiln = near("curing_kiln", x + 16, y - 1) as Crafter
        kiln.setRecipe(g, Recipes["epoxy_glass"])
        val packer = near("cargo_packer", x + 8, y + 4) as Crafter
        packer.setRecipe(g, Recipes["epoxy_canister"])
        val synth = near("vitae_synth", x + 12, y + 4) as Crafter
        synth.setRecipe(g, Recipes["vitae_elixir"])
        val seq = near("gene_sequencer", x + 16, y + 4) as Crafter
        seq.setRecipe(g, Recipes["telomere_strand"])
        val launcher = near("launcher", x + 20, y + 1) as Crafter
        launcher.setCargoMode(g, true)
        // keep everything busy for the picture
        g.inventory.add(Items.CARBON, 0)
        repeat(9) { forge.input.add(Items.SOLANITE_ORE, 1) }
        liq.input.add(Items.SOLANITE_INGOT, 3); liq.input.add(Items.SAP, 6)
        kiln.input.add(Items.LIQUID_SOLANITE, 9)
        packer.input.add(Items.LIQUID_SOLANITE, 12); packer.input.add(Items.EPOXY_BRICK, 2)
        listOf("telomere_strand" to 4, "cell_lattice" to 4, "epoxy_glass" to 2, "cryo_coolant" to 4).forEach { (id, n) -> synth.input.add(Items[id], n) }
        seq.input.add(Items.VITAE_PLASMA, 6); seq.input.add(Items["processing_unit"], 2)
        repeat(10) { launcher.accept(Items.ROCKET_SEGMENT) }
        repeat(g.cargoCapacity) { launcher.accept(if (it % 3 == 0) Items.XENOCRETE_SACK else Items.EPOXY_CANISTER) }
        g.stats.shipped[Items.EPOXY_CANISTER] = 1240; g.stats.shipped[Items.XENOCRETE_SACK] = 860; g.stats.shipped[Items.VITAE_CRYOCASE] = 312; g.stats.cargoLaunches = 61
        g.missionIndex = g.missions.size - 1
        repeat(60 * 3) { g.tick(1f / 60f) }
        launcher.progress = .9f
        g.time = Game.DAY_LENGTH * 0.15f
        return g
    }

    private fun gameShot(name: String, build: () -> Game = { demo() }, focus: ((Game) -> Pair<Float, Float>)? = null, view: (GameView) -> Unit = {}, setup: (UiState, Game) -> Unit) {
        Terrain.synchronous = true
        val ctl = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val a = ctl.get()
        Sprites.load(a)
        val g = build()
        val ui = UiState()
        setup(ui, g)
        val f = focus?.invoke(g) ?: (g.buildings.last().x.toFloat() - 3 to g.buildings.last().y.toFloat() + 1)
        a.setContent {
            FactonicTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView({ GameView(it, g).also { v -> v.centerOn(f.first, f.second); v.selected = ui.selected; view(v) } }, Modifier.fillMaxSize())
                    GameHud(g, ui, {}, {}, {}, { _, _ -> }, { f })
                }
            }
        }
        snap(a, name)
    }

    @Test fun exoticBase() = gameShot("exotic", { exotic() }, { g -> g.buildings.first { it.type.id == "curing_kiln" }.let { it.x + 1f to it.y + 2f } }) { _, _ -> }
    @Test fun exoticInspect() = gameShot("exotic_inspect", { exotic() }, { g -> g.buildings.first { it.type.id == "curing_kiln" }.let { it.x + 4f to it.y + 2f } }) { ui, g -> ui.selected = g.buildings.first { it.type.id == "launcher" } }
    @Test fun exports() = gameShot("exports", { exotic() }) { ui, _ -> ui.panel = PanelKind.EXPORTS }
    @Test fun map() = gameShot("map") { ui, _ -> ui.panel = PanelKind.MAP }
    @Test fun hidden() = gameShot("hidden", focus = { g -> g.world.cx + 6f to g.world.cy + 3f }) { ui, _ -> ui.hudHidden = true }
    @Test fun compact() = gameShot("compact") { ui, _ -> ui.missionMin = true; ui.statusMin = true }
    @Test fun ghost() = gameShot("ghost", view = { v -> v.zoom *= 1.3f }) { _, g -> g.tool = Tool.Place(Buildings["assembler"]) }
    @Test fun lakes() = gameShot("lakes", focus = { g -> lakeSpot(g) }, view = { v -> v.zoom *= .5f }) { ui, _ -> ui.hudHidden = true }

    private fun lakeSpot(g: Game): Pair<Float, Float> {
        val w = g.world
        for (r in 20..200 step 4) for (a in 0 until 32) {
            val x = (w.cx + kotlin.math.cos(a / 32f * 6.28f) * r).toInt(); val y = (w.cy + kotlin.math.sin(a / 32f * 6.28f) * r).toInt()
            if (w.groundAt(x, y) == com.factonic.game.world.Ground.LAKE && w.groundAt(x + 8, y) != com.factonic.game.world.Ground.LAKE) return x + 4f to y.toFloat()
        }
        return w.cx.toFloat() to w.cy.toFloat()
    }

    @Test fun game() = gameShot("game") { _, _ -> }
    @Test fun research() = gameShot("research") { ui, _ -> ui.panel = PanelKind.RESEARCH }
    @Test fun beltLine() = gameShot("belt_line", view = { v -> v.zoom *= 1.2f; v.previewLine(1, 7) }) { _, g -> g.tool = Tool.Place(Buildings["belt"]); g.inventory.add(Items["belt"], 40) }
    @Test fun beltGhost() = gameShot("belt_ghost", view = { v -> v.zoom *= 1.2f }) { _, g -> g.tool = Tool.Place(Buildings["belt"]); g.inventory.add(Items["belt"], 40) }
    @Test fun craftQueue() = gameShot("craft_queue") { ui, g ->
        ui.panel = PanelKind.CRAFT
        g.inventory.add(Items["ferrite_plate"], 60); g.inventory.add(Items["gear"], 20)
        g.queueCraft(Recipes["gear"], 3); g.queueCraft(Recipes["belt"], 2)
        repeat(30) { g.tick(1f / 60f) }
    }
    @Test fun craft() = gameShot("craft") { ui, _ -> ui.panel = PanelKind.CRAFT }
    @Test fun inspect() = gameShot("inspect") { ui, g -> ui.selected = g.buildings.first { it is Crafter } }
    @Test fun night() = gameShot("night") { _, g -> g.time = Game.DAY_LENGTH * 0.62f; repeat(150) { g.tick(1f / 60f) } }

    /** A belt line diving under two crossing belts, then a splitter sharing it three ways. */
    private fun logistics(): Game {
        val g = demo()
        val w = g.world
        listOf("belt" to 80, "tunnel_belt" to 4, "splitter" to 2).forEach { (id, n) -> g.inventory.add(Items[id], n) }
        var spot: Pair<Int, Int>? = null
        loop@ for (r in 6..90) for (y in w.cy - r..w.cy + r) for (x in w.cx - r..w.cx + r) {
            if ((-1..12).all { dx -> (-1..8).all { dy -> g.canPlace(Buildings["chest"], x + dx, y + dy) == null } }) { spot = x to y; break@loop }
        }
        val (x, y) = spot!!
        val r = y + 4
        val start = g.place(Buildings["belt"], x, r, 1) as Belt
        g.place(Buildings["belt"], x + 1, r, 1)
        g.place(Buildings["tunnel_belt"], x + 2, r, 1)
        g.place(Buildings["tunnel_belt"], x + 5, r, 1)
        g.place(Buildings["belt"], x + 6, r, 1)
        g.place(Buildings["splitter"], x + 7, r, 1)
        for (k in 8..11) g.place(Buildings["belt"], x + k, r, 1)
        for (k in 1..3) g.place(Buildings["belt"], x + 7, r - k, 0)
        for (k in 1..3) g.place(Buildings["belt"], x + 7, r + k, 2)
        for (cx in listOf(x + 3, x + 4)) for (k in 0..8) g.place(Buildings["belt"], cx, y + k, 2)
        val cross = g.world.at(x + 3, y) as Belt
        repeat(60 * 12) {
            if (it % 20 == 0) start.accept(if (it % 40 == 0) Items["gear"] else Items.COPPER_PLATE)
            if (it % 30 == 0) cross.accept(Items["circuit"])
            g.tick(1f / 60f)
        }
        g.time = Game.DAY_LENGTH * 0.15f
        return g
    }

    @Test fun logisticsShot() = gameShot("logistics", { logistics() }, { g -> g.buildings.first { it is Splitter }.let { it.x - 2f to it.y + .5f } }, view = { v -> v.zoom *= 1.15f }) { _, _ -> }
    @Test fun tunnelGhost() = gameShot("tunnel_ghost", { logistics() }, { g -> g.buildings.first { it is Splitter }.let { it.x - 2f to it.y + .5f } }) { ui, g ->
        g.tool = Tool.Place(Buildings["tunnel_belt"]); ui.selected = g.buildings.first { it is Tunnel }
    }
    @Test fun splitterInspect() = gameShot("splitter_inspect", { logistics() }, { g -> g.buildings.first { it is Splitter }.let { it.x + 2f to it.y + .5f } }) { ui, g ->
        val sp = g.buildings.first { it is Splitter } as Splitter
        sp.filter = Items.COPPER_PLATE; ui.selected = sp
    }
    @Test fun stats() = gameShot("stats") { ui, _ -> ui.panel = PanelKind.STATS }
    @Test fun settings() = gameShot("settings") { ui, _ -> ui.panel = PanelKind.SETTINGS }
    @Test fun researchQueue() = gameShot("research_queue") { ui, g -> g.queueResearch(Techs["engines"]); ui.panel = PanelKind.RESEARCH }

    @Test fun title() {
        val a = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        a.setContent { FactonicTheme { TitleScreen(true, {}, {}) } }
        snap(a, "title")
    }
}
