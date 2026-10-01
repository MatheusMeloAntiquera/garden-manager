package com.matheusantiquera.gardenmanager.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val GardenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalGardenColors = staticCompositionLocalOf { LightGardenColors }

/** Acesso às cores de estado: `GardenTheme.colors.water`, etc. */
object GardenTheme {
    val colors: GardenColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGardenColors.current
}

@Composable
fun GardenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGardenColors provides if (darkTheme) DarkGardenColors else LightGardenColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = GardenTypography,
            shapes = GardenShapes,
            content = content,
        )
    }
}
