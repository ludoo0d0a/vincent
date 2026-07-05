package fr.geoking.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.getCurrentYear
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.VincentColors
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.add_field_vintage
import vincent.composeapp.generated.resources.add_field_vintage_label
import vincent.composeapp.generated.resources.edit_region_search

/** French regions shown as quick chips; the rest appear behind "…". */
val PopularWineCategories = listOf(
    WineCategory.BORDEAUX,
    WineCategory.BOURGOGNE,
    WineCategory.RHONE,
    WineCategory.CHAMPAGNE,
)

/** Extra regions (France + abroad) for the searchable full list. */
val ExtraWineRegions = listOf(
    "Provence", "Loire", "Languedoc", "Alsace", "Beaujolais", "Sud-Ouest", "Jura", "Savoie", "Corse",
    "Italie", "Toscane", "Piémont", "Vénétie", "Espagne", "Rioja", "Portugal", "Allemagne", "Autriche",
    "États-Unis", "Californie", "Australie", "Chili", "Argentine", "Afrique du Sud", "Nouvelle-Zélande",
    "Grèce", "Hongrie", "Liban", "Maroc", "Géorgie",
)

val PopularWineColors = listOf(WineColor.RED, WineColor.WHITE, WineColor.ROSE)

val CommonAlcoholLevels = listOf(11.0, 12.0, 12.5, 13.0, 13.5, 14.0)

val AllAlcoholLevels: List<Double> = buildList {
    var v = 9.0
    while (v <= 16.0) {
        add(v)
        v += 0.5
    }
}

val PopularGrapes = listOf(
    "Cabernet Sauvignon", "Merlot", "Pinot Noir", "Syrah", "Grenache", "Chardonnay",
    "Sauvignon Blanc", "Chenin", "Riesling", "Gamay", "Viognier",
)

@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Text(if (expanded) "▾" else "▸", fontSize = 12.sp, color = VincentColors.Muted)
        }
        if (expanded) content()
    }
}

@Composable
fun <T> QuickChipPicker(
    label: String,
    quickOptions: List<T>,
    allOptions: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    equals: (T, T) -> Boolean = { a, b -> a == b },
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(!quickOptions.any { equals(it, selected) }) }

    Column {
        Text(
            label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickOptions.forEach { opt ->
                Chip(
                    text = labelOf(opt),
                    selected = equals(opt, selected) && !expanded,
                    onClick = { expanded = false; onSelect(opt) },
                )
            }
            Chip(text = "…", selected = expanded, onClick = { expanded = !expanded })
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            SearchableOptionList(
                options = allOptions,
                selected = selected,
                labelOf = labelOf,
                equals = equals,
                onSelect = { onSelect(it); expanded = false },
            )
        }
    }
}

@Composable
fun QuickRegionPicker(
    label: String,
    selectedCategory: WineCategory,
    selectedProvenance: String,
    categoryLabelOf: @Composable (WineCategory) -> String,
    onSelectCategory: (WineCategory) -> Unit,
    onSelectRegion: (String) -> Unit,
) {
    val categoryByLabel = WineCategory.entries.associateBy { categoryLabelOf(it) }
    val categoryLabels = WineCategory.entries.map { categoryLabelOf(it) }
    val allLabels = (categoryLabels + ExtraWineRegions).distinct().sorted()

    val catLabel = categoryLabelOf(selectedCategory)
    val selectedLabel = if (selectedProvenance.isNotBlank() && selectedProvenance != catLabel) {
        selectedProvenance
    } else {
        catLabel
    }

    var expanded by remember(selectedCategory, selectedProvenance) {
        mutableStateOf(!PopularWineCategories.contains(selectedCategory) || selectedProvenance.isNotBlank())
    }
    var query by remember { mutableStateOf("") }

    Column {
        Text(
            label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PopularWineCategories.forEach { cat ->
                val text = categoryLabelOf(cat)
                Chip(
                    text = text,
                    selected = categoryLabelOf(selectedCategory) == text && selectedProvenance.isBlank() && !expanded,
                    onClick = {
                        expanded = false
                        onSelectCategory(cat)
                    },
                )
            }
            Chip(text = "…", selected = expanded, onClick = { expanded = !expanded })
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.edit_region_search), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            val filtered = allLabels.filter { it.contains(query, ignoreCase = true) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VincentColors.Surface)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState()),
            ) {
                filtered.forEach { region ->
                    val on = region == selectedLabel
                    Text(
                        region,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.W700 else FontWeight.W500,
                        color = if (on) VincentColors.Accent else VincentColors.Fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cat = categoryByLabel[region]
                                if (cat != null) onSelectCategory(cat) else onSelectRegion(region)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SearchableOptionList(
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    equals: (T, T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    val filtered = options.filter { labelOf(it).contains(query, ignoreCase = true) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        filtered.forEach { opt ->
            val on = equals(opt, selected)
            Text(
                labelOf(opt),
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.W700 else FontWeight.W500,
                color = if (on) VincentColors.Accent else VincentColors.Fg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun VintageQuickPicker(
    selected: String,
    showLabel: Boolean = true,
    onSelect: (String) -> Unit,
) {
    val quickYears = remember {
        val y = getCurrentYear()
        (y downTo y - 2).map { it.toString() }
    }
    var expanded by remember(selected) { mutableStateOf(selected.isNotBlank() && selected !in quickYears && selected != "NM") }
    var pickerYear by remember(selected) {
        mutableIntStateOf(selected.toIntOrNull() ?: getCurrentYear())
    }

    Column {
        if (showLabel) {
            Text(
                stringResource(Res.string.add_field_vintage_label),
                fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickYears.forEach { year ->
                Chip(text = year, selected = year == selected && !expanded, onClick = { expanded = false; onSelect(year) })
            }
            Chip(text = "NM", selected = selected == "NM" && !expanded, onClick = { expanded = false; onSelect("NM") })
            Chip(text = "…", selected = expanded, onClick = { expanded = !expanded })
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StepButton(Icons.Filled.Remove) {
                    pickerYear--
                    onSelect(pickerYear.toString())
                }
                Text(
                    pickerYear.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W800,
                    color = VincentColors.Fg,
                    modifier = Modifier.weight(1f),
                )
                StepButton(Icons.Filled.Add) {
                    pickerYear++
                    onSelect(pickerYear.toString())
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VincentColors.Surface)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState()),
            ) {
                val y = getCurrentYear()
                ((y + 1) downTo 1950).forEach { year ->
                    val s = year.toString()
                    ChipListRow(s, s == selected) { pickerYear = year; onSelect(s) }
                }
            }
            OutlinedTextField(
                value = if (selected !in quickYears && selected != "NM") selected else "",
                onValueChange = onSelect,
                singleLine = true,
                label = { Text(stringResource(Res.string.add_field_vintage), fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun AlcoholQuickPicker(
    label: String,
    selected: Double,
    onSelect: (Double) -> Unit,
) {
    val selectedLabel = if (selected > 0.0) formatAlcohol(selected) else ""
    var expanded by remember { mutableStateOf(selected > 0.0 && selected !in CommonAlcoholLevels) }

    Column {
        Text(
            label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommonAlcoholLevels.forEach { level ->
                val text = formatAlcohol(level)
                Chip(
                    text = text,
                    selected = selected == level && !expanded,
                    onClick = { expanded = false; onSelect(level) },
                )
            }
            Chip(text = "…", selected = expanded, onClick = { expanded = !expanded })
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            SearchableOptionList(
                options = AllAlcoholLevels,
                selected = if (selected > 0.0) selected else CommonAlcoholLevels.first(),
                labelOf = { formatAlcohol(it) },
                equals = { a, b -> a == b },
                onSelect = { onSelect(it); expanded = false },
            )
        } else if (selectedLabel.isNotBlank() && selected !in CommonAlcoholLevels) {
            Spacer(Modifier.height(4.dp))
            Text(selectedLabel, fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
fun GrapeChipEditor(
    grapes: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PopularGrapes.filter { it !in grapes }.take(6).forEach { g ->
                Chip(text = g, selected = false, onClick = { onChange(grapes + g) })
            }
        }
        grapes.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { g ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(VincentColors.Surface)
                            .border(1.dp, VincentColors.Border, RoundedCornerShape(9.dp))
                            .clickable { onChange(grapes - g) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(g, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                        Spacer(Modifier.width(6.dp))
                        Text("×", fontSize = 11.sp, color = VincentColors.Muted)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Cépage…", fontSize = 12.sp) },
            )
            Spacer(Modifier.width(8.dp))
            Chip(text = "+", selected = false, onClick = {
                val g = draft.trim()
                if (g.isNotBlank() && g !in grapes) onChange(grapes + g)
                draft = ""
            })
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VincentColors.Accent else VincentColors.Surface2)
            .border(1.dp, if (selected) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.W600,
            color = if (selected) Color.White else VincentColors.Fg,
        )
    }
}

@Composable
private fun ChipListRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        color = if (selected) VincentColors.Accent else VincentColors.Fg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = VincentColors.Fg, modifier = Modifier.size(18.dp))
    }
}

private fun formatAlcohol(v: Double): String {
    val s = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
    return "$s %"
}
