package com.factonic.game.sim

import com.factonic.game.data.BKind
import com.factonic.game.data.BType
import com.factonic.game.data.Cat
import com.factonic.game.data.Item
import com.factonic.game.data.Items
import com.factonic.game.data.Recipe
import com.factonic.game.data.Recipes
import com.factonic.game.data.Stack
import com.factonic.game.data.Tunnels
import com.factonic.game.world.Res
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val DX = intArrayOf(0, 1, 0, -1)
val DY = intArrayOf(-1, 0, 1, 0)

/** Small item-count bag keyed by item index. */
class Bag {
    val map = LinkedHashMap<Item, Int>()
    operator fun get(i: Item) = map[i] ?: 0
    fun add(i: Item, n: Int) {
        val v = (map[i] ?: 0) + n
        if (v <= 0) map.remove(i) else map[i] = v
    }

    fun total() = map.values.sum()
    fun isEmpty() = map.isEmpty()
    fun stacks() = map.entries.map { Stack(it.key, it.value) }
    fun clear() = map.clear()
    fun write(o: DataOutputStream) {
        o.writeShort(map.size); for ((k, v) in map) {
            o.writeUTF(k.id); o.writeInt(v)
        }
    }

    fun read(i: DataInputStream) {
        map.clear(); repeat(i.readShort().toInt()) {
            val id = i.readUTF();
            val n = i.readInt(); Items.byId[id]?.let { map[it] = n }
        }
    }
}

abstract class Building(val type: BType, val x: Int, val y: Int, var dir: Int) {
    /** kW requested during the last tick. */
    var demand = 0f
    var working = false
    val size get() = type.size

    open fun tick(g: Game, dt: Float) {}

    /** Can [item] be inserted by an inserter / drill / belt? */
    open fun canAccept(item: Item): Boolean = false
    open fun accept(item: Item): Boolean = false

    /** Offer an item for an inserter that is feeding [target]. */
    open fun offer(target: Building?, filter: (Item) -> Boolean): Item? = null
    open fun take(item: Item): Boolean = false

    /** Items returned to the player when this building is removed. */
    open fun contents(): List<Stack> = emptyList()
    open fun status(g: Game): String = if (working) "Working" else "Idle"

    /** Something the player should fix (shown as an alert), or null. */
    open fun problem(g: Game): String? = null
    /** Seconds the current [problem] has persisted (maintained by the alert scan, not saved). */
    var problemSecs = 0

    open fun save(o: DataOutputStream) {}
    open fun load(i: DataInputStream) {}

    /** Called when neighbours change (belts recompute curves). */
    open fun neighboursChanged(g: Game) {}

    fun covers(tx: Int, ty: Int) = tx >= x && ty >= y && tx < x + size && ty < y + size
    val centerX get() = x + size / 2f
    val centerY get() = y + size / 2f
}

// ----------------------------------------------------------------------------------- belts
class BeltItem(var item: Item, var pos: Float)

open class Belt(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    val items = ArrayList<BeltItem>(4)

    /** 0 straight, -1 curving in from the left, +1 from the right. */
    var curve = 0

    companion object {
        const val GAP = 0.25f
    }

    /** Does this belt drop items onto the tile in front of it? (A tunnel entrance sends them underground instead.) */
    open val outputsToWorld: Boolean get() = true

    /** Where items leave this belt. */
    protected open fun target(g: Game): Building? = g.world.at(x + DX[dir], y + DY[dir])

    override fun tick(g: Game, dt: Float) {
        if (items.isEmpty()) return
        val step = type.speed * dt
        var limit = 1f
        var i = items.size - 1
        while (i >= 0) {
            val it = items[i]
            if (it.pos < limit) it.pos = min(it.pos + step, limit)
            if (i == items.size - 1 && it.pos >= 1f && pass(g, it.item)) {
                items.removeAt(i); i--; continue
            }
            limit = it.pos - GAP
            i--
        }
    }

    private fun pass(g: Game, item: Item): Boolean = when (val next = target(g)) {
        is Belt -> next.receiveMoving(item, dir, this)
        is Splitter -> next.receive(item)
        else -> false
    }

    private fun canPlace(p: Float): Boolean {
        for (it in items) if (abs(it.pos - p) < GAP - 0.001f) return false
        return true
    }

    private fun insertAt(item: Item, p: Float) {
        var k = 0
        while (k < items.size && items[k].pos < p) k++
        items.add(k, BeltItem(item, p))
    }

    /**
     * An item travelling in world direction [moveDir] arrives from a neighbour ([src]).
     * From behind it lands at the start of the belt, from the side in the middle (side-loading).
     */
    open fun receiveMoving(item: Item, moveDir: Int, src: Building?): Boolean {
        if ((moveDir + 2) % 4 == dir) return false // head-on
        val p = if (moveDir == dir || curve != 0) 0f else 0.5f
        if (!canPlace(p)) return false
        insertAt(item, p); return true
    }

    /** Kept for compatibility with older callers. */
    fun receiveFromBelt(item: Item, src: Belt): Boolean = receiveMoving(item, src.dir, src)

    override fun canAccept(item: Item) = canPlace(0.5f) || canPlace(0.25f)
    override fun accept(item: Item): Boolean {
        for (p in floatArrayOf(0.5f, 0.25f, 0.75f, 0f)) if (canPlace(p)) {
            insertAt(item, p); return true
        }
        return false
    }

    override fun offer(target: Building?, filter: (Item) -> Boolean): Item? {
        for (k in items.indices.reversed()) if (filter(items[k].item)) return items[k].item
        return null
    }

    override fun take(item: Item): Boolean {
        for (k in items.indices.reversed()) if (items[k].item == item) {
            items.removeAt(k); return true
        }
        return false
    }

    override fun contents() = items.map { Stack(it.item, 1) }
    override fun status(g: Game) = "${items.size} item(s) on belt"

    override fun neighboursChanged(g: Game) {
        fun feeds(dx: Int, dy: Int, need: Int): Boolean {
            val b = g.world.at(x + dx, y + dy)
            return b is Belt && b.dir == need && b.outputsToWorld
        }
        // local -> world: back = opposite of dir; left = dir-1; right = dir+1
        val back = (dir + 2) % 4
        val left = (dir + 3) % 4
        val right = (dir + 1) % 4
        val fromBack = feeds(DX[back], DY[back], dir)
        val fromLeft = feeds(DX[left], DY[left], right)
        val fromRight = feeds(DX[right], DY[right], left)
        curve = if (!fromBack && fromLeft && !fromRight) -1 else if (!fromBack && fromRight && !fromLeft) 1 else 0
    }

    override fun save(o: DataOutputStream) {
        o.writeByte(items.size); items.forEach { o.writeUTF(it.item.id); o.writeFloat(it.pos) }
    }

    override fun load(i: DataInputStream) {
        items.clear(); repeat(i.readByte().toInt()) {
            val id = i.readUTF();
            val p = i.readFloat(); Items.byId[id]?.let { items += BeltItem(it, p) }
        }
    }
}

// ----------------------------------------------------------------------------------- tunnel belts
/**
 * One end of an underground belt. An entrance carries items to the hood in its front half and hands them
 * to its paired exit, up to [Tunnels.SPAN] tiles further along; the exit puts them back on the surface.
 * Pairing is done by [Game.linkTunnel].
 */
class Tunnel(type: BType, x: Int, y: Int, dir: Int) : Belt(type, x, y, dir) {
    var entry = true
    var partner: Tunnel? = null

    override val outputsToWorld: Boolean get() = !entry
    override fun target(g: Game): Building? = if (entry) partner else super.target(g)

    override fun receiveMoving(item: Item, moveDir: Int, src: Building?): Boolean {
        // an exit only takes items from its own entrance; nothing can be fed into its hooded back
        if (!entry && src !== partner) return false
        return super.receiveMoving(item, moveDir, src)
    }

    override fun neighboursChanged(g: Game) { curve = 0 }

    override fun status(g: Game) = when {
        partner == null -> if (entry) "Entrance - place an exit up to ${Tunnels.SPAN + 1} tiles ahead" else "Exit - no entrance"
        entry -> "Entrance - ${items.size} item(s)"
        else -> "Exit - ${items.size} item(s)"
    }

    override fun problem(g: Game): String? = if (partner == null) (if (entry) "Tunnel has no exit" else "Tunnel has no entrance") else null

    override fun save(o: DataOutputStream) {
        super.save(o); o.writeBoolean(entry)
    }

    override fun load(i: DataInputStream) {
        super.load(i); entry = i.readBoolean()
    }
}

// ----------------------------------------------------------------------------------- splitter
/**
 * A 1x1 belt hub. Belts pointing into it are inputs; every other neighbouring belt is an output, served in
 * turn. With a [filter] set, matching items only leave through the front and everything else only through
 * the other sides, which turns it into a sorter.
 */
class Splitter(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    val buffer = ArrayList<Item>(2)
    var filter: Item? = null
    private var next = 0
    /** Visual: time since the last item went through, and the side it left by. */
    var flash = 1f
    var lastSide = -1

    fun receive(item: Item): Boolean {
        if (buffer.size >= 2) return false
        buffer += item; return true
    }

    override fun tick(g: Game, dt: Float) {
        flash += dt
        working = flash < .5f
        if (buffer.isEmpty()) return
        val item = buffer[0]
        val f = filter
        for (k in 0 until 4) {
            val side = (next + k) % 4
            if (f != null && (item == f) != (side == dir)) continue
            val nb = g.world.at(x + DX[side], y + DY[side]) as? Belt ?: continue
            if (nb.receiveMoving(item, side, this)) {
                buffer.removeAt(0); next = (side + 1) % 4; flash = 0f; lastSide = side
                return
            }
        }
    }

    override fun canAccept(item: Item) = buffer.size < 2
    override fun accept(item: Item) = receive(item)
    override fun contents() = buffer.map { Stack(it, 1) }
    override fun status(g: Game): String {
        val f = filter
        return when {
            buffer.size >= 2 && flash > 1f -> "Blocked - no free output"
            f != null -> "Sorting ${f.name} to the front"
            else -> if (working) "Splitting" else "Waiting for items"
        }
    }

    override fun save(o: DataOutputStream) {
        o.writeUTF(filter?.id ?: ""); o.writeByte(buffer.size); buffer.forEach { o.writeUTF(it.id) }
    }

    override fun load(i: DataInputStream) {
        filter = Items.byId[i.readUTF()]
        buffer.clear(); repeat(i.readByte().toInt()) { Items.byId[i.readUTF()]?.let { buffer += it } }
    }
}

// ----------------------------------------------------------------------------------- inserter
class Inserter(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    var held: Item? = null

    /** 0 = hand over source, 1 = hand over target. */
    var arm = 0f
    private var cooldown = 0f

    override fun tick(g: Game, dt: Float) {
        val speed = type.speed * g.powerSat
        val src = g.world.at(x - DX[dir], y - DY[dir])
        val dst = g.world.at(x + DX[dir], y + DY[dir])
        demand = 0f
        working = false
        val h = held
        if (h == null) {
            if (arm > 0f) {
                arm = max(0f, arm - dt * speed); demand = type.power; working = true; return
            }
            cooldown -= dt
            if (cooldown > 0) return
            cooldown = 0.08f
            if (src == null || dst == null || src === dst) return
            val item = src.offer(dst) { dst.canAccept(it) } ?: return
            if (src.take(item)) held = item
        } else {
            if (arm < 1f) {
                arm = min(1f, arm + dt * speed); demand = type.power; working = true; return
            }
            if (dst != null && dst.accept(h)) {
                held = null; g.onInserterMoved()
            }
        }
    }

    override fun contents() = held?.let { listOf(Stack(it, 1)) } ?: emptyList()
    override fun status(g: Game) = if (held != null) "Carrying ${held!!.name}" else "Waiting for items"
    override fun save(o: DataOutputStream) {
        o.writeUTF(held?.id ?: ""); o.writeFloat(arm)
    }

    override fun load(i: DataInputStream) {
        held = Items.byId[i.readUTF()]; arm = i.readFloat()
    }
}

// ----------------------------------------------------------------------------------- output helper
/** The k-th tile (0 until size) along the output side of a building. */
private fun outX(b: Building, k: Int) = when (b.dir) {
    0, 2 -> b.x + k; 1 -> b.x + b.size; else -> b.x - 1
}

private fun outY(b: Building, k: Int) = when (b.dir) {
    0 -> b.y - 1; 2 -> b.y + b.size; else -> b.y + k
}

// ----------------------------------------------------------------------------------- drill / extractor
class Drill(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    var progress = 0f
    var out: Item? = null
    var anim = 0f
    private var cursor = 0
    private var outCursor = 0
    var depleted = false
    /** Infinite liquid sources (Glowsap pools, Vitae springs). */
    val sap get() = type.kind == BKind.EXTRACTOR || type.kind == BKind.BIO_PUMP
    fun mines(r: Int) = when (type.kind) {
        BKind.EXTRACTOR -> r == Res.SAP
        BKind.BIO_PUMP -> r == Res.VITAE
        BKind.RARE_DRILL -> Res.isRare(r)
        else -> Res.isOre(r)
    }

    override fun tick(g: Game, dt: Float) {
        demand = 0f; working = false
        out?.let { item ->
            val n = size
            for (k in 0 until n) {
                val c = (outCursor + k) % n
                val t = g.world.at(outX(this, c), outY(this, c)) ?: continue
                if (t !== this && t.accept(item)) {
                    out = null; outCursor = (c + 1) % n; break
                }
            }
            if (out != null) return
        }
        // find a minable tile
        var found = -1
        for (k in 0 until size * size) {
            val c = (cursor + k) % (size * size)
            val tx = x + c % size;
            val ty = y + c / size
            val r = g.world.resAt(tx, ty)
            if (mines(r)) {
                found = c; break
            }
        }
        if (found < 0) {
            depleted = true; return
        }
        depleted = false
        demand = type.power
        working = true
        val sp = type.speed * g.powerSat * (if (sap) 1f else g.miningMul)
        anim += dt * sp * 6f
        progress += dt * sp
        if (progress >= 1f) {
            progress -= 1f
            val tx = x + found % size;
            val ty = y + found / size
            val i = g.world.idx(tx, ty)
            val r = g.world.res[i].toInt()
            out = Res.item(r)
            if (!sap) {
                g.world.amount[i]--
                if (g.world.amount[i] <= 0) {
                    g.world.res[i] = 0; g.world.amount[i] = 0
                }
                cursor = (found + 1) % (size * size)
            }
            out?.let { g.stats.produced(it, 1) }
        }
    }

    override fun offer(target: Building?, filter: (Item) -> Boolean) = out?.takeIf(filter)
    override fun take(item: Item): Boolean {
        if (out == item) {
            out = null; return true
        }; return false
    }

    override fun contents() = out?.let { listOf(Stack(it, 1)) } ?: emptyList()
    override fun problem(g: Game): String? = if (depleted) (if (sap) "Nothing to pump" else "Ore depleted") else null
    override fun status(g: Game) = when {
        depleted -> when (type.kind) {
            BKind.EXTRACTOR -> "No sap pool underneath"
            BKind.BIO_PUMP -> "No Vitae spring underneath"
            BKind.RARE_DRILL -> "No Solanite or Terravite underneath"
            else -> "No ore underneath"
        }
        out != null -> "Output blocked"
        g.powerSat < 0.5f -> "Low power"
        else -> "Mining"
    }

    override fun save(o: DataOutputStream) {
        o.writeFloat(progress); o.writeUTF(out?.id ?: "")
    }

    override fun load(i: DataInputStream) {
        progress = i.readFloat(); out = Items.byId[i.readUTF()]
    }
}

// ----------------------------------------------------------------------------------- crafter
class Crafter(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    var recipe: Recipe? = null
    val input = Bag()
    val output = Bag()
    var progress = 0f
    var crafting = false
    var anim = 0f
    var launches = 0
    val cat: Cat get() = type.cat!!

    // ---- Orbital Launcher only
    /** false = satellite rockets (Astral Science), true = cargo rockets to Earth. */
    var cargoMode = false
    val cargo = Bag()
    val flight = Bag()
    var launchNow = false
    val isLauncher get() = type.cat == Cat.LAUNCH

    init {
        if (type.cat == Cat.LAUNCH) recipe = Recipes["sci_astral"]
    }

    fun setRecipe(g: Game, r: Recipe?) {
        if (r == recipe) return
        // refund inputs & partial craft
        for (s in input.stacks()) g.inventory.add(s.item, s.n)
        for (s in output.stacks()) g.inventory.add(s.item, s.n)
        if (crafting) recipe?.inputs?.forEach { g.inventory.add(it.item, it.n) }
        input.clear(); output.clear(); crafting = false; progress = 0f
        recipe = r
    }

    private fun outputFull(r: Recipe) = r.outputs.any { output[it.item] >= maxOf(it.n * 2, if (cat == Cat.LAUNCH) 200 else 20) }

    fun setCargoMode(g: Game, on: Boolean) {
        if (on == cargoMode || crafting) return
        input[Items.SATELLITE].let { if (it > 0) { g.inventory.add(Items.SATELLITE, it); input.add(Items.SATELLITE, -it) } }
        for (s in cargo.stacks()) g.inventory.add(s.item, s.n)
        cargo.clear(); launchNow = false
        cargoMode = on
    }

    /** Is the rocket on the pad fuelled and loaded so it could lift off now? */
    fun readyToLaunch(g: Game) = !crafting && input[Items.ROCKET_SEGMENT] >= g.rocketSegments &&
        (if (cargoMode) cargo.total() > 0 else input[Items.SATELLITE] >= 1 && output[Items.SCI_A] < 200)

    private fun tickLauncher(g: Game, dt: Float) {
        if (!crafting) {
            val full = cargo.total() >= g.cargoCapacity
            if (readyToLaunch(g) && (!cargoMode || full || launchNow)) {
                input.add(Items.ROCKET_SEGMENT, -g.rocketSegments)
                if (cargoMode) {
                    for (s in cargo.stacks()) flight.add(s.item, s.n)
                    cargo.clear()
                } else input.add(Items.SATELLITE, -1)
                launchNow = false
                crafting = true; progress = 0f
                g.sfx += "launch"
            } else return
        }
        demand = type.power
        working = true
        val sp = type.speed * g.powerSat
        anim += dt * sp
        progress += dt * sp / 40f
        if (progress >= 1f) {
            progress = 0f; crafting = false; launches++
            if (flight.isEmpty()) {
                output.add(Items.SCI_A, 100); g.stats.produced(Items.SCI_A, 100); g.onRocketLaunched()
            } else {
                g.onCargoLaunched(flight.stacks()); flight.clear()
            }
        }
    }

    override fun tick(g: Game, dt: Float) {
        demand = 0f; working = false
        if (isLauncher) { tickLauncher(g, dt); return }
        if (recipe == null && cat.auto) {
            val first = input.map.keys.firstOrNull()
            if (first != null) recipe = Recipes.autoFor(cat, first)
        }
        val r = recipe ?: return
        if (!crafting) {
            if (outputFull(r)) return
            if (r.inputs.all { input[it.item] >= it.n }) {
                r.inputs.forEach { input.add(it.item, -it.n) }
                crafting = true
            } else {
                // smelters switch recipe when they run dry and something else arrives
                if (cat.auto && input.isEmpty()) recipe = null
                return
            }
        }
        demand = type.power
        working = true
        val sp = type.speed * g.powerSat
        anim += dt * sp
        progress += dt * sp / r.time
        if (progress >= 1f) {
            progress = 0f; crafting = false
            r.outputs.forEach { output.add(it.item, it.n); g.stats.produced(it.item, it.n) }
        }
    }

    fun inputCap(s: Stack) = if (cat == Cat.LAUNCH) s.n * 2 else maxOf(s.n * 3, 6)

    override fun canAccept(item: Item): Boolean {
        if (isLauncher) {
            val g = Game.current ?: return false
            return when {
                item == Items.ROCKET_SEGMENT -> input[item] < g.rocketSegments * 2
                item == Items.SATELLITE -> !cargoMode && input[item] < 2
                item.isExport -> cargoMode && cargo.total() < g.cargoCapacity
                else -> false
            }
        }
        val r = recipe
        if (r == null) return cat.auto && input.isEmpty() && Recipes.autoFor(cat, item) != null && g_unlocked(item)
        val s = r.inputs.firstOrNull { it.item == item } ?: return false
        return input[item] < inputCap(s)
    }

    private fun g_unlocked(item: Item) = Game.current?.let { g -> Recipes.autoFor(cat, item)?.let { g.unlocked.contains(it.id) } } ?: true

    override fun accept(item: Item): Boolean {
        if (!canAccept(item)) return false
        if (recipe == null && cat.auto) recipe = Recipes.autoFor(cat, item)
        if (isLauncher && item.isExport) cargo.add(item, 1) else input.add(item, 1)
        return true
    }

    override fun offer(target: Building?, filter: (Item) -> Boolean): Item? =
        output.map.keys.firstOrNull { filter(it) }

    override fun take(item: Item): Boolean {
        if (output[item] > 0) {
            output.add(item, -1); return true
        }; return false
    }

    override fun contents(): List<Stack> {
        val l = ArrayList<Stack>()
        l += input.stacks(); l += output.stacks()
        if (isLauncher) {
            l += cargo.stacks()
            if (crafting) { l += Stack(Items.ROCKET_SEGMENT, Game.current?.rocketSegments ?: 10); if (flight.isEmpty()) l += Stack(Items.SATELLITE, 1) else l += flight.stacks() }
        } else if (crafting) recipe?.inputs?.let { l += it }
        return l
    }

    override fun problem(g: Game): String? = if (!isLauncher && !cat.auto && recipe == null) "No recipe set" else null

    override fun status(g: Game): String {
        if (isLauncher) return when {
            crafting && g.powerSat < .5f -> "Low power"
            crafting -> if (flight.isEmpty()) "Launching satellite" else "Cargo rocket lifting off"
            input[Items.ROCKET_SEGMENT] < g.rocketSegments -> "Needs ${g.rocketSegments} Rocket Segments"
            cargoMode && cargo.isEmpty() -> "Waiting for export cargo"
            cargoMode -> "Loading cargo ${cargo.total()}/${g.cargoCapacity}"
            input[Items.SATELLITE] < 1 -> "Needs a Beacon Satellite"
            else -> "Output full"
        }
        val r = recipe ?: return if (cat.auto) "Waiting for ore" else "No recipe selected"
        return when {
            crafting && g.powerSat < .5f -> "Low power"
            crafting -> "Crafting ${r.name}"
            outputFull(r) -> "Output full"
            else -> "Missing ingredients"
        }
    }

    override fun save(o: DataOutputStream) {
        o.writeUTF(recipe?.id ?: ""); input.write(o); output.write(o); o.writeFloat(progress); o.writeBoolean(crafting); o.writeInt(launches)
        o.writeBoolean(cargoMode); cargo.write(o); flight.write(o)
    }

    override fun load(i: DataInputStream) {
        recipe = Recipes.byId[i.readUTF()]; input.read(i); output.read(i); progress = i.readFloat(); crafting = i.readBoolean(); launches = i.readInt()
        cargoMode = i.readBoolean(); cargo.read(i); flight.read(i)
        if (type.cat == Cat.LAUNCH) recipe = Recipes["sci_astral"]
    }
}

// ----------------------------------------------------------------------------------- lab
class Lab(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    val packs = Bag()
    var progress = 0f
    var busy = false
    var anim = 0f

    override fun tick(g: Game, dt: Float) {
        demand = 0f; working = false
        val tech = g.currentTech ?: return
        if (!busy) {
            if (tech.packs.all { packs[it] > 0 }) {
                tech.packs.forEach { packs.add(it, -1) }; busy = true
            } else return
        }
        demand = type.power
        working = true
        val sp = g.labMul * g.powerSat
        anim += dt * sp
        progress += dt * sp / tech.unitTime
        if (progress >= 1f) {
            progress = 0f; busy = false; g.addResearchUnit()
        }
    }

    override fun canAccept(item: Item) = item.isScience && packs[item] < 10
    override fun accept(item: Item): Boolean {
        if (!canAccept(item)) return false; packs.add(item, 1); return true
    }

    override fun offer(target: Building?, filter: (Item) -> Boolean): Item? =
        if (target is Lab) packs.map.keys.firstOrNull { packs[it] > 1 && filter(it) } else null

    override fun take(item: Item): Boolean {
        if (packs[item] > 0) {
            packs.add(item, -1); return true
        }; return false
    }

    override fun contents() = packs.stacks()
    override fun status(g: Game): String {
        val t = g.currentTech ?: return "No research selected"
        return if (busy) "Researching ${t.name}" else "Needs " + t.packs.filter { packs[it] == 0 }.joinToString { it.name }
    }

    override fun save(o: DataOutputStream) {
        packs.write(o); o.writeFloat(progress); o.writeBoolean(busy)
    }

    override fun load(i: DataInputStream) {
        packs.read(i); progress = i.readFloat(); busy = i.readBoolean()
    }
}

// ----------------------------------------------------------------------------------- storage
class Chest(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    val items = Bag()
    val capacity = 800
    override fun canAccept(item: Item) = items.total() < capacity
    override fun accept(item: Item): Boolean {
        if (!canAccept(item)) return false; items.add(item, 1); return true
    }

    override fun offer(target: Building?, filter: (Item) -> Boolean) = items.map.keys.firstOrNull(filter)
    override fun take(item: Item): Boolean {
        if (items[item] > 0) {
            items.add(item, -1); return true
        }; return false
    }

    override fun contents() = items.stacks()
    override fun status(g: Game) = "${items.total()} / $capacity items"
    override fun save(o: DataOutputStream) = items.write(o)
    override fun load(i: DataInputStream) = items.read(i)
}

// ----------------------------------------------------------------------------------- power
class Generator(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    val fuel = Bag()
    var energy = 0f // kJ left in the current piece of fuel (combustion) or stored (accumulator)
    var output = 0f // kW currently produced
    var anim = 0f
    var onVent = false

    val maxOutput: Float
        get() = when (type.kind) {
            BKind.CORE -> 400f
            BKind.COMBUSTION -> 900f
            BKind.SOLAR -> 100f
            BKind.GEOTHERMAL -> 1800f
            BKind.ACCUMULATOR -> 300f
            else -> 0f
        }

    fun capacity(g: Game): Float = when (type.kind) {
        BKind.CORE -> 400f
        BKind.COMBUSTION -> if (energy > 0 || fuel[Items.CARBON] > 0) 900f else 0f
        BKind.SOLAR -> 100f * g.daylight
        BKind.GEOTHERMAL -> if (onVent) 1800f else 0f
        else -> 0f
    }

    fun burn(kj: Float) {
        var need = kj
        while (need > 0) {
            if (energy <= 0) {
                if (fuel[Items.CARBON] > 0) {
                    fuel.add(Items.CARBON, -1); energy += 4000f
                } else break
            }
            val d = min(energy, need); energy -= d; need -= d
        }
    }

    override fun canAccept(item: Item) = type.kind == BKind.COMBUSTION && item == Items.CARBON && fuel[item] < 20
    override fun accept(item: Item): Boolean {
        if (!canAccept(item)) return false; fuel.add(item, 1); return true
    }

    override fun contents() = fuel.stacks()
    override fun problem(g: Game): String? = if (type.kind == BKind.COMBUSTION && energy <= 0f && fuel[Items.CARBON] <= 0) "Out of Carbonite" else null
    override fun status(g: Game) = when (type.kind) {
        BKind.COMBUSTION -> if (capacity(g) == 0f) "Out of Carbonite" else "Output ${fmtKw(output)}"
        BKind.ACCUMULATOR -> "Charge ${(energy / 5000f * 100).toInt()}%"
        BKind.SOLAR -> if (g.daylight < .05f) "Night - no output" else "Output ${fmtKw(output)}"
        BKind.GEOTHERMAL -> if (!onVent) "Must sit on a vent" else "Output ${fmtKw(output)}"
        else -> "Output ${fmtKw(output)}"
    }

    override fun save(o: DataOutputStream) {
        fuel.write(o); o.writeFloat(energy)
    }

    override fun load(i: DataInputStream) {
        fuel.read(i); energy = i.readFloat()
    }
}

class Lamp(type: BType, x: Int, y: Int, dir: Int) : Building(type, x, y, dir) {
    var lit = 0f
    override fun tick(g: Game, dt: Float) {
        val night = g.daylight < .55f
        demand = if (night) type.power else 0f
        working = night && g.powerSat > .3f
        lit += ((if (working) 1f else 0f) - lit) * min(1f, dt * 3f)
    }
}

fun fmtKw(kw: Float): String = when {
    kw >= 1000f -> String.format("%.2f MW", kw / 1000f)
    else -> String.format("%.0f kW", kw)
}

fun createBuilding(t: BType, x: Int, y: Int, dir: Int): Building = when (t.kind) {
    BKind.BELT -> Belt(t, x, y, dir)
    BKind.TUNNEL -> Tunnel(t, x, y, dir)
    BKind.SPLITTER -> Splitter(t, x, y, dir)
    BKind.INSERTER -> Inserter(t, x, y, dir)
    BKind.DRILL, BKind.EXTRACTOR, BKind.RARE_DRILL, BKind.BIO_PUMP -> Drill(t, x, y, dir)
    BKind.CRAFTER -> Crafter(t, x, y, dir)
    BKind.LAB -> Lab(t, x, y, dir)
    BKind.CHEST -> Chest(t, x, y, dir)
    BKind.LAMP -> Lamp(t, x, y, dir)
    BKind.CORE, BKind.COMBUSTION, BKind.SOLAR, BKind.ACCUMULATOR, BKind.GEOTHERMAL -> Generator(t, x, y, dir)
}
