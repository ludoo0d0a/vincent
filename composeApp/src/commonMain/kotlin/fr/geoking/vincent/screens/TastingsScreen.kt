package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.CsvFileImportButton
import fr.geoking.vincent.ui.DataExportCard
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.Stars
import fr.geoking.vincent.ui.VCard
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface TastingImportStatus {
    data class Success(val count: Int) : TastingImportStatus
    data object WrongType : TastingImportStatus
}

@Composable
fun TastingsScreen(onBack: () -> Unit) {
    var importStatus by remember { mutableStateOf<TastingImportStatus?>(null) }
    var exportOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importCsv = rememberCsvImport(onLoading = { busy = it }) { text ->
        val result = CsvFormat.parse(text)
        importStatus = if (result.type == CsvFormat.ImportType.TASTINGS) {
            TastingImportStatus.Success(Tastings.import(result.tastings))
        } else {
            TastingImportStatus.WrongType
        }
    }

    val exportCsv = rememberCsvExport("vincent-degustations.csv", { CsvFormat.tastingsToCsv(Tastings.all.toList()) }) { ok ->
        exportOk = ok
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.tastings_title),
            subtitle = pluralStringResource(Res.plurals.tastings_subtitle, Tastings.all.size, Tastings.all.size),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.format_ploc_desc),
                busy = busy,
            ) {
                CsvFileImportButton(onClick = importCsv, enabled = !busy)
            }

            when (val status = importStatus) {
                is TastingImportStatus.Success -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(pluralStringResource(Res.plurals.tastings_import_success, status.count, status.count))
                }
                TastingImportStatus.WrongType -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(stringResource(Res.string.tastings_import_wrong_type))
                }
                null -> Unit
            }

            Spacer(Modifier.height(14.dp))

            DataExportCard(
                title = stringResource(Res.string.transfer_export_title),
                description = pluralStringResource(Res.plurals.tastings_subtitle, Tastings.all.size, Tastings.all.size),
                buttonLabel = stringResource(Res.string.export_tastings_button),
                onExport = exportCsv,
            )

            exportOk?.let { ok ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    stringResource(if (ok) Res.string.transfer_export_success else Res.string.transfer_export_canceled),
                )
            }

            Spacer(Modifier.height(14.dp))

            Tastings.all.forEach { tasting ->
                TastingCard(tasting)
                Spacer(Modifier.height(11.dp))
            }

            if (Tastings.all.isEmpty()) {
                androidx.compose.material3.Text(
                    stringResource(Res.string.tastings_empty),
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
private fun TastingCard(t: Tasting) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(t.wineName, fontSize = 14.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    androidx.compose.material3.Text(t.date, fontSize = 11.sp, color = VincentColors.Muted)
                }
                Stars(t.rating)
            }
            if (t.notes.isNotEmpty()) {
                androidx.compose.material3.Text(
                    t.notes,
                    fontSize = 12.5.sp,
                    color = VincentColors.Fg,
                    modifier = Modifier.padding(top = 8.dp),
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
