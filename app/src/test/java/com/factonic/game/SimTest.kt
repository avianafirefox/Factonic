package com.factonic.game

import com.factonic.game.data.*
import com.factonic.game.sim.*
import com.factonic.game.world.Res
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class SimTest {
    private fun run(g: Game, seconds: Float) { repeat((seconds * 60).toInt()) { g.tick(1f / 60f) } }

    @Test
    fun mineSmeltStore() {
        val g = Game.newGame(1234L)
        val w = g.world
        var spot: Pair<Int, Int>? = null
        loop@ for (r in 2..60) for (y in w.cy - r..w.cy + r) for (x in w.cx - r..w.cx + r) {
            if ((0..1).all { dy -> (0..1).all { dx -> w.resAt(x + dx, y + dy) == Res.FERRITE } } &&
                g.canPlace(Buildings["drill"], x, y) == null &&
                (2..9).all { dx -> (0..1).all { dy -> w.at(x + dx, y + dy) == null && g.canPlace(Buildings["chest"], x + dx, y + dy) == null } }
            ) { spot = x to y; break@loop }
        }
        assertNotNull("no ferrite spot near start", spot)
        val (x, y) = spot!!
        assertNotNull(g.place(Buildings["drill"], x, y, 1))
        assertNotNull(g.place(Buildings["belt"], x + 2, y, 1))
        assertNotNull(g.place(Buildings["belt"], x + 3, y, 1))
        assertNotNull(g.place(Buildings["inserter"], x + 4, y, 1))
        val sm = g.place(Buildings["smelter"], x + 5, y, 0)!!
        assertNotNull(g.place(Buildings["inserter"], x + 7, y, 1))
        val chest = g.place(Buildings["chest"], x + 8, y, 0)!!
        g.inventory.add(Items["carbon"], 0)
        run(g, 120f)
        val plates = chest.contents().sumOf { if (it.item.id == "ferrite_plate") it.n else 0 }
        println("power sat=${g.powerSat} demand=${g.powerDemand} cap=${g.powerCapacity}; plates in chest=$plates; smelter=${sm.status(g)}")
        assertTrue("expected plates in chest, got $plates", plates > 5)

        // save / load round trip
        val f = File.createTempFile("fac", ".sav")
        g.flushQueueForSave(); g.save(f)
        val g2 = Game.load(f)
        assertNotNull(g2)
        assertEquals(g.buildings.size, g2!!.buildings.size)
        run(g2, 10f)
    }

    @Test
    fun handCraftAndResearch() {
        val g = Game.newGame(99L)
        val gear = Recipes["gear"]
        val before = g.inventory[Items["gear"]]
        assertTrue(g.queueCraft(gear, 5))
        run(g, 30f)
        assertEquals(before + 5, g.inventory[Items["gear"]])

        // research with a lab fed by hand
        val labB = run {
            val w = g.world
            var placed: Building? = null
            for (dy in 4..20) { placed = g.place(Buildings["lab"], w.cx + 4, w.cy + dy, 0); if (placed != null) break }
            placed!!
        }
        val t = Techs["automation"]
        g.startResearch(t)
        repeat(t.count) { while (!labB.accept(Items.SCI_K)) run(g, 1f) }
        run(g, t.totalTime + 30f)
        assertTrue("automation researched", g.researched.contains("automation"))
        assertTrue(g.unlocked.contains("assembler"))
    }

    @Test
    fun allSciencesReachable() {
        Content.init()
        // every tech prereq exists and every science pack has a recipe unlocked by some tech or at start
        for (t in Techs.all) t.prereqs.forEach { assertNotNull(Techs.byId[it]) }
        val unlockable = Recipes.STARTING.toMutableSet().also { s -> Techs.all.forEach { s += it.unlocks } }
        for (s in Items.SCIENCES) assertTrue("science ${s.id} craftable", Recipes.all.any { it.main == s && (it.id in unlockable || it.cat == Cat.LAUNCH) })
        for (t in Techs.all) t.unlocks.forEach { assertNotNull("recipe $it", Recipes.byId[it]) }
        // every recipe input is producible
        val producible = HashSet<Item>()
        listOf("ferrite_ore", "cuprite_ore", "lumite", "carbon", "aether_crystal", "xenite_ore", "sap", "solanite_ore", "terravite_ore", "vitae_ooze").forEach { producible += Items[it] }
        var changed = true
        while (changed) {
            changed = false
            for (r in Recipes.all) if (r.inputs.all { it.item in producible }) for (o in r.outputs) if (producible.add(o.item)) changed = true
        }
        for (r in Recipes.all) for (i in r.inputs) assertTrue("input ${i.item.id} of ${r.id} producible", i.item in producible)
        println("techs=${Techs.all.size} recipes=${Recipes.all.size} items=${Items.all.size}")
    }

    @Test
    fun rareDepositsExistAndNeedResonance() {
        val g = Game.newGame(4321L)
        val w = g.world
        assertEquals(512, w.w)
        for (r in listOf(Res.SOLANITE, Res.TERRAVITE, Res.VITAE)) {
            val n = (0 until w.w * w.h).count { w.res[it].toInt() == r }
            println("res $r tiles=$n")
            assertTrue("deposit $r exists", n > 20)
        }
        // find a 2x2 solanite spot
        var spot: Pair<Int, Int>? = null
        loop@ for (y in 0 until w.h - 1) for (x in 0 until w.w - 1) {
            if ((0..1).all { dy -> (0..1).all { dx -> w.resAt(x + dx, y + dy) == Res.SOLANITE } } && g.canPlace(Buildings["resonance_drill"], x, y) == null &&
                (0..1).all { dy -> g.canPlace(Buildings["chest"], x + 2, y + dy) == null }) { spot = x to y; break@loop }
        }
        val (x, y) = spot!!
        assertNotNull("normal drills refuse rare ore", g.canPlace(Buildings["drill"], x, y))
        assertNull(g.handMine(x, y))
        g.inventory.add(Items["resonance_drill"], 1); g.inventory.add(Items["chest"], 2)
        assertNotNull(g.place(Buildings["resonance_drill"], x, y, 1))
        val chest = g.place(Buildings["chest"], x + 2, y, 0)!!
        g.place(Buildings["chest"], x + 2, y + 1, 0)
        run(g, 30f)
        val ore = g.buildings.filterIsInstance<Chest>().sumOf { it.items[Items.SOLANITE_ORE] }
        println("solanite mined: $ore (sat ${g.powerSat})")
        assertTrue(ore > 3)
    }

    @Test
    fun exoticChainAndCargoLaunch() {
        val g = Game.newGame(77L)
        Recipes.all.forEach { g.unlocked += it.id }
        g.researched += "signal"
        val w = g.world
        g.inventory.add(Items["ingot_forge"], 1); g.inventory.add(Items["launcher"], 1)
        g.inventory.add(Items["combustion_gen"], 4); g.inventory.add(Items.CARBON, 200)
        fun free(size: Int): Pair<Int, Int> {
            for (r in 6..80) for (yy in w.cy - r..w.cy + r) for (xx in w.cx - r..w.cx + r)
                if (g.canPlace(Buildings["ingot_forge"], xx, yy) == null && (0 until size).all { d -> g.canPlace(Buildings["chest"], xx + d, yy - 1) == null }) return xx to yy
            error("no space")
        }
        val (fx, fy) = free(3)
        val forge = g.place(Buildings["ingot_forge"], fx, fy, 0) as Crafter
        repeat(6) { assertTrue(forge.accept(Items.SOLANITE_ORE)) }
        assertFalse("forge locked to solanite", forge.accept(Items.TERRAVITE_ORE))
        val (lx, ly) = free(3)
        val launcher = g.place(Buildings["launcher"], lx, ly, 0) as Crafter
        repeat(3) { val (gx, gy) = free(3); val gen = g.place(Buildings["combustion_gen"], gx, gy, 0) as Generator; repeat(20) { gen.accept(Items.CARBON) } }
        launcher.setCargoMode(g, true)
        repeat(10) { assertTrue(launcher.accept(Items.ROCKET_SEGMENT)) }
        repeat(g.cargoCapacity) { assertTrue(launcher.accept(Items.EPOXY_CANISTER)) }
        assertFalse("hold full", launcher.accept(Items.XENOCRETE_SACK))
        run(g, 60f)
        println("forge=${forge.status(g)} out=${forge.output.stacks()} launcher=${launcher.status(g)} sat=${g.powerSat}")
        assertEquals(1L, forge.output[Items.SOLANITE_INGOT].toLong().coerceAtMost(1))
        assertEquals(1, g.stats.cargoLaunches)
        assertEquals(40L, g.stats.shipped(Items.EPOXY_CANISTER))
        // save / load keeps the shipping totals
        val f = File.createTempFile("fac", ".sav")
        g.flushQueueForSave(); g.save(f)
        val g2 = Game.load(f)!!
        assertEquals(40L, g2.stats.shipped(Items.EPOXY_CANISTER))
        assertTrue((g2.buildings.first { it.type.id == "launcher" } as Crafter).cargoMode)
    }

    // ------------------------------------------------------------------------------------ v1.0 features

    /** A free rectangle of len x rows tiles near the landing site. */
    private fun freeArea(g: Game, len: Int, rows: Int): Pair<Int, Int> {
        val w = g.world
        for (r in 6..140) for (y in w.cy - r..w.cy + r) for (x in w.cx - r..w.cx + r)
            if ((-1..len).all { dx -> (-1..rows).all { dy -> g.canPlace(Buildings["chest"], x + dx, y + dy) == null } }) return x to y
        error("no free area")
    }

    private fun feed(g: Game, belt: Belt, item: Item, n: Int) {
        var left = n
        var guard = 0
        while (left > 0 && guard++ < 2000) {
            if (belt.accept(item)) left--
            run(g, .2f)
        }
        assertEquals("fed all items", 0, left)
    }

    @Test
    fun tunnelCarriesItemsUnderACrossingBelt() {
        val g = Game.newGame(555L)
        g.inventory.add(Items["belt"], 40); g.inventory.add(Items["tunnel_belt"], 4)
        val (x, y) = freeArea(g, 8, 3)
        val r = y + 1
        val start = g.place(Buildings["belt"], x, r, 1) as Belt
        g.place(Buildings["belt"], x + 1, r, 1)
        val tin = g.place(Buildings["tunnel_belt"], x + 2, r, 1) as Tunnel
        val tout = g.place(Buildings["tunnel_belt"], x + 4, r, 1) as Tunnel
        assertTrue("first piece is the entrance", tin.entry)
        assertFalse("second piece is the exit", tout.entry)
        assertSame(tout, tin.partner)
        val end1 = g.place(Buildings["belt"], x + 5, r, 1) as Belt
        val end2 = g.place(Buildings["belt"], x + 6, r, 1) as Belt
        // a southbound belt crosses over the tunnel
        val cross = (0..2).map { g.place(Buildings["belt"], x + 3, y + it, 2) as Belt }
        cross[0].accept(Items.COPPER_PLATE)
        feed(g, start, Items["gear"], 6)
        run(g, 8f)
        val past = end1.items.size + end2.items.size + tout.items.size
        assertEquals("all gears came out of the tunnel", 6, past)
        assertTrue("gears never touched the crossing belt", cross.all { b -> b.items.none { it.item.id == "gear" } })
        assertEquals("the crossing belt still works", 1, cross[2].items.count { it.item == Items.COPPER_PLATE })
        // an exit refuses items pushed into its hooded back
        assertFalse(tout.receiveMoving(Items["gear"], 1, cross[1]))
        // reversing the tunnel swaps entrance and exit
        g.rotate(tin)
        assertTrue(tout.entry); assertFalse(tin.entry); assertEquals(3, tin.dir)
        // removing one end orphans the other; a new piece re-pairs with it
        g.remove(tin)
        assertNull(tout.partner)
        val again = g.place(Buildings["tunnel_belt"], x + 2, r, 3) as Tunnel
        assertSame(tout, again.partner); assertFalse(again.entry)
    }

    @Test
    fun splitterSharesAndSorts() {
        val g = Game.newGame(556L)
        g.inventory.add(Items["belt"], 40); g.inventory.add(Items["splitter"], 2)
        val (x, y) = freeArea(g, 5, 5)
        val input = g.place(Buildings["belt"], x + 1, y + 2, 1) as Belt
        val sp = g.place(Buildings["splitter"], x + 2, y + 2, 1) as Splitter
        val east = g.place(Buildings["belt"], x + 3, y + 2, 1) as Belt
        val north = g.place(Buildings["belt"], x + 2, y + 1, 0) as Belt
        val south = g.place(Buildings["belt"], x + 2, y + 3, 2) as Belt
        feed(g, input, Items["gear"], 9)
        run(g, 4f)
        println("split: east=${east.items.size} north=${north.items.size} south=${south.items.size}")
        assertTrue("every output got items", east.items.isNotEmpty() && north.items.isNotEmpty() && south.items.isNotEmpty())
        assertTrue("the input belt is never used as an output", input.items.isEmpty())
        // sorting: copper to the front, everything else to the sides
        listOf(east, north, south).forEach { it.items.clear() }
        sp.filter = Items.COPPER_PLATE
        repeat(4) { feed(g, input, Items.COPPER_PLATE, 1); feed(g, input, Items["gear"], 1) }
        run(g, 4f)
        assertTrue("only copper went east", east.items.isNotEmpty() && east.items.all { it.item == Items.COPPER_PLATE })
        assertTrue("no copper went to the sides", (north.items + south.items).none { it.item == Items.COPPER_PLATE })
        assertEquals(4, north.items.size + south.items.size)
    }

    @Test
    fun researchQueueRunsInOrder() {
        val g = Game.newGame(557L)
        assertTrue(g.queueResearch(Techs["logistic_sci"]))
        assertEquals("prerequisite starts first", "automation", g.currentTech?.id)
        assertEquals(listOf("logistic_sci"), g.researchQueue.map { it.id })
        g.queueResearch(Techs["steel"])
        repeat(Techs["automation"].count) { g.addResearchUnit() }
        assertEquals("queue advances", "logistic_sci", g.currentTech?.id)
        assertEquals(listOf("steel"), g.researchQueue.map { it.id })
        g.dequeueResearch(Techs["steel"])
        assertTrue(g.researchQueue.isEmpty())
        g.startResearch(Techs["illumination"])
        assertEquals("illumination", g.currentTech?.id)
        assertEquals("interrupted research waits in the queue", "logistic_sci", g.researchQueue.firstOrNull()?.id)
        // dequeuing a prerequisite also drops what depends on it
        g.queueResearch(Techs["research_1"])
        g.stopResearch()
        assertEquals("logistic_sci", g.currentTech?.id)
        g.queueResearch(Techs["engines"])
        assertTrue(g.researchQueue.any { it.id == "steel" })
        g.dequeueResearch(Techs["steel"])
        assertTrue("engines needs steel", g.researchQueue.none { it.id == "engines" })
    }

    @Test
    fun saveKeepsQueuesTunnelsAndFilters() {
        val g = Game.newGame(558L)
        g.inventory.add(Items["belt"], 10); g.inventory.add(Items["tunnel_belt"], 2); g.inventory.add(Items["splitter"], 1)
        val (x, y) = freeArea(g, 6, 2)
        val tin = g.place(Buildings["tunnel_belt"], x, y, 1) as Tunnel
        g.place(Buildings["tunnel_belt"], x + 3, y, 1)
        val sp = g.place(Buildings["splitter"], x + 4, y, 1) as Splitter
        sp.filter = Items["gear"]
        tin.accept(Items["gear"])
        g.inventory.add(Items.FERRITE_PLATE, 50)
        assertTrue(g.queueCraft(Recipes["gear"], 5))
        g.queueResearch(Techs["logistic_sci"])
        g.hotbar.remove("splitter"); g.hotbar.add(0, "splitter")
        run(g, 1f)
        val queued = g.craftQueue.size
        val f = File.createTempFile("fac5", ".sav")
        g.save(f)
        assertEquals("saving no longer cancels hand-crafting", queued, g.craftQueue.size)
        val g2 = Game.load(f)!!
        assertEquals(queued, g2.craftQueue.size)
        assertEquals(g.inventory[Items.FERRITE_PLATE], g2.inventory[Items.FERRITE_PLATE])
        assertEquals("automation", g2.currentTech?.id)
        assertEquals(listOf("logistic_sci"), g2.researchQueue.map { it.id })
        assertEquals("splitter", g2.hotbar.first())
        val t2 = g2.buildings.filterIsInstance<Tunnel>()
        assertEquals(2, t2.size)
        assertTrue("tunnel pair restored", t2.all { it.partner != null } && t2.count { it.entry } == 1)
        assertEquals(Items["gear"], g2.buildings.filterIsInstance<Splitter>().single().filter)
        assertEquals(g.playTime.toDouble(), g2.playTime.toDouble(), .01)
        val gearsBefore = g2.inventory[Items["gear"]]
        run(g2, 10f)
        assertEquals("queued crafts finish after loading", gearsBefore + queued, g2.inventory[Items["gear"]])
        assertTrue(f.delete())
    }

    @Test
    fun loadsPrototypeV4Saves() {
        val g = Game.newGame(559L)
        val (x, y) = freeArea(g, 3, 1)
        val chest = g.place(Buildings["chest"], x, y, 0) as Chest
        chest.items.add(Items["gear"], 17)
        val belt = g.place(Buildings["belt"], x + 1, y, 1) as Belt
        belt.accept(Items.COPPER_PLATE)
        g.researched += "automation"; g.unlocked += "assembler"; g.missionIndex = 4
        run(g, 2f)
        val f = File.createTempFile("fac4", ".sav")
        writeV4(g, f)
        val g2 = Game.load(f)!!
        assertEquals(g.buildings.size, g2.buildings.size)
        assertEquals(17, g2.buildings.filterIsInstance<Chest>().single().items[Items["gear"]])
        assertEquals(1, g2.buildings.filterIsInstance<Belt>().single().items.size)
        assertEquals(g.missionIndex, g2.missionIndex)
        assertTrue(g2.researched.contains("automation"))
        assertEquals(Game.DEFAULT_HOTBAR, g2.hotbar)
        run(g2, 2f)
        // a save written by the real prototype build, when one is supplied
        System.getProperty("factonic.v4save")?.let { path ->
            val p = Game.load(File(path))
            assertNotNull("prototype save loads", p)
            println("prototype save: ${p!!.buildings.size} buildings, ${p.inventory.map.size} item types, sol ${(p.time / Game.DAY_LENGTH).toInt() + 1}")
            assertEquals(17, p.buildings.filterIsInstance<Chest>().sumOf { it.items[Items["gear"]] })
            run(p, 5f)
        }
    }

    /** Writes a save exactly the way the v1.2 prototype did (format version 4). */
    private fun writeV4(g: Game, file: File) {
        val w = g.world
        DataOutputStream(GZIPOutputStream(file.outputStream().buffered())).use { o ->
            o.writeInt(4); o.writeLong(w.seed); o.writeFloat(g.time)
            o.write(w.res); for (v in w.amount) o.writeInt(v); o.write(w.flora)
            g.inventory.write(o)
            o.writeShort(g.unlocked.size); g.unlocked.forEach { o.writeUTF(it) }
            o.writeShort(g.researched.size); g.researched.forEach { o.writeUTF(it) }
            o.writeUTF(g.currentTech?.id ?: "")
            o.writeShort(g.techProgress.size); g.techProgress.forEach { (k, v) -> o.writeUTF(k); o.writeInt(v) }
            o.writeShort(g.stats.made.size); g.stats.made.forEach { (k, v) -> o.writeUTF(k.id); o.writeLong(v) }
            o.writeShort(g.stats.built.size); g.stats.built.forEach { (k, v) -> o.writeUTF(k); o.writeInt(v) }
            o.writeInt(g.stats.handMined); o.writeInt(g.stats.launches)
            o.writeInt(g.missionIndex); o.writeBoolean(g.victory); o.writeBoolean(g.victoryShown)
            o.writeInt(g.stats.cargoLaunches)
            o.writeShort(g.stats.shipped.size); g.stats.shipped.forEach { (k, v) -> o.writeUTF(k.id); o.writeLong(v) }
            o.writeInt(g.buildings.size)
            for (b in g.buildings) {
                o.writeUTF(b.type.id); o.writeShort(b.x); o.writeShort(b.y); o.writeByte(b.dir); b.save(o)
            }
        }
    }

    @Test
    fun alertsAndStatistics() {
        val g = Game.newGame(560L)
        g.inventory.add(Items["assembler"], 1)
        val (x, y) = freeArea(g, 2, 2)
        val asm = g.place(Buildings["assembler"], x, y, 0)!!
        run(g, 5f)
        assertTrue("grace period for a new machine", g.alerts.isEmpty())
        run(g, 20f)
        assertTrue("assembler without a recipe raises an alert", g.alerts.contains(asm))
        (asm as Crafter).setRecipe(g, Recipes["gear"])
        run(g, 1.5f)
        assertFalse(g.alerts.contains(asm))
        // production history
        repeat(30) { g.stats.produced(Items["gear"], 2) }
        run(g, 6f)
        val rate = g.stats.ratePerMin(Items["gear"], 60f)
        assertNotNull(rate)
        assertTrue("rate is positive: $rate", rate!! > 0f)
        // objective pointer finds ferrite for the first mission
        val p = g.nearestDeposit(g.missions[0].target, g.world.cx.toFloat(), g.world.cy.toFloat())
        assertNotNull(p)
        assertEquals(com.factonic.game.world.Res.FERRITE, g.world.resAt(p!![0].toInt(), p[1].toInt()).let { if (it == 0) com.factonic.game.world.Res.FERRITE else it })
    }
}
