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
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.ProviderCapability
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.ImportSourceButtons
import fr.geoking.vincent.ui.DataExportCard
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ExternalProviderButtons
import fr.geoking.vincent.ui.ImportStatusBanner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface WineImportStatus {
    data class Success(val count: Int, val source: String) : WineImportStatus
    data object CsvEmpty : WineImportStatus
    data object ExternalEmpty : WineImportStatus
}

@Composable
fun WinesManagementScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf<WineImportStatus?>(null) }
    var exportOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    val externalProviders = remember { WineDataSource.supporting(ProviderCapability.LIST_WINES) }
    val emptyMsg = stringResource(Res.string.data_import_empty)

    val importCsv = rememberCsvImport(onLoading = { busy = it }) { text ->
        val result = CsvFormat.parse(text)
        val count = if (result.type == CsvFormat.ImportType.BOTTLES) {
            Cellar.importBottles(result.bottles)
        } else 0
        importStatus = if (count > 0) WineImportStatus.Success(count, result.source) else WineImportStatus.CsvEmpty
    }

    val exportCsv = rememberCsvExport("vincent-vins.csv", { Cellar.exportCsv() }) { ok ->
        exportOk = ok
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.wines_management_title),
            subtitle = stringResource(Res.string.data_management_wines_subtitle, Cellar.totalBottles()),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.wines_management_import_desc),
                busy = busy,
            ) {
                ImportSourceButtons(
                    sources = listOf(
                        stringResource(Res.string.import_ploc) to importCsv,
                        stringResource(Res.string.import_vivino) to importCsv,
                        stringResource(Res.string.import_cellartracker) to importCsv,
                    ),
                    enabled = !busy,
                )
                if (externalProviders.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ExternalProviderButtons(externalProviders, enabled = !busy) { provider ->
                        busy = true
                        importStatus = null
                        scope.launch {
                            val items = WineDataSource.listWines(provider.id)
                            importStatus = if (items.isNotEmpty()) {
                                WineImportStatus.Success(Cellar.importBottles(items), provider.displayName)
                            } else {
                                WineImportStatus.ExternalEmpty
                            }
                            busy = false
                        }
                    }
                }
            }

            importStatus?.let { status ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    when (status) {
                        is WineImportStatus.Success -> pluralStringResource(
                            Res.plurals.transfer_import_success,
                            status.count,
                            status.count,
                            status.source,
                        )
                        WineImportStatus.CsvEmpty -> stringResource(Res.string.transfer_import_none)
                        WineImportStatus.ExternalEmpty -> emptyMsg
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            DataExportCard(
                title = stringResource(Res.string.transfer_export_title),
                description = stringResource(Res.string.transfer_export_desc, Cellar.references()),
                buttonLabel = stringResource(Res.string.export_wines_button),
                onExport = exportCsv,
            )

            exportOk?.let { ok ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    stringResource(if (ok) Res.string.transfer_export_success else Res.string.transfer_export_canceled),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
