package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Producers
import fr.geoking.vincent.data.ProviderCapability
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ExternalProviderButtons
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.RedImportButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface ProducerImportStatus {
    data class Success(val count: Int, val source: String) : ProducerImportStatus
    data object WrongType : ProducerImportStatus
    data object Empty : ProducerImportStatus
}

@Composable
fun ProducersScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf<ProducerImportStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    val externalProviders = remember { WineDataSource.supporting(ProviderCapability.LIST_PRODUCERS) }
    val emptyMsg = stringResource(Res.string.data_import_empty)

    val importCsv = rememberCsvImport(onLoading = { busy = it }) { text ->
        val result = CsvFormat.parse(text, CsvFormat.ImportType.PRODUCERS)
        importStatus = if (result.type == CsvFormat.ImportType.PRODUCERS) {
            ProducerImportStatus.Success(Producers.import(result.producers), result.source)
        } else {
            ProducerImportStatus.WrongType
        }
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.producers_title),
            subtitle = stringResource(Res.string.data_management_producers_subtitle, Producers.all.size),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.format_ploc_desc),
                busy = busy,
            ) {
                RedImportButton(stringResource(Res.string.import_ploc), enabled = !busy, onClick = importCsv)
                if (externalProviders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ExternalProviderButtons(externalProviders, enabled = !busy) { provider ->
                        busy = true
                        importStatus = null
                        scope.launch {
                            val items = WineDataSource.listProducers(provider.id)
                            importStatus = if (items.isNotEmpty()) {
                                ProducerImportStatus.Success(Producers.import(items), provider.displayName)
                            } else {
                                ProducerImportStatus.Empty
                            }
                            busy = false
                        }
                    }
                }
            }

            when (val status = importStatus) {
                is ProducerImportStatus.Success -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(pluralStringResource(Res.plurals.data_import_done, status.count, status.count, status.source))
                }
                ProducerImportStatus.WrongType -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(stringResource(Res.string.producers_import_wrong_type))
                }
                ProducerImportStatus.Empty -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(emptyMsg)
                }
                null -> Unit
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
