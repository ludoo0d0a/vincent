package fr.geoking.vincent

import androidx.compose.runtime.Composable

/**
 * Handles the platform back gesture/button. On Android it maps to the system
 * back press; while [enabled] is true, [onBack] is invoked instead of the
 * default (which would finish the activity).
 */
@Composable
expect fun NavBackHandler(enabled: Boolean, onBack: () -> Unit)
