package com.factonic.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.factonic.game.audio.Sfx
import com.factonic.game.data.Recipes
import com.factonic.game.data.Tech
import com.factonic.game.data.Techs
import com.factonic.game.sim.Game

private val NODE_W = 168.dp
private val NODE_H = 62.dp
private val COL_W = 210.dp
private val ROW_H = 78.dp

@Composable
fun ResearchPanel(game: Game, ui: UiState, modifier: Modifier) {
    var sel by remember { mutableStateOf(game.currentTech ?: Techs.all.firstOrNull { game.techAvailable(it) }) }
    @Suppress("UNUSED_VARIABLE") val tk = ui.tick
    val maxTier = Techs.all.maxOf { it.tier }
    val maxRow = Techs.all.maxOf { it.row }
    Panel(modifier.fillMaxWidth(.96f).fillMaxHeight(.9f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Row(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("RESEARCH TREE", 15, Pal.pink)
                    Spacer(Modifier.width(14.dp))
                    com.factonic.game.data.Items.SCIENCES.forEach { GameIcon(it.id, 22.dp) }
                    Spacer(Modifier.weight(1f))
                    Body("${game.researched.size} / ${Techs.all.size} researched", 12, Pal.dim)
                }
                Spacer(Modifier.height(8.dp))
                val d = LocalDensity.current
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0x66000000))
                        .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                ) {
                    val w = COL_W * maxTier + 20.dp
                    val h = ROW_H * (maxRow + 1) + 20.dp
                    Box(Modifier.size(w, h).padding(10.dp)) {
                        Canvas(Modifier.matchParentSize()) {
                            for (t in Techs.all) for (p in t.prereqs) {
                                val pt = Techs[p]
                                val x0 = with(d) { (COL_W * (pt.tier - 1) + NODE_W).toPx() }
                                val y0 = with(d) { (ROW_H * pt.row + NODE_H / 2).toPx() }
                                val x1 = with(d) { (COL_W * (t.tier - 1)).toPx() }
                                val y1 = with(d) { (ROW_H * t.row + NODE_H / 2).toPx() }
                                val path = Path().apply {
                                    moveTo(x0, y0); cubicTo((x0 + x1) / 2, y0, (x0 + x1) / 2, y1, x1, y1)
                                }
                                val col = when {
                                    game.researched.contains(t.id) -> Pal.teal.copy(.7f)
                                    game.researched.contains(p) -> Pal.amber.copy(.6f)
                                    else -> Color(0x553A3060)
                                }
                                drawPath(path, col, style = Stroke(width = 2.5f * density))
                                drawCircle(col, 3.5f * density, Offset(x1, y1))
                            }
                        }
                        for (t in Techs.all) {
                            TechNode(game, t, t == sel, Modifier.offset(COL_W * (t.tier - 1), ROW_H * t.row)) { sel = t }
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Row {
                    Spacer(Modifier.weight(1f)); GButton("CLOSE", color = Pal.dim) { ui.panel = PanelKind.NONE }
                }
                Spacer(Modifier.height(8.dp))
                sel?.let { TechDetail(game, it) }
                ResearchQueue(game) { sel = it }
            }
        }
    }
}

@Composable
private fun TechNode(game: Game, t: Tech, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val done = game.researched.contains(t.id)
    val avail = game.techAvailable(t)
    val active = game.currentTech == t
    val queuePos = game.researchQueue.indexOf(t)
    val border = when {
        selected -> Color.White
        active -> Pal.pink
        done -> Pal.teal
        avail -> Pal.amber
        else -> Pal.line
    }
    val bg = when {
        done -> Color(0xFF173A3E)
        active -> Color(0xFF3A1A40)
        avail -> Color(0xFF2E2440)
        else -> Color(0xFF1A1630)
    }
    Row(
        modifier.size(NODE_W, NODE_H).clip(RoundedCornerShape(10.dp)).background(bg)
            .border(if (selected || active) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            GameIcon(t.icon, 40.dp, alpha = if (avail || done) 1f else .45f)
            if (queuePos >= 0) Box(
                Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(6.dp)).background(Pal.amber).padding(horizontal = 4.dp)
            ) { Body("${queuePos + 1}", 9, Pal.bg, FontWeight.Bold) }
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Body(t.name, 11, if (avail || done) Pal.text else Pal.dim, FontWeight.SemiBold)
            Row { t.packs.forEach { GameIcon(it.id, 14.dp) } }
            if (active || (!done && game.progressOf(t) > 0)) {
                Box(Modifier.padding(top = 2.dp).width(100.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF241D40))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(game.progressOf(t) / t.count.toFloat()).background(Pal.pink))
                }
            }
        }
    }
}

@Composable
private fun TechDetail(game: Game, t: Tech) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameIcon(t.icon, 56.dp)
        Spacer(Modifier.width(10.dp))
        Title(t.name, 14)
    }
    Spacer(Modifier.height(6.dp))
    Body(t.desc, 12, Pal.dim)
    Spacer(Modifier.height(10.dp))
    Title("COST", 10, Pal.amber)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        t.packs.forEach { GameIcon(it.id, 26.dp) }
        Spacer(Modifier.width(6.dp))
        Body("× ${t.count}", 13, weight = FontWeight.Bold)
    }
    Body("${t.unitTime.toInt()}s per unit per lab  •  ${"%.1f".format(t.totalTime / 60f)} lab-min", 11, Pal.dim)
    Spacer(Modifier.height(10.dp))
    if (t.unlocks.isNotEmpty()) {
        Title("UNLOCKS", 10, Pal.teal)
        Spacer(Modifier.height(4.dp))
        t.unlocks.forEach { id ->
            val r = Recipes.byId[id] ?: return@forEach
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                GameIcon(r.main.id, 26.dp); Spacer(Modifier.width(6.dp)); Body(r.name, 12)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (t.prereqs.isNotEmpty()) {
        Title("REQUIRES", 10, Pal.dim)
        t.prereqs.forEach { p ->
            val pt = Techs[p]
            Body((if (game.researched.contains(p)) "✓ " else "• ") + pt.name, 12, if (game.researched.contains(p)) Pal.teal else Pal.red)
        }
        Spacer(Modifier.height(10.dp))
    }
    val queued = game.researchQueue.contains(t)
    when {
        game.researched.contains(t.id) -> Body("Researched", 13, Pal.teal, FontWeight.Bold)
        game.currentTech == t -> {
            Body("In progress: ${game.progressOf(t)} / ${t.count}", 13, Pal.pink, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            GButton("STOP", color = Pal.dim) { game.stopResearch() }
        }

        game.techAvailable(t) && game.currentTech == null -> GButton("START RESEARCH", Modifier.fillMaxWidth(), Pal.pink) { game.startResearch(t) }
        game.techAvailable(t) -> {
            GButton("RESEARCH NOW", Modifier.fillMaxWidth(), Pal.pink) { game.startResearch(t) }
            Spacer(Modifier.height(6.dp))
            QueueButton(game, t, queued)
            Spacer(Modifier.height(4.dp))
            Body("Research now pauses ${game.currentTech?.name} and puts it back in the queue.", 10, Pal.dim)
        }
        else -> {
            QueueButton(game, t, queued)
            Spacer(Modifier.height(4.dp))
            Body("Queuing a locked technology queues its missing prerequisites first.", 10, Pal.dim)
        }
    }
}

@Composable
private fun QueueButton(game: Game, t: Tech, queued: Boolean) {
    if (queued) GButton("REMOVE FROM QUEUE", Modifier.fillMaxWidth(), Pal.dim) { game.dequeueResearch(t) }
    else GButton("ADD TO QUEUE", Modifier.fillMaxWidth(), Pal.amber) { game.queueResearch(t) }
}

@Composable
private fun ResearchQueue(game: Game, onSelect: (Tech) -> Unit) {
    if (game.researchQueue.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Title("QUEUE", 10, Pal.amber)
        Spacer(Modifier.weight(1f))
        Body("CLEAR", 10, Pal.red, FontWeight.Bold, Modifier.clickable { Sfx.play("click"); game.researchQueue.clear() })
    }
    Spacer(Modifier.height(4.dp))
    game.researchQueue.toList().forEachIndexed { i, q ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF241D40))
                .clickable { onSelect(q) }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Body("${i + 1}", 11, Pal.amber, FontWeight.Bold, Modifier.width(18.dp))
            GameIcon(q.icon, 24.dp)
            Spacer(Modifier.width(6.dp))
            Body(q.name, 11, if (game.techAvailable(q)) Pal.text else Pal.dim, modifier = Modifier.weight(1f))
            Body("✕", 13, Pal.dim, FontWeight.Bold, Modifier.clickable { Sfx.play("click"); game.dequeueResearch(q) }.padding(horizontal = 4.dp))
        }
    }
}
