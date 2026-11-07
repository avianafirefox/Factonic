package com.factonic.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.factonic.game.audio.Music
import com.factonic.game.audio.Sfx
import com.factonic.game.render.GameView
import com.factonic.game.render.GameViewListener
import com.factonic.game.render.Sprites
import com.factonic.game.sim.Building
import com.factonic.game.sim.Game
import com.factonic.game.sim.SaveIO
import com.factonic.game.sim.Tool
import com.factonic.game.ui.*
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    private val saveFile by lazy { File(filesDir, "factonic.sav") }
    private val backupFile by lazy { File(filesDir, "factonic.sav.bak") }
    private var game by mutableStateOf<Game?>(null)
    private var gameView: GameView? = null
    private var inGame by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        Settings.load(this)
        Sprites.load(this)
        Sfx.init(this)
        Music.init(this)
        setContent {
            FactonicTheme {
                val g = game
                if (inGame && g != null) GameScreen(g) else TitleScreen(
                    hasSave = saveFile.exists() || backupFile.exists(),
                    onContinue = { continueGame() },
                    onNew = { newGame() }
                )
            }
        }
    }

    private fun continueGame() {
        SaveIO.awaitIdle()
        val loaded = Game.load(saveFile)
        if (loaded != null) {
            Game.loadNote?.let { loaded.message(it) }
            game = loaded
        } else {
            // never overwrite a save we couldn't read: keep it next to the new one
            if (saveFile.exists()) saveFile.renameTo(File(filesDir, "factonic.sav.broken"))
            val g = Game.newGame(System.currentTimeMillis())
            g.message("Your save could not be read, so a new planet was started. The old file was kept as factonic.sav.broken.")
            game = g
        }
        inGame = true
    }

    private fun newGame() {
        SaveIO.awaitIdle()
        saveFile.delete(); backupFile.delete()
        game = Game.newGame(System.currentTimeMillis())
        inGame = true
    }

    /** Snapshots the game on the UI thread; compression and disk writes happen in the background. */
    private fun save() {
        val g = game ?: return
        if (!inGame) return
        try {
            SaveIO.writeAsync(saveFile, g.snapshot())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView?.appPaused = true
        save()
        Music.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameView?.appPaused = false
        Music.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Music.release()
    }

    /** Back closes whatever is on top: dialogs, panels, the selection, the tool - then opens the menu. */
    private fun onBack(g: Game, ui: UiState) {
        when {
            ui.victoryOpen -> ui.victoryOpen = false
            ui.panel == PanelKind.HELP || ui.panel == PanelKind.SETTINGS -> ui.panel = PanelKind.MENU
            ui.panel != PanelKind.NONE -> ui.panel = PanelKind.NONE
            ui.selected != null -> ui.selected = null
            g.tool != Tool.None -> g.tool = Tool.None
            ui.hudHidden -> ui.hudHidden = false
            else -> ui.panel = PanelKind.MENU
        }
        ui.tick++
    }

    @Composable
    private fun GameScreen(g: Game) {
        val ui = remember(g) { UiState(g) }
        LaunchedEffect(g) {
            while (true) {
                delay(60_000); save()
            }
        }
        BackHandler { onBack(g, ui) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            key(g) {
                AndroidView(factory = { ctx ->
                    GameView(ctx, g).also { v ->
                        gameView = v
                        v.keepScreenOn = Settings.keepAwake
                        v.listener = object : GameViewListener {
                            override fun onSelect(b: Building?) {
                                ui.selected = b
                            }

                            override fun onToast(msg: String) = ui.toast(msg)
                            override fun onToolCleared() { ui.tick++; Live.tick++ }
                            override fun onToolPicked(id: String) { ui.use(id); ui.tick++; Live.tick++ }
                        }
                    }
                }, modifier = Modifier.fillMaxSize())
            }
            LaunchedEffect(ui.panel, ui.selected, ui.hudHidden) {
                gameView?.menuPaused = ui.panel == PanelKind.MENU || ui.panel == PanelKind.HELP || ui.panel == PanelKind.SETTINGS
                gameView?.selected = ui.selected
                gameView?.overlays = !ui.hudHidden
            }
            val awake = Settings.keepAwake
            LaunchedEffect(awake) { gameView?.keepScreenOn = awake }
            GameHud(
                g, ui,
                onSave = { save() },
                onExit = { inGame = false; gameView = null },
                onNew = { newGame() },
                centerOn = { x, y -> gameView?.centerOn(x, y) },
                camPos = { gameView?.let { it.camX to it.camY } ?: (g.world.cx.toFloat() to g.world.cy.toFloat()) },
                onHub = { gameView?.goHome() },
                flyTo = { x, y -> gameView?.flyTo(x, y) }
            )
        }
    }
}

@Composable
fun TitleScreen(hasSave: Boolean, onContinue: () -> Unit, onNew: () -> Unit) {
    var help by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    BackHandler(enabled = help || settings || confirmNew) {
        help = false; settings = false; confirmNew = false
    }
    val inf = rememberInfiniteTransition(label = "title")
    val pan by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse), label = "pan")
    val glow by inf.animateFloat(.6f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "glow")
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        Image(
            painterResource(R.drawable.title_bg), null,
            Modifier.fillMaxSize().graphicsScale(1.08f, pan), contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF00B0818), .45f to Color(0x990B0818), .75f to Color(0x000B0818))
            )
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 56.dp).width(360.dp)) {
            Text(
                "FACTONIC", fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = 52.sp, color = Color.White,
                letterSpacing = 4.sp,
                style = TextStyle(shadow = Shadow(Pal.teal.copy(alpha = glow), Offset(0f, 0f), 28f))
            )
            Body("AUTOMATE AN ALIEN WORLD", 14, Pal.teal, FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Body("Stranded on planet Xyra-7. Build an industry, master six sciences and reach Earth - then ship the planet's three wonders home by the rocketload.", 13, Pal.dim)
            Spacer(Modifier.height(22.dp))
            if (hasSave) {
                GButton("CONTINUE", Modifier.fillMaxWidth(), Pal.teal, onClick = onContinue)
                Spacer(Modifier.height(10.dp))
            }
            when {
                !hasSave -> GButton("LAND ON XYRA-7", Modifier.fillMaxWidth(), Pal.amber, onClick = onNew)
                !confirmNew -> GButton("NEW PLANET", Modifier.fillMaxWidth(), Pal.amber) { confirmNew = true }
                else -> GButton("TAP AGAIN - YOUR SAVED PLANET WILL BE LOST", Modifier.fillMaxWidth(), Pal.red) { confirmNew = false; onNew() }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                GButton("HOW TO PLAY", Modifier.weight(1f), Pal.text, filled = false) { help = true }
                Spacer(Modifier.width(10.dp))
                GButton("SETTINGS", Modifier.weight(1f), Pal.text, filled = false) { settings = true }
            }
            Spacer(Modifier.height(18.dp))
            Body("Xyra-7 hides three elements Earth has never seen. Refine them and launch every last drop home.", 11, Pal.amber, modifier = Modifier.alpha(.85f))
        }
        Body("v${BuildConfig.VERSION_NAME}", 10, Pal.dim, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
        if (help || settings) Box(Modifier.fillMaxSize().background(Color(0x99060410)))
        if (help) HelpPanel(Modifier.align(Alignment.Center)) { help = false }
        if (settings) SettingsPanel(Modifier.align(Alignment.Center)) { settings = false }
    }
}

private fun Modifier.graphicsScale(scale: Float, pan: Float) = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = (pan - .5f) * 60f)
)
