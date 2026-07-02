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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.VCard

private sealed interface RackImportStatus {
    data class Success(val count: Int) : RackImportStatus
    data object WrongType : RackImportStatus
}

@Composable
fun RacksManagementScreen(onBack: () -> Unit) {
    var importStatus by remember { mutableStateOf<RackImportStatus?>(null) }
    var exportOk by remember { mutableStateOf<Boolean?>(null) }

    val importCsv = rememberCsvImport { text ->
        val result = CsvFormat.parse(text)
        if (result.type == CsvFormat.ImportType.RACKS) {
            result.racks.forEach { Racks.add(it) }
            importStatus = RackImportStatus.Success(result.racks.size)
        } else {
            importStatus = RackImportStatus.WrongType
        }
    }

    val exportCsv = rememberCsvExport("vincent-casiers.csv", { CsvFormat.racksToCsv(Racks.all.toList()) }) { ok ->
        exportOk = ok
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.racks_management_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(stringResource(Res.string.racks_management_subtitle), fontSize = 11.5.sp, color = VincentColors.Muted)
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
                        stringResource(Res.string.format_vincent_desc),
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = importCsv,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.transfer_import_button), fontWeight = FontWeight.W700, color = VincentColors.Accent)
                    }
                }
            }

            when (val status = importStatus) {
                is RackImportStatus.Success -> {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp)) {
                        Text(pluralStringResource(Res.plurals.cellar_import_success, status.count, status.count), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep)
                    }
                }
                RackImportStatus.WrongType -> {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp)) {
                        Text(stringResource(Res.string.cellar_import_wrong_type), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep)
                    }
                }
                null -> Unit
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
                        stringResource(Res.string.data_management_racks_subtitle, Racks.all.size),
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = exportCsv,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.export_racks_button), fontWeight = FontWeight.W700, color = VincentColors.Accent)
                    }
                }
            }

            exportOk?.let { ok ->
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) {
                    Text(
                        stringResource(if (ok) Res.string.transfer_export_success else Res.string.transfer_export_canceled),
                        fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
