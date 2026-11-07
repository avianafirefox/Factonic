package com.factonic.game.sim

import com.factonic.game.data.BKind
import com.factonic.game.data.BType
import com.factonic.game.data.Bonus
import com.factonic.game.data.Buildings
import com.factonic.game.data.Cat
import com.factonic.game.data.Content
import com.factonic.game.data.Item
import com.factonic.game.data.Items
import com.factonic.game.data.Recipe
import com.factonic.game.data.Recipes
import com.factonic.game.data.Stack
import com.factonic.game.data.Tech
import com.factonic.game.data.Techs
import com.factonic.game.data.Tunnels
import com.factonic.game.world.Flora
import com.factonic.game.world.Ground
import com.factonic.game.world.Res
import com.factonic.game.world.World
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min

class Stats {
    companion object {
        /** Seconds per production-history bucket. */
        const val BUCKET = 5f
        /** Buckets kept (10 minutes). */
        const val HIST = 120
    }

    val made = HashMap<Item, Long>()
    val built = HashMap<String, Int>()
    var handMined = 0
    var launches = 0
    var cargoLaunches = 0
    val shipped = LinkedHashMap<Item, Long>()
    fun shipped(i: Item) = shipped[i] ?: 0L

    /** Rolling production history per item (not saved: it restarts with each session). */
    val history = HashMap<Item, IntArray>()
    var bucket = 0
        private set
    /** Completed buckets so far (caps at HIST - 1). */
    var filled = 0
        private set
    private var clock = 0f

    fun produced(i: Item, n: Int) {
        made[i] = (made[i] ?: 0L) + n
        history.getOrPut(i) { IntArray(HIST) }[bucket] += n
    }

    fun made(i: Item) = made[i] ?: 0L

    fun advance(dt: Float) {
        clock += dt
        while (clock >= BUCKET) {
            clock -= BUCKET
            bucket = (bucket + 1) % HIST
            if (filled < HIST - 1) filled++
            for (h in history.values) h[bucket] = 0
        }
    }

    /** Items per minute over the last [seconds] (complete buckets only), or null if there is no data yet. */
    fun ratePerMin(i: Item, seconds: Float): Float? {
        val n = min((seconds / BUCKET).toInt().coerceIn(1, HIST - 1), filled)
        if (n == 0) return null
        val h = history[i] ?: return 0f
        var sum = 0
        for (k in 1..n) sum += h[(bucket - k + HIST) % HIST]
        return sum / (n * BUCKET) * 60f
    }

    /** The last [n] complete buckets for an item, oldest first. */
    fun series(i: Item, n: Int): IntArray {
        val out = IntArray(n)
        val h = history[i] ?: return out
        for (k in 0 until min(n, filled)) out[n - 1 - k] = h[(bucket - 1 - k + HIST) % HIST]
        return out
    }
}

class CraftJob(val recipe: Recipe, val planId: Int, val reserved: List<Stack>, val fromPool: List<Stack>, var started: Boolean = false, var progress: Float = 0f)

/** [target] lists resource types the objective pointer leads to (see [Game.nearestDeposit]). */
class Mission(val title: String, val desc: String, val check: (Game) -> Boolean, val progress: (Game) -> String = { "" }, val target: IntArray = IntArray(0))

sealed class Tool {
    object None : Tool()
    object Remove : Tool()
    data class Place(val type: BType) : Tool()
}

class FloatText(val text: String, var x: Float, var y: Float, var life: Float = 1.2f, val color: Int = 0xFFFFFFFF.toInt())

/** Writes saves on a background thread so autosaves never hitch the game. */
object SaveIO {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "factonic-save").apply { isDaemon = true } }

    fun writeAsync(file: File, data: ByteArray) {
        exec.execute { write(file, data) }
    }

    /** Waits until every queued save has hit the disk. */
    fun awaitIdle() {
        try { exec.submit {}.get() } catch (_: Exception) {}
    }

    /** gzip [data] into a temp file, fsync it, keep the previous save as .bak, then swap the new one in. */
    fun write(file: File, data: ByteArray): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        val fos = FileOutputStream(tmp)
        try {
            val gz = GZIPOutputStream(BufferedOutputStream(fos, 1 shl 16))
            gz.write(data); gz.finish(); gz.flush()
            try { fos.fd.sync() } catch (_: Exception) {}
            gz.close()
        } finally {
            fos.close()
        }
        val bak = File(file.path + ".bak")
        if (file.exists()) { bak.delete(); file.renameTo(bak) }
        tmp.renameTo(file)
    } catch (e: Exception) {
        e.printStackTrace(); false
    }
}

class Game(val world: World) {
    companion object {
        const val SAVE_VERSION = 5
        const val DAY_LENGTH = 420f // seconds per full day
        var current: Game? = null
        /** Set by [load] when the main save was unreadable and the backup was used instead. */
        var loadNote: String? = null

        val DEFAULT_HOTBAR = listOf("belt", "inserter", "drill", "smelter", "chest", "assembler")

        fun newGame(seed: Long): Game {
            Content.init()
            val w = World(seed)
            w.generate()
            val g = Game(w)
            current = g
            g.setupStart()
            return g
        }

        /** Loads a save (any supported version), falling back to the .bak copy if the main file is damaged. */
        fun load(file: File): Game? {
            loadNote = null
            readFile(file)?.let { return it }
            val bak = File(file.path + ".bak")
            if (bak.exists()) readFile(bak)?.let {
                loadNote = "Your last save was damaged - restored the previous autosave."
                return it
            }
            return null
        }

        private fun readFile(file: File): Game? = try {
            Content.init()
            DataInputStream(GZIPInputStream(file.inputStream().buffered(1 shl 16)).buffered(1 shl 16)).use { inp ->
                val ver = inp.readInt()
                if (ver < 4 || ver > SAVE_VERSION) return null
                val seed = inp.readLong()
                val w = World(seed)
                w.generate()
                val g = Game(w)
                g.readState(inp, ver)
                current = g
                g
            }
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    val buildings = ArrayList<Building>()
    val inventory = Bag()
    val unlocked = HashSet<String>()
    val researched = HashSet<String>()
    var currentTech: Tech? = null
    val techProgress = HashMap<String, Int>()
    /** Technologies waiting to be researched after the current one, in order. */
    val researchQueue = ArrayList<Tech>()
    val stats = Stats()
    val craftQueue = ArrayList<CraftJob>()
    var missionIndex = 0
    var victory = false
    var victoryShown = false
    /** Seconds actually played (the simulation only runs while the game is open and unpaused). */
    var playTime = 0f
    /** The quick-build bar, most recently used first. Saved with the game. */
    val hotbar = ArrayList<String>(DEFAULT_HOTBAR)
    /** Buildings that have had a problem long enough to deserve the player's attention. */
    val alerts = ArrayList<Building>()

    var time = DAY_LENGTH * 0.08f
    var tool: Tool = Tool.None
    var placeDir = 0
    val floats = ArrayList<FloatText>()
    val messages = ArrayList<String>()
    /** Sound cues for the view to play (drained every frame). */
    val sfx = ArrayList<String>()
    /** True while a save is being read: buildings keep their saved links instead of re-pairing. */
    private var loading = false

    // bonuses
    var miningMul = 1f
    var labMul = 1f
    var cargoMul = 1f
    var segmentDiscount = 0
    val rocketSegments get() = 10 - segmentDiscount
    val cargoCapacity get() = (40 * cargoMul).toInt()

    // power
    var powerSat = 1f
    var powerDemand = 0f
    var powerSupply = 0f
    var powerCapacity = 0f
    var storedEnergy = 0f
    var storedMax = 0f
    private var lastDemand = 0f
    private val gens = ArrayList<Generator>()
    private var alertClock = 0f

    /** 0 = midnight dark, 1 = full day. Phase 0 is sunrise. */
    val daylight: Float
        get() {
            val phase = (time / DAY_LENGTH) % 1f
            val sun = kotlin.math.sin(phase * 2 * PI).toFloat()
            return (sun * 1.6f + .35f).coerceIn(0f, 1f)
        }

    private val basicOres = intArrayOf(Res.FERRITE, Res.CUPRITE, Res.LUMITE, Res.CARBON)

    val missions: List<Mission> = listOf(
        Mission("Gather Ferrite", "Tap a blue Ferrite Ore patch to mine it by hand. Collect 10 ore. Follow the arrow at the edge of the screen if you can't see any.",
            { it.stats.handMined >= 10 || it.stats.made(Items.FERRITE_ORE) >= 10 }, { "${minOf(10, it.stats.handMined)}/10" }, intArrayOf(Res.FERRITE)),
        Mission("Automate Mining", "Pick the Mining Drill in the hotbar, drag its ghost onto ore and tap it to build. Point its chute at a Storage Crate or belt.",
            { (it.stats.built["drill"] ?: 0) >= 1 }, target = basicOres),
        Mission("Smelt Plates", "Place a Smelter and feed it ore with a drill or an inserter. Produce 20 Ferrite Plates.",
            { it.stats.made(Items.FERRITE_PLATE) >= 20 }, { "${minOf(20L, it.stats.made(Items.FERRITE_PLATE))}/20" }),
        Mission("Belt It Up", "Lay 10 conveyor belts and place 2 inserters to move items between machines.",
            { (it.stats.built["belt"] ?: 0) >= 10 && (it.stats.built["inserter"] ?: 0) >= 2 }),
        Mission("First Research", "Build a Research Lab, feed it Kinetic Science and research Automation.",
            { it.researched.contains("automation") }),
        Mission("Mass Production", "Place an Assembler set to Kinetic Science. Produce 30 packs.",
            { it.stats.made(Items.SCI_K) >= 30 }, { "${minOf(30L, it.stats.made(Items.SCI_K))}/30" }),
        Mission("Logistic Science", "Research Logistic Science to unlock green packs. Tip: queue several technologies so your labs never sit idle.", { it.researched.contains("logistic_sci") }),
        Mission("Sap and Polymers", "Research Sap Extraction and Polymer Chemistry. Glowsap pools glow amber.", { it.researched.contains("polymers") }, target = intArrayOf(Res.SAP)),
        Mission("Chemical Science", "Research Chemical Science (blue packs).", { it.researched.contains("chemical_sci") }),
        Mission("Fabrication Science", "Research Fabrication Science (purple packs). You will need Aether Crystal.", { it.researched.contains("fabrication_sci") }, target = intArrayOf(Res.AETHER)),
        Mission("Quantum Science", "Research Quantum Science (yellow packs). Xenite glows green.", { it.researched.contains("quantum_sci") }, target = intArrayOf(Res.XENITE)),
        Mission("Reach Orbit", "Build an Orbital Launcher and launch a rocket to earn Astral Science.", { it.stats.launches >= 1 }),
        Mission("Earth Uplink", "Research the Earth Uplink using all six sciences to reach home.", { it.researched.contains("signal") }),
        Mission("Rare Elements", "Research Resonance Mining. Mine 50 Solanite or Terravite with a Resonance Drill - the deposits lie far from the landing site (follow the arrow or open the Map).",
            { it.stats.made(Items.SOLANITE_ORE) + it.stats.made(Items.TERRAVITE_ORE) >= 50 }, { "${minOf(50L, it.stats.made(Items.SOLANITE_ORE) + it.stats.made(Items.TERRAVITE_ORE))}/50" },
            intArrayOf(Res.SOLANITE, Res.TERRAVITE)),
        Mission("Xyra Epoxy", "Forge, liquefy and cure Solanite into Epoxy Film, Epoxy Bricks and Epoxy Glass.",
            { it.stats.made(Items.EPOXY_FILM) > 0 && it.stats.made(Items.EPOXY_BRICK) > 0 && it.stats.made(Items.EPOXY_GLASS) > 0 },
            { listOf(Items.EPOXY_FILM, Items.EPOXY_BRICK, Items.EPOXY_GLASS).count { i -> it.stats.made(i) > 0 }.toString() + "/3" }),
        Mission("First Shipment", "Pack Epoxy Canisters or Xenocrete Sacks, switch a launcher to Cargo and send a rocket to Earth.", { it.stats.cargoLaunches >= 1 }),
        Mission("The Vitae Project", "Harvest Vitae Ooze from a pink spring and synthesize the first Vitae Elixir.", { it.stats.made(Items.VITAE_ELIXIR) >= 1 },
            target = intArrayOf(Res.VITAE)),
        Mission("Ship Everything", "Earth needs every canister, sack and cryo-case you can make. There is no limit - build bigger and keep the rockets flying.",
            { false }, { "${it.stats.cargoLaunches} rockets" }),
    )

    val mission: Mission? get() = missions.getOrNull(missionIndex)

    // ------------------------------------------------------------------------------------ setup
    private fun setupStart() {
        unlocked += Recipes.STARTING
        val cx = world.cx - 1
        val cy = world.cy - 1
        world.clearArea(cx - 2, cy - 2, cx + 4, cy + 4)
        place(Buildings["power_core"], cx, cy, 0, free = true)
        listOf(
            "drill" to 4, "smelter" to 3, "chest" to 4, "belt" to 40, "inserter" to 10,
            "combustion_gen" to 1, "lab" to 1, "ferrite_plate" to 40, "copper_plate" to 20, "carbon" to 20, "lumite" to 10,
        ).forEach { (id, n) -> inventory.add(Items[id], n) }
        message("Crash landing survived. The Lander Core still supplies power.")
    }

    // ------------------------------------------------------------------------------------ tick
    fun tick(dt: Float) {
        time += dt
        playTime += dt
        stats.advance(dt)
        // ---- power: resolve last tick's demand against this tick's supply
        var constCap = 0f
        var combCap = 0f
        var accCharge = 0f
        var accDischarge = 0f
        storedEnergy = 0f; storedMax = 0f
        gens.clear()
        for (b in buildings) if (b is Generator) {
            gens += b
            when (b.type.kind) {
                BKind.COMBUSTION -> combCap += b.capacity(this)
                BKind.ACCUMULATOR -> {
                    storedEnergy += b.energy; storedMax += 5000f
                    accDischarge += min(300f, b.energy / dt)
                    accCharge += min(300f, (5000f - b.energy) / dt)
                }

                else -> constCap += b.capacity(this)
            }
        }
        val demand = lastDemand
        powerDemand = demand
        powerCapacity = constCap + combCap
        if (demand <= 0f) {
            powerSat = 1f
        }
        val genCap = constCap + combCap
        val combLoad: Float
        val constLoad: Float
        if (genCap >= demand) {
            powerSat = 1f
            constLoad = if (constCap > 0) min(1f, demand / constCap) else 0f
            combLoad = if (combCap > 0) ((demand - constCap) / combCap).coerceIn(0f, 1f) else 0f
            // surplus from free sources charges accumulators (combustion fuel is never burned for this)
            val surplus = (constCap - demand).coerceAtLeast(0f)
            val charge = min(surplus, accCharge)
            distributeAcc(gens, charge * dt, true)
            powerSupply = demand
        } else {
            constLoad = 1f; combLoad = 1f
            val deficit = demand - genCap
            val dis = min(deficit, accDischarge)
            distributeAcc(gens, dis * dt, false)
            powerSupply = genCap + dis
            powerSat = if (demand > 0) (powerSupply / demand).coerceIn(0f, 1f) else 1f
        }
        for (g in gens) {
            when (g.type.kind) {
                BKind.COMBUSTION -> {
                    val c = g.capacity(this); g.output = c * combLoad; g.burn(g.output * dt); g.anim += dt * combLoad
                }

                BKind.ACCUMULATOR -> {}
                else -> {
                    g.output = g.capacity(this) * constLoad; g.anim += dt * (if (g.output > 0) 1f else 0f)
                }
            }
        }

        // ---- buildings
        var newDemand = 0f
        for (i in buildings.indices) {
            val b = buildings[i]
            b.tick(this, dt)
            newDemand += b.demand
        }
        lastDemand = newDemand

        // ---- hand crafting
        tickCrafting(dt)

        // ---- floating texts
        val it = floats.iterator()
        while (it.hasNext()) {
            val f = it.next(); f.life -= dt; f.y -= dt * .8f; if (f.life <= 0) it.remove()
        }

        // ---- alerts, once a second
        alertClock += dt
        if (alertClock >= 1f) {
            alertClock -= 1f; scanAlerts()
        }

        // ---- missions
        val m = mission
        if (m != null && m.check(this)) {
            message("Objective complete: ${m.title}")
            sfx += "objective"
            missionIndex++
        }
    }

    private fun distributeAcc(gens: List<Generator>, kj: Float, charge: Boolean) {
        var left = kj
        for (g in gens) if (g.type.kind == BKind.ACCUMULATOR && left > 0) {
            if (charge) {
                val d = min(left, 5000f - g.energy); g.energy += d; left -= d; g.output = -d
            } else {
                val d = min(left, g.energy); g.energy -= d; left -= d; g.output = d
            }
        }
    }

    // ------------------------------------------------------------------------------------ alerts
    /** How long a problem has to last before it becomes an alert (newly placed machines get a grace period). */
    private fun alertDelay(b: Building) = when (b) {
        is Crafter -> 20
        is Tunnel -> 15
        else -> 6
    }

    private fun scanAlerts() {
        alerts.clear()
        var fresh = 0
        for (b in buildings) {
            val p = b.problem(this)
            if (p == null) {
                b.problemSecs = 0; continue
            }
            b.problemSecs++
            val delay = alertDelay(b)
            if (b.problemSecs >= delay) {
                alerts += b
                if (b.problemSecs == delay && fresh < 2) {
                    fresh++; message("${b.type.name}: $p")
                }
            }
        }
        if (fresh > 0) sfx += "alert"
    }

    // ------------------------------------------------------------------------------------ crafting
    fun canHandCraft(r: Recipe) = r.cat == Cat.ASSEMBLE && unlocked.contains(r.id)

    /** Plan a recursive hand-craft. Returns the list of jobs (in order) or null if impossible. */
    fun planCraft(r: Recipe, times: Int): List<Recipe>? {
        val inv = HashMap<Item, Int>()
        inventory.map.forEach { (k, v) -> inv[k] = v }
        val plan = ArrayList<Recipe>()
        fun need(item: Item, n: Int, depth: Int): Boolean {
            val have = inv[item] ?: 0
            if (have >= n) {
                inv[item] = have - n; return true
            }
            if (depth > 8) return false
            val sub = Recipes.handFor(item)?.takeIf { canHandCraft(it) } ?: return false
            inv[item] = 0
            val missing = n - have
            val per = sub.outputs.first { it.item == item }.n
            val runs = ceil(missing / per.toFloat()).toInt()
            repeat(runs) {
                for (s in sub.inputs) if (!need(s.item, s.n, depth + 1)) return false
                plan += sub
            }
            inv[item] = (inv[item] ?: 0) + runs * per - missing
            return true
        }
        repeat(times) {
            for (s in r.inputs) if (!need(s.item, s.n, 0)) return null
            plan += r
            r.outputs.forEach { o -> inv[o.item] = (inv[o.item] ?: 0) + o.n }
        }
        return plan
    }

    /** How many times [r] could be hand-crafted right now (including missing intermediates), capped at [cap]. */
    fun maxCraftable(r: Recipe, cap: Int = 99): Int {
        if (!canHandCraft(r)) return 0
        var lo = 0
        var hi = cap
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (planCraft(r, mid) != null) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun queueCraft(r: Recipe, times: Int): Boolean {
        if (!canHandCraft(r)) return false
        val plan = planCraft(r, times)
        if (plan == null) {
            message("Missing ingredients for ${r.name}"); sfx += "error"; return false
        }
        // Split each job's inputs into items produced earlier in this plan (taken at job start)
        // and raw items reserved from the inventory right now.
        val pool = HashMap<Item, Int>()
        val planId = ++planCounter
        for (job in plan) {
            val reserved = ArrayList<Stack>()
            val fromPool = ArrayList<Stack>()
            for (s in job.inputs) {
                val take = min(pool[s.item] ?: 0, s.n)
                if (take > 0) {
                    pool[s.item] = (pool[s.item] ?: 0) - take; fromPool += Stack(s.item, take)
                }
                if (s.n - take > 0) reserved += Stack(s.item, s.n - take)
            }
            job.outputs.forEach { pool[it.item] = (pool[it.item] ?: 0) + it.n }
            reserved.forEach { inventory.add(it.item, -it.n) }
            craftQueue += CraftJob(job, planId, reserved, fromPool)
        }
        return true
    }

    private var planCounter = 0

    private fun tickCrafting(dt: Float) {
        val job = craftQueue.firstOrNull() ?: return
        if (!job.started) {
            if (job.fromPool.any { inventory[it.item] < it.n }) {
                // the player used the intermediates elsewhere: abort this plan
                cancelPlan(job.planId); return
            }
            job.fromPool.forEach { inventory.add(it.item, -it.n) }
            job.started = true
        }
        job.progress += dt / job.recipe.time
        if (job.progress >= 1f) {
            craftQueue.removeAt(0)
            job.recipe.outputs.forEach { inventory.add(it.item, it.n); stats.produced(it.item, it.n) }
            if (sfx.size < 8) sfx += "craft"
        }
    }

    private fun cancelPlan(planId: Int) {
        val it = craftQueue.iterator()
        while (it.hasNext()) {
            val j = it.next()
            if (j.planId != planId) continue
            j.reserved.forEach { s -> inventory.add(s.item, s.n) }
            if (j.started) j.fromPool.forEach { s -> inventory.add(s.item, s.n) }
            it.remove()
        }
    }

    fun cancelCrafting() {
        for (j in craftQueue) {
            j.reserved.forEach { s -> inventory.add(s.item, s.n) }
            if (j.started) j.fromPool.forEach { s -> inventory.add(s.item, s.n) }
        }
        craftQueue.clear()
    }

    fun cancelLast() {
        val last = craftQueue.lastOrNull() ?: return
        cancelPlan(last.planId)
    }

    // ------------------------------------------------------------------------------------ research
    fun techAvailable(t: Tech) = !researched.contains(t.id) && t.prereqs.all { researched.contains(it) }
    fun progressOf(t: Tech) = techProgress[t.id] ?: 0

    /** Start [t] now. Whatever was running goes back to the front of the queue, so no progress is ever lost. */
    fun startResearch(t: Tech) {
        if (!techAvailable(t)) return
        val prev = currentTech
        researchQueue.remove(t)
        if (prev != null && prev != t) researchQueue.add(0, prev)
        currentTech = t
        message("Researching ${t.name}")
    }

    /** Stop the current research. The next queued technology starts straight away. */
    fun stopResearch() {
        currentTech = null
        advanceResearchQueue()
    }

    /** Queue [t] (and any prerequisites that aren't researched or queued yet). Starts at once if nothing is running. */
    fun queueResearch(t: Tech): Boolean {
        if (researched.contains(t.id) || t == currentTech || researchQueue.contains(t)) return false
        fun add(x: Tech) {
            if (researched.contains(x.id) || x == currentTech || researchQueue.contains(x)) return
            x.prereqs.forEach { add(Techs[it]) }
            researchQueue += x
        }
        add(t)
        if (currentTech == null) advanceResearchQueue()
        return true
    }

    /** Remove [t] from the queue together with everything queued that depends on it. */
    fun dequeueResearch(t: Tech) {
        if (!researchQueue.remove(t)) return
        var changed = true
        while (changed) {
            changed = false
            val it = researchQueue.iterator()
            while (it.hasNext()) {
                val q = it.next()
                val missing = q.prereqs.any { p -> !researched.contains(p) && currentTech?.id != p && researchQueue.none { it.id == p } }
                if (missing) {
                    it.remove(); changed = true
                }
            }
        }
    }

    private fun advanceResearchQueue() {
        if (currentTech != null) return
        val next = researchQueue.firstOrNull { techAvailable(it) } ?: return
        researchQueue.remove(next)
        currentTech = next
        message("Researching ${next.name}")
    }

    fun addResearchUnit() {
        val t = currentTech ?: return
        val p = (techProgress[t.id] ?: 0) + 1
        techProgress[t.id] = p
        if (p >= t.count) completeTech(t)
    }

    private fun completeTech(t: Tech) {
        researched += t.id
        unlocked += t.unlocks
        applyBonus(t)
        currentTech = null
        researchQueue.remove(t)
        message("Research complete: ${t.name}")
        sfx += "research"
        if (t.bonus == Bonus.VICTORY) victory = true
        advanceResearchQueue()
    }

    private fun applyBonus(t: Tech) {
        when (t.bonus) {
            Bonus.MINING -> miningMul += t.bonusAmount
            Bonus.LAB_SPEED -> labMul += t.bonusAmount
            Bonus.CARGO -> cargoMul += t.bonusAmount
            Bonus.BOOSTERS -> segmentDiscount += t.bonusAmount.toInt()
            else -> {}
        }
    }

    fun onRocketLaunched() {
        stats.launches++
        message("Rocket launched! Astral Science collected in the launcher.")
    }

    fun onCargoLaunched(load: List<Stack>) {
        stats.launches++; stats.cargoLaunches++
        load.forEach { stats.shipped[it.item] = stats.shipped(it.item) + it.n }
        val n = load.sumOf { it.n }
        message("Cargo rocket #${stats.cargoLaunches} is on its way to Earth with $n crates")
    }

    /** Humans whose lives the Vitae shipments can save (4 doses per cryo-case). */
    val livesSaved get() = stats.shipped(Items.VITAE_CRYOCASE) * 4

    fun onInserterMoved() {}

    // ------------------------------------------------------------------------------------ building
    fun canPlace(t: BType, x: Int, y: Int): String? {
        var anyOre = false
        var anyRare = false
        var anyVitae = false
        var anySap = false
        var anyVent = false
        for (dy in 0 until t.size) for (dx in 0 until t.size) {
            val tx = x + dx
            val ty = y + dy
            if (!world.inside(tx, ty)) return "Out of bounds"
            if (world.groundAt(tx, ty) == Ground.LAKE) return "Can't build on acid lakes"
            if (world.at(tx, ty) != null) return "Space occupied"
            val r = world.resAt(tx, ty)
            if (Res.isOre(r)) anyOre = true
            if (Res.isRare(r)) anyRare = true
            if (r == Res.VITAE) anyVitae = true
            if (r == Res.SAP) anySap = true
            if (r == Res.VENT) anyVent = true
        }
        return when (t.kind) {
            BKind.DRILL -> if (!anyOre) (if (anyRare) "Too dense - needs a Resonance Drill" else "Drills must be placed on ore") else null
            BKind.RARE_DRILL -> if (!anyRare) "Must sit on Solanite or Terravite" else null
            BKind.BIO_PUMP -> if (!anyVitae) "Must sit on a Vitae spring" else null
            BKind.EXTRACTOR -> if (!anySap) "Must be placed on a Glowsap pool" else null
            BKind.GEOTHERMAL -> if (!anyVent) "Must be placed on a geothermal vent" else null
            else -> null
        }
    }

    fun place(t: BType, x: Int, y: Int, dir: Int, free: Boolean = false): Building? {
        if (canPlace(t, x, y) != null) return null
        val item = Items[t.id]
        if (!free) {
            if (inventory[item] <= 0) return null
            inventory.add(item, -1)
        }
        val b = createBuilding(t, x, y, if (t.rotatable) dir else 0)
        for (dy in 0 until t.size) for (dx in 0 until t.size) {
            val i = world.idx(x + dx, y + dy)
            world.bld[i] = b
            world.flora[i] = Flora.NONE.toByte()
        }
        if (b is Generator && t.kind == BKind.GEOTHERMAL) {
            b.onVent = (0 until t.size).any { dy -> (0 until t.size).any { dx -> world.resAt(x + dx, y + dy) == Res.VENT } }
        }
        buildings += b
        if (!free) stats.built[t.id] = (stats.built[t.id] ?: 0) + 1
        if (b is Tunnel && !loading) linkTunnel(b)
        refreshAround(b)
        return b
    }

    fun remove(b: Building): Boolean {
        if (b.type.kind == BKind.CORE) {
            message("The Lander Core can't be removed"); return false
        }
        for (dy in 0 until b.size) for (dx in 0 until b.size) world.bld[world.idx(b.x + dx, b.y + dy)] = null
        buildings.remove(b)
        alerts.remove(b)
        if (b is Tunnel) {
            b.partner?.let { it.partner = null; refreshAround(it) }
            b.partner = null
        }
        inventory.add(Items[b.type.id], 1)
        b.contents().forEach { inventory.add(it.item, it.n) }
        refreshAround(b)
        return true
    }

    fun rotate(b: Building) {
        if (!b.type.rotatable) return
        if (b is Tunnel) {
            rotateTunnel(b); return
        }
        b.dir = (b.dir + 1) % 4
        refreshAround(b)
    }

    fun setDir(b: Building, d: Int) {
        if (!b.type.rotatable || b.dir == d || b is Tunnel) return
        b.dir = d; refreshAround(b)
    }

    fun refreshAround(b: Building) {
        for (dy in -1..b.size) for (dx in -1..b.size) world.at(b.x + dx, b.y + dy)?.neighboursChanged(this)
    }

    // ------------------------------------------------------------------------------------ tunnels
    /**
     * Who a tunnel piece at (x, y) facing [dir] would pair with: an unpaired entrance behind it (the new piece
     * becomes its exit, second = true) or an unpaired exit ahead of it (the new piece becomes its entrance).
     * The first same-facing tunnel in each direction blocks the search, so tunnels never cross-link.
     */
    fun tunnelPartnerFor(x: Int, y: Int, dir: Int, self: Tunnel? = null): Pair<Tunnel, Boolean>? {
        for (k in 1..Tunnels.SPAN + 1) {
            val o = world.at(x - DX[dir] * k, y - DY[dir] * k)
            if (o is Tunnel && o !== self && o.dir == dir) {
                if (o.entry && o.partner == null) return o to true
                break
            }
        }
        for (k in 1..Tunnels.SPAN + 1) {
            val o = world.at(x + DX[dir] * k, y + DY[dir] * k)
            if (o is Tunnel && o !== self && o.dir == dir) {
                if (!o.entry && o.partner == null) return o to false
                break
            }
        }
        return null
    }

    fun linkTunnel(t: Tunnel) {
        t.partner?.let { it.partner = null; refreshAround(it) }
        t.partner = null
        val found = tunnelPartnerFor(t.x, t.y, t.dir, t)
        when {
            found == null -> t.entry = true
            found.second -> pairTunnels(found.first, t)
            else -> pairTunnels(t, found.first)
        }
    }

    private fun pairTunnels(entry: Tunnel, exit: Tunnel) {
        entry.entry = true; exit.entry = false
        entry.partner = exit; exit.partner = entry
        refreshAround(entry); refreshAround(exit)
    }

    private fun flipItems(t: Tunnel) {
        t.items.forEach { it.pos = 1f - it.pos }
        t.items.reverse()
    }

    private fun rotateTunnel(t: Tunnel) {
        val p = t.partner
        if (p != null) {
            // reverse the whole tunnel: the exit becomes the entrance and vice versa
            for (e in listOf(t, p)) {
                e.dir = (e.dir + 2) % 4; e.entry = !e.entry; flipItems(e); refreshAround(e)
            }
        } else {
            t.dir = (t.dir + 1) % 4
            linkTunnel(t)
            refreshAround(t)
        }
    }

    /** After loading: re-pair tunnels from their saved roles. */
    private fun relinkTunnels() {
        for (b in buildings) if (b is Tunnel && b.entry && b.partner == null) {
            for (k in 1..Tunnels.SPAN + 1) {
                val o = world.at(b.x + DX[b.dir] * k, b.y + DY[b.dir] * k)
                if (o is Tunnel && o.dir == b.dir) {
                    if (!o.entry && o.partner == null) {
                        b.partner = o; o.partner = b
                    }
                    break
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------ deposits (objective pointer)
    private var deposits: HashMap<Int, FloatArray>? = null
    private var depositsAt = -1000f

    /** Centre of the nearest deposit of any of [types], as (x, y) in tiles, or null if there is none left. */
    fun nearestDeposit(types: IntArray, fromX: Float, fromY: Float): FloatArray? {
        if (types.isEmpty()) return null
        if (deposits == null || time - depositsAt > 30f) {
            deposits = indexDeposits(); depositsAt = time
        }
        val d = deposits ?: return null
        var best: FloatArray? = null
        var bd = Float.MAX_VALUE
        for (t in types) {
            val pts = d[t] ?: continue
            var k = 0
            while (k < pts.size) {
                val dx = pts[k] - fromX
                val dy = pts[k + 1] - fromY
                val dd = dx * dx + dy * dy
                if (dd < bd) {
                    bd = dd; best = floatArrayOf(pts[k], pts[k + 1])
                }
                k += 2
            }
        }
        return best
    }

    /** One point per 8x8 cell that contains a resource: the centroid of its tiles. */
    private fun indexDeposits(): HashMap<Int, FloatArray> {
        val c = 8
        val cw = (world.w + c - 1) / c
        val ch = (world.h + c - 1) / c
        val types = 12
        val sx = FloatArray(types * cw * ch)
        val sy = FloatArray(types * cw * ch)
        val n = IntArray(types * cw * ch)
        for (y in 0 until world.h) for (x in 0 until world.w) {
            val r = world.res[world.idx(x, y)].toInt()
            if (r <= 0 || r >= types) continue
            val k = (r * ch + y / c) * cw + x / c
            sx[k] += x + .5f; sy[k] += y + .5f; n[k]++
        }
        val out = HashMap<Int, FloatArray>()
        for (r in 1 until types) {
            val pts = ArrayList<Float>()
            for (cell in 0 until cw * ch) {
                val k = r * cw * ch + cell
                if (n[k] > 0) {
                    pts += sx[k] / n[k]; pts += sy[k] / n[k]
                }
            }
            if (pts.isNotEmpty()) out[r] = pts.toFloatArray()
        }
        return out
    }

    /** Hand-mine one unit of the resource at a tile. */
    fun handMine(tx: Int, ty: Int): Item? {
        val r = world.resAt(tx, ty)
        if (Res.isRare(r)) {
            floats += FloatText("Too dense - needs a Resonance Drill", tx + .5f, ty + .2f, 1.6f, 0xFFFFC46B.toInt()); sfx += "error"; return null
        }
        if (!Res.isOre(r)) return null
        val i = world.idx(tx, ty)
        val item = Res.item(r) ?: return null
        world.amount[i]--
        if (world.amount[i] <= 0) {
            world.res[i] = 0; world.amount[i] = 0
        }
        inventory.add(item, 1)
        stats.handMined++
        stats.produced(item, 1)
        floats += FloatText("+1 ${item.name}", tx + .5f, ty + .2f)
        sfx += "mine"
        return item
    }

    fun message(s: String) {
        messages += s
    }

    // ------------------------------------------------------------------------------------ save / load
    /** Writes the save synchronously (used by tests and when the app is closing). */
    fun save(file: File) {
        SaveIO.write(file, snapshot())
    }

    /** Serialises the whole game into memory. Cheap enough to run on the UI thread; compression happens in [SaveIO]. */
    fun snapshot(): ByteArray {
        val bytes = ByteArrayOutputStream(1 shl 21)
        val o = DataOutputStream(bytes)
        o.writeInt(SAVE_VERSION)
        o.writeLong(world.seed)
        o.writeFloat(time)
        o.write(world.res)
        val ab = ByteBuffer.allocate(world.amount.size * 4)
        ab.asIntBuffer().put(world.amount)
        o.write(ab.array())
        o.write(world.flora)
        inventory.write(o)
        o.writeShort(unlocked.size); unlocked.forEach { o.writeUTF(it) }
        o.writeShort(researched.size); researched.forEach { o.writeUTF(it) }
        o.writeUTF(currentTech?.id ?: "")
        o.writeShort(techProgress.size); techProgress.forEach { (k, v) -> o.writeUTF(k); o.writeInt(v) }
        o.writeShort(stats.made.size); stats.made.forEach { (k, v) -> o.writeUTF(k.id); o.writeLong(v) }
        o.writeShort(stats.built.size); stats.built.forEach { (k, v) -> o.writeUTF(k); o.writeInt(v) }
        o.writeInt(stats.handMined); o.writeInt(stats.launches)
        o.writeInt(missionIndex); o.writeBoolean(victory); o.writeBoolean(victoryShown)
        o.writeInt(stats.cargoLaunches)
        o.writeShort(stats.shipped.size); stats.shipped.forEach { (k, v) -> o.writeUTF(k.id); o.writeLong(v) }
        // ---- v5
        o.writeFloat(playTime)
        o.writeShort(hotbar.size); hotbar.forEach { o.writeUTF(it) }
        o.writeShort(researchQueue.size); researchQueue.forEach { o.writeUTF(it.id) }
        o.writeInt(planCounter)
        o.writeInt(craftQueue.size)
        fun stacks(l: List<Stack>) {
            o.writeShort(l.size); l.forEach { o.writeUTF(it.item.id); o.writeInt(it.n) }
        }
        for (j in craftQueue) {
            o.writeUTF(j.recipe.id); o.writeInt(j.planId); o.writeBoolean(j.started); o.writeFloat(j.progress)
            stacks(j.reserved); stacks(j.fromPool)
        }
        // each building's state is length-prefixed so unknown or damaged entries can be skipped
        o.writeInt(buildings.size)
        val block = ByteArrayOutputStream(256)
        val bo = DataOutputStream(block)
        for (b in buildings) {
            o.writeUTF(b.type.id); o.writeShort(b.x); o.writeShort(b.y); o.writeByte(b.dir)
            block.reset(); b.save(bo); bo.flush()
            o.writeInt(block.size()); block.writeTo(o)
        }
        o.flush()
        return bytes.toByteArray()
    }

    /** Old builds emptied the craft queue before saving. The queue is saved now, so this is only kept for callers that want it. */
    fun flushQueueForSave() {
        if (craftQueue.isNotEmpty()) cancelCrafting()
    }

    private fun readState(i: DataInputStream, ver: Int) {
        loading = true
        try {
            time = i.readFloat()
            i.readFully(world.res)
            val ab = ByteArray(world.amount.size * 4)
            i.readFully(ab)
            ByteBuffer.wrap(ab).asIntBuffer().get(world.amount)
            i.readFully(world.flora)
            inventory.read(i)
            repeat(i.readShort().toInt()) { unlocked += i.readUTF() }
            repeat(i.readShort().toInt()) { researched += i.readUTF() }
            currentTech = Techs.byId[i.readUTF()]
            repeat(i.readShort().toInt()) { techProgress[i.readUTF()] = i.readInt() }
            repeat(i.readShort().toInt()) {
                val id = i.readUTF()
                val v = i.readLong(); Items.byId[id]?.let { stats.made[it] = v }
            }
            repeat(i.readShort().toInt()) { stats.built[i.readUTF()] = i.readInt() }
            stats.handMined = i.readInt(); stats.launches = i.readInt()
            missionIndex = i.readInt(); victory = i.readBoolean(); victoryShown = i.readBoolean()
            stats.cargoLaunches = i.readInt()
            repeat(i.readShort().toInt()) {
                val id = i.readUTF(); val v = i.readLong(); Items.byId[id]?.let { stats.shipped[it] = v }
            }
            researched.forEach { Techs.byId[it]?.let { t -> applyBonus(t) } }
            // techs added in later versions unlock their recipes if the save already researched them
            researched.forEach { Techs.byId[it]?.let { t -> unlocked += t.unlocks } }
            if (ver >= 5) {
                playTime = i.readFloat()
                hotbar.clear()
                repeat(i.readShort().toInt()) { val id = i.readUTF(); if (Buildings.byId.containsKey(id)) hotbar += id }
                if (hotbar.isEmpty()) hotbar += DEFAULT_HOTBAR
                repeat(i.readShort().toInt()) { Techs.byId[i.readUTF()]?.let { t -> if (!researched.contains(t.id)) researchQueue += t } }
                planCounter = i.readInt()
                fun stacks(): List<Stack> {
                    val l = ArrayList<Stack>()
                    repeat(i.readShort().toInt()) {
                        val id = i.readUTF(); val n = i.readInt()
                        Items.byId[id]?.let { l += Stack(it, n) }
                    }
                    return l
                }
                repeat(i.readInt()) {
                    val r = Recipes.byId[i.readUTF()]
                    val planId = i.readInt(); val started = i.readBoolean(); val progress = i.readFloat()
                    val reserved = stacks(); val fromPool = stacks()
                    if (r != null) craftQueue += CraftJob(r, planId, reserved, fromPool, started, progress)
                    else {
                        reserved.forEach { s -> inventory.add(s.item, s.n) }
                        if (started) fromPool.forEach { s -> inventory.add(s.item, s.n) }
                    }
                }
                repeat(i.readInt()) {
                    val id = i.readUTF()
                    val x = i.readShort().toInt()
                    val y = i.readShort().toInt()
                    val d = i.readByte().toInt()
                    val data = ByteArray(i.readInt())
                    i.readFully(data)
                    val t = Buildings.byId[id] ?: return@repeat
                    val b = place(t, x, y, d, free = true) ?: return@repeat
                    try {
                        b.load(DataInputStream(ByteArrayInputStream(data)))
                    } catch (e: Exception) {
                        e.printStackTrace() // keep the building, lose only its contents
                    }
                }
            } else {
                val n = i.readInt()
                repeat(n) {
                    val t = Buildings[i.readUTF()]
                    val x = i.readShort().toInt()
                    val y = i.readShort().toInt()
                    val d = i.readByte().toInt()
                    val b = place(t, x, y, d, free = true)
                    b?.load(i) ?: createBuilding(t, x, y, d).load(i)
                }
            }
            relinkTunnels()
            buildings.forEach { it.neighboursChanged(this) }
        } finally {
            loading = false
        }
    }
}
