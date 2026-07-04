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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.vincent.data.ProviderCapability
import fr.geoking.vincent.data.Regions
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ExternalProviderButtons
import fr.geoking.vincent.ui.ImportStatusBanner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface RegionImportStatus {
    data class Success(val count: Int, val source: String) : RegionImportStatus
    data class Error(val message: String) : RegionImportStatus
}

@Composable
fun RegionsManagementScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<RegionImportStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    val providers = remember { WineDataSource.supporting(ProviderCapability.LIST_REGIONS) }
    val emptyMsg = stringResource(Res.string.data_import_empty)

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.regions_management_title),
            subtitle = pluralStringResource(Res.plurals.regions_management_subtitle, Regions.all.size, Regions.all.size),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.regions_management_import_desc),
                busy = busy,
            ) {
                ExternalProviderButtons(providers, enabled = !busy) { provider ->
                    busy = true
                    status = null
                    scope.launch {
                        val items = WineDataSource.listRegions(provider.id)
                        status = if (items.isNotEmpty()) {
                            RegionImportStatus.Success(Regions.import(items), provider.displayName)
                        } else {
                            RegionImportStatus.Error(emptyMsg)
                        }
                        busy = false
                    }
                }
            }

            status?.let { s ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    when (s) {
                        is RegionImportStatus.Success -> pluralStringResource(
                            Res.plurals.data_import_done,
                            s.count,
                            s.count,
                            s.source,
                        )
                        is RegionImportStatus.Error -> s.message
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
