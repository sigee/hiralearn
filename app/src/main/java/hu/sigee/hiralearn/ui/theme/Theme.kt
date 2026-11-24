package hu.sigee.hiralearn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import hu.sigee.hiralearn.R

// "Ink & Seal": washi paper, sumi ink, an indigo seal. Vermilion only ever means wrong / not known,
// matcha green means right / done.

/** Klee One: a textbook handwriting font, so learners see the correct hand-written kana shapes. */
val KanaFont = FontFamily(
    Font(R.font.klee_one_regular, FontWeight.Normal),
    Font(R.font.klee_one_semibold, FontWeight.SemiBold),
)

/** Noto Serif JP (Mincho style) for headings and text. */
val SerifFont = FontFamily(
    Font(R.font.noto_serif_jp_regular, FontWeight.Normal),
    Font(R.font.noto_serif_jp_semibold, FontWeight.SemiBold),
)

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val InkTypography = Typography().withFont(SerifFont)

private val Light = lightColorScheme(
    primary = Color(0xFF2D4D8E),
    onPrimary = Color(0xFFF7F0E1),
    primaryContainer = Color(0xFFDCE2EF),
    onPrimaryContainer = Color(0xFF0F1F42),
    secondary = Color(0xFF7A6A50),
    onSecondary = Color(0xFFF7F0E1),
    secondaryContainer = Color(0xFFEADFC8),
    onSecondaryContainer = Color(0xFF2E2619),
    tertiary = Color(0xFF5F7D3A),
    tertiaryContainer = Color(0xFFDCE5C6),
    onTertiaryContainer = Color(0xFF1F2E0D),
    background = Color(0xFFF5EEDF),
    onBackground = Color(0xFF23222A),
    surface = Color(0xFFF5EEDF),
    onSurface = Color(0xFF23222A),
    surfaceVariant = Color(0xFFEADFC8),
    onSurfaceVariant = Color(0xFF5E533F),
    surfaceContainerLowest = Color(0xFFFBF7EE),
    surfaceContainerLow = Color(0xFFF8F2E6),
    surfaceContainer = Color(0xFFF3EBDA),
    surfaceContainerHigh = Color(0xFFEFE5D1),
    surfaceContainerHighest = Color(0xFFEADFC8),
    outline = Color(0xFFB9A57E),
    outlineVariant = Color(0xFFDCCBA6),
    error = Color(0xFFB8452F),
    onError = Color(0xFFFBF7EE),
    errorContainer = Color(0xFFF2D5CC),
    onErrorContainer = Color(0xFF5A1A0E),
)

/** "Night ink": dark paper, pale ink, a light indigo seal. */
private val Dark = darkColorScheme(
    primary = Color(0xFF9DB3E6),
    onPrimary = Color(0xFF0F1A33),
    primaryContainer = Color(0xFF26324F),
    onPrimaryContainer = Color(0xFFDCE2EF),
    secondary = Color(0xFFCDBB98),
    onSecondary = Color(0xFF2E2619),
    secondaryContainer = Color(0xFF3A3427),
    onSecondaryContainer = Color(0xFFEADFC8),
    tertiary = Color(0xFFB4CB8E),
    tertiaryContainer = Color(0xFF2E3B22),
    onTertiaryContainer = Color(0xFFD5E3B8),
    background = Color(0xFF17161B),
    onBackground = Color(0xFFE9E4D8),
    surface = Color(0xFF17161B),
    onSurface = Color(0xFFE9E4D8),
    surfaceVariant = Color(0xFF2A2830),
    onSurfaceVariant = Color(0xFFBDB4A2),
    surfaceContainerLowest = Color(0xFF121116),
    surfaceContainerLow = Color(0xFF1D1C22),
    surfaceContainer = Color(0xFF212026),
    surfaceContainerHigh = Color(0xFF29282E),
    surfaceContainerHighest = Color(0xFF323138),
    outline = Color(0xFF6B6455),
    outlineVariant = Color(0xFF3F3B33),
    error = Color(0xFFE5937F),
    onError = Color(0xFF3B0E05),
    errorContainer = Color(0xFF4A2219),
    onErrorContainer = Color(0xFFF2D5CC),
)

/** Right / wrong colours, which Material's scheme doesn't have. */
@Immutable
data class FeedbackColors(val correct: Color, val onCorrect: Color, val wrong: Color, val onWrong: Color)

private val LightFeedback = FeedbackColors(Color(0xFFDCE5C6), Color(0xFF2F4318), Color(0xFFF2D5CC), Color(0xFF5A1A0E))
private val DarkFeedback = FeedbackColors(Color(0xFF2E3B22), Color(0xFFD5E3B8), Color(0xFF4A2219), Color(0xFFF2D5CC))

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

/** The calligraphy desk: paper, ink, seal, washes and the wooden scroll rods. */
@Immutable
data class InkColors(
    val paperTop: Color,
    val paperBottom: Color,
    /** Lighter sheet used for scrolls and manuscript squares. */
    val sheet: Color,
    val fiber: Color,
    val ink: Color,
    val pencil: Color,
    val gridLine: Color,
    val gridDash: Color,
    val seal: Color,
    val matchaWash: Color,
    val indigoWash: Color,
    val inkWash: Color,
    val vermilion: Color,
    val woodLight: Color,
    val woodDark: Color,
    val mount: Color,
)

private val LightInk = InkColors(
    paperTop = Color(0xFFF7F0E1), paperBottom = Color(0xFFEFE4CF), sheet = Color(0xFFFBF7EE),
    fiber = Color(0xFF7A5A28), ink = Color(0xFF1E1D24), pencil = Color(0xFF7D786F),
    gridLine = Color(0xFFC9B48A), gridDash = Color(0xFFDCCBA6), seal = Color(0xFF2D4D8E),
    matchaWash = Color(0x2E6E8C46), indigoWash = Color(0x262D4D8E), inkWash = Color(0x0F23222A),
    vermilion = Color(0xFFB8452F), woodLight = Color(0xFF5A3B22), woodDark = Color(0xFF2F1D10), mount = Color(0xFFD9C9A5),
)

private val DarkInk = InkColors(
    paperTop = Color(0xFF1D1C22), paperBottom = Color(0xFF141318), sheet = Color(0xFF22212A),
    fiber = Color(0xFFFFFFFF), ink = Color(0xFFEDE7DA), pencil = Color(0xFF8C8679),
    gridLine = Color(0xFF4A453B), gridDash = Color(0xFF38342D), seal = Color(0xFF9DB3E6),
    matchaWash = Color(0x40809E5A), indigoWash = Color(0x405A78BE), inkWash = Color(0x14FFFFFF),
    vermilion = Color(0xFFE5937F), woodLight = Color(0xFF6B4A2E), woodDark = Color(0xFF3A2516), mount = Color(0xFF4A4232),
)

val LocalInkColors = staticCompositionLocalOf { LightInk }

@Composable
fun HiraLearnTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFeedbackColors provides if (dark) DarkFeedback else LightFeedback,
        LocalInkColors provides if (dark) DarkInk else LightInk,
    ) {
        val scheme = if (dark) Dark else Light
        MaterialTheme(colorScheme = scheme, typography = InkTypography) {
            // Screens draw washi paper themselves and use transparent containers, so no Surface sets the text
            // colour; without this, text falls back to Compose's default black, which is invisible in night ink.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
