package com.matheusantiquera.gardenmanager.feature.maintenance

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme

/** Ícone e cores de um tipo de manutenção, como no canvas. Tipo desconhecido usa o calendário. */
data class MaintenanceTypeVisual(@DrawableRes val icon: Int, val container: Color, val content: Color)

@Composable
fun maintenanceTypeVisual(typeName: String): MaintenanceTypeVisual {
    val scheme = MaterialTheme.colorScheme
    val garden = GardenTheme.colors
    return when (typeName.lowercase()) {
        "rega" -> MaintenanceTypeVisual(R.drawable.ic_water, garden.waterContainer, garden.water)
        "adubação" -> MaintenanceTypeVisual(R.drawable.ic_sprout, scheme.primaryContainer, scheme.primary)
        "poda" -> MaintenanceTypeVisual(R.drawable.ic_scissors, scheme.primaryContainer, scheme.primary)
        "replante" -> MaintenanceTypeVisual(R.drawable.ic_leaf, scheme.primaryContainer, scheme.primary)
        "mudança de ambiente" -> MaintenanceTypeVisual(R.drawable.ic_home, scheme.surfaceVariant, scheme.onSurfaceVariant)
        else -> MaintenanceTypeVisual(R.drawable.ic_calendar, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

/** Ícone do tipo numa caixa arredondada colorida, o avatar das linhas e das sheets de manutenção. */
@Composable
fun MaintenanceTypeIcon(typeName: String, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 20.dp, corner: Dp = 12.dp) {
    val visual = maintenanceTypeVisual(typeName)
    Surface(modifier = modifier.size(size), shape = RoundedCornerShape(corner), color = visual.container, contentColor = visual.content) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(visual.icon), contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}
