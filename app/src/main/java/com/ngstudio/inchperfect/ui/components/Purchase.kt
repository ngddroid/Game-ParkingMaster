package com.ngstudio.inchperfect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.ngstudio.inchperfect.BuildConfig
import com.ngstudio.inchperfect.ads.Ads
import com.ngstudio.inchperfect.ads.Placement
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type

/** Something that can be bought with coins or stars (or, in debug builds, a simulated ad). */
class Offer(val title: String, val coins: Int = 0, val stars: Int = 0, val grant: () -> Unit)

/**
 * Unified unlock dialog. Shows the price and a BUY button; when rewarded ads are available
 * (debug builds use a simulated ad) it also offers WATCH AD to unlock for free.
 */
@Composable
fun PurchaseDialog(offer: Offer, preview: (@Composable () -> Unit)? = null, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val profile = GameRepo.profile.value
    val affordable = if (offer.stars > 0) GameRepo.availableStars >= offer.stars else profile.coins >= offer.coins
    GameDialog(onDismiss = if (loading) null else onClose, glow = Palette.Gold) {
        Column(Modifier.width(480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("UNLOCK", style = Type.Small.copy(color = Palette.TextDim))
                    Text(offer.title.uppercase(), style = Type.Title.copy(color = Palette.Text, fontSize = 20.sp), maxLines = 1)
                }
                if (!loading) RoundButton(Ic.CLOSE, size = 40.dp, onClick = onClose)
            }
            if (preview != null) { Spacer(Modifier.height(6.dp)); preview() }
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(50.dp)) {
                    CircularProgressIndicator(color = Palette.Gold, modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading ad…", style = Type.BodyBold.copy(color = Palette.Text))
                }
            } else {
                val label = if (offer.stars > 0) "★ %,d".format(offer.stars) else "%,d".format(offer.coins)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChunkyButton(if (offer.coins == 0 && offer.stars == 0) "FREE" else "BUY $label", Modifier.weight(1f), color = if (affordable) Palette.Lime else Color(0xFF3A4060),
                        textColor = if (affordable) Palette.Midnight else Palette.TextDim, icon = if (offer.stars > 0) Ic.STAR else Ic.COIN, height = 50.dp, sound = Sfx.PURCHASE,
                        style = Type.Button.copy(fontSize = 15.sp)) {
                        val ok = if (offer.stars > 0) GameRepo.spendStars(offer.stars) else GameRepo.spend(offer.coins)
                        if (ok) { offer.grant(); Audio.play(Sfx.UNLOCK); onClose() } else { error = if (offer.stars > 0) "Not enough stars" else "Not enough coins"; Audio.play(Sfx.LOCKED) }
                    }
                    if (Ads.rewarded.available) {
                        ChunkyButton("WATCH AD", Modifier.weight(1f), color = Palette.Gold, textColor = Palette.Midnight, icon = Ic.PLAY, height = 50.dp, style = Type.Button.copy(fontSize = 15.sp)) {
                            loading = true
                            Ads.rewarded.show(Placement.FREE_BOOSTER, onReward = { offer.grant(); Audio.play(Sfx.UNLOCK); loading = false; onClose() }, onFail = { loading = false })
                        }
                    }
                }
                error?.let { Text(it, style = Type.BodyBold.copy(color = Palette.Red)) }
                if (BuildConfig.DEBUG && Ads.rewarded.available) Text("Debug build · simulated ad", style = Type.Small.copy(color = Palette.TextDim), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Suppress("unused") private val keep = Arrangement.Center
