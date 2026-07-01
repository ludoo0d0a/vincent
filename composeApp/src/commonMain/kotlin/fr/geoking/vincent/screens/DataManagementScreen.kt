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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.XWinesData
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenTastings: () -> Unit,
    onOpenProducers: () -> Unit,
    onOpenSuppliers: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.data_management_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(stringResource(Res.string.data_management_subtitle), fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(Res.string.data_management_section_transfer))
            DataLink(stringResource(Res.string.transfer_title), onOpenImportExport)

            SectionHeader(stringResource(Res.string.data_management_section_referentials))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataLink(stringResource(Res.string.ploc_tastings), onOpenTastings)
                DataLink(stringResource(Res.string.ploc_producers), onOpenProducers)
                DataLink(stringResource(Res.string.ploc_suppliers), onOpenSuppliers)
                DataLink(
                    label = stringResource(Res.string.data_management_racks),
                    sublabel = stringResource(Res.string.data_management_racks_subtitle, Racks.all.size),
                    onClick = {} // Currently managed in Cellar screen, but we keep the placeholder
                )
            }

            SectionHeader(stringResource(Res.string.data_management_section_offline))
            XWinesRow()

            Spacer(Modifier.height(24.dp))
        }
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

@Composable
private fun XWinesRow() {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val notConfigured = stringResource(Res.string.settings_xwines_not_configured)
    val genericError = stringResource(Res.string.settings_xwines_error)
    val downloading = XWinesData.isDownloading
    val count = XWinesData.count
    val updatedLabel = XWinesData.updatedAtLabel

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.settings_xwines_title), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
            val status = when {
                error != null -> error!!
                updatedLabel.isBlank() -> stringResource(Res.string.settings_xwines_never)
                else -> stringResource(Res.string.settings_xwines_count, count) +
                    "  ·  " + stringResource(Res.string.settings_xwines_updated, updatedLabel)
            }
            Spacer(Modifier.height(2.dp))
            Text(status, fontSize = 11.sp, fontWeight = FontWeight.W500, color = if (error != null) VincentColors.Accent else VincentColors.Faint)
        }
        Spacer(Modifier.width(12.dp))
        val actionLabel = when {
            downloading -> stringResource(Res.string.settings_xwines_downloading)
            updatedLabel.isBlank() -> stringResource(Res.string.settings_xwines_download)
            else -> stringResource(Res.string.settings_xwines_update)
        }
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (downloading) VincentColors.Surface2 else VincentColors.AccentSoft)
                .border(1.dp, if (downloading) VincentColors.Border else VincentColors.Accent, RoundedCornerShape(10.dp))
                .clickable(enabled = !downloading) {
                    error = null
                    scope.launch {
                        val result = XWinesData.update()
                        result.exceptionOrNull()?.let { e ->
                            error = if (e.message?.contains("not configured") == true) notConfigured else genericError
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (downloading) {
                CircularProgressIndicator(Modifier.size(15.dp), color = VincentColors.Accent, strokeWidth = 2.dp)
            } else {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
            }
        }
    }
}
