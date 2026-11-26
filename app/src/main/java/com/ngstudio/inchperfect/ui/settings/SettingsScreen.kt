package com.ngstudio.inchperfect.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.BuildConfig
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.game.input.Scheme
import com.ngstudio.inchperfect.ui.components.ChunkyButton
import com.ngstudio.inchperfect.ui.components.GameDialog
import com.ngstudio.inchperfect.ui.components.GlassPanel
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.Icon
import com.ngstudio.inchperfect.ui.components.CityBackground
import com.ngstudio.inchperfect.ui.components.RoundButton
import com.ngstudio.inchperfect.ui.components.SectionTitle
import com.ngstudio.inchperfect.ui.components.Segmented
import com.ngstudio.inchperfect.ui.components.SliderRow
import com.ngstudio.inchperfect.ui.components.Toggle
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type

@Composable
fun SettingsScreen(onBack: () -> Unit, onLegal: (String) -> Unit) {
    val s by GameRepo.settings.collectAsState()
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        CityBackground(intensity = 0.5f)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Ic.BACK, onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text("SETTINGS", style = Type.Title.copy(color = Palette.Text))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Audio")
                            SliderLine(Ic.MUSIC, "Music", s.music) { v -> GameRepo.updateSettings { it.copy(music = v) }; Audio.musicVolume = v; Audio.applyMusicVolume() }
                            SliderLine(Ic.SOUND, "Sound effects", s.sfx) { v -> GameRepo.updateSettings { it.copy(sfx = v) }; Audio.sfxVolume = v }
                            SliderLine(Ic.CAR, "Engine", s.engine) { v -> GameRepo.updateSettings { it.copy(engine = v) }; Audio.engineVolume = v }
                            ToggleLine(Ic.VIBRATE, "Haptics", s.haptics) { v -> GameRepo.updateSettings { it.copy(haptics = v) } }
                        }
                    }
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Graphics")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Quality", style = Type.BodyBold.copy(color = Palette.Text), modifier = Modifier.weight(1f))
                                Segmented(listOf("auto" to "Auto", "low" to "Low", "medium" to "Med", "high" to "High", "ultra" to "Ultra"), s.quality, { q -> GameRepo.updateSettings { it.copy(quality = q) } })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Frame rate", style = Type.BodyBold.copy(color = Palette.Text), modifier = Modifier.weight(1f))
                                Segmented(listOf(30 to "30", 60 to "60", 90 to "90"), s.fps, { f -> GameRepo.updateSettings { it.copy(fps = f) } })
                            }
                            ToggleLine(Ic.CHART, "Show speed", s.showFps) { v -> GameRepo.updateSettings { it.copy(showFps = v) } }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Controls")
                            Segmented(Scheme.entries.map { it to it.title }, s.scheme, { sc -> GameRepo.updateSettings { it.copy(scheme = sc) } })
                            SliderLine(Ic.WHEEL, "Steering sensitivity", (s.sensitivity - 0.6f) / 1.0f) { v -> GameRepo.updateSettings { it.copy(sensitivity = 0.6f + v * 1.0f) } }
                            SliderLine(Ic.PLUS, "Control size", (s.ctrlSize - 0.75f) / 0.5f) { v -> GameRepo.updateSettings { it.copy(ctrlSize = 0.75f + v * 0.5f) } }
                            SliderLine(Ic.EYE, "Control opacity", (s.ctrlOpacity - 0.35f) / 0.65f) { v -> GameRepo.updateSettings { it.copy(ctrlOpacity = 0.35f + v * 0.65f) } }
                            ToggleLine(Ic.ROTATE, "Wheel auto-centre", s.autoCenter) { v -> GameRepo.updateSettings { it.copy(autoCenter = v) } }
                            ToggleLine(Ic.SHIELD, "Left-handed layout", s.leftHanded) { v -> GameRepo.updateSettings { it.copy(leftHanded = v) } }
                            ToggleLine(Ic.TARGET, "Parking sensor beeps", s.sensors) { v -> GameRepo.updateSettings { it.copy(sensors = v) } }
                        }
                    }
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("About")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChunkyButton("TERMS", Modifier.weight(1f), color = Color(0xFF3A86FF), icon = Ic.DOC, height = 46.dp) { onLegal("terms") }
                                ChunkyButton("PRIVACY", Modifier.weight(1f), color = Color(0xFF7B61FF), icon = Ic.SHIELD, height = 46.dp) { onLegal("privacy") }
                            }
                            ChunkyButton("SHARE THE GAME", Modifier.fillMaxWidth(), color = LocalAccent.current.secondary, icon = Ic.SPARK, height = 46.dp) {
                                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "I'm parking inch-perfect in Inch Perfect: Car Parking. 10,000 levels, can you beat me?") }
                                ctx.startActivity(Intent.createChooser(i, "Share"))
                            }
                            ChunkyButton("RESET PROGRESS", Modifier.fillMaxWidth(), color = Palette.Red, icon = Ic.RESTART, height = 44.dp) { confirmReset = true }
                            Text("Version ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · debug" else ""}", style = Type.Small.copy(color = Palette.TextDim), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (confirmReset) GameDialog(onDismiss = { confirmReset = false }, glow = Palette.Red) {
            Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RESET EVERYTHING?", style = Type.Title.copy(color = Palette.Red))
                Text("All levels, stars, coins, cars and purchases on this device will be erased. This cannot be undone.", style = Type.Body.copy(color = Palette.TextDim), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChunkyButton("CANCEL", Modifier.weight(1f), color = Color(0xFF4A5378)) { confirmReset = false }
                    ChunkyButton("ERASE", Modifier.weight(1f), color = Palette.Red) { GameRepo.resetAll(); confirmReset = false }
                }
            }
        }
    }
}

@Composable
private fun SliderLine(ic: Ic, label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ic, LocalAccent.current.primary, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = Type.BodyBold.copy(color = Palette.Text), modifier = Modifier.width(150.dp))
        SliderRow(value, onChange, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("${(value * 100).toInt()}", style = Type.Small.copy(color = Palette.TextDim, fontSize = 12.sp), modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun ToggleLine(ic: Ic, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ic, LocalAccent.current.primary, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = Type.BodyBold.copy(color = Palette.Text), modifier = Modifier.weight(1f))
        Toggle(on, onChange)
    }
}
