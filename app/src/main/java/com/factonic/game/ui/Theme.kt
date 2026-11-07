package com.factonic.game.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.factonic.game.audio.Sfx
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.factonic.game.R
import com.factonic.game.render.Sprites

/**
 * Global refresh signal for the HUD. The simulation lives outside Compose, so widgets that show
 * game numbers call [live] to re-read them on every tick (Compose would otherwise skip them,
 * because the Game object itself never changes identity).
 */
object Live {
    var tick by mutableIntStateOf(0)
}

@Composable
fun live(): Int = Live.tick

/** Progress value that glides between HUD refreshes instead of jumping (and snaps back when a cycle restarts). */
@Composable
fun smooth(target: Float): Float {
    val a = remember { Animatable(target.coerceIn(0f, 1f)) }
    LaunchedEffect(target) {
        val t = target.coerceIn(0f, 1f)
        if (t < a.value - .02f) a.snapTo(t) else a.animateTo(t, tween(140, easing = LinearEasing))
    }
    return a.value
}

/** Rounded progress bar with an optional label on the left and percentage on the right. */
@Composable
fun ProgressBar(
    fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp,
    label: String? = null, right: String? = null,
) {
    val f = smooth(fraction)
    androidx.compose.foundation.layout.Column(modifier) {
        if (label != null || right != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (label != null) Text(label, color = Pal.dim, fontFamily = Exo, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                else Spacer(Modifier.weight(1f))
                Text(right ?: "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%", color = color, fontFamily = Exo, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
        }
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Pal.panel2).border(1.dp, Pal.line, RoundedCornerShape(height / 2))) {
            if (f > 0.002f) Box(
                Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = .55f), color)))
            )
        }
    }
}

/** Small square icon button used for panel controls (minimise, close, ...). */
@Composable
fun IconBtn(glyph: String, color: Color = Pal.dim, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Pal.panel2).border(1.dp, Pal.line, RoundedCornerShape(8.dp))
            .clickable { Sfx.play("click"); onClick() },
        contentAlignment = Alignment.Center
    ) { Text(glyph, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp) }
}

object Pal {
    val bg = Color(0xFF0E0B1C)
    val panel = Color(0xF21A1530)
    val panel2 = Color(0xFF241D40)
    val line = Color(0xFF3A3060)
    val teal = Color(0xFF3FF2E0)
    val amber = Color(0xFFFFB84A)
    val pink = Color(0xFFFF5AD8)
    val red = Color(0xFFFF4A5A)
    val green = Color(0xFF7DFF9A)
    val text = Color(0xFFEDE8FF)
    val dim = Color(0xFF9C93C2)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Orbitron = FontFamily(
    Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.orbitron, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
    Font(R.font.orbitron, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Exo = FontFamily(
    Font(R.font.exo2, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.exo2, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.exo2, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@Composable
fun FactonicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Pal.teal, secondary = Pal.amber, background = Pal.bg, surface = Pal.panel),
        typography = MaterialTheme.typography.copy(
            bodyMedium = TextStyle(fontFamily = Exo, fontSize = 14.sp, color = Pal.text),
        ),
        content = content
    )
}

object Icons {
    private val cache = HashMap<String, ImageBitmap>()
    fun get(name: String): ImageBitmap? {
        cache[name]?.let { return it }
        val b: Bitmap = Sprites[name] ?: return null
        return b.asImageBitmap().also { cache[name] = it }
    }
}

@Composable
fun GameIcon(name: String, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val img = remember(name) { Icons.get(name) }
    if (img != null) Image(img, null, modifier.size(size), alpha = alpha, filterQuality = FilterQuality.High)
    else Box(modifier.size(size))
}

@Composable
fun Title(text: String, size: Int = 16, color: Color = Pal.text, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, color = color, fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = 1.sp)

@Composable
fun Body(text: String, size: Int = 13, color: Color = Pal.text, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, color = color, fontFamily = Exo, fontWeight = weight, fontSize = size.sp, lineHeight = (size + 4).sp)

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF2221A3E), Color(0xF2140F27))))
            .border(BorderStroke(1.dp, Pal.line), RoundedCornerShape(14.dp)),
        content = content
    )
}

@Composable
fun GButton(
    text: String, modifier: Modifier = Modifier, color: Color = Pal.teal, enabled: Boolean = true,
    filled: Boolean = true, onClick: () -> Unit
) {
    val c = if (enabled) color else Pal.dim.copy(alpha = .5f)
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) c.copy(alpha = .18f) else Color.Transparent)
            .border(1.5.dp, c, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { Sfx.play("click"); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = c, fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun ToolButton(
    icon: String?, label: String, active: Boolean = false, accent: Color = Pal.teal, glyph: String? = null,
    modifier: Modifier = Modifier, progress: Float? = null, badge: String? = null, onClick: () -> Unit
) {
    Box(
        modifier
            .size(50.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) accent.copy(alpha = .25f) else Color(0xE61A1530))
            .border(if (active) 2.dp else 1.dp, if (active) accent else Pal.line, RoundedCornerShape(11.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Sfx.play("click"); onClick() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) GameIcon(icon, 26.dp)
            if (glyph != null) Text(glyph, color = if (active) accent else Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text(label, color = if (active) accent else Pal.dim, fontFamily = Exo, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, lineHeight = 10.sp)
        }
        if (progress != null) {
            val f = smooth(progress)
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp, vertical = 3.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Pal.panel2)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(accent))
            }
        }
        if (badge != null) Box(
            Modifier.align(Alignment.TopEnd).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(accent).padding(horizontal = 4.dp)
        ) { Text(badge, color = Pal.bg, fontFamily = Exo, fontWeight = FontWeight.Bold, fontSize = 8.sp, lineHeight = 10.sp) }
    }
}

/** A small pill switch in the game's palette. */
@Composable
fun Toggle(on: Boolean) {
    Box(
        Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) Pal.teal.copy(alpha = .35f) else Pal.bg)
            .border(1.5.dp, if (on) Pal.teal else Pal.line, RoundedCornerShape(12.dp)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(horizontal = 3.dp).size(17.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Pal.teal else Pal.dim))
    }
}

@Composable
fun Chip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE61A1530))
            .border(1.dp, Pal.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

fun fmtCount(n: Int): String = when {
    n >= 10000 -> "${n / 1000}k"
    n >= 1000 -> String.format("%.1fk", n / 1000f)
    else -> n.toString()
}
