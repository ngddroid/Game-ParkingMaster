package com.ngstudio.inchperfect.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.core.Economy
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.game.level.Districts
import com.ngstudio.inchperfect.game.level.LEVELS_PER_DISTRICT
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.ui.components.CarPreview
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.CurrencyPill
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.CityBackground
import com.ngstudio.inchperfect.ui.components.ProgressBar
import com.ngstudio.inchperfect.ui.components.RoundButton
import com.ngstudio.inchperfect.ui.components.click
import com.ngstudio.inchperfect.ui.components.darker
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type

enum class Tab(val id: String, val label: String, val ic: Ic) {
    GARAGE("garage", "Garage", Ic.GARAGE), STORE("store", "Store", Ic.STORE), PLAY("play", "Play", Ic.PLAY),
    LEVELS("levels", "Levels", Ic.LEVELS), REWARDS("rewards", "Rewards", Ic.REWARDS),
}

@Composable
fun MainScreen(initialTab: String, onPlay: (Int) -> Unit, onSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.id == initialTab } ?: Tab.PLAY) }
    val profile by GameRepo.profile.collectAsState()
    GameRepo.progressVersion.collectAsState().value
    Box(Modifier.fillMaxSize()) {
        CityBackground(intensity = if (tab == Tab.PLAY) 1f else 0.55f)
        Column(Modifier.fillMaxSize()) {
            TopBar(profile.coins, GameRepo.availableStars, onSettings) { tab = Tab.STORE }
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 6.dp)) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                    when (t) {
                        Tab.PLAY -> PlayTab(onPlay) { tab = it }
                        Tab.LEVELS -> LevelsTab(onPlay)
                        Tab.GARAGE -> GarageTab()
                        Tab.STORE -> StoreTab()
                        Tab.REWARDS -> RewardsTab(onPlay)
                    }
                }
            }
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
private fun TopBar(coins: Int, stars: Int, onSettings: () -> Unit, onShop: () -> Unit) {
    val acc = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // rank badge
        val cleared = GameRepo.cleared
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(acc.primary, acc.secondary))), contentAlignment = Alignment.Center) {
            Icon(Ic.CROWN, Palette.Midnight, 24.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(Economy.rankTitle(cleared).uppercase(), style = Type.Section.copy(color = Palette.Text, fontSize = 14.sp))
            Text("%,d levels cleared".format(cleared), style = Type.Small.copy(color = Palette.TextDim))
        }
        Spacer(Modifier.weight(1f))
        CurrencyPill(Ic.STAR, stars, color = Palette.Gold)
        Spacer(Modifier.width(10.dp))
        CurrencyPill(Ic.COIN, coins, onPlus = onShop)
        Spacer(Modifier.width(12.dp))
        RoundButton(Ic.SETTINGS, size = 42.dp, onClick = onSettings)
    }
}

@Composable
private fun BottomBar(sel: Tab, onSelect: (Tab) -> Unit) {
    val acc = LocalAccent.current
    Box(Modifier.fillMaxWidth().height(68.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0x00070A1A), Color(0xF0070A1A)), 0f, size.height * 0.4f))
            drawLine(acc.primary.copy(alpha = 0.35f), Offset(size.width * 0.05f, size.height * 0.28f), Offset(size.width * 0.95f, size.height * 0.28f), 2f)
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
            for (t in Tab.entries) {
                val selected = t == sel
                if (t == Tab.PLAY) {
                    val inf = rememberInfiniteTransition(label = "play")
                    val glow by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1300), androidx.compose.animation.core.RepeatMode.Reverse), label = "g")
                    Box(Modifier.offset(y = (-8).dp).size(70.dp).clickable(remember { MutableInteractionSource() }, null) { click(); onSelect(t) }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Brush.radialGradient(listOf(acc.secondary.copy(alpha = 0.5f * glow), Color.Transparent)), size.minDimension * 0.7f)
                            drawCircle(acc.secondary.darker(0.4f), size.minDimension / 2 - 2f, center + Offset(0f, 5f))
                            drawCircle(Brush.verticalGradient(listOf(acc.secondary.copy(red = minOf(1f, acc.secondary.red + 0.15f)), acc.secondary)), size.minDimension / 2 - 4f)
                            drawCircle(Color.White.copy(alpha = 0.7f), size.minDimension / 2 - 4f, style = Stroke(3f))
                        }
                        Icon(Ic.PLAY, Color.White, 32.dp, Modifier.offset(x = 2.dp))
                    }
                } else {
                    val a by animateFloatAsState(if (selected) 1f else 0f, label = "sel")
                    Column(Modifier.width(84.dp).clickable(remember { MutableInteractionSource() }, null) { click(); onSelect(t) }.padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.graphicsLayer { scaleX = 1f + 0.12f * a; scaleY = 1f + 0.12f * a }) { Icon(t.ic, if (selected) acc.primary else Palette.TextDim, 26.dp) }
                        Text(t.label.uppercase(), style = Type.Small.copy(color = if (selected) acc.primary else Palette.TextDim, fontSize = 10.sp))
                        Box(Modifier.padding(top = 3.dp).width((22 * a).dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(acc.primary))
                    }
                }
            }
        }
    }
}

// ================================================================= PLAY
@Composable
private fun PlayTab(onPlay: (Int) -> Unit, goTab: (Tab) -> Unit) {
    val acc = LocalAccent.current
    val profile by GameRepo.profile.collectAsState()
    val missions by GameRepo.missions.collectAsState()
    val current = GameRepo.currentLevel()
    val district = Districts.of(current)
    val spec = Cars.byId(profile.selectedCar)
    val custom = profile.customFor(spec.id)
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        // showroom
        Box(Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val inf = rememberInfiniteTransition(label = "turn")
            val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "r")
            Canvas(Modifier.size(300.dp)) {
                drawCircle(Brush.radialGradient(listOf(acc.primary.copy(alpha = 0.32f), Color.Transparent)), size.minDimension / 2)
                drawCircle(Color(0xFF151B38), size.minDimension * 0.42f)
                drawCircle(acc.primary.copy(alpha = 0.55f), size.minDimension * 0.42f, style = Stroke(4f))
                for (k in 0 until 24) {
                    val a = Math.toRadians((k * 15 + rot).toDouble())
                    val r1 = size.minDimension * 0.38f; val r2 = size.minDimension * 0.42f
                    drawLine(acc.primary.copy(alpha = 0.3f), center + Offset((kotlin.math.cos(a) * r1).toFloat(), (kotlin.math.sin(a) * r1).toFloat()), center + Offset((kotlin.math.cos(a) * r2).toFloat(), (kotlin.math.sin(a) * r2).toFloat()), 2f)
                }
            }
            CarPreview(profile.look(), spec.length, spec.width, Modifier.size(260.dp), rotation = rot, glow = custom.glow, scaleM = 0.78f)
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(spec.name.uppercase(), style = Type.Title.copy(color = Palette.Text))
                Text(spec.tagline, style = Type.Small.copy(color = Palette.TextDim))
            }
        }
        // play column
        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            GlassPanel(Modifier.fillMaxWidth(), glow = Color(district.tint.toLong() or 0xFF000000)) {
                Column {
                    Text("DISTRICT ${district.index + 1} · ${district.signature.uppercase()}", style = Type.Small.copy(color = Color(district.tint.toLong() or 0xFF000000)))
                    Text(district.name.uppercase(), style = Type.Title.copy(color = Palette.Text))
                    Spacer(Modifier.height(6.dp))
                    val start = district.index * LEVELS_PER_DISTRICT + 1
                    var done = 0
                    for (i in start until start + LEVELS_PER_DISTRICT) if (GameRepo.stars[i] > 0) done++
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressBar(done / LEVELS_PER_DISTRICT.toFloat(), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("$done/$LEVELS_PER_DISTRICT", style = Type.Small.copy(color = Palette.TextDim))
                    }
                }
            }
            ChunkyButton("PLAY  LEVEL $current", Modifier.fillMaxWidth(), color = acc.secondary, icon = Ic.PLAY, height = 60.dp, shimmer = true,
                style = Type.Button.copy(fontSize = 22.sp)) { onPlay(current) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = GameRepo.today()
                MiniCard(Ic.CALENDAR, "DAILY", if (profile.loginClaimedDay != today) "Claim!" else "Done", Modifier.weight(1f), profile.loginClaimedDay != today) { goTab(Tab.REWARDS) }
                MiniCard(Ic.WHEEL, "SPIN", if (profile.spinDay != today) "Free!" else "Done", Modifier.weight(1f), profile.spinDay != today) { goTab(Tab.REWARDS) }
                val ready = missions.count { it.done && !it.claimed }
                MiniCard(Ic.TARGET, "TASKS", "${missions.count { it.done }}/${missions.size}", Modifier.weight(1f), ready > 0) { goTab(Tab.REWARDS) }
                val chestReady = profile.chestStars >= 50
                MiniCard(Ic.CHEST, "CHEST", if (chestReady) "Open!" else "${profile.chestStars}/50", Modifier.weight(1f), chestReady) {
                    if (chestReady) {
                        com.ngstudio.inchperfect.audio.Audio.play(com.ngstudio.inchperfect.audio.Sfx.CHEST)
                        GameRepo.update { it.copy(chestStars = it.chestStars - 50) }
                        GameRepo.addCoins(750); GameRepo.addBooster("rewind", 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCard(ic: Ic, title: String, sub: String, modifier: Modifier, hot: Boolean, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Box(modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC121936))
        .border(1.5.dp, if (hot) acc.secondary else acc.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
        .clickable { click(); onClick() }.padding(horizontal = 8.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Icon(ic, if (hot) acc.secondary else acc.primary, 22.dp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(title, style = Type.Small.copy(color = Palette.Text, fontSize = 10.sp), maxLines = 1)
                Text(sub, style = Type.Small.copy(color = if (hot) acc.secondary else Palette.TextDim, fontSize = 10.sp), maxLines = 1)
            }
        }
        if (hot) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp).size(8.dp).background(Palette.Red, CircleShape))
    }
}
