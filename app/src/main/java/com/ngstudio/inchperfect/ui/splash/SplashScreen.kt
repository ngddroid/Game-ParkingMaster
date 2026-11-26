package com.ngstudio.inchperfect.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Sfx
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.game.level.LevelGenerator
import com.ngstudio.inchperfect.game.physics.Cars
import com.ngstudio.inchperfect.ui.components.CarPreview
import com.ngstudio.inchperfect.ui.components.CityBackground
import com.ngstudio.inchperfect.ui.components.ProgressBar
import com.ngstudio.inchperfect.ui.theme.Bungee
import com.ngstudio.inchperfect.ui.theme.LocalAccent
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Animated splash: a car glides into a glowing bay while the game warms up. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val acc = LocalAccent.current
    val car = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    val ticks = remember { Animatable(0f) }
    var load by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        launch { car.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
        launch { delay(700); logo.animateTo(1f, spring(0.45f, Spring.StiffnessLow)) }
        launch { delay(1100); ticks.animateTo(1f, tween(700, easing = LinearEasing)) }
        // warm-up: generate the next level and wait (briefly) for audio
        withContext(Dispatchers.Default) {
            val lvl = LevelGenerator.level(GameRepo.currentLevel())
            com.ngstudio.inchperfect.game.render.Textures.warm(com.ngstudio.inchperfect.game.render.Renderer.neededTextures(lvl))
        }
        load = 0.6f
        var waited = 0
        while (!Audio.ready && waited < 40) { delay(100); waited++ }
        load = 1f
        Audio.play(Sfx.WHOOSH, 0.6f)
        delay(900)
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        CityBackground(intensity = 0.8f)
        // bay + car
        Box(Modifier.align(Alignment.Center).offset(y = (-70).dp).size(260.dp, 110.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val bw = size.width * 0.62f; val bh = size.height * 0.78f
                val x0 = (size.width - bw) / 2; val y0 = (size.height - bh) / 2
                val glow = 0.4f + 0.6f * car.value
                drawRoundRect(acc.primary.copy(alpha = 0.10f * glow), Offset(x0, y0), Size(bw, bh), CornerRadius(10f))
                drawRoundRect(acc.primary.copy(alpha = glow), Offset(x0, y0), Size(bw, bh), CornerRadius(10f),
                    style = Stroke(5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), -car.value * 120f)))
            }
            val ease = car.value
            CarPreview(GameRepo.profile.value.look(), Cars.REF.length, Cars.REF.width,
                Modifier.fillMaxSize().graphicsLayer { translationX = (-900f) * (1f - ease); rotationZ = -14f * (1f - ease) })
        }
        Column(Modifier.align(Alignment.Center).offset(y = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "INCH PERFECT",
                style = TextStyle(fontFamily = Bungee, fontSize = 46.sp, letterSpacing = 3.sp,
                    brush = Brush.verticalGradient(listOf(Color.White, acc.primary))),
                modifier = Modifier.graphicsLayer { scaleX = 0.6f + 0.4f * logo.value; scaleY = 0.6f + 0.4f * logo.value; alpha = logo.value.coerceIn(0f, 1f) },
            )
            // ruler ticks
            Canvas(Modifier.width(360.dp).height(18.dp)) {
                val n = 40
                for (i in 0..n) {
                    val f = i.toFloat() / n
                    if (f > ticks.value) break
                    val h = if (i % 10 == 0) size.height else if (i % 5 == 0) size.height * 0.65f else size.height * 0.38f
                    drawLine(if (i % 10 == 0) acc.secondary else acc.primary.copy(alpha = 0.8f), Offset(size.width * f, 0f), Offset(size.width * f, h), 3f)
                }
            }
            Text("CAR PARKING", style = Type.Small.copy(color = Palette.TextDim, letterSpacing = 8.sp, fontSize = 13.sp), modifier = Modifier.padding(top = 6.dp))
        }
        ProgressBar(load, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).width(240.dp), height = 6.dp)
    }
}
