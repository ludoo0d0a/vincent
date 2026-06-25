package fr.geoking.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors

@Composable
fun RecentRow(b: Bottle, onOpenBottle: (Bottle) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable { onOpenBottle(b) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottleThumb(b, Modifier.size(width = 26.dp, height = 54.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${b.domain} ${b.vintage}", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Icon(sourceIcon(b.source), contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(Res.string.recent_source_rack_format, stringResource(b.source.label), b.cellarSpot), fontSize = 10.5.sp, color = VincentColors.Muted)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(b.addedLabel, style = MonoNumber, fontSize = 10.sp, color = VincentColors.Faint)
            Text("×${b.quantity}", style = MonoNumber, fontSize = 11.sp, color = VincentColors.Fg, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

fun sourceIcon(source: AddSource): ImageVector = when (source) {
    AddSource.VOICE -> Icons.Filled.Mic
    AddSource.SCAN -> Icons.Filled.QrCodeScanner
    AddSource.PHOTO -> Icons.Filled.CameraAlt
    AddSource.MANUAL -> Icons.Filled.Edit
}
