package com.ngstudio.inchperfect.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.ngstudio.inchperfect.ui.components.Pulse
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngstudio.inchperfect.BuildConfig
import com.ngstudio.inchperfect.R
import com.ngstudio.inchperfect.ads.Ads
import com.ngstudio.inchperfect.ads.Placement
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.audio.Track
import com.ngstudio.inchperfect.core.Achievement
import com.ngstudio.inchperfect.core.Economy
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.core.Haptics
import com.ngstudio.inchperfect.game.GameSession
import com.ngstudio.inchperfect.game.GameView
import com.ngstudio.inchperfect.game.SessionListener
import com.ngstudio.inchperfect.game.SessionState
import com.ngstudio.inchperfect.game.input.Controls
import com.ngstudio.inchperfect.game.level.Districts
import com.ngstudio.inchperfect.game.level.LEVEL_COUNT
import com.ngstudio.inchperfect.game.level.Level
import com.ngstudio.inchperfect.game.level.LevelGenerator
import com.ngstudio.inchperfect.game.level.Lighting
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.render.Quality
import com.ngstudio.inchperfect.game.render.Renderer
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GameDialog
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.CityBackground
import com.ngstudio.inchperfect.ui.components.ProgressBar
import com.ngstudio.inchperfect.ui.components.RoundButton
import com.ngstudio.inchperfect.ui.components.StarRow
import com.ngstudio.inchperfect.ui.components.click
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Loaded(val level: Level, val session: GameSession, val renderer: Renderer)

/** Plays one level. [onExit] returns to the menu; [onNext] opens the next level. */
@Composable
fun GameScreen(levelNumber: Int, onExit: () -> Unit, onNext: (Int) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    var loaded by remember(levelNumber) { mutableStateOf<Loaded?>(null) }
    val profile by GameRepo.profile.collectAsState()
    val settings by GameRepo.settings.collectAsState()
    var outcome by remember(levelNumber) { mutableStateOf<GameRepo.Outcome?>(null) }
    var retryKey by remember(levelNumber) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // ---- load level + build renderer off the main thread
    LaunchedEffect(levelNumber, retryKey) {
        loaded = null
        outcome = null
        val tStart = System.nanoTime()
        val typeface = ResourcesCompat.getFont(ctx, R.font.bungee)!!
        val l = withContext(Dispatchers.Default) { LevelGenerator.level(levelNumber) }
        android.util.Log.i("IPPerf", "L$levelNumber generated in %.0f ms (attempts ${l.attempts})".format((System.nanoTime() - tStart) / 1e6))
        val spec = Cars.byId(profile.selectedCar)
        val listener = object : SessionListener {
            override fun onCrash(speed: Double) { Audio.play(Sfx.CRASH, (0.6 + speed / 6).toFloat().coerceAtMost(1f)); Haptics.heavy() }
            override fun onCone() { Audio.play(Sfx.CONE); Haptics.medium() }
            override fun onCoin() { Audio.play(Sfx.COIN); Haptics.light() }
            override fun onGear(gear: Int) { Audio.play(Sfx.GEAR); Haptics.light() }
            override fun onSensor(level: Int) { if (level >= 2 && settings.sensors) Audio.play(Sfx.BEEP, 0.4f + level * 0.15f, 0.9f + level * 0.12f) }
            override fun onTable() { Audio.play(Sfx.TABLE) }
            override fun onRewind() { Audio.play(Sfx.REWIND) }
            override fun onParked(stars: Int) { Audio.play(Sfx.FANFARE); Haptics.medium() }
        }
        val session = GameSession(l, spec, profile.look(), listener).also { it.underglow = profile.customFor(spec.id).glow }
        val q = GameRepo.effectiveQuality()
        val r = Renderer(typeface, q, GameRepo.skin())
        val tPrep = System.nanoTime()
        withContext(Dispatchers.Default) { r.prepare(l) }
        android.util.Log.i("IPPerf", "L$levelNumber renderer prepared in %.0f ms".format((System.nanoTime() - tPrep) / 1e6))
        loaded = Loaded(l, session, r)
        Audio.playMusic(if (l.lighting == Lighting.NIGHT) Track.NIGHT else Track.GAME)
        Audio.startEngine(spec.engine)
    }

    // pause when the app goes to background
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, loaded) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) { loaded?.session?.pause(); Audio.stopEngine(); Audio.pauseMusic() }
            if (e == Lifecycle.Event.ON_RESUME && loaded != null) { Audio.resumeMusic(); Audio.startEngine(Cars.byId(profile.selectedCar).engine) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); Audio.stopEngine() }
    }

    Box(Modifier.fillMaxSize().background(Palette.Midnight)) {
        val ld = loaded
        if (ld == null) {
            LoadingCard(levelNumber)
        } else {
            val session = ld.session
            val hud by session.hud.collectAsState()
            // engine audio follows the car
            LaunchedEffect(session) {
                while (true) {
                    val c = session.world.car
                    Audio.speed = c.v
                    Audio.throttle = session.world.input.throttle
                    Audio.rpm = 850.0 + kotlin.math.abs(c.v) * 520 + session.world.input.throttle * 900
                    delay(33)
                }
            }
            androidx.compose.runtime.key(session) { AndroidView(
                factory = { context ->
                    val typeface = ResourcesCompat.getFont(context, R.font.bungee)!!
                    val controls = Controls(session.controls, typeface, settings.controls()) { kind -> if (kind == Controls.FB_GEAR) { Audio.play(Sfx.GEAR, 0.7f); Haptics.light() } else Haptics.light() }
                    com.ngstudio.inchperfect.game.input.ActiveInput.controls = controls
                    com.ngstudio.inchperfect.game.input.ActiveInput.state = session.controls
                    GameView(context, session, ld.renderer, controls, settings.fps, settings.quality == "auto") { _: Quality -> }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = {
                    it.stop()
                    com.ngstudio.inchperfect.game.input.ActiveInput.controls = null
                    com.ngstudio.inchperfect.game.input.ActiveInput.state = null
                },
            ) }
            Hud(ld.level, session, hud, profile.rewind, profile.ghost, profile.slowmo, settings.showFps, onPause = { session.pause(); Audio.play(Sfx.WHOOSH) }, onRestart = { retryKey++ })
            androidx.activity.compose.BackHandler {
                when (hud.state) {
                    SessionState.PAUSED -> session.resume()
                    SessionState.PARKED, SessionState.CRASHED -> onExit()
                    else -> { session.pause(); Audio.play(Sfx.WHOOSH) }
                }
            }
            IntroCard(ld.level, hud.state == SessionState.INTRO && ld.level.newMechanic == null) { session.skipIntro() }

            // ---- outcome handling
            LaunchedEffect(hud.state) {
                if (hud.state == SessionState.PARKED && outcome == null) {
                    val w = session.world
                    outcome = GameRepo.recordWin(ld.level.number, session.stars, w.runTime, w.precision >= 90, w.coinsCollected, w.car.odometer, w.car.reverseDist, w.conesHit)
                    GameRepo.addPlayTime(w.runTime)
                }
                if (hud.state == SessionState.CRASHED && !ld.level.tutorial) {
                    GameRepo.recordCrash(session.world.car.odometer, session.world.car.reverseDist)
                }
            }

            when (hud.state) {
                SessionState.PAUSED -> PauseDialog(ld.level, onResume = { session.resume() }, onRestart = { retryKey++ }, onLevels = onExit, onSettings = onSettings)
                SessionState.CRASHED -> if (!ld.level.tutorial) CrashDialog(hud.crashKind,
                    hasRewind = profile.rewind > 0,
                    onRewind = { if (GameRepo.useBooster("rewind")) { GameRepo.noteRewind(); session.useRewind() } },
                    onAdRewind = { Ads.rewarded.show(Placement.REWIND, { GameRepo.noteRewind(); session.useRewind() }) },
                    onRetry = { retryKey++ }, onMenu = onExit,
                ) else OopsBanner()
                SessionState.PARKED -> outcome?.let { o ->
                    ResultDialog(ld.level, session, o,
                        onNext = { if (ld.level.number < LEVEL_COUNT) onNext(ld.level.number + 1) else onExit() },
                        onRetry = { retryKey++ }, onMenu = onExit,
                        onDouble = { Ads.rewarded.show(Placement.DOUBLE_COINS, { GameRepo.addCoins(o.reward.coins); Audio.play(Sfx.PURCHASE) }) })
                }
                else -> {}
            }
            // first-time mechanic card
            val newMech = ld.level.newMechanic
            var showMech by remember(ld) { mutableStateOf(newMech != null) }
            if (showMech && newMech != null && hud.state != SessionState.PARKED) {
                GameDialog(onDismiss = { showMech = false; session.skipIntro() }, glow = Palette.Lime) {
                    Column(Modifier.widthIn(max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NEW!", style = Type.Small.copy(color = Palette.Lime, fontSize = 14.sp, letterSpacing = 4.sp))
                        Text(newMech.title.uppercase(), style = Type.Title.copy(color = Palette.Text), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(newMech.blurb, style = Type.Body.copy(color = Palette.TextDim, fontSize = 16.sp), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        ChunkyButton("GOT IT", Modifier.width(200.dp), color = Palette.Lime, textColor = Palette.Midnight) { showMech = false; session.skipIntro() }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(n: Int) {
    Box(Modifier.fillMaxSize()) {
        CityBackground(intensity = 0.7f)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            val d = Districts.of(n)
            Text(d.name.uppercase(), style = Type.Small.copy(color = Color(d.tint.toLong() or 0xFF000000), letterSpacing = 4.sp, fontSize = 13.sp))
            Text("LEVEL $n", style = Type.Hero.copy(color = Palette.Text))
            Spacer(Modifier.height(12.dp))
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) { a.animateTo(1f, tween(900)) }
            ProgressBar(a.value, Modifier.width(220.dp))
        }
    }
}

// ================================================================== HUD
@Composable
private fun Hud(level: Level, s: GameSession, hud: com.ngstudio.inchperfect.game.Hud, rewinds: Int, ghosts: Int, slows: Int, showFps: Boolean, onPause: () -> Unit, onRestart: () -> Unit) {
    val acc = LocalAccent.current
    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // top-left: pause + boosters in one row (keeps the left edge free for the controls)
        Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Ic.PAUSE, onClick = onPause)
            Spacer(Modifier.width(4.dp))
            RoundButton(Ic.REWIND, size = 40.dp, badge = rewinds.toString(), enabled = rewinds > 0 && hud.state == SessionState.PLAYING) {
                if (GameRepo.useBooster("rewind")) { GameRepo.noteRewind(); s.useRewind() }
            }
            RoundButton(Ic.GHOST, size = 40.dp, badge = if (hud.ghost) "ON" else ghosts.toString(), enabled = ghosts > 0 && !hud.ghost) {
                if (GameRepo.useBooster("ghost")) s.startGhost()
            }
            RoundButton(Ic.SLOW, size = 40.dp, badge = slows.toString(), enabled = slows > 0 && !hud.slowMo) {
                if (GameRepo.useBooster("slowmo")) s.startSlowMo(12.0)
            }
            if (BuildConfig.DEBUG_TOOLS) RoundButton(Ic.SPARK, size = 40.dp, accent = Palette.Magenta, badge = if (hud.autoSolve) "ON" else "AI") { s.toggleAutoSolve() }
        }
        // top-centre: level, timer, star thresholds
        GlassPanel(Modifier.align(Alignment.TopCenter).offset(x = 40.dp).width(230.dp), padding = 8.dp, corner = 18.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LEVEL ${level.number} · ${level.name.uppercase()}", style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val over = hud.time > hud.t3
                    Text("%d:%04.1f".format((hud.time / 60).toInt(), hud.time % 60), style = Type.Number.copy(color = if (hud.time > hud.t2) Palette.Red else if (over) Palette.Orange else Palette.Text, fontSize = 20.sp))
                    Spacer(Modifier.width(10.dp))
                    StarRow(hud.starsNow, 16.dp)
                }
                // star threshold bar
                val frac = (hud.time / hud.t2).toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth(0.85f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0A0E22))) {
                    Box(Modifier.fillMaxWidth(1f - frac).height(5.dp).background(Brush.horizontalGradient(listOf(Palette.Lime, Palette.Gold))))
                }
            }
        }
        // top-right: restart + peek
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var peek by remember { mutableStateOf(false) }
            RoundButton(Ic.EYE, accent = if (peek) Palette.Lime else acc.primary) { peek = !peek; s.peekOn(peek) }
            RoundButton(Ic.RESTART, onClick = onRestart)
        }
        // turntable rotate button
        AnimatedVisibility(hud.canRotate, Modifier.align(Alignment.Center).offset(y = 60.dp), enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            ChunkyButton("ROTATE", Modifier.width(180.dp), color = Palette.Gold, textColor = Palette.Midnight, icon = Ic.ROTATE) { s.rotateTable() }
        }
        // tutorial hint
        val hint = hud.hint
        AnimatedVisibility(hint != null && hud.state == SessionState.PLAYING, Modifier.align(Alignment.BottomCenter).offset(x = 24.dp, y = (-22).dp), enter = slideInVertically { it } + fadeIn(), exit = fadeOut()) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE60B1026)).border(1.5.dp, acc.primary.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(hint ?: "", style = Type.BodyBold.copy(color = Palette.Text, fontSize = 14.sp), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
            }
        }
        if (hud.cones > 0 && hud.state == SessionState.PLAYING) {
            Row(Modifier.align(Alignment.TopCenter).offset(y = 74.dp, x = 150.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC3A1A0A)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("CONES ${hud.cones}", style = Type.Small.copy(color = Palette.Orange))
            }
        }
        if (showFps) Text("${hud.speedKmh} km/h · ${s.fps.toInt()} fps", style = Type.Small.copy(color = Palette.TextDim), modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun OopsBanner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(glow = Palette.Orange) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OOPS!", style = Type.Title.copy(color = Palette.Orange))
                Text("Rewinding. Practice levels are on the house.", style = Type.Body.copy(color = Palette.Text))
            }
        }
    }
}

// ============================================================= dialogs
@Composable
private fun PauseDialog(level: Level, onResume: () -> Unit, onRestart: () -> Unit, onLevels: () -> Unit, onSettings: () -> Unit) {
    val settings by GameRepo.settings.collectAsState()
    GameDialog(onDismiss = onResume) {
        Column(Modifier.width(360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PAUSED", style = Type.Title.copy(color = Palette.Text))
            Text("Level ${level.number} · ${level.name}", style = Type.Body.copy(color = Palette.TextDim))
            Spacer(Modifier.height(16.dp))
            ChunkyButton("RESUME", Modifier.fillMaxWidth(), color = Palette.Lime, textColor = Palette.Midnight, icon = Ic.PLAY, onClick = onResume)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("RESTART", Modifier.weight(1f), color = Color(0xFF3A86FF), icon = Ic.RESTART, height = 48.dp, onClick = onRestart)
                ChunkyButton("LEVELS", Modifier.weight(1f), color = Color(0xFF4A5378), icon = Ic.LEVELS, height = 48.dp, onClick = onLevels)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Ic.MUSIC, accent = if (settings.music > 0) Palette.Lime else Palette.Red) {
                    GameRepo.updateSettings { it.copy(music = if (it.music > 0) 0f else 0.55f) }; Audio.musicVolume = GameRepo.settings.value.music; Audio.applyMusicVolume()
                }
                RoundButton(Ic.SOUND, accent = if (settings.sfx > 0) Palette.Lime else Palette.Red) {
                    GameRepo.updateSettings { it.copy(sfx = if (it.sfx > 0) 0f else 0.9f) }; Audio.sfxVolume = GameRepo.settings.value.sfx
                }
                RoundButton(Ic.VIBRATE, accent = if (settings.haptics) Palette.Lime else Palette.Red) { GameRepo.updateSettings { it.copy(haptics = !it.haptics) } }
                RoundButton(Ic.SETTINGS, onClick = onSettings)
            }
        }
    }
}

@Composable
private fun CrashDialog(kind: String, hasRewind: Boolean, onRewind: () -> Unit, onAdRewind: () -> Unit, onRetry: () -> Unit, onMenu: () -> Unit) {
    val (what, tip) = when (kind) {
        "traffic" -> "You hit passing traffic" to "Stop at the line and wait for a clear gap."
        "car" -> "You scraped a parked car" to "Use the sensors: red arcs mean under 30 cm."
        "truck" -> "You clipped a truck" to "Long vehicles: swing wide before turning in."
        "bollard" -> "A bollard came up" to "Watch the bollards and go when they drop."
        "pillar" -> "You hit a pillar" to "Peek (eye button) to see the whole garage first."
        "kerb" -> "You mounted the kerb" to "Straighten up earlier when parallel parking."
        "hedge" -> "You drove into a hedge" to "Turn later and use reverse to fix the angle."
        "container" -> "You hit a container" to "Creep forward: no throttle means slow roll."
        "edge" -> "You left the lot" to "Follow the arrow at the screen edge to the bay."
        else -> "You hit a wall" to "Brake earlier: every inch counts."
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(650); shown = true }
    if (!shown) return
    GameDialog(onDismiss = null, glow = Palette.Red) {
        Column(Modifier.width(360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CRASHED!", style = Type.Hero.copy(color = Palette.Red, fontSize = 30.sp))
            Text(what, style = Type.BodyBold.copy(color = Palette.Text, fontSize = 15.sp))
            Text("Tip: $tip", style = Type.Body.copy(color = Palette.TextDim, fontSize = 13.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (hasRewind) {
                ChunkyButton("REWIND 3s", Modifier.fillMaxWidth(), color = Palette.Cyan, textColor = Palette.Midnight, icon = Ic.REWIND, sound = Sfx.REWIND, onClick = onRewind)
                Spacer(Modifier.height(10.dp))
            } else if (Ads.rewarded.available) {
                ChunkyButton("WATCH AD · REWIND", Modifier.fillMaxWidth(), color = Palette.Cyan, textColor = Palette.Midnight, icon = Ic.REWIND, onClick = onAdRewind)
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChunkyButton("RETRY", Modifier.weight(1f), color = Palette.Orange, icon = Ic.RESTART, onClick = onRetry)
                ChunkyButton("MENU", Modifier.weight(1f), color = Color(0xFF4A5378), icon = Ic.HOME, onClick = onMenu)
            }
        }
    }
}

@Composable
private fun ResultDialog(level: Level, s: GameSession, o: GameRepo.Outcome, onNext: () -> Unit, onRetry: () -> Unit, onMenu: () -> Unit, onDouble: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var starsShown by remember { mutableIntStateOf(0) }
    var doubled by remember { mutableStateOf(false) }
    val precision = s.world.precision
    val perfect = precision >= 90
    LaunchedEffect(Unit) {
        delay(900); shown = true
        for (i in 1..s.stars) { delay(380); starsShown = i; Audio.play(when (i) { 1 -> Sfx.STAR1; 2 -> Sfx.STAR2; else -> Sfx.STAR3 }); Haptics.light() }
        if (perfect) { delay(300); Audio.play(Sfx.PERFECT) }
    }
    if (!shown) return
    GameDialog(onDismiss = null, glow = Palette.Lime) {
        Row(Modifier.width(600.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (perfect) "INCH PERFECT!" else "PARKED!", style = Type.Hero.copy(color = if (perfect) Palette.Gold else Palette.Lime, fontSize = 26.sp))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..3) {
                        val sc = remember { Animatable(0f) }
                        LaunchedEffect(starsShown >= i) { if (starsShown >= i) sc.animateTo(1f, spring(0.4f, Spring.StiffnessMedium)) }
                        Box(Modifier.size(if (i == 2) 56.dp else 46.dp).graphicsLayer { scaleX = 0.4f + 0.6f * sc.value; scaleY = 0.4f + 0.6f * sc.value }) {
                            Icon(Ic.STAR, if (starsShown >= i) Palette.Gold else Color(0xFF2A3154), if (i == 2) 56.dp else 46.dp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val coins = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(400); coins.animateTo(o.reward.coins.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.COIN, Palette.Gold, 28.dp); Spacer(Modifier.width(6.dp))
                    Text("+%,d".format(coins.value.toInt() * (if (doubled) 2 else 1)), style = Type.Title.copy(color = Palette.Gold))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (o.firstClear && level.number < LEVEL_COUNT) Pill("LEVEL ${level.number + 1} UNLOCKED", Palette.Lime)
                    else if (o.newBest) Pill("NEW BEST", Palette.Gold)
                }
                if (o.newAchievements.isNotEmpty()) AchievementToast(o.newAchievements.first())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1.15f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("TIME", "%.1fs".format(s.world.runTime), "3★ ≤ %.0fs".format(level.t3))
                    Stat("PRECISION", "${precision.toInt()}%", if (perfect) "+25% coins" else "90% = perfect")
                    Stat("CONES", s.world.conesHit.toString(), if (s.world.conesHit == 0) "clean!" else "-1★ each")
                }
                Spacer(Modifier.height(12.dp))
                if (Ads.rewarded.available && !doubled && o.reward.coins > 0) {
                    ChunkyButton("WATCH AD · x2 COINS", Modifier.fillMaxWidth(), color = Palette.Gold, textColor = Palette.Midnight, icon = Ic.COIN, height = 44.dp,
                        style = Type.Button.copy(fontSize = 15.sp)) { doubled = true; onDouble() }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChunkyButton("", Modifier.width(58.dp), color = Color(0xFF3A86FF), icon = Ic.RESTART, height = 50.dp, onClick = onRetry)
                    ChunkyButton("", Modifier.width(58.dp), color = Color(0xFF4A5378), icon = Ic.HOME, height = 50.dp, onClick = onMenu)
                    ChunkyButton("NEXT", Modifier.weight(1f), color = Palette.Lime, textColor = Palette.Midnight, icon = Ic.NEXT, shimmer = true, height = 50.dp, onClick = onNext)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(text, style = Type.Small.copy(color = Palette.Midnight, fontSize = 11.sp),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Cinematic title card shown during the fly-over. */
@Composable
private fun IntroCard(level: Level, visible: Boolean, onTap: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(250)) + slideInVertically { -it / 3 }, exit = fadeOut(tween(300))) {
        Box(Modifier.fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onTap() }, contentAlignment = Alignment.Center) {
            val d = Districts.of(level.number)
            val tint = Color(d.tint.toLong() or 0xFF000000)
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xD90B1026)).border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(horizontal = 34.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.name.uppercase()} · ${if (level.isMaster) "MASTER LEVEL" else if (level.isBoss) "BOSS LEVEL" else d.signature.uppercase()}",
                    style = Type.Small.copy(color = tint, letterSpacing = 3.sp, fontSize = 11.sp))
                Text("LEVEL ${level.number}", style = Type.Hero.copy(color = Palette.Text, fontSize = 36.sp))
                Text(level.name.uppercase(), style = Type.Section.copy(color = Palette.TextDim, fontSize = 14.sp, letterSpacing = 2.sp))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRow(3, 16.dp); Spacer(Modifier.width(6.dp))
                    Text("under %.0fs · no cones".format(level.t3), style = Type.Small.copy(color = Palette.Gold, fontSize = 12.sp))
                }
                Spacer(Modifier.height(8.dp))
                Pulse { v -> Text("TAP TO START", style = Type.Small.copy(color = Palette.Text.copy(alpha = 0.5f + 0.5f * v), letterSpacing = 3.sp)) }
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(98.dp)) {
        Text(title, style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp))
        Text(value, style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp))
        Text(sub, style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun AchievementToast(a: Achievement) {
    Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFC93C)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.TROPHY, Palette.Gold, 18.dp); Spacer(Modifier.width(8.dp))
        Text("Achievement: ${a.title}  +${a.reward}", style = Type.BodyBold.copy(color = Palette.Gold))
    }
}
