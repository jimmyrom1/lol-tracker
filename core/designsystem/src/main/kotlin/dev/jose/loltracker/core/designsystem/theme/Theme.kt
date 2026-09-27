package dev.jose.loltracker.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta inspirada en el cliente del juego (dorado y azul "hextech"), sin usar recursos de Riot.
private val Gold = Color(0xFFC8AA6E)
private val GoldDark = Color(0xFF785A28)
private val Teal = Color(0xFF0AC8B9)
private val Navy = Color(0xFF010A13)
private val NavySurface = Color(0xFF0A1428)
private val NavySurfaceHigh = Color(0xFF1E2328)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Navy,
    primaryContainer = GoldDark,
    onPrimaryContainer = Color(0xFFF0E6D2),
    secondary = Teal,
    onSecondary = Navy,
    // Chips seleccionados e indicador de la barra inferior: dorado apagado en vez del lila por defecto.
    secondaryContainer = Color(0xFF3C3220),
    onSecondaryContainer = Color(0xFFF0E6D2),
    background = Navy,
    onBackground = Color(0xFFF0E6D2),
    surface = NavySurface,
    onSurface = Color(0xFFF0E6D2),
    surfaceVariant = NavySurfaceHigh,
    onSurfaceVariant = Color(0xFFA09B8C),
    surfaceContainer = Color(0xFF0F1B2D),
    surfaceContainerHigh = Color(0xFF16233A),
    outline = Color(0xFF463714),
    outlineVariant = Color(0xFF2B2F36),
    // Snackbars: fondo crema con la acción en dorado oscuro.
    inverseSurface = Color(0xFFF0E6D2),
    inverseOnSurface = Navy,
    inversePrimary = GoldDark,
)

private val LightColors = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E3BD),
    onPrimaryContainer = Color(0xFF2A1E05),
    secondary = Color(0xFF00696F),
    secondaryContainer = Color(0xFFF1E4C8),
    onSecondaryContainer = Color(0xFF2A1E05),
    background = Color(0xFFFBF8F2),
    surface = Color(0xFFFBF8F2),
    surfaceVariant = Color(0xFFEDE4D3),
    onSurfaceVariant = Color(0xFF4E4639),
    outline = Color(0xFFB9A57E),
)

/** Colores semánticos que Material 3 no trae: victoria y derrota. */
@Immutable
data class ResultColors(val win: Color, val onWin: Color, val loss: Color, val onLoss: Color)

private val DarkResultColors = ResultColors(
    win = Color(0xFF1F4E79), onWin = Color(0xFFBFE0FF),
    loss = Color(0xFF6B1F24), onLoss = Color(0xFFFFC9CC),
)
private val LightResultColors = ResultColors(
    win = Color(0xFFD6E9FF), onWin = Color(0xFF0B3A63),
    loss = Color(0xFFFFDADC), onLoss = Color(0xFF6B1F24),
)

val LocalResultColors = staticCompositionLocalOf { DarkResultColors }

@Composable
fun LolTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalResultColors provides if (darkTheme) DarkResultColors else LightResultColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
