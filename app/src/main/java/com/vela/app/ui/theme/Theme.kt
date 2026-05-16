package com.vela.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = VelaPrimary,
    onPrimary = VelaOnPrimary,
    primaryContainer = VelaPrimaryContainer,
    onPrimaryContainer = VelaOnPrimaryContainer,
    secondary = VelaSecondary,
    onSecondary = VelaOnSecondary,
    secondaryContainer = VelaSecondaryContainer,
    onSecondaryContainer = VelaOnSecondaryContainer,
    tertiary = VelaTertiary,
    onTertiary = VelaOnTertiary,
    tertiaryContainer = VelaTertiaryContainer,
    onTertiaryContainer = VelaOnTertiaryContainer,
    background = VelaBackground,
    onBackground = VelaOnBackground,
    surface = VelaSurface,
    onSurface = VelaOnSurface,
    surfaceVariant = VelaSurfaceVariant,
    onSurfaceVariant = VelaOnSurfaceVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = VelaDarkPrimary,
    onPrimary = VelaDarkOnPrimary,
    primaryContainer = VelaDarkPrimaryContainer,
    onPrimaryContainer = VelaDarkOnPrimaryContainer,
    secondary = VelaDarkSecondary,
    onSecondary = VelaDarkOnSecondary,
    secondaryContainer = VelaDarkSecondaryContainer,
    onSecondaryContainer = VelaDarkOnSecondaryContainer,
    tertiary = VelaDarkTertiary,
    onTertiary = VelaDarkOnTertiary,
    tertiaryContainer = VelaDarkTertiaryContainer,
    onTertiaryContainer = VelaDarkOnTertiaryContainer,
    background = VelaDarkBackground,
    onBackground = VelaDarkOnBackground,
    surface = VelaDarkSurface,
    onSurface = VelaDarkOnSurface,
    surfaceVariant = VelaDarkSurfaceVariant,
    onSurfaceVariant = VelaDarkOnSurfaceVariant,
)

@Composable
fun VelaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VelaTypography,
        content = content,
    )
}
