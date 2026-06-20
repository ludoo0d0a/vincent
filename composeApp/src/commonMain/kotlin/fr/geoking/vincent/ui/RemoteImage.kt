package fr.geoking.vincent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** Loads a product image from a remote URL or a local file path. No-op when [uri] is null. */
@Composable
expect fun RemoteImage(
    url: String?,
    modifier: Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
)
