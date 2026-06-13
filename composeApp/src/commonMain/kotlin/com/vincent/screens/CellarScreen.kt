package com.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincent.data.Cellar
import com.vincent.model.Bottle
import com.vincent.model.RackCell
import com.vincent.model.RackMode
import com.vincent.model.SampleData
import com.vincent.model.WineColor
import com.vincent.theme.MonoNumber
import com.vincent.theme.VincentColors
import com.vincent.ui.ColorTag
import com.vincent.ui.ScreenHeader
import com.vincent.ui.VCard
import com.vincent.ui.WineBottle

@Composable
fun CellarScreen(
    modifier: Modifier = Modifier,
    onOpenBottle: (Bottle) -> Unit,
) {
    var mode by remember { mutableStateOf(RackMode.VINTAGE) }
    val occupied = SampleData.rackA.count { it.occupied }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader("Casiers", "${SampleData.rackA.size} emplacements · $occupied occupés")

        Column(Modifier.padding(horizontal = 16.dp)) {
            CellarTabs()
            Spacer(Modifier.height(10.dp))
            ModeSelector(mode) { mode = it }
            Spacer(Modifier.height(11.dp))
            RackGrid(mode)
            Spacer(Modifier.height(11.dp))
            PeekCard(onOpenBottle)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CellarTabs() {
    val tabs = listOf("Cave A", "Cave B", "Réfrigérée")
    var selected by remember { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) VincentColors.Accent else VincentColors.Surface)
                    .border(1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(9.dp))
                    .clickable { selected = i }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(t, fontSize = 12.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: RackMode, onMode: (RackMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RackMode.entries.forEach { m ->
            val on = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) VincentColors.Surface else Color.Transparent)
                    .clickable { onMode(m) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W700,
                    color = if (on) VincentColors.Accent else VincentColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun RackGrid(mode: RackMode) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SampleData.rackA.chunked(6).forEach { rowCells ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        rowCells.first().row,
                        fontSize = 10.sp,
                        color = VincentColors.Faint,
                        modifier = Modifier.width(14.dp),
                    )
                    rowCells.forEach { cell -> Cell(cell, mode, Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touchez un mode pour changer l'étiquette", fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W500)
                Text("73%", style = MonoNumber, color = VincentColors.Muted)
            }
        }
    }
}

@Composable
private fun Cell(cell: RackCell, mode: RackMode, modifier: Modifier = Modifier) {
    if (!cell.occupied) {
        Box(
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(VincentColors.Surface2)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(7.dp)),
        )
        return
    }
    val tint = lerp(Color.White, cell.color!!.glass, 0.20f)
    val borderTint = lerp(Color.White, cell.color.glass, 0.34f)
    val label = when (mode) {
        RackMode.COLOR -> ""
        RackMode.PRICE -> "${cell.price}€"
        RackMode.VINTAGE -> cell.vintage.orEmpty()
        RackMode.CATEGORY -> cell.category?.short.orEmpty()
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(7.dp))
            .background(tint)
            .border(if (cell.selected) 2.5.dp else 1.dp, if (cell.selected) VincentColors.Accent else borderTint, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.82f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(label, style = MonoNumber, fontSize = 8.sp, color = Color(0xFF2A1717))
            }
        }
    }
}

@Composable
private fun PeekCard(onOpenBottle: (Bottle) -> Unit) {
    val bottle = Cellar.bottles.first { it.cellarSpot == "B3" || it.id == "leoville-2016" }
    VCard(Modifier.fillMaxWidth().clickable { onOpenBottle(bottle) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WineBottle(WineColor.RED, Modifier.size(width = 30.dp, height = 46.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Emplacement B3", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                Text("${bottle.domain} ${bottle.vintage} · ${bottle.price} €", fontSize = 11.sp, color = VincentColors.Muted)
            }
            ColorTag(bottle.color, label = bottle.category.label)
        }
    }
}
