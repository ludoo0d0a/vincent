package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Producers
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.Regions
import fr.geoking.vincent.data.Suppliers
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.VincentBackup
import fr.geoking.vincent.data.VincentImportMode
import fr.geoking.vincent.data.VincentImportResult
import fr.geoking.vincent.data.VincentParsedBackup
import fr.geoking.vincent.data.readLocalBytes
import fr.geoking.vincent.data.rememberLabelImageSaver
import fr.geoking.vincent.data.rememberRackImageSaver
import fr.geoking.vincent.data.rememberVincentExport
import fr.geoking.vincent.data.rememberVincentImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.SectionHeader
import fr.geoking.vincent.ui.VCard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface BackupUiStatus {
    data class ImportSuccess(val result: VincentImportResult) : BackupUiStatus
    data object ImportError : BackupUiStatus
    data object ExportSuccess : BackupUiStatus
    data object ExportCanceled : BackupUiStatus
}

@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    onOpenWines: () -> Unit,
    onOpenRacks: () -> Unit,
    onOpenTastings: () -> Unit,
    onOpenProducers: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenRegions: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val labelSaver = rememberLabelImageSaver()
    val rackSaver = rememberRackImageSaver()
    var busy by remember { mutableStateOf(false) }
    var includePhotos by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<BackupUiStatus?>(null) }
    var pendingImport by remember { mutableStateOf<VincentParsedBackup?>(null) }
    var importMode by remember { mutableStateOf(VincentImportMode.MERGE) }

    val exportSummary = stringResource(
        Res.string.vincent_backup_export_summary,
        Cellar.bottles.size,
        Racks.all.size,
        Tastings.all.size,
        Producers.all.size,
        Suppliers.all.size,
        Regions.all.size,
    )

    val importBackup = rememberVincentImport(onLoading = { busy = it }) { bytes ->
        status = null
        runCatching { VincentBackup.parseImport(bytes) }
            .onSuccess { pendingImport = it }
            .onFailure { status = BackupUiStatus.ImportError }
    }

    val exportBackup = rememberVincentExport(
        includePhotos = includePhotos,
        content = { VincentBackup.buildExport(includePhotos, ::readLocalBytes) },
        onResult = { ok ->
            status = if (ok) BackupUiStatus.ExportSuccess else BackupUiStatus.ExportCanceled
        },
    )

    pendingImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(Res.string.vincent_backup_import_mode_title)) },
            text = {
                Column {
                    ImportModeRow(
                        selected = importMode == VincentImportMode.MERGE,
                        label = stringResource(Res.string.vincent_backup_import_mode_merge),
                        onClick = { importMode = VincentImportMode.MERGE },
                    )
                    ImportModeRow(
                        selected = importMode == VincentImportMode.REPLACE,
                        label = stringResource(Res.string.vincent_backup_import_mode_replace),
                        onClick = { importMode = VincentImportMode.REPLACE },
                    )
                    if (importMode == VincentImportMode.REPLACE) {
                        Text(
                            stringResource(Res.string.vincent_backup_import_mode_replace_warning),
                            fontSize = 12.sp,
                            color = VincentColors.Red,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val backup = parsed
                        pendingImport = null
                        busy = true
                        scope.launch {
                            runCatching {
                                VincentBackup.applyImport(backup, importMode, labelSaver, rackSaver)
                            }.onSuccess { result ->
                                status = BackupUiStatus.ImportSuccess(result)
                            }.onFailure {
                                status = BackupUiStatus.ImportError
                            }
                            busy = false
                        }
                    },
                ) {
                    Text(stringResource(Res.string.vincent_backup_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(Res.string.cellar_action_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.data_management_title),
            subtitle = stringResource(Res.string.data_management_subtitle),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(Res.string.data_management_section_transfer))

            DataImportCard(
                title = stringResource(Res.string.vincent_backup_import_title),
                description = stringResource(Res.string.vincent_backup_import_desc),
                busy = busy,
            ) {
                OutlinedButton(
                    onClick = importBackup,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.vincent_backup_import_button),
                        fontWeight = FontWeight.W700,
                        color = VincentColors.Accent,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            VCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(Res.string.vincent_backup_export_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                        color = VincentColors.Fg,
                    )
                    Text(
                        stringResource(Res.string.vincent_backup_export_desc),
                        fontSize = 12.sp,
                        color = VincentColors.Muted,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        exportSummary,
                        fontSize = 11.5.sp,
                        color = VincentColors.Muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = includePhotos,
                            onCheckedChange = { includePhotos = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.vincent_backup_include_photos),
                            fontSize = 13.sp,
                            color = VincentColors.Fg,
                        )
                    }
                    OutlinedButton(
                        onClick = exportBackup,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.vincent_backup_export_button),
                            fontWeight = FontWeight.W700,
                            color = VincentColors.Accent,
                        )
                    }
                }
            }

            status?.let { s ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    when (s) {
                        is BackupUiStatus.ImportSuccess -> stringResource(
                            Res.string.vincent_backup_import_success,
                            s.result.bottles,
                            s.result.racks,
                            s.result.tastings,
                            s.result.photosRestored,
                        )
                        BackupUiStatus.ImportError -> stringResource(Res.string.vincent_backup_import_error)
                        BackupUiStatus.ExportSuccess -> stringResource(Res.string.vincent_backup_export_success)
                        BackupUiStatus.ExportCanceled -> stringResource(Res.string.vincent_backup_export_canceled)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(Res.string.data_management_section_data))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataLink(
                    label = stringResource(Res.string.data_management_wines),
                    sublabel = stringResource(Res.string.data_management_wines_subtitle, Cellar.totalBottles()),
                    onClick = onOpenWines,
                )
                DataLink(
                    label = stringResource(Res.string.data_management_racks),
                    sublabel = stringResource(Res.string.data_management_racks_subtitle, Racks.all.size),
                    onClick = onOpenRacks,
                )
                DataLink(
                    label = stringResource(Res.string.data_management_tastings),
                    sublabel = pluralStringResource(Res.plurals.tastings_subtitle, Tastings.all.size, Tastings.all.size),
                    onClick = onOpenTastings,
                )
                DataLink(
                    label = stringResource(Res.string.data_management_producers),
                    sublabel = stringResource(Res.string.data_management_producers_subtitle, Producers.all.size),
                    onClick = onOpenProducers,
                )
                DataLink(
                    label = stringResource(Res.string.data_management_suppliers),
                    sublabel = stringResource(Res.string.data_management_suppliers_subtitle, Suppliers.all.size),
                    onClick = onOpenSuppliers,
                )
                DataLink(
                    label = stringResource(Res.string.data_management_regions),
                    sublabel = pluralStringResource(Res.plurals.regions_management_subtitle, Regions.all.size, Regions.all.size),
                    onClick = onOpenRegions,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 13.sp, color = VincentColors.Fg)
    }
}

@Composable
private fun DataLink(label: String, onClick: () -> Unit, sublabel: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
            if (sublabel != null) {
                Text(sublabel, fontSize = 11.sp, color = VincentColors.Muted)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(13.dp))
    }
}
