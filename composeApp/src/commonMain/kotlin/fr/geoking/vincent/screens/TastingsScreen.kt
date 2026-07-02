package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.Stars
import fr.geoking.vincent.ui.VCard

private sealed interface TastingImportStatus {
    data class Success(val count: Int) : TastingImportStatus
    data object WrongType : TastingImportStatus
}

@Composable
fun TastingsScreen(onBack: () -> Unit) {
    var importStatus by remember { mutableStateOf<TastingImportStatus?>(null) }
    var exportOk by remember { mutableStateOf<Boolean?>(null) }

    val importCsv = rememberCsvImport { text ->
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
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.tastings_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(pluralStringResource(Res.plurals.tastings_subtitle, Tastings.all.size, Tastings.all.size), fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = importCsv,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.import_action), fontWeight = FontWeight.W700)
                }
                OutlinedButton(
                    onClick = exportCsv,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = VincentColors.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.export_tastings_button), fontWeight = FontWeight.W700, color = VincentColors.Accent)
                }
            }

            when (val status = importStatus) {
                is TastingImportStatus.Success -> Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) { Text(pluralStringResource(Res.plurals.tastings_import_success, status.count, status.count), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                TastingImportStatus.WrongType -> Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) { Text(stringResource(Res.string.tastings_import_wrong_type), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                null -> Unit
            }

            Tastings.all.forEach { tasting ->
                TastingCard(tasting)
                Spacer(Modifier.height(11.dp))
            }

            if (Tastings.all.isEmpty()) {
                Text(stringResource(Res.string.tastings_empty), fontSize = 13.sp, color = VincentColors.Muted, modifier = Modifier.padding(vertical = 24.dp))
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
                    Text(t.wineName, fontSize = 14.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    Text(t.date, fontSize = 11.sp, color = VincentColors.Muted)
                }
                Stars(t.rating)
            }
            if (t.notes.isNotEmpty()) {
                Text(t.notes, fontSize = 12.5.sp, color = VincentColors.Fg, modifier = Modifier.padding(top = 8.dp), lineHeight = 18.sp)
            }
        }
    }
}
