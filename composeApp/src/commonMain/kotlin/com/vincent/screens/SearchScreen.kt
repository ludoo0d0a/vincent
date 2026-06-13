package com.vincent.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vincent.data.Cellar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincent.model.WineColor
import com.vincent.theme.MonoNumber
import com.vincent.theme.VincentColors
import com.vincent.ui.ScreenHeader

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    var colorIdx by remember { mutableIntStateOf(0) }
    val color = WineColor.entries[colorIdx]
    val count = Cellar.matching(color).size
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader("Filtrer", "Affinez votre recherche", trailing = {
            Text("Réinitialiser", fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent)
        })
        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField("Mot-clé, cépage…")
            Spacer(Modifier.height(15.dp))

            GroupLabel("Couleur")
            ColorPicker(colorIdx) { colorIdx = it }
            Spacer(Modifier.height(15.dp))

            GroupLabel("Prix")
            PriceRange()
            Spacer(Modifier.height(15.dp))

            GroupLabel("Provenance")
            ChipFlow(listOf("Bordeaux", "Bourgogne", "Rhône", "Provence", "Italie", "Espagne"), preselected = setOf(0, 2))
            Spacer(Modifier.height(15.dp))

            GroupLabel("Caviste / magasin")
            ChipFlow(listOf("Lavinia", "Nicolas", "Caviste local", "En ligne"), preselected = setOf(0))
            Spacer(Modifier.height(15.dp))

            GroupLabel("Occasion")
            ChipFlow(listOf("À offrir", "Cave de garde", "Tous les jours", "Fête"), preselected = setOf(1))
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text("Voir $count bouteille${if (count > 1) "s" else ""}", fontWeight = FontWeight.W700)
            }
            Spacer(Modifier.height(70.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Muted, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WineColor.entries.forEachIndexed { i, c ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (on) VincentColors.AccentSoft else VincentColors.Surface)
                    .border(if (on) 1.5.dp else 1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(12.dp))
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(c.glass))
                Spacer(Modifier.height(6.dp))
                Text(c.label, fontSize = 10.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
            }
        }
    }
}

@Composable
private fun PriceRange() {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("10 €", style = MonoNumber, color = VincentColors.Fg)
            Text("120 €", style = MonoNumber, color = VincentColors.Fg)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(VincentColors.Border))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(0.14f))
                Box(Modifier.weight(0.56f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(VincentColors.Accent))
                Spacer(Modifier.weight(0.30f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(0.14f))
                Knob()
                Spacer(Modifier.weight(0.56f))
                Knob()
                Spacer(Modifier.weight(0.30f))
            }
        }
    }
}

@Composable
private fun Knob() {
    Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(Color.White).border(2.dp, VincentColors.Accent, RoundedCornerShape(50)))
}

@Composable
private fun ChipFlow(items: List<String>, preselected: Set<Int> = emptySet()) {
    val selected = remember { mutableStateOf(preselected) }
    // simple 2-per-row flow
    items.chunked(3).forEach { row ->
        Row(Modifier.padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            row.forEach { label ->
                val idx = items.indexOf(label)
                val on = selected.value.contains(idx)
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (on) VincentColors.Fg else VincentColors.Surface)
                        .border(1.dp, if (on) VincentColors.Fg else VincentColors.Border, RoundedCornerShape(20.dp))
                        .clickable {
                            selected.value = selected.value.toMutableSet().apply { if (on) remove(idx) else add(idx) }
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
                }
            }
        }
    }
}
