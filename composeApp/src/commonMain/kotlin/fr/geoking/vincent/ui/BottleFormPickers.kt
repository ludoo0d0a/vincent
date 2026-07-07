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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import vincent.composeapp.generated.resources.add_voice_field_done
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
    val isCustom = !quickOptions.any { equals(it, selected) }
    var showSheet by remember { mutableStateOf(false) }

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
                    selected = equals(opt, selected),
                    onClick = { onSelect(opt) },
                )
            }
            Chip(
                text = if (isCustom) labelOf(selected) else "…",
                selected = isCustom,
                onClick = { showSheet = true },
            )
        }
    }

    if (showSheet) {
        OptionPickerSheet(
            title = label,
            options = allOptions,
            selected = selected,
            labelOf = labelOf,
            equals = equals,
            onSelect = { onSelect(it); showSheet = false },
            onDismiss = { showSheet = false },
        )
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

    val isCustom = !PopularWineCategories.contains(selectedCategory) || selectedProvenance.isNotBlank()
    var showSheet by remember { mutableStateOf(false) }

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
                    selected = categoryLabelOf(selectedCategory) == text && selectedProvenance.isBlank(),
                    onClick = { onSelectCategory(cat) },
                )
            }
            Chip(
                text = if (isCustom) selectedLabel else "…",
                selected = isCustom,
                onClick = { showSheet = true },
            )
        }
    }

    if (showSheet) {
        PickerBottomSheet(title = label, onDismiss = { showSheet = false }) {
            var query by remember { mutableStateOf("") }
            SheetSearchField(query, onChange = { query = it })
            Spacer(Modifier.height(10.dp))
            val filtered = allLabels.filter { it.contains(query, ignoreCase = true) }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            ) {
                filtered.forEach { region ->
                    SheetOptionRow(text = region, selected = region == selectedLabel) {
                        val cat = categoryByLabel[region]
                        if (cat != null) onSelectCategory(cat) else onSelectRegion(region)
                        showSheet = false
                    }
                }
            }
        }
    }
}

/**
 * Material 3 modal bottom sheet used as the "advanced" step behind the "…" chips.
 * Compact, rounded, and themed with the Vincent palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VincentColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VincentColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Searchable option list rendered inside a [PickerBottomSheet]. */
@Composable
private fun <T> OptionPickerSheet(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    equals: (T, T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerBottomSheet(title = title, onDismiss = onDismiss) {
        var query by remember { mutableStateOf("") }
        SheetSearchField(query, onChange = { query = it })
        Spacer(Modifier.height(10.dp))
        val filtered = options.filter { labelOf(it).contains(query, ignoreCase = true) }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        ) {
            filtered.forEach { opt ->
                SheetOptionRow(text = labelOf(opt), selected = equals(opt, selected)) { onSelect(opt) }
            }
        }
    }
}

@Composable
private fun SheetSearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = VincentColors.Muted, modifier = Modifier.size(18.dp)) },
        placeholder = { Text(stringResource(Res.string.edit_region_search), fontSize = 13.sp) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A single, compact, tappable option row with a trailing check for the current value. */
@Composable
private fun SheetOptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) VincentColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) VincentColors.Accent else VincentColors.Fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(18.dp))
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
    val isCustom = selected.isNotBlank() && selected !in quickYears && selected != "NM"
    var showSheet by remember { mutableStateOf(false) }

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
                Chip(text = year, selected = year == selected, onClick = { onSelect(year) })
            }
            Chip(text = "NM", selected = selected == "NM", onClick = { onSelect("NM") })
            Chip(
                text = if (isCustom) selected else "…",
                selected = isCustom,
                onClick = { showSheet = true },
            )
        }
    }

    if (showSheet) {
        VintagePickerSheet(
            selected = selected,
            onSelect = onSelect,
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun VintagePickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickerYear by remember { mutableIntStateOf(selected.toIntOrNull() ?: getCurrentYear()) }
    var custom by remember { mutableStateOf(if (selected.toIntOrNull() != null) selected else "") }

    PickerBottomSheet(title = stringResource(Res.string.add_field_vintage_label), onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton(Icons.Filled.Remove) { pickerYear--; custom = pickerYear.toString() }
            Text(
                pickerYear.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.W800,
                color = VincentColors.Fg,
                modifier = Modifier.weight(1f),
            )
            StepButton(Icons.Filled.Add) { pickerYear++; custom = pickerYear.toString() }
            Chip(text = stringResource(Res.string.add_voice_field_done), selected = true) {
                onSelect(pickerYear.toString()); onDismiss()
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
        ) {
            val y = getCurrentYear()
            ((y + 1) downTo 1950).forEach { year ->
                val s = year.toString()
                SheetOptionRow(text = s, selected = s == selected) {
                    pickerYear = year; onSelect(s); onDismiss()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it; onSelect(it) },
            singleLine = true,
            label = { Text(stringResource(Res.string.add_field_vintage), fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AlcoholQuickPicker(
    label: String,
    selected: Double,
    onSelect: (Double) -> Unit,
) {
    val isCustom = selected > 0.0 && selected !in CommonAlcoholLevels
    var showSheet by remember { mutableStateOf(false) }

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
                    selected = selected == level,
                    onClick = { onSelect(level) },
                )
            }
            Chip(
                text = if (isCustom) formatAlcohol(selected) else "…",
                selected = isCustom,
                onClick = { showSheet = true },
            )
        }
    }

    if (showSheet) {
        OptionPickerSheet(
            title = label,
            options = AllAlcoholLevels,
            selected = if (selected > 0.0) selected else CommonAlcoholLevels.first(),
            labelOf = { formatAlcohol(it) },
            equals = { a, b -> a == b },
            onSelect = { onSelect(it); showSheet = false },
            onDismiss = { showSheet = false },
        )
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
