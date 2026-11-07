package com.factonic.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.factonic.game.BuildConfig
import com.factonic.game.Settings
import com.factonic.game.data.*
import com.factonic.game.sim.*
import kotlinx.coroutines.delay
import com.factonic.game.audio.Music
import com.factonic.game.audio.Sfx

enum class PanelKind { NONE, CRAFT, RESEARCH, MENU, HELP, EXPORTS, MAP, STATS, SETTINGS }

class UiState(private val game: Game? = null) {
    var panel by mutableStateOf(PanelKind.NONE)
    var selected by mutableStateOf<Building?>(null)
    var tick by mutableIntStateOf(0)
    val toasts = mutableStateListOf<Pair<Long, String>>()
    val hotbar = mutableStateListOf<String>().also { it.addAll(game?.hotbar ?: Game.DEFAULT_HOTBAR) }
    var missionOpen by mutableStateOf(true)
    var victoryOpen by mutableStateOf(false)
    /** Screenshot mode: everything hidden except a faint button to bring it back. */
    var hudHidden by mutableStateOf(false)
    var missionMin by mutableStateOf(false)
    var statusMin by mutableStateOf(false)
    var toolbarMin by mutableStateOf(false)
    var panelMin by mutableStateOf(false)

    fun toast(s: String) {
        if (toasts.any { it.second == s }) return
        toasts += System.currentTimeMillis() to s
        while (toasts.size > 4) toasts.removeAt(0)
    }

    fun use(id: String) {
        hotbar.remove(id); hotbar.add(0, id); while (hotbar.size > 7) hotbar.removeAt(hotbar.size - 1)
        game?.hotbar?.let { it.clear(); it.addAll(hotbar) }
    }
}

@Composable
fun GameHud(
    game: Game, ui: UiState, onSave: () -> Unit, onExit: () -> Unit, onNew: () -> Unit, centerOn: (Float, Float) -> Unit,
    camPos: () -> Pair<Float, Float> = { game.world.cx.toFloat() to game.world.cy.toFloat() },
    onHub: () -> Unit = {},
    flyTo: (Float, Float) -> Unit = centerOn,
) {
    // refresh ~5 times a second
    LaunchedEffect(game) {
        while (true) {
            delay(120); ui.tick++; Live.tick++
            val now = System.currentTimeMillis()
            ui.toasts.removeAll { now - it.first > 3500 }
            if (game.victory && !game.victoryShown) {
                game.victoryShown = true; ui.victoryOpen = true
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick

    if (ui.hudHidden) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(34.dp).alpha(.35f).clip(CircleShape)
                    .background(Color(0x991A1530)).border(1.dp, Pal.line, CircleShape).clickable { ui.hudHidden = false },
                contentAlignment = Alignment.Center
            ) { Body("◉", 15, Pal.text, FontWeight.Bold) }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        // ---------------------------------------------------------------- top bar: mission (left) + status (right)
        // Status chips are measured first; the mission card takes whatever width remains so the two never overlap.
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f, fill = false).padding(end = 8.dp).then(if (ui.missionMin) Modifier else Modifier.widthIn(max = 236.dp))) {
                MissionCard(game, ui)
            }
            Row(verticalAlignment = Alignment.Top) {
                AlertChip(game) { b ->
                    flyTo(b.centerX, b.centerY); ui.selected = b; ui.panel = PanelKind.NONE
                    b.problem(game)?.let { ui.toast("${b.type.name}: $it") }
                }
                if (!ui.statusMin) {
                    if (game.unlocked.contains("launcher")) {
                        ExportsChip(game) { ui.panel = PanelKind.EXPORTS }
                        Spacer(Modifier.width(6.dp))
                    }
                    ResearchChip(game) { ui.panel = PanelKind.RESEARCH }
                    Spacer(Modifier.width(6.dp))
                    PowerChip(game)
                    Spacer(Modifier.width(6.dp))
                    ClockChip(game) { ui.panel = PanelKind.STATS }
                    Spacer(Modifier.width(6.dp))
                }
                MiniButton("⌂") { onHub() }
                Spacer(Modifier.width(4.dp))
                MiniButton(if (ui.statusMin) "‹" else "›") { ui.statusMin = !ui.statusMin }
                Spacer(Modifier.width(4.dp))
                MiniButton("◉") { ui.hudHidden = true; ui.toast("UI hidden - tap the faint ◉ to bring it back") }
                Spacer(Modifier.width(4.dp))
                MiniButton("≡") { ui.panel = PanelKind.MENU }
            }
        }
        // ---------------------------------------------------------------- toasts
        Column(Modifier.align(Alignment.TopCenter).padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ui.toasts.forEach { (_, s) ->
                Chip(Modifier.padding(2.dp)) { Body(s, 12, weight = FontWeight.SemiBold) }
            }
        }
        // ---------------------------------------------------------------- bottom toolbar
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            (game.tool as? Tool.Place)?.let { t ->
                if (!ui.toolbarMin && Settings.hints) Chip(Modifier.padding(bottom = 6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Pal.teal)); Spacer(Modifier.width(6.dp))
                    Body(t.type.name, 11, Pal.teal, FontWeight.Bold); Spacer(Modifier.width(8.dp))
                    Body(placeHint(t.type), 10, Pal.dim)
                }
            }
            if (game.tool == Tool.Remove && !ui.toolbarMin && Settings.hints) Chip(Modifier.padding(bottom = 6.dp)) {
                Body("Remove", 11, Pal.red, FontWeight.Bold); Spacer(Modifier.width(8.dp))
                Body("Tap a building to pick it up • swipe across buildings to clear a row • tap empty ground to stop", 10, Pal.dim)
            }
            if (ui.toolbarMin) MiniButton("▴  TOOLS", wide = true) { ui.toolbarMin = false }
            else Toolbar(game, ui, Modifier)
        }

        // ---------------------------------------------------------------- side panel for a selected building
        ui.selected?.let { b ->
            if (ui.panel == PanelKind.NONE) BuildingPanel(game, b, ui, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 58.dp, bottom = if (ui.toolbarMin) 46.dp else 72.dp))
        }

        // ---------------------------------------------------------------- modal panels
        if (ui.panel != PanelKind.NONE) {
            Box(Modifier.fillMaxSize().background(Color(0x99060410)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { ui.panel = PanelKind.NONE })
        }
        when (ui.panel) {
            PanelKind.CRAFT -> CraftPanel(game, ui, Modifier.align(Alignment.Center))
            PanelKind.RESEARCH -> ResearchPanel(game, ui, Modifier.align(Alignment.Center))
            PanelKind.MENU -> MenuPanel(game, ui, onSave, onExit, onNew, Modifier.align(Alignment.Center))
            PanelKind.STATS -> StatsPanel(game, Modifier.align(Alignment.Center)) { ui.panel = PanelKind.NONE }
            PanelKind.SETTINGS -> SettingsPanel(Modifier.align(Alignment.Center)) { ui.panel = PanelKind.NONE }
            PanelKind.HELP -> HelpPanel(Modifier.align(Alignment.Center)) { ui.panel = PanelKind.NONE }
            PanelKind.EXPORTS -> ExportsPanel(game, Modifier.align(Alignment.Center)) { ui.panel = PanelKind.NONE }
            PanelKind.MAP -> MapPanel(game, camPos(), Modifier.align(Alignment.Center), onHub = { onHub(); ui.panel = PanelKind.NONE }) { x, y -> centerOn(x, y); ui.panel = PanelKind.NONE }
            PanelKind.NONE -> {}
        }
        if (ui.victoryOpen) VictoryPanel(game, Modifier.align(Alignment.Center)) { ui.victoryOpen = false }
    }
}

// ------------------------------------------------------------------------------------------- HUD widgets
@Composable
fun MiniButton(text: String, wide: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).then(if (wide) Modifier.padding(0.dp) else Modifier.width(30.dp)).clip(RoundedCornerShape(9.dp))
            .background(Color(0xE61A1530)).border(1.dp, Pal.line, RoundedCornerShape(9.dp))
            .clickable { Sfx.play("click"); onClick() }.padding(horizontal = if (wide) 12.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) { Body(text, if (wide) 11 else 15, Pal.text, FontWeight.Bold) }
}

@Composable
fun MissionCard(game: Game, ui: UiState) {
    live()
    val m = game.mission
    if (ui.missionMin) {
        Chip(Modifier.clickable { ui.missionMin = false }) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Pal.amber)); Spacer(Modifier.width(6.dp))
            Title(if (m == null) "DONE" else "${game.missionIndex + 1}/${game.missions.size}", 10, Pal.amber)
            m?.progress(game)?.takeIf { it.isNotEmpty() }?.let { Spacer(Modifier.width(6.dp)); Body(it, 10, Pal.teal, FontWeight.Bold) }
            Spacer(Modifier.width(6.dp)); Body("▸", 12, Pal.dim)
        }
        return
    }
    Panel(Modifier.fillMaxWidth().clickable { ui.missionOpen = !ui.missionOpen }) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Pal.amber))
                Spacer(Modifier.width(6.dp))
                Title(if (m == null) "ALL OBJECTIVES DONE" else "OBJECTIVE ${game.missionIndex + 1}/${game.missions.size}", 9, Pal.amber)
                Spacer(Modifier.weight(1f))
                Body(if (ui.missionOpen) "▴" else "▾", 13, Pal.dim)
                Spacer(Modifier.width(10.dp))
                Body("–", 16, Pal.dim, FontWeight.Bold, Modifier.clickable { ui.missionMin = true })
            }
            if (m != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val p = m.progress(game)
                    Box(Modifier.weight(1f, fill = false)) { Title(m.title, 12) }
                    if (p.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(p, color = Pal.teal, fontFamily = Exo, fontWeight = FontWeight.Bold, fontSize = 11.sp, softWrap = false, maxLines = 1)
                    }
                }
                if (ui.missionOpen) {
                    Spacer(Modifier.height(2.dp)); Body(m.desc, 11, Pal.dim)
                }
            } else Body("Keep expanding your factory across the planet.", 11, Pal.dim)
        }
    }
}

@Composable
fun ExportsChip(game: Game, onClick: () -> Unit) {
    live()
    Chip(Modifier.clickable(onClick = onClick)) {
        GameIcon("rocket", 22.dp)
        Spacer(Modifier.width(4.dp))
        Column {
            Body("Earth exports", 9, Pal.dim)
            val total = Items.EXPORTS.sumOf { game.stats.shipped(it) }
            Body(if (game.researched.contains("signal")) "${fmtLong(total)} • ${game.stats.cargoLaunches} rockets" else "Uplink needed", 10, Pal.amber, FontWeight.Bold)
        }
    }
}

fun fmtLong(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1e6)
    n >= 10_000 -> "${n / 1000}k"
    else -> n.toString()
}

@Composable
fun PowerChip(game: Game) {
    live()
    Chip {
        Text("⚡", color = if (game.powerSat < .99f) Pal.red else Pal.amber, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Column {
            Body("${fmtKw(game.powerDemand)} / ${fmtKw(game.powerCapacity)}", 10, weight = FontWeight.Bold)
            Box(Modifier.width(88.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(Pal.panel2)) {
                val f = if (game.powerCapacity <= 0) 1f else (game.powerDemand / game.powerCapacity).coerceIn(0f, 1f)
                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(if (game.powerSat < .99f) Pal.red else Pal.teal))
            }
            if (game.storedMax > 0) Body("Stored ${(game.storedEnergy / game.storedMax * 100).toInt()}%", 9, Pal.dim)
            else if (game.powerSat < .99f) Body("Brownout ${(game.powerSat * 100).toInt()}%", 9, Pal.red)
        }
    }
}

fun placeHint(t: BType): String = when (t.kind) {
    BKind.BELT -> "Tap the ghost to build • drag the arrow to lay a line • drag the ghost to move • long-press a building to copy it"
    BKind.TUNNEL -> "Build an entrance, then an exit up to ${Tunnels.SPAN + 1} tiles ahead facing the same way • belts can cross in between"
    BKind.SPLITTER -> "Belts pointing in are inputs, every other belt next to it is an output • the arrow marks the front for filtering"
    else -> "Tap the ghost to build • drag it to move • long-press a building to copy it (with its recipe) • tap elsewhere to put away"
}

/** Compact warning chip: tap to fly to the next building that needs attention. */
@Composable
fun AlertChip(game: Game, onJump: (Building) -> Unit) {
    live()
    val n = game.alerts.size
    if (n == 0) return
    var next by remember { mutableIntStateOf(0) }
    Chip(Modifier.clickable {
        val list = game.alerts
        if (list.isNotEmpty()) {
            val b = list[next % list.size]
            next = (next + 1) % list.size
            Sfx.play("open")
            onJump(b)
        }
    }) {
        Text("⚠", color = Pal.amber, fontSize = 15.sp)
        Spacer(Modifier.width(3.dp))
        Body("$n", 11, Pal.amber, FontWeight.Bold)
    }
    Spacer(Modifier.width(6.dp))
}

@Composable
fun ClockChip(game: Game, onClick: () -> Unit = {}) {
    live()
    val d = game.daylight
    Chip(Modifier.clickable(onClick = onClick)) {
        Text(if (d > .5f) "☀" else "☾", color = if (d > .5f) Pal.amber else Color(0xFFB9A6FF), fontSize = 15.sp)
        Spacer(Modifier.width(3.dp))
        val day = (game.time / Game.DAY_LENGTH).toInt() + 1
        Body("Sol $day", 10, weight = FontWeight.Bold)
    }
}

@Composable
fun ResearchChip(game: Game, onClick: () -> Unit) {
    live()
    val t = game.currentTech
    Chip(Modifier.clickable(onClick = onClick)) {
        GameIcon(t?.icon ?: "sci_kinetic", 22.dp)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.width(104.dp)) {
            Body(t?.name ?: "No research", 10, if (t == null) Pal.amber else Pal.text, FontWeight.Bold, Modifier.fillMaxWidth())
            if (t != null) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(Pal.panel2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(game.progressOf(t) / t.count.toFloat()).background(Pal.pink))
                }
                val q = game.researchQueue.size
                Body("${game.progressOf(t)} / ${t.count}" + if (q > 0) "  •  +$q queued" else "", 9, Pal.dim)
            } else Body("Tap to choose", 9, Pal.dim)
        }
    }
}

@Composable
fun Toolbar(game: Game, ui: UiState, modifier: Modifier) {
    live()
    val tool = game.tool
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Box(Modifier.height(50.dp), contentAlignment = Alignment.Center) { MiniButton("▾") { ui.toolbarMin = true } }
        Spacer(Modifier.width(5.dp))
        ToolButton(null, "Inspect", active = tool == Tool.None, glyph = "☝") { game.tool = Tool.None }
        Spacer(Modifier.width(5.dp))
        ToolButton(
            "gear", "Craft", active = ui.panel == PanelKind.CRAFT, accent = Pal.amber,
            progress = game.craftQueue.firstOrNull()?.progress, badge = game.craftQueue.size.takeIf { it > 0 }?.let { if (it > 99) "99+" else "$it" }
        ) { ui.panel = PanelKind.CRAFT }
        Spacer(Modifier.width(5.dp))
        ToolButton("sci_kinetic", "Research", active = ui.panel == PanelKind.RESEARCH, accent = Pal.pink) { ui.panel = PanelKind.RESEARCH }
        Spacer(Modifier.width(5.dp))
        ToolButton(null, "Map", active = ui.panel == PanelKind.MAP, accent = Pal.teal, glyph = "◈") { ui.panel = PanelKind.MAP }
        Spacer(Modifier.width(5.dp))
        ToolButton(null, "Remove", active = tool == Tool.Remove, accent = Pal.red, glyph = "✕") {
            game.tool = if (tool == Tool.Remove) Tool.None else Tool.Remove
            ui.selected = null
        }
        Spacer(Modifier.width(10.dp))
        // hotbar
        Panel {
            Row(Modifier.padding(3.dp)) {
                ui.hotbar.forEach { id ->
                    val bt = Buildings.byId[id] ?: return@forEach
                    val item = Items[id]
                    val n = game.inventory[item]
                    val active = (tool as? Tool.Place)?.type == bt
                    HotSlot(id, n, active, unlocked = game.unlocked.contains(id)) {
                        if (active) game.tool = Tool.None
                        else if (n > 0) {
                            game.tool = Tool.Place(bt); ui.selected = null
                        } else {
                            val r = Recipes.byId[id]
                            if (r != null && game.canHandCraft(r)) {
                                if (game.queueCraft(r, 1)) ui.toast("Crafting ${bt.name}…")
                            } else ui.toast("${bt.name} is not researched yet")
                        }
                    }
                }
            }
        }
        if (tool is Tool.Place) {
            Spacer(Modifier.width(10.dp))
            if (tool.type.rotatable) {
                ToolButton(null, "Rotate", glyph = arrowFor(game.placeDir)) { game.placeDir = (game.placeDir + 1) % 4 }
                Spacer(Modifier.width(5.dp))
            }
            ToolButton(null, "Done", glyph = "✓", accent = Pal.green) { game.tool = Tool.None }
        }
    }
}

fun arrowFor(d: Int) = when (d) {
    0 -> "↑"; 1 -> "→"; 2 -> "↓"; else -> "←"
}

@Composable
fun HotSlot(id: String, n: Int, active: Boolean, unlocked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(2.dp).size(44.dp).clip(RoundedCornerShape(9.dp))
            .background(if (active) Pal.teal.copy(alpha = .32f) else Pal.panel2)
            .border(if (active) 2.5.dp else 0.dp, if (active) Pal.teal else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GameIcon(id, if (active) 36.dp else 32.dp, alpha = if (n > 0) 1f else if (unlocked) .45f else .2f)
        Text(fmtCount(n), Modifier.align(Alignment.BottomEnd).padding(2.dp), color = if (n > 0) Pal.text else Pal.dim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        if (active) Box(Modifier.align(Alignment.TopCenter).padding(top = 2.dp).width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Pal.teal))
    }
}

// ------------------------------------------------------------------------------------------- craft panel
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CraftPanel(game: Game, ui: UiState, modifier: Modifier) {
    live()
    val groups = listOf(Group.LOGISTICS, Group.PRODUCTION, Group.POWER, Group.SCIENCE, Group.COMPONENT, Group.MATERIAL, Group.EXOTIC)
    var tab by remember { mutableStateOf(Group.LOGISTICS) }
    var showInv by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Recipe?>(null) }
    @Suppress("UNUSED_VARIABLE") val tk = ui.tick
    Panel(modifier.fillMaxWidth(.92f).fillMaxHeight(.9f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
            // tabs
            Column(Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Title("FABRICATOR", 14, Pal.amber)
                Spacer(Modifier.height(10.dp))
                groups.forEach { g ->
                    TabRow(g.label, !showInv && tab == g) { tab = g; showInv = false; sel = null }
                }
                TabRow("Inventory", showInv) { showInv = true; sel = null }
            }
            Spacer(Modifier.width(12.dp))
            // grid
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (showInv) {
                    val stacks = game.inventory.stacks().sortedBy { it.item.idx }
                    LazyVerticalGrid(GridCells.Adaptive(70.dp)) {
                        items(stacks, key = { it.item.id }) { s ->
                            val bt = s.item.building
                            ItemCell(s.item.id, s.n, (game.tool as? Tool.Place)?.type == bt && bt != null, true) {
                                if (bt != null && bt.kind != BKind.CORE) {
                                    game.tool = Tool.Place(bt); ui.use(bt.id); ui.panel = PanelKind.NONE; ui.selected = null
                                } else ui.toast(s.item.name + ": " + s.item.desc)
                            }
                        }
                    }
                } else {
                    val recipes = Recipes.all.filter { it.cat == Cat.ASSEMBLE && it.main.group == tab }
                    LazyVerticalGrid(GridCells.Adaptive(70.dp)) {
                        items(recipes, key = { it.id }) { r ->
                            val ok = game.unlocked.contains(r.id)
                            Box(Modifier.combinedClickable(onClick = { sel = r }, onLongClick = {
                                if (ok) game.queueCraft(r, 5)
                            })) {
                                ItemCell(r.main.id, game.inventory[r.main], sel == r, ok, null)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            // detail
            Column(Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                val r = sel
                if (r == null) {
                    Body("Select a recipe to see its ingredients. Tap Craft to queue it; ingredients you're missing are crafted automatically when possible.", 12, Pal.dim)
                    Spacer(Modifier.height(10.dp))
                    Body("Long-press a recipe to craft 5.", 12, Pal.dim)
                } else RecipeDetail(game, ui, r)
            }
        }
        CraftQueueStrip(game)
      }
    }
}

/** The hand-crafting queue, shown along the bottom of the Fabricator. */
@Composable
fun CraftQueueStrip(game: Game) {
    live()
    val q = game.craftQueue
    Row(
        Modifier.fillMaxWidth().background(Color(0x55060410)).border(1.dp, Pal.line).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Title("QUEUE", 10, Pal.amber, Modifier.width(62.dp))
        val job = q.firstOrNull()
        if (job == null) {
            Body("Nothing crafting. Pick a recipe and tap Craft - missing parts are queued automatically.", 11, Pal.dim)
            return@Row
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(Pal.amber.copy(.18f)).border(1.5.dp, Pal.amber, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            GameIcon(job.recipe.main.id, 34.dp)
        }
        Spacer(Modifier.width(10.dp))
        val left = (1f - job.progress).coerceAtLeast(0f) * job.recipe.time
        val total = q.sumOf { it.recipe.time.toDouble() }.toFloat() - job.progress * job.recipe.time
        ProgressBar(
            job.progress, Pal.amber, Modifier.width(190.dp), 10.dp,
            label = job.recipe.name, right = "${(job.progress * 100).toInt()}%  •  ${String.format("%.1f", left)}s"
        )
        Spacer(Modifier.width(12.dp))
        // what comes next, grouped by recipe in queue order
        val next = ArrayList<Pair<Recipe, Int>>()
        for (j in q.drop(1)) {
            if (next.isNotEmpty() && next.last().first == j.recipe) next[next.size - 1] = j.recipe to next.last().second + 1
            else next += j.recipe to 1
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            next.take(7).forEach { (r, n) ->
                Box(Modifier.padding(end = 4.dp).size(34.dp).clip(RoundedCornerShape(7.dp)).background(Pal.panel2), contentAlignment = Alignment.Center) {
                    GameIcon(r.main.id, 26.dp)
                    Text("$n", Modifier.align(Alignment.BottomEnd).padding(end = 2.dp), color = Pal.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (next.size > 7) Body("+${next.size - 7}", 11, Pal.dim)
        }
        Column(horizontalAlignment = Alignment.End) {
            Body("${q.size} job${if (q.size == 1) "" else "s"} • ${String.format("%.0f", total.coerceAtLeast(0f))}s", 10, Pal.dim)
            Row {
                SmallAction("UNDO LAST", Pal.amber) { game.cancelLast() }
                Spacer(Modifier.width(6.dp))
                SmallAction("CLEAR", Pal.red) { game.cancelCrafting() }
            }
        }
    }
}

@Composable
fun SmallAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 3.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, color, RoundedCornerShape(7.dp))
            .clickable { Sfx.play("click"); onClick() }.padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Body(text, 9, color, FontWeight.Bold) }
}

@Composable
fun TabRow(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) Pal.amber.copy(alpha = .18f) else Color.Transparent)
            .border(1.dp, if (active) Pal.amber else Pal.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp)
    ) { Body(label, 13, if (active) Pal.amber else Pal.text, FontWeight.SemiBold) }
}

@Composable
fun ItemCell(id: String, n: Int, selected: Boolean, enabled: Boolean, onClick: (() -> Unit)?) {
    val m = Modifier.padding(4.dp).size(64.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) Pal.amber.copy(alpha = .22f) else Pal.panel2)
        .border(if (selected) 2.dp else 1.dp, if (selected) Pal.amber else Pal.line, RoundedCornerShape(10.dp))
    Box(if (onClick != null) m.clickable(onClick = onClick) else m, contentAlignment = Alignment.Center) {
        GameIcon(id, 46.dp, alpha = if (enabled) 1f else .25f)
        if (!enabled) Text("🔒", Modifier.align(Alignment.TopEnd).padding(3.dp), fontSize = 10.sp)
        if (n > 0) Text(fmtCount(n), Modifier.align(Alignment.BottomEnd).padding(4.dp), color = Pal.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RecipeDetail(game: Game, ui: UiState, r: Recipe) {
    live()
    val ok = game.unlocked.contains(r.id)
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameIcon(r.main.id, 54.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Title(r.name, 14)
            Body("${r.time}s  •  makes ${r.outputs.first().n}", 11, Pal.dim)
        }
    }
    Spacer(Modifier.height(6.dp))
    Body(r.main.desc, 12, Pal.dim)
    Spacer(Modifier.height(10.dp))
    Title("INGREDIENTS", 10, Pal.amber)
    Spacer(Modifier.height(4.dp))
    r.inputs.forEach { s ->
        val have = game.inventory[s.item]
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcon(s.item.id, 28.dp)
            Spacer(Modifier.width(8.dp))
            Body(s.item.name, 12, modifier = Modifier.weight(1f))
            Body("$have / ${s.n}", 12, if (have >= s.n) Pal.green else Pal.red, FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(12.dp))
    if (!ok) {
        val tech = Techs.all.firstOrNull { it.unlocks.contains(r.id) }
        Body("Locked. Research ${tech?.name ?: "?"} to unlock.", 12, Pal.red)
        return
    }
    val can = remember(r, game.inventory.total(), game.craftQueue.size) { game.maxCraftable(r, 99) }
    Body(if (can > 0) "You can craft ${if (can >= 99) "99+" else can.toString()} now" else "Not enough materials", 11, if (can > 0) Pal.green else Pal.red)
    Spacer(Modifier.height(6.dp))
    Row {
        GButton("CRAFT 1", color = Pal.amber, enabled = can >= 1) { game.queueCraft(r, 1) }
        Spacer(Modifier.width(8.dp))
        GButton("×5", color = Pal.amber, enabled = can >= 5) { game.queueCraft(r, 5) }
        Spacer(Modifier.width(8.dp))
        GButton("MAX", color = Pal.amber, enabled = can >= 1) { game.queueCraft(r, minOf(can, 50)) }
    }
    val bt = r.main.building
    if (bt != null && game.inventory[r.main] > 0) {
        Spacer(Modifier.height(8.dp))
        GButton("PLACE ${bt.name.uppercase()}", Modifier.fillMaxWidth()) {
            game.tool = Tool.Place(bt); ui.use(bt.id); ui.panel = PanelKind.NONE; ui.selected = null
        }
    }
    val machines = when {
        r.cat != Cat.ASSEMBLE -> r.cat.machine
        r.main.isScience -> "Assembler"
        else -> "hand or Assembler"
    }
    Spacer(Modifier.height(8.dp))
    Body("Made by $machines.", 11, Pal.dim)
}

// ------------------------------------------------------------------------------------------- menu / help / victory
@Composable
fun MenuPanel(game: Game, ui: UiState, onSave: () -> Unit, onExit: () -> Unit, onNew: () -> Unit, modifier: Modifier) {
    var confirmNew by remember { mutableStateOf(false) }
    Panel(modifier.width(360.dp).fillMaxHeight(.92f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Title("FACTONIC", 22, Pal.teal)
            Body("Paused  •  Sol ${(game.time / Game.DAY_LENGTH).toInt() + 1}  •  ${fmtDuration(game.playTime)} played", 12, Pal.dim)
            Spacer(Modifier.height(16.dp))
            GButton("RESUME", Modifier.fillMaxWidth()) { ui.panel = PanelKind.NONE }
            Spacer(Modifier.height(8.dp))
            GButton("SAVE GAME", Modifier.fillMaxWidth(), Pal.amber) { onSave(); ui.toast("Game saved"); ui.panel = PanelKind.NONE }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                GButton("STATISTICS", Modifier.weight(1f), Pal.teal) { ui.panel = PanelKind.STATS }
                Spacer(Modifier.width(8.dp))
                GButton("SETTINGS", Modifier.weight(1f), Pal.text) { ui.panel = PanelKind.SETTINGS }
            }
            Spacer(Modifier.height(8.dp))
            GButton("HOW TO PLAY", Modifier.fillMaxWidth(), Pal.text, filled = false) { ui.panel = PanelKind.HELP }
            Spacer(Modifier.height(8.dp))
            if (!confirmNew) GButton("NEW PLANET", Modifier.fillMaxWidth(), Pal.pink) { confirmNew = true }
            else GButton("TAP AGAIN TO ABANDON THIS PLANET", Modifier.fillMaxWidth(), Pal.red) { onNew() }
            Spacer(Modifier.height(8.dp))
            GButton("SAVE & EXIT TO TITLE", Modifier.fillMaxWidth(), Pal.dim) { onSave(); onExit() }
        }
    }
}

fun fmtDuration(seconds: Float): String {
    val m = (seconds / 60f).toInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

// ------------------------------------------------------------------------------------------- settings
@Composable
fun SettingsPanel(modifier: Modifier, onClose: () -> Unit) {
    var snd by remember { mutableStateOf(Sfx.enabled) }
    Panel(modifier.width(440.dp).fillMaxHeight(.9f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("SETTINGS", 18, Pal.teal); Spacer(Modifier.weight(1f)); GButton("CLOSE", onClick = onClose)
            }
            Spacer(Modifier.height(12.dp))
            Title("AUDIO", 10, Pal.amber)
            ToggleRow("Music", "An ambient soundtrack composed by the game", Settings.music) { Settings.music = it; Music.refresh() }
            if (Settings.music) VolumeRow("Music volume", Settings.musicVolume) { Settings.musicVolume = it; Music.refresh() }
            ToggleRow("Sound effects", null, snd) { Sfx.enabled = it; snd = it }
            if (snd) VolumeRow("Effects volume", Settings.sfxVolume, onDone = { Sfx.play("craft") }) { Settings.sfxVolume = it }
            Spacer(Modifier.height(12.dp))
            Title("GAMEPLAY", 10, Pal.amber)
            ToggleRow("Objective pointer", "An arrow at the screen edge leads to the resource your objective needs", Settings.objectivePointer) { Settings.objectivePointer = it }
            ToggleRow("Control hints", "One-line reminders above the toolbar while building", Settings.hints) { Settings.hints = it }
            ToggleRow("Vibration", null, Settings.haptics) { Settings.haptics = it }
            ToggleRow("Keep screen on", "Stop the screen dimming while you watch the factory run", Settings.keepAwake) { Settings.keepAwake = it }
            Spacer(Modifier.height(12.dp))
            Title("ABOUT", 10, Pal.amber)
            Spacer(Modifier.height(4.dp))
            Body("Factonic ${BuildConfig.VERSION_NAME}", 12, Pal.text, FontWeight.SemiBold)
            Body("The music and sound effects are synthesised by the game on your device. Fonts: Orbitron and Exo 2 (SIL Open Font License 1.1).", 11, Pal.dim)
        }
    }
}

@Composable
fun ToggleRow(label: String, desc: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Pal.panel2)
            .clickable { Sfx.play("click"); onChange(!value) }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Body(label, 13, Pal.text, FontWeight.SemiBold)
            if (desc != null) Body(desc, 10, Pal.dim)
        }
        Spacer(Modifier.width(10.dp))
        Toggle(value)
    }
}

@Composable
fun VolumeRow(label: String, value: Float, onDone: () -> Unit = {}, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Body(label, 12, Pal.dim, modifier = Modifier.width(110.dp))
        Slider(
            value, onChange, Modifier.weight(1f), onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Pal.teal, activeTrackColor = Pal.teal, inactiveTrackColor = Pal.panel2)
        )
        Body("${(value * 100).toInt()}%", 11, Pal.teal, FontWeight.Bold, Modifier.width(40.dp))
    }
}

// ------------------------------------------------------------------------------------------- statistics
@Composable
fun StatsPanel(game: Game, modifier: Modifier, onClose: () -> Unit) {
    live()
    var window by remember { mutableStateOf(60f) }
    Panel(modifier.fillMaxWidth(.94f).fillMaxHeight(.9f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Row(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.width(210.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Title("STATISTICS", 15, Pal.teal)
                Spacer(Modifier.height(10.dp))
                StatLine("Time played", fmtDuration(game.playTime))
                StatLine("Planet day", "Sol ${(game.time / Game.DAY_LENGTH).toInt() + 1}")
                StatLine("Objectives", "${minOf(game.missionIndex, game.missions.size)} / ${game.missions.size}")
                StatLine("Research", "${game.researched.size} / ${Techs.all.size}")
                StatLine("Buildings", "${game.buildings.size}")
                StatLine("Belts", "${game.buildings.count { it is Belt }}")
                StatLine("Machines", "${game.buildings.count { it is Crafter || it is Lab }}")
                StatLine("Drills", "${game.buildings.count { it is Drill }}")
                StatLine("Power use", "${fmtKw(game.powerDemand)} / ${fmtKw(game.powerCapacity)}")
                StatLine("Mined by hand", fmtLong(game.stats.handMined.toLong()))
                StatLine("Rockets", "${game.stats.launches}")
                if (game.stats.cargoLaunches > 0) StatLine("Exports shipped", fmtLong(Items.EXPORTS.sumOf { game.stats.shipped(it) }))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("PRODUCTION", 12, Pal.amber)
                    Spacer(Modifier.width(10.dp))
                    Body("items per minute", 11, Pal.dim)
                    Spacer(Modifier.weight(1f))
                    GButton("1 MIN", color = if (window == 60f) Pal.teal else Pal.dim, filled = window == 60f) { window = 60f }
                    Spacer(Modifier.width(6.dp))
                    GButton("10 MIN", color = if (window == 595f) Pal.teal else Pal.dim, filled = window == 595f) { window = 595f }
                    Spacer(Modifier.width(10.dp))
                    GButton("CLOSE", color = Pal.dim, onClick = onClose)
                }
                Spacer(Modifier.height(8.dp))
                val buckets = (window / Stats.BUCKET).toInt()
                val rows = game.stats.made.keys
                    .map { it to (game.stats.ratePerMin(it, window) ?: 0f) }
                    .sortedWith(compareByDescending<Pair<Item, Float>> { it.second }.thenByDescending { game.stats.made(it.first) })
                if (rows.isEmpty()) Body("Nothing produced yet. Mine some ore to get started.", 12, Pal.dim)
                else LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.first.id }) { (item, rate) ->
                        ProductionRow(game, item, rate, buckets)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Body(label, 12, Pal.dim, modifier = Modifier.weight(1f))
        Body(value, 12, Pal.text, FontWeight.Bold)
    }
}

@Composable
private fun ProductionRow(game: Game, item: Item, rate: Float, buckets: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Pal.panel2).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameIcon(item.id, 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(item.name, Modifier.weight(1f), color = Pal.text, fontFamily = Exo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val series = game.stats.series(item, buckets)
        val peak = maxOf(1, series.maxOrNull() ?: 1)
        androidx.compose.foundation.Canvas(Modifier.width(120.dp).height(22.dp)) {
            val w = size.width
            val h = size.height
            val n = series.size
            if (n > 1) {
                val line = Path()
                val area = Path()
                area.moveTo(0f, h)
                for (k in 0 until n) {
                    val x = k * w / (n - 1)
                    val y = h - series[k] / peak.toFloat() * (h - 2f)
                    if (k == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    area.lineTo(x, y)
                }
                area.lineTo(w, h); area.close()
                drawPath(area, Pal.teal.copy(alpha = .18f))
                drawPath(line, Pal.teal, style = Stroke(width = 1.5f * density))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(fmtRate(rate), Modifier.width(64.dp), color = if (rate > 0f) Pal.teal else Pal.dim, fontFamily = Exo, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        Text(fmtLong(game.stats.made(item)), Modifier.width(56.dp), color = Pal.dim, fontFamily = Exo, fontSize = 11.sp, maxLines = 1)
    }
}

fun fmtRate(r: Float): String = when {
    r <= 0f -> "–"
    r < 10f -> String.format("%.1f/min", r)
    else -> "${r.toInt()}/min"
}

val HELP = listOf(
    "Move" to "Drag with one finger to pan - even with a building selected. Pinch to zoom. Open the Map to jump anywhere on the planet.",
    "Mine" to "Tap an ore patch to mine one piece. Hold to keep mining.",
    "Craft" to "Open Craft to build items by hand. Missing intermediates are crafted automatically.",
    "Build" to "Pick a building from the hotbar: a ghost appears in the middle of the screen. Drag the ghost to move it, tap it to build, tap it again to rotate. Long-press any building to pick up a copy of it as your tool. Tap empty ground to put the tool away.",
    "Belts" to "A belt ghost has a round arrow handle on its front. Drag the handle to stretch a line of belts - in any of the four directions - and let go to build it. The ghost waits at the end of the line, so you can keep going or turn a corner. Tap the handle to rotate.",
    "Drills" to "Place on ore. The yellow chute marks the output side; put a belt, crate or machine there.",
    "Tunnels" to "Tunnel Belts carry a belt under up to five tiles so two lines can cross. Build the entrance, then the exit further along facing the same way. Tap a tunnel end to see its partner; rotating one end reverses the pair.",
    "Splitters" to "A Splitter takes items from every belt pointing into it and shares them between all the other belts around it. Give it a filter and it sorts: that item leaves through the front, everything else through the sides.",
    "Inserters" to "Move items from the tile behind them to the tile in front. Machines only take what their recipe needs.",
    "Machines" to "Tap a building to inspect it, choose a recipe, add items from your inventory or collect output. Long-press an assembler to copy it together with its recipe.",
    "Power" to "Everything shares one planetary grid. The Lander Core gives 400 kW; add Combustion Generators (Carbonite), Solar Arrays and Geothermal Taps.",
    "Research" to "Labs consume science packs. Pick a technology in the Research tree. Queue several - locked ones bring their prerequisites along - and your labs move on by themselves. Six sciences lead to the Orbital Launcher and the Earth Uplink.",
    "Alerts" to "The ⚠ chip counts machines that need you: depleted drills, generators out of fuel, machines without a recipe, unpaired tunnels. Tap it to fly to each in turn.",
    "Statistics" to "Tap the Sol clock (or open the menu) for production rates per minute over the last 1 or 10 minutes.",
    "Xyra elements" to "After the Uplink, Earth asks for three things found only here: Solanite (amber), Terravite (grey-green) and Vitae springs (pink). They lie far from the landing site and need Resonance Drills and Vitae Harvesters.",
    "Exports" to "Solanite becomes Xyra Epoxy, Terravite becomes Xenocrete cement packed in epoxy-film sacks, and Vitae becomes the Elixir of immortality. Switch a launcher to Cargo and send as many rockets as you can - there is no limit.",
    "Objectives" to "Follow the objective card in the top left. When an objective needs a resource, an arrow at the edge of the screen points to the nearest deposit - tap it to fly there.",
    "Remove" to "With the Remove tool, tap a building to pick it up, or swipe across several to clear them in one go. Everything inside goes back to your inventory.",
    "Screenshots" to "Use the ◉ button to hide the whole interface. Use – and ▾ to tuck away single panels. The ⌂ button flies back to your Lander Core. The back button closes panels and opens the menu.",
    "Night" to "Solar stops at night. The planet's flora glows; Lumen Lamps light your base.",
)

@Composable
fun HelpPanel(modifier: Modifier, onClose: () -> Unit) {
    Panel(modifier.fillMaxWidth(.8f).fillMaxHeight(.85f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("HOW TO PLAY", 18, Pal.teal); Spacer(Modifier.weight(1f)); GButton("CLOSE", onClick = onClose)
            }
            Spacer(Modifier.height(12.dp))
            HELP.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Title(k.uppercase(), 11, Pal.amber, Modifier.width(110.dp))
                    Body(v, 13)
                }
            }
            Spacer(Modifier.height(10.dp))
            Body("Xyra-7 is quiet. Nothing will attack your factory - the only challenge is the planet itself.", 12, Pal.dim)
        }
    }
}

@Composable
fun VictoryPanel(game: Game, modifier: Modifier, onClose: () -> Unit) {
    live()
    Box(Modifier.fillMaxSize().background(Color(0xCC060410)))
    Panel(modifier.width(460.dp)) {
        Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameIcon("tech_signal", 90.dp)
            Title("UPLINK ESTABLISHED", 22, Pal.teal)
            Spacer(Modifier.height(8.dp))
            Body("Earth answers. Their scientists have studied your data: Xyra-7 holds three elements found nowhere else - Solanite, Terravite and Vitae. They don't want you home yet. They want everything you can send.", 14)
            Spacer(Modifier.height(6.dp))
            Body("New research is open: Resonance Mining leads to Xyra Epoxy, Xenocrete cement and the Vitae Elixir.", 12, Pal.amber)
            Spacer(Modifier.height(8.dp))
            Body("Sol ${(game.time / Game.DAY_LENGTH).toInt() + 1}  •  ${game.buildings.size} buildings  •  ${game.stats.launches} rockets", 12, Pal.dim)
            Spacer(Modifier.height(16.dp))
            GButton("START SHIPPING", onClick = onClose)
        }
    }
}

// ------------------------------------------------------------------------------------------- building panel
@Composable
fun BuildingPanel(game: Game, b: Building, ui: UiState, modifier: Modifier) {
    live()
    @Suppress("UNUSED_VARIABLE") val tk = ui.tick
    Panel(modifier.width(if (ui.panelMin) 220.dp else 280.dp).then(if (ui.panelMin) Modifier else Modifier.fillMaxHeight())) {
        Column(Modifier.padding(11.dp).then(if (ui.panelMin) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon(b.type.id, 38.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Title(b.type.name, 12)
                    Body(b.status(game), 11, if (b.working) Pal.green else Pal.amber, FontWeight.SemiBold)
                }
                Spacer(Modifier.width(6.dp))
                IconBtn(if (ui.panelMin) "▾" else "–") { ui.panelMin = !ui.panelMin }
                Spacer(Modifier.width(10.dp))
                IconBtn("✕", Pal.red) { ui.selected = null }
            }
            Spacer(Modifier.height(7.dp))
            BuildingProgress(game, b)
            if (ui.panelMin) return@Column
            Spacer(Modifier.height(4.dp))
            Body(BuildingDesc.of(b.type.id), 11, Pal.dim)
            if (b.type.power > 0) Body("Power: ${fmtKw(b.type.power)} while working", 11, Pal.dim)
            Spacer(Modifier.height(10.dp))
            when (b) {
                is Crafter -> if (b.isLauncher) LauncherUi(game, b) else CrafterUi(game, b)
                is Lab -> LabUi(game, b, ui)
                is Chest -> ChestUi(game, b)
                is Generator -> GeneratorUi(game, b)
                is Drill -> DrillUi(game, b)
                is Splitter -> SplitterUi(game, b)
                is Tunnel -> TunnelUi(b)
                is Belt -> Body("Items on belt: ${b.items.size}", 12)
                is Inserter -> Body("Takes from behind, drops in front (${arrowFor(b.dir)}).", 12)
                else -> {}
            }
            Spacer(Modifier.height(14.dp))
            Row {
                if (b.type.rotatable) {
                    GButton("ROTATE ${arrowFor(b.dir)}") { game.rotate(b) }
                    Spacer(Modifier.width(8.dp))
                }
                if (b.type.kind != BKind.CORE) GButton("PICK UP", color = Pal.red) {
                    game.remove(b); ui.selected = null
                }
            }
        }
    }
}

/** Every building gets its own progress bar right under its name. */
@Composable
fun BuildingProgress(game: Game, b: Building) {
    live()
    val (f, label, color) = when (b) {
        is Crafter -> when {
            b.isLauncher && b.crafting -> Triple(b.progress, if (b.cargoMode) "Cargo rocket climbing to orbit" else "Satellite climbing to orbit", if (b.cargoMode) Pal.amber else Pal.teal)
            b.isLauncher -> Triple(if (b.cargoMode) b.cargo.total() / game.cargoCapacity.toFloat() else b.input[Items.ROCKET_SEGMENT] / game.rocketSegments.toFloat(),
                if (b.cargoMode) "Cargo hold ${b.cargo.total()} / ${game.cargoCapacity}" else "Rocket segments ${b.input[Items.ROCKET_SEGMENT]} / ${game.rocketSegments}", Pal.dim.copy(alpha = 1f).let { if (b.cargoMode) Pal.amber else Pal.teal })
            b.recipe == null -> Triple(0f, "Idle - no recipe", Pal.dim)
            else -> Triple(b.progress, b.recipe!!.name + if (b.working) "" else " (waiting)", Pal.amber)
        }
        is Lab -> Triple(b.progress, game.currentTech?.let { "${it.name}  ${game.progressOf(it)}/${it.count}" } ?: "No research selected", Pal.pink)
        is Drill -> Triple(b.progress, if (b.out != null) "Output blocked" else if (b.sap) "Pumping" else "Mining", Pal.teal)
        is Generator -> when (b.type.kind) {
            BKind.COMBUSTION -> Triple((b.energy / 4000f), "Fuel burn  •  ${b.fuel[Items.CARBON]} Carbonite left", Pal.amber)
            BKind.ACCUMULATOR -> Triple(b.energy / 5000f, "Charge", Pal.green)
            else -> Triple(if (b.maxOutput > 0) maxOf(0f, b.output) / b.maxOutput else 0f, "Output ${fmtKw(maxOf(0f, b.output))}", Pal.green)
        }
        is Chest -> Triple(b.items.total() / b.capacity.toFloat(), "Storage ${b.items.total()} / ${b.capacity}", Pal.teal)
        is Belt -> Triple(b.items.size / 4f, "Belt load ${b.items.size}/4", Pal.amber)
        is Splitter -> Triple(b.buffer.size / 2f, if (b.filter != null) "Sorting" else "Buffer ${b.buffer.size}/2", Pal.amber)
        is Inserter -> Triple(b.arm, b.held?.let { "Moving ${it.name}" } ?: "Waiting", Pal.teal)
        else -> Triple(game.powerSat, "Grid power", Pal.green)
    }
    ProgressBar(f, color, Modifier.fillMaxWidth(), 8.dp, label = label)
}

@Composable
fun SlotRow(item: Item, label: String, actionLabel: String?, enabled: Boolean = true, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Pal.panel2).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIcon(item.id, 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Body(item.name, 12, weight = FontWeight.SemiBold)
            Body(label, 11, Pal.dim)
        }
        if (actionLabel != null) Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(if (enabled) Pal.teal.copy(.2f) else Color.Transparent)
                .border(1.dp, if (enabled) Pal.teal else Pal.line, RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Body(actionLabel, 11, if (enabled) Pal.teal else Pal.dim, FontWeight.Bold) }
    }
}

@Composable
fun CrafterUi(game: Game, b: Crafter) {
    live()
    val r = b.recipe
    if (!b.cat.auto && b.cat != Cat.LAUNCH) {
        Title("RECIPE", 10, Pal.amber)
        Spacer(Modifier.height(4.dp))
        val options = Recipes.all.filter { it.cat == b.cat && game.unlocked.contains(it.id) && (b.cat != Cat.ASSEMBLE || it.main.building?.kind != BKind.CORE) }
        FlowRow(options.size, perRow = 5) { i ->
            val o = options[i]
            Box(
                Modifier.padding(3.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (o == r) Pal.amber.copy(.25f) else Pal.panel2)
                    .border(if (o == r) 2.dp else 0.dp, if (o == r) Pal.amber else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { Sfx.play("click"); b.setRecipe(game, o) },
                contentAlignment = Alignment.Center
            ) { GameIcon(o.main.id, 34.dp) }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (r == null) {
        Body(if (b.cat.auto) "Picks its recipe from the first ore it receives." else "Choose a recipe above.", 12, Pal.dim)
        if (b.cat.auto) {
            Spacer(Modifier.height(6.dp))
            Recipes.all.filter { it.cat == b.cat }.map { it.inputs.first().item }.forEach { ore ->
                val sr = Recipes.autoFor(b.cat, ore)
                if (sr != null && game.unlocked.contains(sr.id) && game.inventory[ore] > 0) {
                    SlotRow(ore, "You have ${game.inventory[ore]}", "ADD 10") {
                        b.recipe = sr; addTo(game, b, ore, 10)
                    }
                }
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameIcon(r.main.id, 30.dp); Spacer(Modifier.width(6.dp))
        Body("${r.name}  •  ${String.format("%.1f", r.time / b.type.speed)}s", 12, weight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    Title("INPUT", 10, Pal.amber)
    r.inputs.forEach { s ->
        val have = game.inventory[s.item]
        SlotRow(s.item, "${b.input[s.item]} loaded  •  needs ${s.n}  •  you have $have", "ADD", have > 0) {
            addTo(game, b, s.item, maxOf(s.n * 2, 5))
        }
    }
    Spacer(Modifier.height(6.dp))
    Title("OUTPUT", 10, Pal.teal)
    r.outputs.forEach { s ->
        val n = b.output[s.item]
        SlotRow(s.item, "$n ready", "TAKE", n > 0) {
            game.inventory.add(s.item, n); b.output.add(s.item, -n)
        }
    }
}

@Composable
fun LauncherUi(game: Game, b: Crafter) {
    live()
    val uplink = game.researched.contains("signal")
    Title("MISSION", 10, Pal.amber)
    Spacer(Modifier.height(4.dp))
    Row {
        GButton("SATELLITE", Modifier.weight(1f), if (!b.cargoMode) Pal.teal else Pal.dim, enabled = !b.crafting || !b.cargoMode, filled = !b.cargoMode) { b.setCargoMode(game, false) }
        Spacer(Modifier.width(6.dp))
        GButton("CARGO", Modifier.weight(1f), if (b.cargoMode) Pal.amber else Pal.dim, enabled = uplink && (!b.crafting || b.cargoMode), filled = b.cargoMode) { b.setCargoMode(game, true) }
    }
    if (!uplink) Body("Cargo rockets unlock with the Earth Uplink.", 11, Pal.dim)
    Spacer(Modifier.height(6.dp))
    val need = game.rocketSegments
    val segHave = game.inventory[Items.ROCKET_SEGMENT]
    SlotRow(Items.ROCKET_SEGMENT, "${b.input[Items.ROCKET_SEGMENT]} loaded • needs $need • you have $segHave", "ADD", segHave > 0) {
        val k = minOf(need * 2 - b.input[Items.ROCKET_SEGMENT], segHave).coerceAtLeast(0)
        game.inventory.add(Items.ROCKET_SEGMENT, -k); b.input.add(Items.ROCKET_SEGMENT, k)
    }
    if (!b.cargoMode) {
        val have = game.inventory[Items.SATELLITE]
        SlotRow(Items.SATELLITE, "${b.input[Items.SATELLITE]} loaded • you have $have", "ADD", have > 0) {
            val k = minOf(2 - b.input[Items.SATELLITE], have).coerceAtLeast(0)
            game.inventory.add(Items.SATELLITE, -k); b.input.add(Items.SATELLITE, k)
        }
        Spacer(Modifier.height(6.dp))
        Title("OUTPUT", 10, Pal.teal)
        val n = b.output[Items.SCI_A]
        SlotRow(Items.SCI_A, "$n ready", "TAKE", n > 0) { game.inventory.add(Items.SCI_A, n); b.output.add(Items.SCI_A, -n) }
    } else {
        Spacer(Modifier.height(6.dp))
        Title("CARGO HOLD  ${b.cargo.total()} / ${game.cargoCapacity}", 10, Pal.amber)
        Items.EXPORTS.forEach { e ->
            val have = game.inventory[e]
            SlotRow(e, "${b.cargo[e]} aboard • you have $have", "LOAD", have > 0 && b.cargo.total() < game.cargoCapacity) {
                val k = minOf(game.cargoCapacity - b.cargo.total(), have).coerceAtLeast(0)
                game.inventory.add(e, -k); b.cargo.add(e, k)
            }
        }
        Spacer(Modifier.height(4.dp))
        Body("Lifts off automatically when the hold is full.", 11, Pal.dim)
        Spacer(Modifier.height(6.dp))
        GButton("LAUNCH NOW", Modifier.fillMaxWidth(), Pal.amber, enabled = b.readyToLaunch(game) && !b.launchNow) { b.launchNow = true }
    }
    Spacer(Modifier.height(6.dp)); Body("Rockets launched: ${b.launches}", 12, Pal.dim)
}

// ------------------------------------------------------------------------------------------- exports + map
@Composable
fun ExportsPanel(game: Game, modifier: Modifier, onClose: () -> Unit) {
    live()
    @Suppress("UNUSED_VARIABLE") val tk = game.stats.cargoLaunches
    Panel(modifier.width(470.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GameIcon("rocket", 34.dp); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Title("EARTH EXPORTS", 16, Pal.amber)
                    Body("No quota. Earth will take everything you send.", 11, Pal.dim)
                }
                GButton("CLOSE", onClick = onClose)
            }
            Spacer(Modifier.height(10.dp))
            val lines = listOf(
                Triple(Items.EPOXY_CANISTER, "canisters", "Super-strong alien epoxy for Earth's engineers"),
                Triple(Items.XENOCRETE_SACK, "sacks", "Xenocrete cement to rebuild Earth's cities"),
                Triple(Items.VITAE_CRYOCASE, "cryo-cases", "${fmtLong(game.livesSaved)} people freed from aging and disease"),
            )
            lines.forEach { (item, unit, flavor) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Pal.panel2).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(item.id, 42.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Body(item.name, 13, weight = FontWeight.Bold)
                        Body(flavor, 11, Pal.dim)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Title(fmtLong(game.stats.shipped(item)), 16, Pal.teal)
                        Body(unit, 10, Pal.dim)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Body("Cargo rockets: ${game.stats.cargoLaunches}", 12, Pal.text, FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Body("Hold size: ${game.cargoCapacity}", 12, Pal.dim)
                Spacer(Modifier.width(16.dp))
                Body("Segments per rocket: ${game.rocketSegments}", 12, Pal.dim)
            }
            if (!game.researched.contains("signal")) {
                Spacer(Modifier.height(8.dp)); Body("Research the Earth Uplink to open trade with Earth.", 12, Pal.amber)
            }
        }
    }
}

@Composable
fun MapPanel(game: Game, cam: Pair<Float, Float>, modifier: Modifier, onHub: () -> Unit = {}, onJump: (Float, Float) -> Unit) {
    live()
    val w = game.world
    val img = remember(game) { buildMapBitmap(game).asImageBitmap() }
    var local by remember { mutableStateOf(true) }
    Panel(modifier.fillMaxHeight(.92f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
                // view window in tiles: whole planet, or a close-up around the camera
                val span = if (local) 48f else w.w.toFloat()
                val ox = if (local) (cam.first - span / 2).coerceIn(0f, w.w - span) else 0f
                val oy = if (local) (cam.second - span / 2).coerceIn(0f, w.h - span) else 0f
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().pointerInput(game, local, ox, oy) {
                    detectTapGestures { o -> onJump(ox + o.x / size.width * span, oy + o.y / size.height * span) }
                }) {
                    drawImage(
                        img,
                        srcOffset = androidx.compose.ui.unit.IntOffset((ox * MAP_SCALE).toInt(), (oy * MAP_SCALE).toInt()),
                        srcSize = androidx.compose.ui.unit.IntSize((span * MAP_SCALE).toInt(), (span * MAP_SCALE).toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None
                    )
                    val k = size.width / span
                    fun at(x: Float, y: Float) = androidx.compose.ui.geometry.Offset((x - ox) * k, (y - oy) * k)
                    drawCircle(Color.White, 5.dp.toPx(), at(cam.first, cam.second), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    val core = game.buildings.firstOrNull { it.type.kind == BKind.CORE }
                    val hx = core?.let { it.x + it.size / 2f } ?: w.cx.toFloat()
                    val hy = core?.let { it.y + it.size / 2f } ?: w.cy.toFloat()
                    val r = if (local) 4.5f * k else 7.dp.toPx()
                    drawCircle(Color.Black, r, at(hx, hy), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                    drawCircle(Pal.amber, r, at(hx, hy), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(170.dp).verticalScroll(rememberScrollState())) {
                Title("XYRA-7", 15, Pal.teal)
                Body("Tap anywhere to fly there.", 11, Pal.dim)
                Spacer(Modifier.height(8.dp))
                GButton("⌂ RETURN TO HUB", Modifier.fillMaxWidth(), Pal.amber, onClick = onHub)
                Spacer(Modifier.height(6.dp))
                GButton(if (local) "VIEW: LOCAL" else "VIEW: PLANET", Modifier.fillMaxWidth(), Pal.teal) { local = !local }
                Spacer(Modifier.height(10.dp))
                MAP_LEGEND.forEach { (c, label) ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c))); Spacer(Modifier.width(6.dp)); Body(label, 11)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).border(2.dp, Color.White, CircleShape)); Spacer(Modifier.width(6.dp)); Body("You are here", 11)
                }
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).border(2.dp, Pal.amber, CircleShape)); Spacer(Modifier.width(6.dp)); Body("Lander Core (hub)", 11)
                }
            }
        }
    }
}

private val MAP_LEGEND = listOf(
    0xFFFFB238 to "Solanite (rare)", 0xFF9FE0B8 to "Terravite (rare)", 0xFFFF5FA8 to "Vitae spring (rare)",
    0xFF4A86FF to "Ferrite", 0xFFFF8A4A to "Cuprite", 0xFFF2EBD0 to "Lumite", 0xFF202028 to "Carbonite",
    0xFFB57BFF to "Aether", 0xFF6BFF5A to "Xenite", 0xFFFFD040 to "Glowsap", 0xFFFF5A1A to "Geothermal vent",
    0xFF3FF2E0 to "Your machines", 0xFFFFC96B to "Your belts", 0xFF8CFF7A to "Your power", 0xFFFF7FE0 to "Your labs",
)

/** Map pixels per tile - lets buildings get a crisp dark outline. */
const val MAP_SCALE = 3

private fun mapBuildingColor(b: Building): Int = when {
    b.type.kind == BKind.CORE -> 0xFFFFFFFF.toInt()
    b is Belt || b is Splitter -> 0xFFFFC96B.toInt()
    b is Inserter -> 0xFFE0F8FF.toInt()
    b is Generator || b is Lamp -> 0xFF8CFF7A.toInt()
    b is Lab -> 0xFFFF7FE0.toInt()
    b is Chest -> 0xFFC9B6FF.toInt()
    else -> 0xFF3FF2E0.toInt()
}

fun buildMapBitmap(game: Game): android.graphics.Bitmap {
    val w = game.world
    val px = IntArray(w.w * w.h)
    val ground = intArrayOf(0xFF4A4458.toInt(), 0xFF2F5A55.toInt(), 0xFF2C2D44.toInt(), 0xFF6A4E6E.toInt(), 0xFF0E5A5C.toInt())
    for (i in px.indices) {
        val r = w.res[i].toInt()
        px[i] = when {
            r == com.factonic.game.world.Res.SOLANITE -> 0xFFFFB238.toInt()
            r == com.factonic.game.world.Res.TERRAVITE -> 0xFF9FE0B8.toInt()
            r == com.factonic.game.world.Res.VITAE -> 0xFFFF5FA8.toInt()
            r == 1 -> 0xFF4A86FF.toInt(); r == 2 -> 0xFFFF8A4A.toInt(); r == 3 -> 0xFFF2EBD0.toInt(); r == 4 -> 0xFF202028.toInt()
            r == 5 -> 0xFFB57BFF.toInt(); r == 6 -> 0xFF6BFF5A.toInt(); r == 7 -> 0xFFFFD040.toInt(); r == 8 -> 0xFFFF5A1A.toInt()
            else -> ground[w.ground[i].toInt().coerceIn(0, 4)]
        }
    }
    // make the rare deposits and vents easier to spot: paint a halo around them
    val tiles = px.copyOf()
    for (y in 0 until w.h) for (x in 0 until w.w) {
        val r = w.res[y * w.w + x].toInt()
        if (r == com.factonic.game.world.Res.SOLANITE || r == com.factonic.game.world.Res.TERRAVITE || r == com.factonic.game.world.Res.VITAE || r == 8) {
            for (dy in -2..2) for (dx in -2..2) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w.w && yy in 0 until w.h && w.res[yy * w.w + xx].toInt() == 0) tiles[yy * w.w + xx] = px[y * w.w + x]
            }
        }
    }
    // darken the terrain slightly so the factory pops
    for (i in tiles.indices) if (w.res[i].toInt() == 0) tiles[i] = scaleRgb(tiles[i], .8f)
    val S = MAP_SCALE
    val W = w.w * S
    val out = IntArray(W * w.h * S)
    for (y in 0 until w.h) for (x in 0 until w.w) {
        val c = tiles[y * w.w + x]
        for (sy in 0 until S) for (sx in 0 until S) out[(y * S + sy) * W + x * S + sx] = c
    }
    // buildings: bright fill, dark outline where the footprint meets open ground or another building
    // (belt runs stay one continuous stroke)
    val black = 0xFF06040C.toInt()
    for (b in game.buildings) {
        val col = mapBuildingColor(b)
        for (dy in 0 until b.size) for (dx in 0 until b.size) {
            val tx = b.x + dx; val ty = b.y + dy
            fun edge(nx: Int, ny: Int): Boolean {
                val n = w.at(nx, ny) ?: return true
                if (n === b) return false
                return !(n is Belt && b is Belt)
            }
            val l = edge(tx - 1, ty); val r = edge(tx + 1, ty); val u = edge(tx, ty - 1); val d = edge(tx, ty + 1)
            for (sy in 0 until S) for (sx in 0 until S) {
                val isEdge = (l && sx == 0) || (r && sx == S - 1) || (u && sy == 0) || (d && sy == S - 1)
                out[(ty * S + sy) * W + tx * S + sx] = if (isEdge) black else col
            }
        }
    }
    return android.graphics.Bitmap.createBitmap(out, W, w.h * S, android.graphics.Bitmap.Config.ARGB_8888)
}

private fun scaleRgb(c: Int, k: Float): Int {
    val r = ((c shr 16 and 255) * k).toInt(); val g = ((c shr 8 and 255) * k).toInt(); val b = ((c and 255) * k).toInt()
    return (c and -0x1000000) or (r shl 16) or (g shl 8) or b
}

private fun addTo(game: Game, b: Crafter, item: Item, n: Int) {
    val k = minOf(n, game.inventory[item])
    if (k <= 0) return
    game.inventory.add(item, -k); b.input.add(item, k)
}

@Composable
fun FlowRow(count: Int, perRow: Int = 6, cell: @Composable (Int) -> Unit) {
    Column {
        var i = 0
        while (i < count) {
            Row { for (k in i until minOf(count, i + perRow)) cell(k) }
            i += perRow
        }
    }
}

@Composable
fun LabUi(game: Game, b: Lab, ui: UiState) {
    live()
    val t = game.currentTech
    if (t == null) {
        Body("No research selected.", 12, Pal.amber)
        Spacer(Modifier.height(6.dp))
        GButton("OPEN RESEARCH", color = Pal.pink) { ui.panel = PanelKind.RESEARCH }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIcon(t.icon, 30.dp); Spacer(Modifier.width(6.dp)); Body(t.name, 12, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
    }
    Title("SCIENCE PACKS", 10, Pal.amber)
    Items.SCIENCES.forEach { s ->
        val need = t?.packs?.contains(s) == true
        if (need || b.packs[s] > 0) {
            val have = game.inventory[s]
            SlotRow(s, "${b.packs[s]} loaded  •  you have $have", "ADD", have > 0) {
                val k = minOf(10 - b.packs[s], have).coerceAtLeast(0)
                game.inventory.add(s, -k); b.packs.add(s, k)
            }
        }
    }
}

@Composable
fun ChestUi(game: Game, b: Chest) {
    live()
    Row {
        GButton("TAKE ALL") {
            b.items.stacks().forEach { game.inventory.add(it.item, it.n) }; b.items.clear()
        }
    }
    Spacer(Modifier.height(6.dp))
    b.items.stacks().forEach { s ->
        SlotRow(s.item, "${s.n} stored", "TAKE") { game.inventory.add(s.item, s.n); b.items.add(s.item, -s.n) }
    }
    if (b.items.isEmpty()) Body("Empty", 12, Pal.dim)
}

@Composable
fun GeneratorUi(game: Game, b: Generator) {
    live()
    Body("Current output: ${fmtKw(maxOf(0f, b.output))} of ${fmtKw(b.maxOutput)}", 12)
    if (b.type.kind == BKind.COMBUSTION) {
        Spacer(Modifier.height(6.dp))
        val have = game.inventory[Items.CARBON]
        SlotRow(Items.CARBON, "${b.fuel[Items.CARBON]} / 20 loaded  •  you have $have", "ADD", have > 0) {
            val k = minOf(20 - b.fuel[Items.CARBON], have).coerceAtLeast(0)
            game.inventory.add(Items.CARBON, -k); b.fuel.add(Items.CARBON, k)
        }
    }
    if (b.type.kind == BKind.ACCUMULATOR) Body("Stored: ${(b.energy / 1000f).let { String.format("%.1f", it) }} / 5.0 MJ", 12)
    Spacer(Modifier.height(8.dp))
    Body("Grid: ${fmtKw(game.powerDemand)} used of ${fmtKw(game.powerCapacity)} available", 11, Pal.dim)
}

@Composable
fun DrillUi(game: Game, b: Drill) {
    live()
    var total = 0
    val kinds = HashMap<String, Int>()
    for (dy in 0 until b.size) for (dx in 0 until b.size) {
        val i = game.world.idx(b.x + dx, b.y + dy)
        val r = game.world.res[i].toInt()
        com.factonic.game.world.Res.item(r)?.let { kinds[it.id] = (kinds[it.id] ?: 0) + game.world.amount[i]; total += game.world.amount[i] }
    }
    kinds.forEach { (id, n) ->
        val item = Items[id]
        SlotRow(item, if (b.sap) "Infinite spring" else "$n remaining underneath", null) {}
    }
    b.out?.let { o ->
        SlotRow(o, "Waiting at the output chute", "TAKE") { game.inventory.add(o, 1); b.out = null }
    }
    Body("Speed: ${String.format("%.2f", b.type.speed * game.miningMul)}/s. Output faces ${arrowFor(b.dir)}.", 11, Pal.dim)
}

@Composable
fun SplitterUi(game: Game, b: Splitter) {
    live()
    Title("FILTER", 10, Pal.amber)
    Spacer(Modifier.height(4.dp))
    Body(b.filter?.let { "${it.name} leaves through the front (the pink arrow). Everything else goes out the sides." }
        ?: "No filter: items are shared evenly between every outgoing belt.", 11, Pal.dim)
    Spacer(Modifier.height(6.dp))
    // items the player has seen so far, so the list stays short
    val known = (game.stats.made.keys + game.inventory.map.keys).distinct().sortedBy { it.idx }
    val options = listOf<Item?>(null) + known
    FlowRow(options.size, perRow = 5) { i ->
        val o = options[i]
        val sel = o == b.filter
        Box(
            Modifier.padding(3.dp).size(42.dp).clip(RoundedCornerShape(8.dp))
                .background(if (sel) Pal.pink.copy(.25f) else Pal.panel2)
                .border(if (sel) 2.dp else 0.dp, if (sel) Pal.pink else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { Sfx.play("click"); b.filter = o },
            contentAlignment = Alignment.Center
        ) {
            if (o == null) Body("∅", 18, Pal.dim, FontWeight.Bold) else GameIcon(o.id, 32.dp)
        }
    }
    Spacer(Modifier.height(6.dp))
    Body("Rotate the splitter to choose which side is the front.", 11, Pal.dim)
}

@Composable
fun TunnelUi(b: Tunnel) {
    live()
    val p = b.partner
    if (p == null) {
        Body(
            if (b.entry) "This entrance has no exit yet. Place another Tunnel Belt facing the same way up to ${Tunnels.SPAN + 1} tiles ahead (${arrowFor(b.dir)})."
            else "This exit lost its entrance. Place a Tunnel Belt facing the same way up to ${Tunnels.SPAN + 1} tiles behind it.", 12, Pal.amber
        )
    } else {
        val dist = kotlin.math.abs(p.x - b.x) + kotlin.math.abs(p.y - b.y)
        Body(if (b.entry) "Entrance. Items surface $dist tiles ahead (${arrowFor(b.dir)})." else "Exit. Items come up from the entrance $dist tiles behind.", 12)
        Spacer(Modifier.height(4.dp))
        Body("Rotate either end to reverse the whole tunnel.", 11, Pal.dim)
    }
}

@Composable
fun Fade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) { content() }
}

@Suppress("unused")
private val unusedBrush = Brush.linearGradient(listOf(Color.Black, Color.White))
