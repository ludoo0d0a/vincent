package fr.geoking.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.WineDataProvider
import fr.geoking.vincent.theme.VincentColors
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

@Composable
fun DataScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VincentColors.Surface2)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                modifier = Modifier.size(18.dp),
                tint = VincentColors.Fg,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            Text(subtitle, fontSize = 11.5.sp, color = VincentColors.Muted)
        }
    }
}

@Composable
fun DataImportCard(
    title: String,
    description: String,
    busy: Boolean,
    content: @Composable () -> Unit,
) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            }
            Text(
                description,
                fontSize = 12.sp,
                color = VincentColors.Muted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            content()
            if (busy) {
                Spacer(Modifier.height(12.dp))
                ImportBusyIndicator()
            }
        }
    }
}

@Composable
fun DataExportCard(
    title: String,
    description: String,
    buttonLabel: String,
    onExport: () -> Unit,
) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            }
            Text(
                description,
                fontSize = 12.sp,
                color = VincentColors.Muted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(46.dp),
                shape = RoundedCornerShape(13.dp),
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel, fontWeight = FontWeight.W700, color = VincentColors.Accent)
            }
        }
    }
}

@Composable
fun CsvProviderGrid(providers: List<Pair<String, () -> Unit>>, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, onClick) ->
                    ImportSourceButton(label, Modifier.weight(1f), enabled = enabled, onClick = onClick)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CsvFileImportButton(onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(13.dp),
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.transfer_import_button), fontWeight = FontWeight.W700, color = VincentColors.Accent)
    }
}

@Composable
fun ExternalProviderButtons(
    providers: List<WineDataProvider>,
    enabled: Boolean,
    onImport: (WineDataProvider) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.forEach { provider ->
            Button(
                onClick = { onImport(provider) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(provider.displayName, fontWeight = FontWeight.W700, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ImportSourceButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) VincentColors.Surface2 else VincentColors.Surface2.copy(alpha = 0.5f))
            .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = if (enabled) VincentColors.Fg else VincentColors.Muted)
    }
}

@Composable
fun ImportStatusBanner(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VincentColors.AccentSoft)
            .padding(13.dp),
    ) {
        Text(message, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep)
    }
}

@Composable
fun ImportBusyIndicator() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = VincentColors.Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(Res.string.data_import_loading), fontSize = 12.sp, color = VincentColors.Muted)
    }
}
