package com.ngstudio.inchperfect.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.ads.Ads
import com.ngstudio.inchperfect.ads.Placement
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.Economy
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.core.UiThemes
import com.ngstudio.inchperfect.game.render.Decal
import com.ngstudio.inchperfect.game.render.WorldSkin
import com.ngstudio.inchperfect.ui.components.CarPreview
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GameDialog
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.Segmented
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type

private enum class STab(val title: String) { SKINS("World Skins"), THEMES("UI Themes"), BOOSTERS("Boosters"), CHESTS("Chests") }

@Composable
fun StoreTab() {
    val acc = LocalAccent.current
    val profile by GameRepo.profile.collectAsState()
    var tab by remember { mutableStateOf(STab.SKINS) }
    var msg by remember { mutableStateOf<String?>(null) }
    var chestPrize by remember { mutableStateOf<String?>(null) }
    var offer by remember { mutableStateOf<com.ngstudio.inchperfect.ui.components.Offer?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STORE", style = Type.Title.copy(color = Palette.Text))
            Spacer(Modifier.width(16.dp))
            Segmented(STab.entries.map { it to it.title }, tab, { tab = it })
            Spacer(Modifier.weight(1f))
            if (Ads.rewarded.available) ChunkyButton("FREE 100", Modifier.width(200.dp), color = Palette.Lime, textColor = Palette.Midnight, icon = Ic.COIN, height = 42.dp) {
                Ads.rewarded.show(Placement.FREE_COINS, { GameRepo.addCoins(100); Audio.play(Sfx.COIN) })
            }
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            STab.SKINS -> LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(WorldSkin.entries) { s ->
                    val owned = s.id in profile.ownedSkins
                    StoreCard(s.title, skinBlurb(s), owned, profile.skin == s.id, "%,d".format(s.price), { SkinSwatch(s) }) {
                        if (owned) GameRepo.update { it.copy(skin = s.id) }
                        else offer = com.ngstudio.inchperfect.ui.components.Offer("${s.title} skin", coins = s.price) { GameRepo.update { it.copy(ownedSkins = it.ownedSkins + s.id, skin = s.id) } }
                    }
                }
            }
            STab.THEMES -> LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(UiThemes.ALL) { t ->
                    val owned = t.id in profile.ownedThemes
                    StoreCard(t.title, "Menu & HUD colours", owned, profile.uiTheme == t.id, "%,d".format(t.price), {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(Brush.verticalGradient(listOf(Color(t.bgTop), Color(t.bgBottom))), cornerRadius = CornerRadius(14f))
                            drawCircle(Color(t.primary), size.height * 0.22f, Offset(size.width * 0.35f, size.height / 2))
                            drawCircle(Color(t.secondary), size.height * 0.22f, Offset(size.width * 0.65f, size.height / 2))
                        }
                    }) {
                        if (owned) GameRepo.update { it.copy(uiTheme = t.id) }
                        else offer = com.ngstudio.inchperfect.ui.components.Offer("${t.title} theme", coins = t.price) { GameRepo.update { it.copy(ownedThemes = it.ownedThemes + t.id, uiTheme = t.id) } }
                    }
                }
            }
            STab.BOOSTERS -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((kind, ic, title, price, have) in listOf(
                    Booster("rewind", Ic.REWIND, "Rewind", Economy.REWIND_PRICE, profile.rewind),
                    Booster("ghost", Ic.GHOST, "Ghost Guide", Economy.GHOST_PRICE, profile.ghost),
                    Booster("slowmo", Ic.SLOW, "Slow-Mo", Economy.SLOWMO_PRICE, profile.slowmo),
                )) {
                    GlassPanel(Modifier.weight(1f), glow = acc.primary, padding = 10.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(ic, acc.primary, 34.dp); Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(title.uppercase(), style = Type.Section.copy(color = Palette.Text, fontSize = 13.sp))
                                    Text("You have $have", style = Type.Small.copy(color = Palette.Lime))
                                }
                            }
                            Text(when (kind) { "rewind" -> "Undo the last 3 seconds, crashes included"; "ghost" -> "A ghost car drives the verified solution"; else -> "Half-speed driving for 12 seconds" },
                                style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp), modifier = Modifier.padding(vertical = 4.dp), maxLines = 2)
                            ChunkyButton("%,d".format(price), Modifier.fillMaxWidth(), icon = Ic.COIN, height = 42.dp, sound = Sfx.PURCHASE, style = Type.Button.copy(fontSize = 15.sp)) {
                                if (GameRepo.spend(price)) GameRepo.addBooster(kind, 1) else msg = "Not enough coins"
                            }
                            if (Ads.rewarded.available) {
                                Spacer(Modifier.height(4.dp))
                                ChunkyButton("WATCH AD", Modifier.fillMaxWidth(), color = Palette.Lime, textColor = Palette.Midnight, height = 38.dp, style = Type.Button.copy(fontSize = 14.sp)) {
                                    Ads.rewarded.show(Placement.FREE_BOOSTER, { GameRepo.addBooster(kind, 1); Audio.play(Sfx.UNLOCK) })
                                }
                            }
                        }
                    }
                }
            }
            STab.CHESTS -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPanel(Modifier.weight(1f), glow = Palette.Gold) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.CHEST, Palette.Gold, 44.dp); Spacer(Modifier.width(10.dp))
                            Column {
                                Text("GARAGE CHEST", style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp))
                                Text("Coins, boosters or a new wrap", style = Type.Small.copy(color = Palette.TextDim))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ChunkyButton("OPEN  %,d".format(Economy.CHEST_PRICE), Modifier.width(260.dp), color = Palette.Gold, textColor = Palette.Midnight, icon = Ic.COIN, sound = Sfx.CHEST) {
                            if (GameRepo.spend(Economy.CHEST_PRICE)) chestPrize = openChest() else msg = "Not enough coins"
                        }
                    }
                }
                GlassPanel(Modifier.weight(1f)) {
                    Column {
                        Text("WHAT'S INSIDE", style = Type.Section.copy(color = acc.primary))
                        for (l in listOf("40%  ·  800–2,500 coins", "30%  ·  3 boosters", "30%  ·  a new wrap (or 3,000 coins)")) Text(l, style = Type.Body.copy(color = Palette.Text), modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Star Chest: free every 50 stars (Home screen).", style = Type.Small.copy(color = Palette.TextDim))
                    }
                }
            }
        }
    }
    offer?.let { o -> com.ngstudio.inchperfect.ui.components.PurchaseDialog(o) { offer = null } }
    chestPrize?.let { p ->
        GameDialog(onDismiss = { chestPrize = null }, glow = Palette.Gold) {
            Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Ic.SPARK, Palette.Gold, 48.dp)
                Text("YOU GOT", style = Type.Small.copy(color = Palette.TextDim))
                Text(p.uppercase(), style = Type.Title.copy(color = Palette.Gold))
                Spacer(Modifier.height(14.dp))
                ChunkyButton("AWESOME", Modifier.fillMaxWidth(), color = Palette.Lime, textColor = Palette.Midnight) { chestPrize = null }
            }
        }
    }
    msg?.let { m ->
        LaunchedEffect(m) { Audio.play(Sfx.LOCKED); kotlinx.coroutines.delay(1600); msg = null }
        Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
            Text(m, style = Type.BodyBold.copy(color = Palette.Text), modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xEE3A1A2A)).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

private data class Booster(val kind: String, val ic: Ic, val title: String, val price: Int, val have: Int)

private fun openChest(): String {
    val r = java.util.Random()
    val roll = r.nextDouble()
    return when {
        roll < 0.4 -> { val c = 800 + r.nextInt(1700); GameRepo.addCoins(c); "%,d coins".format(c) }
        roll < 0.7 -> { GameRepo.addBooster("rewind", 1); GameRepo.addBooster("ghost", 1); GameRepo.addBooster("slowmo", 1); "3 boosters" }
        else -> {
            val missing = Decal.entries.filter { it.id !in GameRepo.profile.value.ownedDecals }
            if (missing.isEmpty()) { GameRepo.addCoins(3000); "3,000 coins" } else {
                val d = missing[r.nextInt(missing.size)]; GameRepo.update { it.copy(ownedDecals = it.ownedDecals + d.id) }; "${d.title} wrap"
            }
        }
    }
}

private fun skinBlurb(s: WorldSkin) = when (s) {
    WorldSkin.CLASSIC -> "Each district's own look"; WorldSkin.GOLDEN -> "Warm sunset glow everywhere"; WorldSkin.BLUEPRINT -> "Technical-drawing mode"
    WorldSkin.AUTUMN -> "Orange leaves everywhere"; WorldSkin.TOY -> "Bright, glossy toy town"; WorldSkin.PIXEL -> "Retro arcade colours"
}

@Composable
private fun SkinSwatch(s: WorldSkin) {
    val profile = GameRepo.profile.value
    val car = com.ngstudio.inchperfect.game.physics.Cars.byId(profile.selectedCar)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val bg = when (s) { WorldSkin.BLUEPRINT -> Color(0xFF0E3A6E); WorldSkin.AUTUMN -> Color(0xFF6E5A3A); WorldSkin.TOY -> Color(0xFF6FBF5A); WorldSkin.GOLDEN -> Color(0xFF5A4636); WorldSkin.PIXEL -> Color(0xFF3A3326); else -> Color(0xFF3B3F46) }
            val line = when (s) { WorldSkin.BLUEPRINT -> Color.White; WorldSkin.AUTUMN -> Color(0xFFFFB25A); WorldSkin.TOY -> Color(0xFFFFE27A); WorldSkin.GOLDEN -> Color(0xFFFFC46B); WorldSkin.PIXEL -> Color(0xFFFFC93C); else -> Color(0xFFF4F6FA) }
            drawRoundRect(bg, cornerRadius = CornerRadius(14f))
            if (s == WorldSkin.GOLDEN) drawRect(Brush.linearGradient(listOf(Color(0x55FF9A3C), Color.Transparent)))
            if (s == WorldSkin.BLUEPRINT) {
                var gx = 0f; while (gx < size.width) { drawLine(line.copy(alpha = 0.12f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += 12f }
                var gy = 0f; while (gy < size.height) { drawLine(line.copy(alpha = 0.12f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += 12f }
            }
            for (i in 0..4) drawLine(line.copy(alpha = 0.85f), Offset(size.width * (0.1f + i * 0.2f), size.height * 0.15f), Offset(size.width * (0.1f + i * 0.2f), size.height * 0.85f), 3f)
            drawRoundRect(line, Offset(size.width * 0.3f + 4, size.height * 0.15f), Size(size.width * 0.2f - 8, size.height * 0.7f), CornerRadius(6f), style = Stroke(3f))
            if (s == WorldSkin.PIXEL) { var px = 0f; while (px < size.width) { drawLine(Color.Black.copy(alpha = 0.18f), Offset(px, 0f), Offset(px, size.height), 1f); px += 6f } }
        }
        if (s != WorldSkin.BLUEPRINT) {
            CarPreview(profile.look(), car.length, car.width, Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), rotation = -90f, scaleM = 0.9f)
        }
    }
}

@Composable
private fun StoreCard(title: String, sub: String, owned: Boolean, active: Boolean, price: String, preview: @Composable () -> Unit, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xE6121936)).border(if (active) 2.5.dp else 1.dp, if (active) acc.primary else Color(0x33FFFFFF), RoundedCornerShape(18.dp)).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(14.dp))) { preview() }
        Spacer(Modifier.height(6.dp))
        Text(title.uppercase(), style = Type.Section.copy(color = Palette.Text, fontSize = 13.sp))
        Text(sub, style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        ChunkyButton(if (active) "EQUIPPED" else if (owned) "EQUIP" else price, Modifier.fillMaxWidth(), color = if (active) Color(0xFF3A4060) else if (owned) Palette.Lime else acc.secondary,
            textColor = if (owned && !active) Palette.Midnight else Color.White, icon = if (!owned) Ic.COIN else null, height = 40.dp, enabled = !active,
            sound = Sfx.CLICK, onClick = onClick)
    }
}
