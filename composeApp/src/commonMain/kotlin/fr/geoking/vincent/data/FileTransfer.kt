package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/** Opens a system file picker and returns the chosen file's text content. */
@Composable
expect fun rememberCsvImport(onLoading: (Boolean) -> Unit = {}, onText: (String) -> Unit): () -> Unit

/** Opens a multi-file picker for a full PLOC CSV export folder. */
@Composable
expect fun rememberPlocBundleImport(onLoading: (Boolean) -> Unit = {}, onFiles: (List<PlocCsvFile>) -> Unit): () -> Unit

/** Opens a system "create document" dialog and writes [content] to the chosen file. */
@Composable
expect fun rememberCsvExport(
    filename: String,
    content: () -> String,
    onResult: (Boolean) -> Unit,
): () -> Unit

/** Opens a system file picker and returns the chosen backup bytes (.json or .vincent zip). */
@Composable
expect fun rememberVincentImport(onLoading: (Boolean) -> Unit, onBytes: (ByteArray) -> Unit): () -> Unit

/** Opens a system "create document" dialog and writes a Vincent backup archive. */
@Composable
expect fun rememberVincentExport(
    includePhotos: Boolean,
    content: suspend () -> ByteArray,
    onResult: (Boolean) -> Unit,
): () -> Unit
