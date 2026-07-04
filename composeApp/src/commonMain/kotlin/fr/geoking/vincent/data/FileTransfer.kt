package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/** Opens a system file picker and returns the chosen file's text content. */
@Composable
expect fun rememberCsvImport(onLoading: (Boolean) -> Unit = {}, onText: (String) -> Unit): () -> Unit

/** Opens a system "create document" dialog and writes [content] to the chosen file. */
@Composable
expect fun rememberCsvExport(
    filename: String,
    content: () -> String,
    onResult: (Boolean) -> Unit,
): () -> Unit
