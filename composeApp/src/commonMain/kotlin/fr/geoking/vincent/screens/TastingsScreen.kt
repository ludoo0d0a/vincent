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
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.RedImportButton
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
    var busy by remember { mutableStateOf(false) }

    val importCsv = rememberCsvImport(onLoading = { busy = it }) { text ->
        val result = CsvFormat.parse(text)
        importStatus = if (result.type == CsvFormat.ImportType.TASTINGS) {
            TastingImportStatus.Success(Tastings.import(result.tastings))
        } else {
            TastingImportStatus.WrongType
        }
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
                RedImportButton(stringResource(Res.string.import_ploc), enabled = !busy, onClick = importCsv)
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

            Spacer(Modifier.height(24.dp))
        }
    }
}
