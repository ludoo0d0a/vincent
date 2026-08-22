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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.vincent.data.Grapes
import fr.geoking.vincent.data.ReferenceDataImport
import fr.geoking.vincent.data.rememberJsonImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.RedImportButton
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface GrapeImportStatus {
    data class Success(val count: Int) : GrapeImportStatus
    data object Empty : GrapeImportStatus
    data object Error : GrapeImportStatus
}

@Composable
fun GrapesManagementScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<GrapeImportStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importJson = rememberJsonImport(onLoading = { busy = it }) { text ->
        runCatching { ReferenceDataImport.parseGrapesJson(text) }
            .onSuccess { items ->
                status = if (items.isNotEmpty()) {
                    GrapeImportStatus.Success(Grapes.import(items))
                } else {
                    GrapeImportStatus.Empty
                }
            }
            .onFailure { status = GrapeImportStatus.Error }
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.grapes_management_title),
            subtitle = pluralStringResource(Res.plurals.grapes_management_subtitle, Grapes.all.size, Grapes.all.size),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.grapes_management_import_desc),
                busy = busy,
            ) {
                RedImportButton(
                    stringResource(Res.string.grapes_management_import_vivc),
                    enabled = !busy,
                    onClick = importJson,
                )
            }

            status?.let { s ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    when (s) {
                        is GrapeImportStatus.Success -> pluralStringResource(
                            Res.plurals.grapes_import_success,
                            s.count,
                            s.count,
                        )
                        GrapeImportStatus.Empty -> stringResource(Res.string.transfer_import_none)
                        GrapeImportStatus.Error -> stringResource(Res.string.reference_import_error)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
