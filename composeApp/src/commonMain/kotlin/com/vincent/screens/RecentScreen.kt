package com.vincent.screens

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
import com.vincent.data.Cellar
import com.vincent.model.AddSource
import com.vincent.model.Bottle
import com.vincent.theme.MonoNumber
import com.vincent.theme.VincentColors
import com.vincent.ui.WineBottle

@Composable
fun RecentScreen(
    onBack: () -> Unit,
    onOpenBottle: (Bottle) -> Unit,
) {
    val today = Cellar.recent.take(2)
    val week = Cellar.recent.drop(2)

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Dernières bouteilles", fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text("12 ajouts récents", fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            DayGroup("Aujourd'hui")
            today.forEach { RecentRow(it, onOpenBottle) }
            DayGroup("Cette semaine")
            week.forEach { RecentRow(it, onOpenBottle) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DayGroup(label: String) {
    Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Muted, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 14.dp, bottom = 9.dp))
}

@Composable
private fun RecentRow(b: Bottle, onOpenBottle: (Bottle) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable { onOpenBottle(b) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WineBottle(b.color, Modifier.size(width = 26.dp, height = 54.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${b.domain} ${b.vintage}", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Icon(sourceIcon(b.source), contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text("${b.source.label} · casier ${b.cellarSpot}", fontSize = 10.5.sp, color = VincentColors.Muted)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(b.addedLabel, style = MonoNumber, fontSize = 10.sp, color = VincentColors.Faint)
            Text("×${b.quantity}", style = MonoNumber, fontSize = 11.sp, color = VincentColors.Fg, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun sourceIcon(source: AddSource): ImageVector = when (source) {
    AddSource.VOICE -> Icons.Filled.Mic
    AddSource.SCAN -> Icons.Filled.QrCodeScanner
    AddSource.PHOTO -> Icons.Filled.CameraAlt
    AddSource.MANUAL -> Icons.Filled.Edit
}
