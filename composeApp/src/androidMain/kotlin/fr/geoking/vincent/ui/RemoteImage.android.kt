package fr.geoking.vincent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

@Composable
actual fun RemoteImage(
    url: String?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    if (url.isNullOrBlank()) return
    val model = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("file://") -> url
        else -> File(url)
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
