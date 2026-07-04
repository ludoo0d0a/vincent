package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Producers
import fr.geoking.vincent.data.ProviderCapability
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.model.Producer
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.CsvFileImportButton
import fr.geoking.vincent.ui.DataExportCard
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ExternalProviderButtons
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.VCard
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
    var exportOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    val externalProviders = remember { WineDataSource.supporting(ProviderCapability.LIST_PRODUCERS) }
    val emptyMsg = stringResource(Res.string.data_import_empty)

    val importCsv = rememberCsvImport(onLoading = { busy = it }) { text ->
        val result = CsvFormat.parse(text)
        importStatus = if (result.type == CsvFormat.ImportType.PRODUCERS) {
            ProducerImportStatus.Success(Producers.import(result.producers), result.source)
        } else {
            ProducerImportStatus.WrongType
        }
    }

    val exportCsv = rememberCsvExport("vincent-producteurs.csv", { CsvFormat.producersToCsv(Producers.all.toList()) }) { ok ->
        exportOk = ok
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
                CsvFileImportButton(onClick = importCsv, enabled = !busy)
                if (externalProviders.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(14.dp))

            DataExportCard(
                title = stringResource(Res.string.transfer_export_title),
                description = stringResource(Res.string.data_management_producers_subtitle, Producers.all.size),
                buttonLabel = stringResource(Res.string.export_producers_button),
                onExport = exportCsv,
            )

            exportOk?.let { ok ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    stringResource(if (ok) Res.string.transfer_export_success else Res.string.transfer_export_canceled),
                )
            }

            Spacer(Modifier.height(14.dp))

            Producers.all.forEach { producer ->
                ProducerCard(producer)
                Spacer(Modifier.height(11.dp))
            }

            if (Producers.all.isEmpty()) {
                androidx.compose.material3.Text(
                    stringResource(Res.string.producers_empty),
                    fontSize = 13.sp,
                    color = VincentColors.Muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProducerCard(p: Producer) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            androidx.compose.material3.Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            if (p.region.isNotEmpty() || p.country.isNotEmpty()) {
                androidx.compose.material3.Text(
                    "${p.region}${if (p.region.isNotEmpty() && p.country.isNotEmpty()) ", " else ""}${p.country}",
                    fontSize = 11.sp,
                    color = VincentColors.Muted,
                )
            }
            if (p.email.isNotEmpty() || p.phone.isNotEmpty() || p.website.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                if (p.email.isNotEmpty()) androidx.compose.material3.Text(p.email, fontSize = 11.sp, color = VincentColors.Accent)
                if (p.phone.isNotEmpty()) androidx.compose.material3.Text(p.phone, fontSize = 11.sp, color = VincentColors.Fg)
                if (p.website.isNotEmpty()) androidx.compose.material3.Text(p.website, fontSize = 11.sp, color = VincentColors.Muted)
            }
        }
    }
}
