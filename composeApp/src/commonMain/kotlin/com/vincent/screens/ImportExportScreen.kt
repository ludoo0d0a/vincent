package com.vincent.screens

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
import com.vincent.data.Cellar
import com.vincent.data.CsvFormat
import com.vincent.data.rememberCsvExport
import com.vincent.data.rememberCsvImport
import com.vincent.theme.VincentColors
import com.vincent.ui.SectionHeader
import com.vincent.ui.VCard

@Composable
fun ImportExportScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }

    val importCsv = rememberCsvImport { text ->
        val result = CsvFormat.parse(text)
        val n = Cellar.importBottles(result.bottles)
        status = if (n > 0) "Importé : $n bouteille${if (n > 1) "s" else ""} · source détectée « ${result.source} »"
        else "Aucune bouteille reconnue dans ce fichier."
    }
    val exportCsv = rememberCsvExport("vincent-cave.csv", { Cellar.exportCsv() }) { ok ->
        status = if (ok) "Cave exportée en CSV." else "Export annulé."
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Importer / Exporter", fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text("Transférez votre cave en CSV", fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            // Import
            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Importer une cave", fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    }
                    Text(
                        "Chargez un export CSV de PLOC, Vivino ou d'un tableur. Les colonnes sont reconnues automatiquement (couleur, millésime, prix, région…).",
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(
                        onClick = importCsv,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
                    ) { Text("Choisir un fichier CSV", fontWeight = FontWeight.W700) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Export
            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Exporter ma cave", fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    }
                    Text(
                        "Génère un fichier CSV de vos ${Cellar.references()} références — réimportable dans Vincent ou lisible dans tout tableur.",
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = exportCsv,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) { Text("Exporter en CSV", fontWeight = FontWeight.W700, color = VincentColors.Accent) }
                }
            }

            if (status != null) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) { Text(status!!, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
            }

            SectionHeader("Formats pris en charge")
            FormatRow("PLOC", "Export CSV (Réglages → Exporter)")
            FormatRow("Vivino", "Export CSV de la cave / liste")
            FormatRow("Vincent", "CSV natif — round-trip complet")
            FormatRow("Tableur", "Tout CSV avec en-têtes lisibles")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormatRow(name: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        Text(detail, fontSize = 11.5.sp, color = VincentColors.Muted)
    }
}
