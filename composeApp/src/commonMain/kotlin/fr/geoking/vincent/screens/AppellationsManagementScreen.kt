package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Appellations
import fr.geoking.vincent.data.ReferenceDataImport
import fr.geoking.vincent.data.rememberJsonImport
import fr.geoking.vincent.data.rememberMapPackDownload
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.RedImportButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface AppellationImportStatus {
    data class Success(val count: Int) : AppellationImportStatus
    data object Empty : AppellationImportStatus
    data object Error : AppellationImportStatus
}

private sealed interface MapPackStatus {
    data class Success(val bytes: Long) : MapPackStatus
    data object Error : MapPackStatus
}

@Composable
fun AppellationsManagementScreen(
    onBack: () -> Unit,
    onOpenOriginsMap: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<AppellationImportStatus?>(null) }
    var mapStatus by remember { mutableStateOf<MapPackStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importJson = rememberJsonImport(onLoading = { busy = it }) { text ->
        runCatching { ReferenceDataImport.parseAppellationsJson(text) }
            .onSuccess { items ->
                status = if (items.isNotEmpty()) {
                    AppellationImportStatus.Success(Appellations.import(items))
                } else {
                    AppellationImportStatus.Empty
                }
            }
            .onFailure { status = AppellationImportStatus.Error }
    }

    val downloadMapPack = rememberMapPackDownload(onLoading = { busy = it }) { result ->
        mapStatus = result?.let { MapPackStatus.Success(it) } ?: MapPackStatus.Error
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.appellations_management_title),
            subtitle = pluralStringResource(
                Res.plurals.appellations_management_subtitle,
                Appellations.all.size,
                Appellations.all.size,
            ),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.appellations_management_import_desc),
                busy = busy,
            ) {
                RedImportButton(
                    stringResource(Res.string.appellations_management_import_siqo),
                    enabled = !busy,
                    onClick = importJson,
                )
                Spacer(Modifier.height(10.dp))
                RedImportButton(
                    stringResource(Res.string.appellations_management_download_map),
                    enabled = !busy,
                    onClick = downloadMapPack,
                )
            }

            status?.let { s ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    when (s) {
                        is AppellationImportStatus.Success -> pluralStringResource(
                            Res.plurals.appellations_import_success,
                            s.count,
                            s.count,
                        )
                        AppellationImportStatus.Empty -> stringResource(Res.string.transfer_import_none)
                        AppellationImportStatus.Error -> stringResource(Res.string.reference_import_error)
                    },
                )
            }

            mapStatus?.let { s ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    when (s) {
                        is MapPackStatus.Success -> stringResource(
                            Res.string.appellations_map_download_success,
                            s.bytes / 1024,
                        )
                        MapPackStatus.Error -> stringResource(Res.string.appellations_map_download_error)
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            RedImportButton(
                stringResource(Res.string.origins_map_open),
                enabled = !busy,
                onClick = onOpenOriginsMap,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.appellations_attribution),
                fontSize = 11.sp,
                color = VincentColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
