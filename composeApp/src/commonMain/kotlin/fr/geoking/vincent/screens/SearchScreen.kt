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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.ScreenHeader

/** A search dimension: its label, the full option list, and how many to show inline. */
private enum class Crit(val label: String, val all: List<String>, val common: Int) {
    TASTE(
        "Goût / arômes",
        listOf("Fruité", "Sec", "Minéral", "Tannique", "Boisé", "Vif", "Rond", "Épicé",
            "Floral", "Léger", "Puissant", "Acidulé", "Doux", "Complexe", "Fumé", "Long en bouche"),
        6,
    ),
    GRAPE(
        "Cépage",
        listOf("Cabernet Sauvignon", "Merlot", "Pinot Noir", "Syrah", "Grenache", "Chardonnay",
            "Sauvignon Blanc", "Chenin", "Riesling", "Gamay", "Cinsault", "Mourvèdre", "Viognier", "Sémillon"),
        5,
    ),
    REGION(
        "Provenance",
        listOf("Bordeaux", "Bourgogne", "Rhône", "Provence", "Loire", "Champagne",
            "Languedoc", "Alsace", "Beaujolais", "Sud-Ouest", "Italie", "Espagne", "Portugal"),
        6,
    ),
    MERCHANT(
        "Caviste / magasin",
        listOf("Lavinia", "Nicolas", "Caviste local", "En ligne", "Producteur", "Foire aux vins"),
        4,
    ),
    OCCASION(
        "Occasion",
        listOf("À offrir", "Cave de garde", "Tous les jours", "Fête", "Accord mets", "Découverte"),
        4,
    ),
}

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    var colorIdx by remember { mutableIntStateOf(0) }
    var selections by remember { mutableStateOf<Map<Crit, Set<String>>>(emptyMap()) }
    var picker by remember { mutableStateOf<Crit?>(null) }

    fun toggle(crit: Crit, item: String) {
        val current = selections[crit] ?: emptySet()
        val next = if (item in current) current - item else current + item
        selections = selections + (crit to next)
    }

    val color = WineColor.entries[colorIdx]
    val count = Cellar.matching(color).size

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader("Filtrer", "Affinez votre recherche", trailing = {
                Text("Réinitialiser", fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent,
                    modifier = Modifier.clickable { selections = emptyMap(); colorIdx = 0 })
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

                Crit.entries.forEach { crit ->
                    CritSection(
                        crit = crit,
                        selected = selections[crit] ?: emptySet(),
                        onToggle = { toggle(crit, it) },
                        onMore = { picker = crit },
                    )
                    Spacer(Modifier.height(15.dp))
                }

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

        // Full-list picker, opened by a section's "…" chip.
        picker?.let { crit ->
            CriteriaPicker(
                crit = crit,
                selected = selections[crit] ?: emptySet(),
                onToggle = { toggle(crit, it) },
                onClose = { picker = null },
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Muted, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun CritSection(crit: Crit, selected: Set<String>, onToggle: (String) -> Unit, onMore: () -> Unit) {
    val common = crit.all.take(crit.common)
    // Always surface selected items even if they live beyond the common subset.
    val shown = (common + selected.filter { it !in common }).distinct()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GroupLabel(crit.label)
        if (selected.isNotEmpty()) {
            Text("${selected.size} sélectionné${if (selected.size > 1) "s" else ""}", fontSize = 10.5.sp, color = VincentColors.Accent, fontWeight = FontWeight.W600)
        }
    }
    (shown + "…").chunked(3).forEach { row ->
        Row(Modifier.padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            row.forEach { item ->
                if (item == "…") MoreChip(onMore) else Chip(item, item in selected) { onToggle(item) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (on) VincentColors.Fg else VincentColors.Surface)
            .border(1.dp, if (on) VincentColors.Fg else VincentColors.Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
    }
}

@Composable
private fun MoreChip(onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(VincentColors.AccentSoft)
            .border(1.dp, VincentColors.Accent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text("…", fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
    }
}

@Composable
private fun CriteriaPicker(crit: Crit, selected: Set<String>, onToggle: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(crit.label, fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text("${crit.all.size} critères · ${selected.size} sélectionné${if (selected.size > 1) "s" else ""}", fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            crit.all.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) { WideChip(item, item in selected) { onToggle(item) } }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
        ) { Text("Appliquer", fontWeight = FontWeight.W700) }
    }
}

@Composable
private fun WideChip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (on) VincentColors.AccentSoft else VincentColors.Surface)
            .border(if (on) 1.5.dp else 1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = if (on) VincentColors.Accent else VincentColors.Fg)
        if (on) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
    }
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
