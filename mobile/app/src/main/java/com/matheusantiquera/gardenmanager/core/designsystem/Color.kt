package com.matheusantiquera.gardenmanager.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Paleta "Musgo" (claro): mesmos valores de mobile/design/README.md.
internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E5E45),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE6D3),
    onPrimaryContainer = Color(0xFF0F2F1E),
    secondary = Color(0xFF4E5B52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7EDE4),
    onSecondaryContainer = Color(0xFF18211B),
    background = Color(0xFFF4F6F1),
    onBackground = Color(0xFF18211B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18211B),
    surfaceVariant = Color(0xFFE7EDE4),
    onSurfaceVariant = Color(0xFF4E5B52),
    outline = Color(0xFFC3CCC2),
    outlineVariant = Color(0xFFE1E7DF),
    error = Color(0xFFA1281F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDA),
    onErrorContainer = Color(0xFFA1281F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE7EDE4),
)

// Variante escura derivada da Musgo (ainda não desenhada no canvas).
internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FD3A8),
    onPrimary = Color(0xFF0F2F1E),
    primaryContainer = Color(0xFF1F4331),
    onPrimaryContainer = Color(0xFFCDE6D3),
    secondary = Color(0xFFB4C0B5),
    onSecondary = Color(0xFF1F2A22),
    secondaryContainer = Color(0xFF26332A),
    onSecondaryContainer = Color(0xFFE1E7DF),
    background = Color(0xFF101611),
    onBackground = Color(0xFFE1E7DF),
    surface = Color(0xFF17201A),
    onSurface = Color(0xFFE1E7DF),
    surfaceVariant = Color(0xFF26332A),
    onSurfaceVariant = Color(0xFFB4C0B5),
    outline = Color(0xFF6B7A6F),
    outlineVariant = Color(0xFF2E3B31),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFF9DEDA),
    surfaceContainerLowest = Color(0xFF0C110D),
    surfaceContainerLow = Color(0xFF141C16),
    surfaceContainer = Color(0xFF17201A),
    surfaceContainerHigh = Color(0xFF1D2820),
    surfaceContainerHighest = Color(0xFF26332A),
)

/** Cores de estado das manutenções, que o Material 3 não tem. A "atrasada" usa `error`/`errorContainer`. */
@Immutable
data class GardenColors(
    val water: Color,
    val waterContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
)

internal val LightGardenColors = GardenColors(
    water = Color(0xFF2F5F8A),
    waterContainer = Color(0xFFD8E6F3),
    pending = Color(0xFF8A5A00),
    pendingContainer = Color(0xFFFBE8C4),
)

internal val DarkGardenColors = GardenColors(
    water = Color(0xFF9CC4E8),
    waterContainer = Color(0xFF1E3A55),
    pending = Color(0xFFF2C46E),
    pendingContainer = Color(0xFF4A3300),
)
