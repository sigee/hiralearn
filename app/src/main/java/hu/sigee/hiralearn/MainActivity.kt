package hu.sigee.hiralearn

import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContentTransitionScope
import hu.sigee.hiralearn.data.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.SystemBarStyle
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hu.sigee.hiralearn.core.SessionMode
import hu.sigee.hiralearn.ui.chart.KanaChartScreen
import hu.sigee.hiralearn.ui.home.HomeScreen
import hu.sigee.hiralearn.ui.home.HomeViewModel
import hu.sigee.hiralearn.ui.kana.KanaDetailScreen
import hu.sigee.hiralearn.ui.session.SessionScreen
import hu.sigee.hiralearn.ui.session.SessionViewModel
import hu.sigee.hiralearn.ui.settings.LicensesScreen
import hu.sigee.hiralearn.ui.settings.SettingsScreen
import hu.sigee.hiralearn.ui.theme.HiraLearnTheme
import hu.sigee.hiralearn.ui.words.GlossaryScreen
import hu.sigee.hiralearn.ui.words.GlossaryViewModel
import hu.sigee.hiralearn.ui.words.WordSessionScreen
import hu.sigee.hiralearn.ui.words.WordSessionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = appContainer
        setContent {
            val mode by container.settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/navigation bar icons must follow the app theme, not only the phone setting.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            HiraLearnTheme(dark = dark) {
                HiraLearnNavHost(container)
            }
        }
    }
}

@Composable
private fun HiraLearnNavHost(container: AppContainer) {
    val nav = rememberNavController()
    // Guard against double taps popping the start destination and leaving a blank screen.
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    // Pages slide in and out like sheets of paper.
    val paper = tween<IntOffset>(380, easing = FastOutSlowInEasing)
    val fade = tween<Float>(300)
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, paper) { it / 3 } + fadeIn(fade) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, paper) { it / 8 } + fadeOut(fade) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, paper) { it / 8 } + fadeIn(fade) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, paper) { it / 3 } + fadeOut(fade) },
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel(factory = HomeViewModel.factory(container)),
                onLearn = { nav.navigate("session/${SessionMode.LEARN}") },
                onReview = { nav.navigate("session/${SessionMode.REVIEW}") },
                onPractice = { nav.navigate("session/${SessionMode.PRACTICE}") },
                onReadWords = { nav.navigate("words") },
                onGlossary = { nav.navigate("glossary") },
                onDrill = { nav.navigate("session/${SessionMode.DRILL}") },
                onKana = { nav.navigate("kana/${it.id}") },
                onKanaChart = { nav.navigate("chart") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("session/{mode}") { entry ->
            val mode = SessionMode.valueOf(entry.arguments?.getString("mode") ?: SessionMode.LEARN.name)
            SessionScreen(
                viewModel = viewModel(factory = SessionViewModel.factory(mode, container)),
                onExit = back,
                onReadWords = { nav.navigate("words") { popUpTo("home") } },
            )
        }
        composable("words") {
            WordSessionScreen(viewModel = viewModel(factory = WordSessionViewModel.factory(container)), onExit = back)
        }
        composable("words/{ids}") { entry ->
            val ids = entry.arguments?.getString("ids").orEmpty().split(",").filter { it.isNotEmpty() }
            WordSessionScreen(viewModel = viewModel(factory = WordSessionViewModel.factory(container, ids)), onExit = back)
        }
        composable("glossary") {
            GlossaryScreen(
                viewModel = viewModel(factory = GlossaryViewModel.factory(container)),
                onPractice = { words -> nav.navigate("words/" + words.joinToString(",") { Uri.encode(it.id) }) },
                onBack = back,
            )
        }
        composable("chart") {
            KanaChartScreen(
                viewModel = viewModel(factory = HomeViewModel.factory(container)),
                onKana = { nav.navigate("kana/${it.id}") },
                onBack = back,
            )
        }
        composable("kana/{id}") { entry ->
            KanaDetailScreen(kanaId = entry.arguments?.getString("id").orEmpty(), onBack = back)
        }
        composable("settings") {
            SettingsScreen(onBack = back, onLicenses = { nav.navigate("licenses") })
        }
        composable("licenses") {
            LicensesScreen(onBack = back)
        }
    }
}
