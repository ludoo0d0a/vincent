package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Producers
import fr.geoking.vincent.data.Regions
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.VCard
import kotlinx.coroutines.launch

private sealed interface ExternalImportStatus {
    data class Success(val count: Int, val label: String) : ExternalImportStatus
    data class Error(val message: String) : ExternalImportStatus
}

@Composable
fun ExternalImportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ExternalImportStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.external_import_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(stringResource(Res.string.external_import_subtitle), fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Wikipedia section
            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Wikipedia", fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    Text(
                        stringResource(Res.string.external_import_wikipedia_desc),
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    ImportButton(
                        label = stringResource(Res.string.external_import_regions_wikipedia),
                        busy = busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val items = WineDataSource.listRegions("wikipedia")
                                if (items.isNotEmpty()) {
                                    val count = Regions.import(items)
                                    status = ExternalImportStatus.Success(count, "Wikipedia")
                                } else {
                                    status = ExternalImportStatus.Error("Aucune région trouvée")
                                }
                                busy = false
                            }
                        }
                    )
                }
            }

            // Grapeminds section
            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Grapeminds", fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                    Text(
                        stringResource(Res.string.external_import_grapeminds_desc),
                        fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
                    )

                    ImportButton(
                        label = stringResource(Res.string.external_import_producers_grapeminds),
                        busy = busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val items = WineDataSource.listProducers("grapeminds")
                                if (items.isNotEmpty()) {
                                    val count = Producers.import(items)
                                    status = ExternalImportStatus.Success(count, "Grapeminds")
                                } else {
                                    status = ExternalImportStatus.Error("Aucun producteur trouvé")
                                }
                                busy = false
                            }
                        }
                    )

                    ImportButton(
                        label = stringResource(Res.string.external_import_regions_grapeminds),
                        busy = busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val items = WineDataSource.listRegions("grapeminds")
                                if (items.isNotEmpty()) {
                                    val count = Regions.import(items)
                                    status = ExternalImportStatus.Success(count, "Grapeminds")
                                } else {
                                    status = ExternalImportStatus.Error("Aucune région trouvée")
                                }
                                busy = false
                            }
                        }
                    )

                    ImportButton(
                        label = stringResource(Res.string.external_import_wines_grapeminds),
                        busy = busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val items = WineDataSource.listWines("grapeminds")
                                if (items.isNotEmpty()) {
                                    val count = Cellar.importBottles(items)
                                    status = ExternalImportStatus.Success(count, "Grapeminds")
                                } else {
                                    status = ExternalImportStatus.Error("Aucun vin trouvé")
                                }
                                busy = false
                            }
                        }
                    )
                }
            }

            status?.let { s ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) {
                    Text(
                        when (s) {
                            is ExternalImportStatus.Success -> pluralStringResource(Res.plurals.data_import_done, s.count, s.count, s.label)
                            is ExternalImportStatus.Error -> s.message
                        },
                        fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep,
                    )
                }
            }

            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VincentColors.Accent, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
    ) {
        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.W700, fontSize = 13.sp)
    }
}
