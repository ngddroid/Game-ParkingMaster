package com.ngstudio.inchperfect.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ngstudio.inchperfect.audio.Audio
import com.ngstudio.inchperfect.audio.Track
import com.ngstudio.inchperfect.core.GameRepo
import com.ngstudio.inchperfect.ui.game.GameScreen
import com.ngstudio.inchperfect.ui.home.MainScreen
import com.ngstudio.inchperfect.ui.legal.LegalScreen
import com.ngstudio.inchperfect.ui.onboarding.OnboardingScreen
import com.ngstudio.inchperfect.ui.settings.SettingsScreen
import com.ngstudio.inchperfect.ui.splash.SplashScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(
        nav, startDestination = "splash",
        enterTransition = { fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.98f) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { fadeOut(tween(200)) },
    ) {
        composable("splash") {
            SplashScreen {
                val next = if (GameRepo.profile.value.onboardingDone) "main/play" else "onboarding"
                nav.navigate(next) { popUpTo("splash") { inclusive = true } }
            }
        }
        composable("onboarding") {
            OnboardingScreen {
                GameRepo.update { it.copy(onboardingDone = true) }
                nav.navigate("main/play") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("main/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) { e ->
            Audio.playMusic(Track.MENU)
            MainScreen(
                initialTab = e.arguments?.getString("tab") ?: "play",
                onPlay = { n -> nav.navigate("game/$n") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("game/{level}", arguments = listOf(navArgument("level") { type = NavType.IntType })) { e ->
            val n = e.arguments?.getInt("level") ?: 1
            GameScreen(
                levelNumber = n,
                onExit = { nav.popBackStack("main/{tab}", inclusive = false) },
                onNext = { next -> nav.navigate("game/$next") { popUpTo("game/{level}") { inclusive = true } } },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onLegal = { doc -> nav.navigate("legal/$doc") })
        }
        composable("legal/{doc}", arguments = listOf(navArgument("doc") { type = NavType.StringType })) { e ->
            LegalScreen(e.arguments?.getString("doc") ?: "terms") { nav.popBackStack() }
        }
    }
}
