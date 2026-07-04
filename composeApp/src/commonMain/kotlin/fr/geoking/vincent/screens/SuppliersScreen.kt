package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.Suppliers
import fr.geoking.vincent.data.rememberCsvExport
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.model.Supplier
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.CsvFileImportButton
import fr.geoking.vincent.ui.DataExportCard
import fr.geoking.vincent.ui.DataImportCard
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.ImportStatusBanner
import fr.geoking.vincent.ui.VCard
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

private sealed interface SupplierImportStatus {
    data class Success(val count: Int) : SupplierImportStatus
    data object WrongType : SupplierImportStatus
}

@Composable
fun SuppliersScreen(onBack: () -> Unit) {
    var importStatus by remember { mutableStateOf<SupplierImportStatus?>(null) }
    var exportOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importCsv = rememberCsvImport(onLoading = { busy = it }) { text ->
        val result = CsvFormat.parse(text)
        importStatus = if (result.type == CsvFormat.ImportType.SUPPLIERS) {
            SupplierImportStatus.Success(Suppliers.import(result.suppliers))
        } else {
            SupplierImportStatus.WrongType
        }
    }

    val exportCsv = rememberCsvExport("vincent-fournisseurs.csv", { CsvFormat.suppliersToCsv(Suppliers.all.toList()) }) { ok ->
        exportOk = ok
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.suppliers_title),
            subtitle = stringResource(Res.string.data_management_suppliers_subtitle, Suppliers.all.size),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            DataImportCard(
                title = stringResource(Res.string.transfer_import_title),
                description = stringResource(Res.string.format_ploc_desc),
                busy = busy,
            ) {
                CsvFileImportButton(onClick = importCsv, enabled = !busy)
            }

            when (val status = importStatus) {
                is SupplierImportStatus.Success -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(pluralStringResource(Res.plurals.suppliers_import_success, status.count, status.count))
                }
                SupplierImportStatus.WrongType -> {
                    Spacer(Modifier.height(14.dp))
                    ImportStatusBanner(stringResource(Res.string.suppliers_import_wrong_type))
                }
                null -> Unit
            }

            Spacer(Modifier.height(14.dp))

            DataExportCard(
                title = stringResource(Res.string.transfer_export_title),
                description = stringResource(Res.string.data_management_suppliers_subtitle, Suppliers.all.size),
                buttonLabel = stringResource(Res.string.export_suppliers_button),
                onExport = exportCsv,
            )

            exportOk?.let { ok ->
                Spacer(Modifier.height(14.dp))
                ImportStatusBanner(
                    stringResource(if (ok) Res.string.transfer_export_success else Res.string.transfer_export_canceled),
                )
            }

            Spacer(Modifier.height(14.dp))

            Suppliers.all.forEach { supplier ->
                SupplierCard(supplier)
                Spacer(Modifier.height(11.dp))
            }

            if (Suppliers.all.isEmpty()) {
                androidx.compose.material3.Text(
                    stringResource(Res.string.suppliers_empty),
                    fontSize = 13.sp,
                    color = VincentColors.Muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SupplierCard(s: Supplier) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            androidx.compose.material3.Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            if (s.type.isNotEmpty()) {
                androidx.compose.material3.Text(s.type, fontSize = 11.sp, color = VincentColors.Muted)
            }
            if (s.email.isNotEmpty() || s.phone.isNotEmpty() || s.website.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                if (s.email.isNotEmpty()) androidx.compose.material3.Text(s.email, fontSize = 11.sp, color = VincentColors.Accent)
                if (s.phone.isNotEmpty()) androidx.compose.material3.Text(s.phone, fontSize = 11.sp, color = VincentColors.Fg)
                if (s.website.isNotEmpty()) androidx.compose.material3.Text(s.website, fontSize = 11.sp, color = VincentColors.Muted)
            }
        }
    }
}
