package fr.geoking.vincent.screens

import androidx.compose.runtime.Composable

/**
 * AR cellar screen: points the camera at a photographed rack and overlays each
 * cell's bottle info. Fully offline (ARCore Augmented Images, no network/AI).
 *
 * Android-only feature; non-Android targets get a no-op placeholder. The whole
 * screen is gated by [fr.geoking.vincent.data.Features.arEnabled].
 */
@Composable
expect fun ArScreen(rackIndex: Int, onBack: () -> Unit)
