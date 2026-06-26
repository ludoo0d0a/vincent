package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/**
 * Applies the user's chosen language ([Settings.language]) to everything below it,
 * re-resolving Compose string resources whenever the choice changes. On targets
 * without runtime locale control this is a transparent pass-through.
 */
@Composable
expect fun ProvideAppLocale(content: @Composable () -> Unit)
