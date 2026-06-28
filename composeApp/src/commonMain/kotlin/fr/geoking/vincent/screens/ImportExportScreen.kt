package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Producers
import fr.geoking.vincent.data.Suppliers
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.VCard

/** Outcome of a CSV import, used to render an inline confirmation banner. */
private sealed interface DataImportStatus {
    data class Success(val count: Int, val source: String) : DataImportStatus
    data object Empty : DataImportStatus
}

/**
 * Single page to manage the app data: import any supported CSV (bottles, racks,
 * tastings, producers, suppliers — auto-detected) and export the cellar to CSV.
 */
@Composable
fun ImportExportScreen(onBack: () -> Unit) {
    var exportOk by remember { mutableStateOf<Boolean?>(null) }
    var importStatus by remember { mutableStateOf<DataImportStatus?>(null) }

    val exportCsv = rememberCsvExport("vincent-cave.csv", { Cellar.exportCsv() }) { ok ->
        exportOk = ok
    }

    val importCsv = rememberCsvImport { text ->
        val result = CsvFormat.parse(text)
        val count = when (result.type) {
            CsvFormat.ImportType.BOTTLES -> Cellar.importBottles(result.bottles)
            CsvFormat.ImportType.TASTINGS -> Tastings.import(result.tastings)
            CsvFormat.ImportType.PRODUCERS -> Producers.import(result.producers)
            CsvFormat.ImportType.SUPPLIERS -> Suppliers.import(result.suppliers)
            else -> 0
        }
        importStatus = if (count > 0) DataImportStatus.Success(count, result.source) else DataImportStatus.Empty
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.transfer_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(stringResource(Res.string.transfer_subtitle), fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.transfer_import_title), fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    }
                    Text(
                        stringResource(Res.string.transfer_import_desc),
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = importCsv,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text(stringResource(Res.string.transfer_import_button), fontWeight = FontWeight.W700, color = VincentColors.Accent) }
                }
            }

            importStatus?.let { status ->
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) {
                    Text(
                        when (status) {
                            is DataImportStatus.Success -> pluralStringResource(Res.plurals.data_import_done, status.count, status.count, status.source)
                            DataImportStatus.Empty -> stringResource(Res.string.transfer_import_none)
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W600,
                        color = VincentColors.AccentDeep,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.transfer_export_title), fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    }
                    Text(
                        stringResource(Res.string.transfer_export_desc, Cellar.references()),
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = exportCsv,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text(stringResource(Res.string.transfer_export_button), fontWeight = FontWeight.W700, color = VincentColors.Accent) }
                }
            }

            exportOk?.let { ok ->
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) {
                    Text(
                        stringResource(if (ok) Res.string.transfer_export_success else Res.string.transfer_export_canceled),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W600,
                        color = VincentColors.AccentDeep,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
