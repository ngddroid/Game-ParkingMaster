package com.ngstudio.inchperfect.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.ads.Ads
import com.ngstudio.inchperfect.ads.Placement
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.Achievements
import com.ngstudio.inchperfect.core.Economy
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.core.Haptics
import com.ngstudio.inchperfect.core.Missions
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.ProgressBar
import com.ngstudio.inchperfect.ui.components.Segmented
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type
import kotlinx.coroutines.launch

private enum class RTab(val title: String) { DAILY("Daily"), SPIN("Lucky Spin"), MISSIONS("Missions"), TROPHIES("Trophies"), STATS("Stats") }

@Composable
fun RewardsTab(onPlay: (Int) -> Unit) {
    var tab by remember { mutableStateOf(RTab.DAILY) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("REWARDS", style = Type.Title.copy(color = Palette.Text))
            Spacer(Modifier.width(16.dp))
            Segmented(RTab.entries.map { it to it.title }, tab, { tab = it })
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            RTab.DAILY -> Daily()
            RTab.SPIN -> Spin()
            RTab.MISSIONS -> MissionsList()
            RTab.TROPHIES -> Trophies()
            RTab.STATS -> StatsView()
        }
    }
}

@Composable
private fun Daily() {
    val acc = LocalAccent.current
    val p by GameRepo.profile.collectAsState()
    val today = GameRepo.today()
    val claimedToday = p.loginClaimedDay == today
    // streak continues if last claim was yesterday
    val streak = if (claimedToday) p.loginStreak else if (p.loginClaimedDay == today - 1) p.loginStreak else 0
    val dayIdx = if (claimedToday) (streak - 1).coerceAtLeast(0) % 7 else streak % 7
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until 7) {
            val past = i < dayIdx || (claimedToday && i == dayIdx)
            val now = i == dayIdx && !claimedToday
            Column(Modifier.weight(1f).height(112.dp).clip(RoundedCornerShape(18.dp))
                .background(if (now) acc.secondary.copy(alpha = 0.3f) else Color(0xE6121936))
                .border(if (now) 2.5.dp else 1.dp, if (now) acc.secondary else if (past) Palette.Lime.copy(alpha = 0.6f) else Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Text("DAY ${i + 1}", style = Type.Small.copy(color = Palette.TextDim))
                Icon(if (i == 6) Ic.CHEST else Ic.COIN, Palette.Gold, if (i == 6) 34.dp else 28.dp)
                Text("%,d".format(Economy.LOGIN_REWARDS[i]), style = Type.Number.copy(color = Palette.Text))
                if (past) Icon(Ic.CHECK, Palette.Lime, 18.dp) else Spacer(Modifier.height(18.dp))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        ChunkyButton(if (claimedToday) "COME BACK TOMORROW" else "CLAIM DAY ${dayIdx + 1}", Modifier.width(340.dp), height = 50.dp, color = Palette.Lime, textColor = Palette.Midnight,
            enabled = !claimedToday, shimmer = !claimedToday, sound = Sfx.CHEST) {
            val reward = Economy.LOGIN_REWARDS[dayIdx]
            GameRepo.addCoins(reward)
            if (dayIdx == 3) GameRepo.addBooster("rewind", 1)
            if (dayIdx == 6) { GameRepo.addBooster("ghost", 1); GameRepo.addBooster("slowmo", 1) }
            GameRepo.update { it.copy(loginClaimedDay = today, loginStreak = streak + 1) }
        }
    }
}

@Composable
private fun Spin() {
    val acc = LocalAccent.current
    val p by GameRepo.profile.collectAsState()
    val today = GameRepo.today()
    val free = p.spinDay != today
    val angle = remember { Animatable(0f) }
    var result by remember { mutableStateOf<String?>(null) }
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val prizes = Economy.SPIN
    val colors = listOf(acc.primary, acc.secondary, Palette.Magenta, Palette.Lime, Palette.Gold, Color(0xFF3A86FF), Color(0xFF7B61FF), Palette.Red)
    fun spin(paid: Boolean) {
        if (spinning) return
        spinning = true; result = null
        val total = prizes.sumOf { it.weight }
        var r = java.util.Random().nextDouble() * total
        var idx = 0
        for ((i, pr) in prizes.withIndex()) { r -= pr.weight; if (r <= 0) { idx = i; break } }
        val seg = 360f / prizes.size
        val target = angle.value - (angle.value % 360f) + 360f * 6 + (360f - (idx * seg + seg / 2))
        if (!paid) GameRepo.update { it.copy(spinDay = today) }
        scope.launch {
            var lastTick = angle.value
            angle.animateTo(target, tween(4200, easing = CubicBezierEasing(0.1f, 0.7f, 0.15f, 1f))) {
                if (value - lastTick > seg) { lastTick = value; Audio.play(Sfx.TICK, 0.6f); Haptics.light() }
            }
            val prize = prizes[idx]
            if (prize.coins > 0) GameRepo.addCoins(prize.coins)
            prize.booster?.let { GameRepo.addBooster(it, 1) }
            result = if (prize.coins > 0) "%,d coins".format(prize.coins) else "+1 ${prize.label}"
            Audio.play(Sfx.UNLOCK)
            spinning = false
        }
    }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val r = size.minDimension / 2
                rotate(angle.value) {
                    val seg = 360f / prizes.size
                    for (i in prizes.indices) {
                        drawArc(colors[i % colors.size].copy(alpha = 0.85f), -90f + i * seg, seg, true)
                        drawArc(Color(0xFF0B1026), -90f + i * seg, seg, true, style = Stroke(4f))
                    }
                    for (i in prizes.indices) {
                        val a = Math.toRadians((-90 + i * seg + seg / 2).toDouble())
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                            save(); translate(center.x + (kotlin.math.cos(a) * r * 0.64).toFloat(), center.y + (kotlin.math.sin(a) * r * 0.64).toFloat())
                            rotate((Math.toDegrees(a) + 90).toFloat())
                            drawText(prizes[i].label, 0f, 10f, paint); restore()
                        }
                    }
                }
                drawCircle(Color.White, r, style = Stroke(8f))
                drawCircle(Color(0xFF0B1026), r * 0.16f)
                drawCircle(acc.primary, r * 0.16f, style = Stroke(5f))
                val tip = Path().apply { moveTo(center.x - 18f, 0f); lineTo(center.x + 18f, 0f); lineTo(center.x, 34f); close() }
                drawPath(tip, Palette.Red)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LUCKY SPIN", style = Type.Hero.copy(color = Palette.Text, fontSize = 26.sp))
            Text("One free spin every day", style = Type.Body.copy(color = Palette.TextDim))
            Spacer(Modifier.height(14.dp))
            ChunkyButton(if (free) "SPIN FREE" else "COME BACK TOMORROW", Modifier.width(300.dp), color = Palette.Gold, textColor = Palette.Midnight, enabled = free && !spinning, shimmer = free, sound = Sfx.SPIN) { spin(false) }
            if (!free && Ads.rewarded.available) {
                Spacer(Modifier.height(10.dp))
                ChunkyButton("WATCH AD · SPIN AGAIN", Modifier.width(300.dp), color = Palette.Lime, textColor = Palette.Midnight, enabled = !spinning, height = 46.dp) { Ads.rewarded.show(Placement.FREE_SPIN, { spin(true) }) }
            }
            result?.let { Text("YOU WON $it!", style = Type.Title.copy(color = Palette.Gold), modifier = Modifier.padding(top = 14.dp)) }
        }
    }
}

@Composable
private fun MissionsList() {
    val list by GameRepo.missions.collectAsState()
    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DAILY MISSIONS · reset at midnight", style = Type.Small.copy(color = Palette.TextDim))
        list.forEachIndexed { i, m ->
            GlassPanel(Modifier.fillMaxWidth(), padding = 8.dp, glow = if (m.done && !m.claimed) Palette.Lime else LocalAccent.current.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.TARGET, if (m.done) Palette.Lime else Palette.TextDim, 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = Type.BodyBold.copy(color = Palette.Text, fontSize = 16.sp))
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressBar(m.progress / m.target.toFloat(), Modifier.weight(1f), color = Palette.Lime, height = 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("${m.progress}/${m.target}", style = Type.Small.copy(color = Palette.TextDim))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    ChunkyButton(if (m.claimed) "DONE" else "+${m.reward}", Modifier.width(120.dp), color = if (m.done && !m.claimed) Palette.Lime else Color(0xFF3A4060),
                        textColor = if (m.done && !m.claimed) Palette.Midnight else Color.White, enabled = m.done && !m.claimed, icon = if (m.claimed) Ic.CHECK else Ic.COIN, height = 44.dp, sound = Sfx.COIN) {
                        Missions.claim(GameRepo, i)
                    }
                }
            }
        }
    }
}

@Composable
private fun Trophies() {
    val got by GameRepo.achievements.collectAsState()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${got.size} / ${Achievements.ALL.size} UNLOCKED", style = Type.Small.copy(color = Palette.TextDim)) }
        items(Achievements.ALL) { a ->
            val ok = a.id in got
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ok) Color(0x33FFC93C) else Color(0xE6121936)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.TROPHY, if (ok) Palette.Gold else Color(0xFF3A4370), 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title.uppercase(), style = Type.Section.copy(color = if (ok) Palette.Gold else Palette.Text, fontSize = 13.sp))
                    Text(a.desc, style = Type.Small.copy(color = Palette.TextDim))
                }
                Text("+${a.reward}", style = Type.Number.copy(color = if (ok) Palette.Lime else Palette.TextDim, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun StatsView() {
    val s by GameRepo.stats.collectAsState()
    val rows = listOf(
        "Levels cleared" to "%,d".format(GameRepo.cleared), "Stars earned" to "%,d".format(GameRepo.totalStars), "Parks" to "%,d".format(s.parks),
        "Inch Perfect parks" to "%,d".format(s.perfects), "Crashes" to "%,d".format(s.crashes), "Cones knocked" to "%,d".format(s.cones),
        "Distance driven" to "%.1f km".format(s.distance / 1000), "Reversed" to "%.0f m".format(s.reverse), "Coins earned" to "%,d".format(s.coinsEarned),
        "Time parking" to "%d min".format((s.playSeconds / 60).toInt()),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (col in rows.chunked(5)) {
            GlassPanel(Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, v) in col) Row {
                        Text(k, style = Type.Body.copy(color = Palette.TextDim), modifier = Modifier.weight(1f))
                        Text(v, style = Type.Number.copy(color = Palette.Text, fontSize = 15.sp))
                    }
                }
            }
        }
    }
}
