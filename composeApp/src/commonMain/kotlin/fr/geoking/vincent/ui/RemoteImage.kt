package fr.geoking.vincent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Loads a remote product image (e.g. Open Food Facts label photo). No-op when [url] is null. */
@Composable
expect fun RemoteImage(url: String?, modifier: Modifier, contentDescription: String? = null)
