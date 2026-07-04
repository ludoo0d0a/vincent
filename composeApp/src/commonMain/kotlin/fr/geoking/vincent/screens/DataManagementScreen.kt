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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.SectionHeader
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

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
    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        DataScreenHeader(
            title = stringResource(Res.string.data_management_title),
            subtitle = stringResource(Res.string.data_management_subtitle),
            onBack = onBack,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
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
