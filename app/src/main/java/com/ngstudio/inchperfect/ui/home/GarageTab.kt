package com.ngstudio.inchperfect.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.ngstudio.inchperfect.R
import com.ngstudio.inchperfect.core.CarCustom
import com.ngstudio.inchperfect.core.Economy
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.game.input.WheelDesign
import com.ngstudio.inchperfect.game.input.WheelPainter
import com.ngstudio.inchperfect.game.input.Wheels
import com.ngstudio.inchperfect.game.physics.CarSpec
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.game.physics.Category
import com.ngstudio.inchperfect.game.render.CarLook
import com.ngstudio.inchperfect.game.render.Decal
import com.ngstudio.inchperfect.game.render.Finish
import com.ngstudio.inchperfect.ui.components.CarPreview
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.Offer
import com.ngstudio.inchperfect.ui.components.ProgressBar
import com.ngstudio.inchperfect.ui.components.PurchaseDialog
import com.ngstudio.inchperfect.ui.components.Segmented
import com.ngstudio.inchperfect.ui.components.click
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type

private enum class GTab(val title: String) { CARS("Cars"), WHEELS("Wheels"), PAINT("Paint"), FINISH("Finish"), WRAP("Wraps"), GLOW("Glow") }

private fun CarSpec.lookWith(c: CarCustom) = CarLook(design, c.color, c.finish, c.decal, c.accent, axleR = axleRear, axleF = axleFront)

@Composable
fun GarageTab() {
    val acc = LocalAccent.current
    val profile by GameRepo.profile.collectAsState()
    var viewing by remember { mutableStateOf(profile.selectedCar) }
    var viewingWheel by remember { mutableStateOf(profile.selectedWheel) }
    var sub by remember { mutableStateOf(GTab.CARS) }
    var filter by remember { mutableStateOf<Category?>(null) }
    var offer by remember { mutableStateOf<Offer?>(null) }
    val spec = Cars.byId(viewing)
    val owned = viewing in profile.ownedCars
    val custom = profile.customFor(viewing)
    fun setCustom(f: (CarCustom) -> CarCustom) = GameRepo.update { p -> p.copy(custom = p.custom + (viewing to f(p.customFor(viewing)))) }
    val ctx = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(ctx.applicationContext, R.font.bungee) }

    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        // ------------------------------------------------ showroom column
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            val inf = rememberInfiniteTransition(label = "g")
            val wob by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "w")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(Brush.radialGradient(listOf(acc.primary.copy(alpha = 0.28f), Color.Transparent), center, size.width * 0.5f), Offset(0f, size.height * 0.1f), size.copy(height = size.height * 0.8f))
                }
                if (sub == GTab.WHEELS) {
                    val wd = Wheels.byId(viewingWheel)
                    Canvas(Modifier.size(170.dp)) {
                        WheelPainter.paint(drawContext.canvas.nativeCanvas, wd, center.x, center.y, size.minDimension / 2 * 0.92f, wob * 6, 255, typeface)
                    }
                } else {
                    CarPreview(spec.lookWith(custom), spec.length, spec.width, Modifier.fillMaxSize(), rotation = wob * 0.6f, glow = custom.glow, scaleM = 1.3f)
                }
                if (sub != GTab.WHEELS && !owned) Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(Palette.Red).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("LOCKED", style = Type.Small.copy(color = Color.White)) }
            }
            if (sub == GTab.WHEELS) {
                val wd = Wheels.byId(viewingWheel)
                Text(wd.name.uppercase(), style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp))
                Text("${wd.rim.name.replace('_', ' ').lowercase()} · ${wd.mat.name.lowercase()}", style = Type.Small.copy(color = Palette.TextDim))
                Spacer(Modifier.height(8.dp))
                val wOwned = wd.id in profile.ownedWheels
                when {
                    profile.selectedWheel == wd.id -> ChunkyButton("EQUIPPED", Modifier.width(240.dp), color = Color(0xFF3A4060), icon = Ic.CHECK, height = 48.dp, enabled = false) {}
                    wOwned -> ChunkyButton("EQUIP", Modifier.width(240.dp), color = Palette.Lime, textColor = Palette.Midnight, icon = Ic.CHECK, height = 48.dp) { GameRepo.update { it.copy(selectedWheel = wd.id) } }
                    else -> ChunkyButton("UNLOCK", Modifier.width(240.dp), color = acc.secondary, icon = Ic.LOCK, height = 48.dp) { offer = wheelOffer(wd) }
                }
            } else {
                Text(spec.name.uppercase(), style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp))
                Text("${spec.category.title} · ${spec.tagline}", style = Type.Small.copy(color = Palette.TextDim), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column { StatBar("SIZE", spec.statSize.toFloat()); StatBar("TURNING", spec.statTurning.toFloat()); StatBar("ACCEL", spec.statAccel.toFloat()) }
                    Column { StatBar("SPEED", spec.statSpeed.toFloat()); StatBar("BRAKES", spec.statBrake.toFloat()) }
                }
                Spacer(Modifier.height(6.dp))
                when {
                    profile.selectedCar == viewing -> ChunkyButton("SELECTED", Modifier.width(240.dp), color = Color(0xFF3A4060), icon = Ic.CHECK, height = 46.dp, enabled = false) {}
                    owned -> ChunkyButton("SELECT", Modifier.width(240.dp), color = Palette.Lime, textColor = Palette.Midnight, icon = Ic.CHECK, height = 46.dp) { GameRepo.update { it.copy(selectedCar = viewing) } }
                    else -> ChunkyButton(if (spec.starPrice > 0) "UNLOCK  ★ %,d".format(spec.starPrice) else "UNLOCK  %,d".format(spec.price), Modifier.width(240.dp),
                        color = acc.secondary, icon = Ic.LOCK, height = 46.dp) { offer = carOffer(spec) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.width(14.dp))
        // ------------------------------------------------- right column
        Column(Modifier.weight(1.2f).fillMaxHeight()) {
            Segmented(GTab.entries.map { it to it.title }, sub, { sub = it })
            Spacer(Modifier.height(8.dp))
            GlassPanel(Modifier.fillMaxSize().padding(bottom = 6.dp), padding = 8.dp) {
                when (sub) {
                    GTab.CARS -> Column {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                            item { Chip("ALL ${Cars.ALL.size}", filter == null) { filter = null } }
                            items(Category.entries.filter { c -> Cars.ALL.any { it.category == c } }) { c -> Chip(c.title.uppercase(), filter == c) { filter = c } }
                        }
                        LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Cars.ALL.filter { filter == null || it.category == filter }, key = { it.id }) { c ->
                                CarCard(c, c.id == viewing, c.id in profile.ownedCars, profile.customFor(c.id), c.id == profile.selectedCar) { viewing = c.id }
                            }
                        }
                    }
                    GTab.WHEELS -> LazyVerticalGrid(GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Wheels.ALL, key = { it.id }) { w ->
                            WheelCard(w, w.id == viewingWheel, w.id in profile.ownedWheels, w.id == profile.selectedWheel, typeface) {
                                viewingWheel = w.id
                                if (w.id in profile.ownedWheels) GameRepo.update { it.copy(selectedWheel = w.id) }
                            }
                        }
                    }
                    GTab.PAINT -> Swatches(Economy.PAINTS, custom.color, { i -> i in profile.ownedPaints }, { i -> Economy.paintPrice(i) }, owned) { i, col ->
                        if (i in profile.ownedPaints) setCustom { it.copy(color = col) }
                        else offer = Offer("Paint #${i + 1}", coins = Economy.paintPrice(i)) { GameRepo.update { it.copy(ownedPaints = it.ownedPaints + i) }; setCustom { it.copy(color = col) } }
                    }
                    GTab.FINISH -> OptionList(Finish.entries.map { f -> Triple(f.title, f.id in profile.ownedFinishes, if (Economy.finishStars(f) > 0) "★ ${Economy.finishStars(f)}" else "Free") }, Finish.entries.indexOf(custom.finish), owned) { i ->
                        val f = Finish.entries[i]
                        if (f.id in profile.ownedFinishes) setCustom { it.copy(finish = f) }
                        else offer = Offer("${f.title} finish", stars = Economy.finishStars(f)) { GameRepo.update { it.copy(ownedFinishes = it.ownedFinishes + f.id) }; setCustom { it.copy(finish = f) } }
                    }
                    GTab.WRAP -> Column {
                        OptionList(Decal.entries.map { d -> Triple(d.title, d.id in profile.ownedDecals, if (Economy.decalPrice(d) > 0) "%,d".format(Economy.decalPrice(d)) else "Free") }, Decal.entries.indexOf(custom.decal), owned, Modifier.weight(1f)) { i ->
                            val d = Decal.entries[i]
                            if (d.id in profile.ownedDecals) setCustom { it.copy(decal = d) }
                            else offer = Offer("${d.title} wrap", coins = Economy.decalPrice(d)) { GameRepo.update { it.copy(ownedDecals = it.ownedDecals + d.id) }; setCustom { it.copy(decal = d) } }
                        }
                        Text("ACCENT COLOUR", style = Type.Small.copy(color = Palette.TextDim))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                            items(listOf(0xFFFFFF, 0x111111, 0xFF3DA5, 0x19E3FF, 0xFFC21A, 0x9BFF3C, 0xFF7A1A, 0x7B61FF)) { col ->
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(col.toLong() or 0xFF000000)).border(if (custom.accent == col) 3.dp else 1.dp, Color.White, CircleShape).clickable { click(); if (owned) setCustom { it.copy(accent = col) } })
                            }
                        }
                    }
                    GTab.GLOW -> Swatches(Economy.UNDERGLOWS.map { if (it == 0) 0x222633 else it }, if (custom.glow == 0) 0x222633 else custom.glow, { i -> i in profile.ownedGlows }, { i -> Economy.underglowPrice(i) }, owned) { i, _ ->
                        val col = Economy.UNDERGLOWS[i]
                        if (i in profile.ownedGlows) setCustom { it.copy(glow = col) }
                        else offer = Offer("Underglow", coins = Economy.underglowPrice(i)) { GameRepo.update { it.copy(ownedGlows = it.ownedGlows + i) }; setCustom { it.copy(glow = col) } }
                    }
                }
            }
        }
    }
    offer?.let { o -> PurchaseDialog(o) { offer = null } }
}

private fun carOffer(spec: CarSpec) = Offer(spec.name, coins = spec.price, stars = spec.starPrice) {
    GameRepo.update { it.copy(ownedCars = it.ownedCars + spec.id, selectedCar = spec.id) }
}

private fun wheelOffer(w: WheelDesign) = Offer("${w.name} wheel", coins = w.price, stars = w.starPrice) {
    GameRepo.update { it.copy(ownedWheels = it.ownedWheels + w.id, selectedWheel = w.id) }
}

@Composable
private fun Chip(text: String, sel: Boolean, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Text(text, style = Type.Small.copy(color = if (sel) Palette.Midnight else Palette.TextDim, fontSize = 10.sp),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) acc.primary else Color(0xFF0A0E22)).clickable { click(); onClick() }.padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun StatBar(label: String, v: Float) {
    Row(Modifier.width(170.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.Small.copy(color = Palette.TextDim, fontSize = 10.sp), modifier = Modifier.width(58.dp))
        ProgressBar(v, Modifier.weight(1f), height = 8.dp)
    }
}

@Composable
private fun CarCard(c: CarSpec, sel: Boolean, owned: Boolean, custom: CarCustom, equipped: Boolean, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF18204A), Color(0xFF0E1330))))
        .border(if (sel) 2.5.dp else 1.dp, if (sel) acc.primary else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
        .clickable { click(); onClick() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(60.dp)) {
            CarPreview(c.lookWith(custom), c.length, c.width, Modifier.fillMaxSize(), scaleM = 1.15f)
            if (!owned) Box(Modifier.align(Alignment.TopEnd)) { Icon(Ic.LOCK, Palette.TextDim, 16.dp) }
            if (equipped) Box(Modifier.align(Alignment.TopStart)) { Icon(Ic.CHECK, Palette.Lime, 16.dp) }
        }
        Text(c.name.uppercase(), style = Type.Small.copy(color = Palette.Text, fontSize = 11.sp), maxLines = 1)
        Text(if (owned) "OWNED" else if (c.starPrice > 0) "★ %,d".format(c.starPrice) else "%,d".format(c.price), style = Type.Small.copy(color = if (owned) Palette.Lime else Palette.Gold, fontSize = 10.sp))
    }
}

@Composable
private fun WheelCard(w: WheelDesign, sel: Boolean, owned: Boolean, equipped: Boolean, typeface: android.graphics.Typeface?, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF18204A), Color(0xFF0E1330))))
        .border(if (sel) 2.5.dp else 1.dp, if (sel) acc.primary else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
        .clickable { click(); onClick() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp)) {
            Canvas(Modifier.fillMaxSize()) { WheelPainter.paint(drawContext.canvas.nativeCanvas, w, center.x, center.y, size.minDimension / 2 * 0.9f, 0f, 255, typeface) }
            if (!owned) Box(Modifier.align(Alignment.TopEnd)) { Icon(Ic.LOCK, Palette.TextDim, 14.dp) }
            if (equipped) Box(Modifier.align(Alignment.TopStart)) { Icon(Ic.CHECK, Palette.Lime, 14.dp) }
        }
        Text(w.name.uppercase(), style = Type.Small.copy(color = Palette.Text, fontSize = 9.sp), maxLines = 1)
        Text(if (owned) "OWNED" else if (w.starPrice > 0) "★ %,d".format(w.starPrice) else "%,d".format(w.price), style = Type.Small.copy(color = if (owned) Palette.Lime else Palette.Gold, fontSize = 9.sp))
    }
}

@Composable
private fun Swatches(colors: List<Int>, selected: Int, owned: (Int) -> Boolean, price: (Int) -> Int, carOwned: Boolean, onPick: (Int, Int) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(62.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(colors) { i, col ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val cc = Color(col.toLong() or 0xFF000000)
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(cc.copy(red = minOf(1f, cc.red + 0.25f), green = minOf(1f, cc.green + 0.25f), blue = minOf(1f, cc.blue + 0.25f)), cc, cc.copy(red = cc.red * 0.6f, green = cc.green * 0.6f, blue = cc.blue * 0.6f))))
                    .border(if (col == selected) 4.dp else 1.5.dp, if (col == selected) Color.White else Color(0x55FFFFFF), CircleShape)
                    .clickable(enabled = carOwned) { click(); onPick(i, col) }, contentAlignment = Alignment.Center) {
                    if (!owned(i)) Icon(Ic.LOCK, Color.White.copy(alpha = 0.85f), 18.dp)
                }
                Text(if (owned(i)) " " else "%,d".format(price(i)), style = Type.Small.copy(color = Palette.Gold, fontSize = 10.sp))
            }
        }
    }
}

@Composable
private fun OptionList(items: List<Triple<String, Boolean, String>>, selected: Int, carOwned: Boolean, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val acc = LocalAccent.current
    androidx.compose.foundation.lazy.LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items.size) { i ->
            val (title, ownedIt, price) = items[i]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (i == selected) acc.primary.copy(alpha = 0.22f) else Color(0xFF10163A))
                .border(1.dp, if (i == selected) acc.primary else Color(0x22FFFFFF), RoundedCornerShape(14.dp)).clickable(enabled = carOwned) { click(); onPick(i) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = Type.Section.copy(color = Palette.Text, fontSize = 13.sp), modifier = Modifier.weight(1f))
                if (i == selected) Icon(Ic.CHECK, Palette.Lime, 20.dp) else Text(if (ownedIt) "OWNED" else price, style = Type.Small.copy(color = if (ownedIt) Palette.Lime else Palette.Gold))
            }
        }
    }
}
