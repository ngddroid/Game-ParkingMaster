package com.ngstudio.inchperfect.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.BuildConfig
import com.ngstudio.inchperfect.ads.Ads
import com.ngstudio.inchperfect.ads.Placement
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.core.Haptics
import com.ngstudio.inchperfect.game.level.Districts
import com.ngstudio.inchperfect.game.level.LEVELS_PER_DISTRICT
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GameDialog
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.ProgressBar
import com.ngstudio.inchperfect.ui.components.StarRow
import com.ngstudio.inchperfect.ui.components.click
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun LevelsTab(onPlay: (Int) -> Unit) {
    val acc = LocalAccent.current
    GameRepo.progressVersion.collectAsState().value
    val current = GameRepo.currentLevel()
    var district by remember { mutableIntStateOf((current - 1) / LEVELS_PER_DISTRICT) }
    var locked by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { rowState.scrollToItem(maxOf(0, district - 1)) }
    LaunchedEffect(district) {
        val start = district * LEVELS_PER_DISTRICT + 1
        val idx = if (current in start until start + LEVELS_PER_DISTRICT) current - start else 0
        gridState.scrollToItem(maxOf(0, idx - 20))
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        // district list
        Column(Modifier.width(230.dp).fillMaxHeight()) {
            androidx.compose.foundation.lazy.LazyColumn(state = rowState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                itemsIndexed(Districts.ALL) { i, d ->
                    val start = i * LEVELS_PER_DISTRICT + 1
                    val unlocked = GameRepo.isUnlocked(start)
                    var done = 0
                    for (k in start until start + LEVELS_PER_DISTRICT) if (GameRepo.stars[k] > 0) done++
                    val tint = Color(d.tint.toLong() or 0xFF000000)
                    val sel = i == district
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(tint.copy(alpha = if (sel) 0.45f else 0.18f), Color(0xCC121936))))
                        .border(if (sel) 2.dp else 1.dp, tint.copy(alpha = if (sel) 1f else 0.35f), RoundedCornerShape(16.dp))
                        .clickable { click(); district = i }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Column(Modifier.align(Alignment.CenterStart)) {
                            Text("${i + 1}. ${d.name.uppercase()}", style = Type.Small.copy(color = Palette.Text, fontSize = 12.sp), maxLines = 1)
                            Text(d.signature, style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp))
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(done / LEVELS_PER_DISTRICT.toFloat(), Modifier.fillMaxWidth(0.8f), color = tint, height = 5.dp)
                        }
                        if (!unlocked) Box(Modifier.align(Alignment.CenterEnd)) { Icon(Ic.LOCK, Palette.TextDim, 20.dp) }
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        // level grid
        Column(Modifier.weight(1f)) {
            val d = Districts.ALL[district]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.name.uppercase(), style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp))
                Spacer(Modifier.width(10.dp))
                Text("LEVELS ${district * LEVELS_PER_DISTRICT + 1}–${(district + 1) * LEVELS_PER_DISTRICT}", style = Type.Small.copy(color = Palette.TextDim))
                Spacer(Modifier.weight(1f))
                ChunkyButton("CURRENT", Modifier.width(190.dp), color = acc.primary, textColor = Palette.Midnight, height = 40.dp, icon = Ic.TARGET) {
                    val dd = (current - 1) / LEVELS_PER_DISTRICT
                    district = dd
                    scope.launch { gridState.animateScrollToItem(maxOf(0, current - dd * LEVELS_PER_DISTRICT - 1 - 10)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(GridCells.Adaptive(66.dp), state = gridState, horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(LEVELS_PER_DISTRICT, key = { it }) { k ->
                    val n = district * LEVELS_PER_DISTRICT + k + 1
                    LevelTile(n, n == current, GameRepo.isUnlocked(n), GameRepo.stars[n].toInt()) {
                        if (GameRepo.isUnlocked(n)) onPlay(n) else {
                            Audio.play(Sfx.LOCKED); Haptics.medium()
                            if (BuildConfig.ADS_SIMULATED) locked = n else toast = "Finish level ${n - 1} to unlock level $n"
                        }
                    }
                }
            }
        }
    }
    locked?.let { n -> UnlockDialog(n, onClose = { locked = null }, onUnlocked = { locked = null; onPlay(n) }) }
    toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(1800); toast = null }
        Box(Modifier.fillMaxSize().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
            Text(msg, style = Type.BodyBold.copy(color = Palette.Text), modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xEE1A2248)).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun LevelTile(n: Int, current: Boolean, unlocked: Boolean, stars: Int, onClick: () -> Unit) {
    val acc = LocalAccent.current
    val boss = n % 10 == 0; val master = n % 100 == 0
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val base = when {
        !unlocked -> Palette.Locked
        master -> Palette.Gold
        boss -> Palette.Magenta
        stars > 0 -> acc.primary.copy(alpha = 0.9f)
        else -> acc.secondary
    }
    Box(Modifier.aspectRatio(1f).graphicsLayer { translationX = shake.value }
        .clip(RoundedCornerShape(16.dp))
        .background(Brush.verticalGradient(listOf(base.copy(alpha = if (unlocked) 0.95f else 0.7f), base.copy(alpha = if (unlocked) 0.6f else 0.5f))))
        .border(if (current) 3.dp else 1.dp, if (current) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        .clickable {
            click()
            if (!unlocked) scope.launch { for (x in listOf(10f, -10f, 6f, -6f, 0f)) shake.animateTo(x, tween(45)) }
            onClick()
        }) {
        if (unlocked) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(n.toString(), style = Type.Number.copy(color = if (master || (boss && false)) Palette.Midnight else Color.White, fontSize = if (n >= 1000) 14.sp else 18.sp))
                StarRow(stars, 11.dp, spacing = 1.dp, dim = Color(0x44000000))
            }
            if (boss) Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { Icon(if (master) Ic.CROWN else Ic.SPARK, Color.White, 13.dp) }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Ic.LOCK, Palette.TextDim, 20.dp)
                Text(n.toString(), style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp))
            }
        }
    }
}

/** Debug-only rewarded-ad unlock (real ads will plug into the same flow later). */
@Composable
private fun UnlockDialog(n: Int, onClose: () -> Unit, onUnlocked: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    GameDialog(onDismiss = if (loading) null else onClose, glow = Palette.Gold) {
        Column(Modifier.width(470.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.LOCK, Palette.Gold, 30.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("LEVEL $n IS LOCKED", style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp))
                    Text("Watch a short ad to unlock it now, or finish level ${n - 1} first.", style = Type.Body.copy(color = Palette.TextDim, fontSize = 13.sp))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(50.dp)) {
                    CircularProgressIndicator(color = Palette.Gold, modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading ad…", style = Type.BodyBold.copy(color = Palette.Text))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChunkyButton("NOT NOW", Modifier.weight(1f), color = Color(0xFF4A5378), height = 50.dp, onClick = onClose)
                    ChunkyButton("WATCH AD", Modifier.weight(1.3f), color = Palette.Gold, textColor = Palette.Midnight, icon = Ic.PLAY, height = 50.dp) {
                        loading = true
                        Ads.rewarded.show(Placement.UNLOCK_LEVEL, onReward = {
                            GameRepo.unlockByAd(n); Audio.play(Sfx.UNLOCK); onUnlocked()
                        }, onFail = { loading = false })
                    }
                }
            }
            if (BuildConfig.DEBUG) Text("Debug build · simulated ad", style = Type.Small.copy(color = Palette.TextDim), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
