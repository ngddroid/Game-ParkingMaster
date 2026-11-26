package com.ngstudio.inchperfect.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.R
import com.ngstudio.inchperfect.core.UiTheme
import com.ngstudio.inchperfect.core.UiThemes

val Bungee = FontFamily(Font(R.font.bungee))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Exo = FontFamily(
    Font(R.font.exo2, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.exo2, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.exo2, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.exo2, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.exo2, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

object Palette {
    val Midnight = Color(0xFF0B1026)
    val Deep = Color(0xFF070A1A)
    val Panel = Color(0xCC121936)
    val PanelSolid = Color(0xFF141B3A)
    val Stroke = Color(0x3319E3FF)
    val Cyan = Color(0xFF19E3FF)
    val Orange = Color(0xFFFF7A1A)
    val Lime = Color(0xFF9BFF3C)
    val Magenta = Color(0xFFFF3DA5)
    val Gold = Color(0xFFFFC93C)
    val Red = Color(0xFFFF4D4D)
    val Text = Color(0xFFF2F5FF)
    val TextDim = Color(0xFF9AA4C7)
    val Locked = Color(0xFF2A3154)
}

data class Accent(val primary: Color, val secondary: Color, val bgTop: Color, val bgBottom: Color)

fun UiTheme.accent() = Accent(Color(primary), Color(secondary), Color(bgTop), Color(bgBottom))

val LocalAccent = staticCompositionLocalOf { UiThemes.ALL[0].accent() }

object Type {
    val Hero = TextStyle(fontFamily = Bungee, fontSize = 40.sp, letterSpacing = 1.sp)
    val Title = TextStyle(fontFamily = Bungee, fontSize = 24.sp, letterSpacing = 0.5.sp)
    val Section = TextStyle(fontFamily = Bungee, fontSize = 16.sp, letterSpacing = 1.sp)
    val Button = TextStyle(fontFamily = Bungee, fontSize = 18.sp, letterSpacing = 1.sp)
    val Number = TextStyle(fontFamily = Bungee, fontSize = 16.sp)
    val Body = TextStyle(fontFamily = Exo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val BodyBold = TextStyle(fontFamily = Exo, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val Small = TextStyle(fontFamily = Exo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
}

@Composable
fun InchPerfectTheme(theme: UiTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides theme.accent(), content = content)
}
