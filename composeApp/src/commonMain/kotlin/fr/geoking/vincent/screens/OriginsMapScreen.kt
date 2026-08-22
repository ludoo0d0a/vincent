package fr.geoking.vincent.screens

import androidx.compose.runtime.Composable
import fr.geoking.vincent.model.Bottle

@Composable
expect fun OriginsMapScreen(
    onBack: () -> Unit,
    initialTab: OriginsMapTab = OriginsMapTab.Cellar,
    highlightOriginKey: String? = null,
    onOpenBottle: (Bottle) -> Unit = {},
)
