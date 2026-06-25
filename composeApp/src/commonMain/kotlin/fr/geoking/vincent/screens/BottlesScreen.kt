package fr.geoking.vincent.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CsvFormat
import fr.geoking.vincent.data.rememberCsvImport
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.BottleThumb
import fr.geoking.vincent.ui.ScreenHeader
import fr.geoking.vincent.ui.Stars

private data class Filter(val label: String, val color: WineColor?, val favOnly: Boolean = false)

private sealed interface BottleImportStatus {
    data class Success(val count: Int, val source: String) : BottleImportStatus
    data object None : BottleImportStatus
    data object WrongType : BottleImportStatus
}

@Composable
fun BottlesScreen(
    modifier: Modifier = Modifier,
    onOpenBottle: (Bottle) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val filterItems = listOf(
        Filter(stringResource(Res.string.bottles_filter_all), null),
        Filter(stringResource(WineColor.RED.label), WineColor.RED),
        Filter(stringResource(WineColor.WHITE.label), WineColor.WHITE),
        Filter(stringResource(WineColor.ROSE.label), WineColor.ROSE),
        Filter(stringResource(Res.string.bottles_filter_favorites), null, favOnly = true),
    )
    val f = filterItems[selected]
    val categoryLabels = WineCategory.entries.associateWith { stringResource(it.label).lowercase() }
    val list = Cellar.bottles.filter {
        (f.color == null || it.color == f.color) && (!f.favOnly || it.favorite)
    }.filter { b ->
        val q = query.trim().lowercase()
        q.isBlank() ||
            b.domain.lowercase().contains(q) ||
            b.appellation.lowercase().contains(q) ||
            categoryLabels[b.category]?.contains(q) == true ||
            b.vintage.lowercase().contains(q)
    }

    var importStatus by remember { mutableStateOf<BottleImportStatus?>(null) }
    val importCsv = rememberCsvImport { text ->
        val result = CsvFormat.parse(text)
        importStatus = if (result.type == CsvFormat.ImportType.BOTTLES) {
            val n = Cellar.importBottles(result.bottles)
            if (n > 0) BottleImportStatus.Success(n, result.source) else BottleImportStatus.None
        } else {
            BottleImportStatus.WrongType
        }
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            stringResource(Res.string.bottles_title),
            pluralStringResource(Res.plurals.bottles_subtitle_format, list.size, Cellar.totalBottles(), list.size),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable { onOpenFavorites() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Favorite, contentDescription = stringResource(Res.string.my_favorites), modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }

                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable { importCsv() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.FileUpload, contentDescription = stringResource(Res.string.import_action), modifier = Modifier.size(18.dp), tint = VincentColors.Accent) }
                }
            },
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            when (val status = importStatus) {
                is BottleImportStatus.Success -> Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) { Text(pluralStringResource(Res.plurals.transfer_import_success, status.count, status.count, "", status.source), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                BottleImportStatus.None -> Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) { Text(stringResource(Res.string.transfer_import_none), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                BottleImportStatus.WrongType -> Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.AccentSoft).padding(13.dp),
                ) { Text(stringResource(Res.string.bottles_import_wrong_type), fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = VincentColors.AccentDeep) }
                null -> Unit
            }
            SearchField(stringResource(Res.string.bottles_search_placeholder), value = query, onValueChange = { query = it })
            Spacer(Modifier.height(11.dp))
            FilterChips(selected, filterItems) { selected = it }
            Spacer(Modifier.height(11.dp))
            list.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    pair.forEach { b -> BottleCard(b, Modifier.weight(1f)) { onOpenBottle(b) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(11.dp))
            }
            if (list.isEmpty()) {
                Text(
                    if (query.isNotBlank()) stringResource(Res.string.bottles_no_result, query)
                    else stringResource(Res.string.bottles_no_result_filter),
                    fontSize = 13.sp, color = VincentColors.Muted, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            Spacer(Modifier.height(70.dp))
        }
    }
}

@Composable
fun SearchField(
    placeholder: String,
    value: String = "",
    onValueChange: (String) -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(9.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = VincentColors.Fg),
            cursorBrush = SolidColor(VincentColors.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = VincentColors.Faint, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun FilterChips(selected: Int, filterItems: List<Filter>, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        filterItems.forEachIndexed { i, filter ->
            val on = i == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) VincentColors.Fg else VincentColors.Surface)
                    .border(1.dp, if (on) VincentColors.Fg else VincentColors.Border, RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (filter.color != null) {
                    Box(Modifier.padding(end = 5.dp).size(8.dp).clip(RoundedCornerShape(50)).background(filter.color.glass))
                }
                Text(filter.label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Muted)
            }
        }
    }
}

@Composable
fun BottleCard(b: Bottle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(lerp(Color.White, b.color.glass, 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            BottleThumb(b, Modifier.size(width = 30.dp, height = 78.dp))
            if (b.favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favori",
                    tint = VincentColors.Accent,
                    modifier = Modifier.align(Alignment.TopStart).size(15.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0D0D11))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("×${b.quantity}", style = MonoNumber, fontSize = 10.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(b.domain, fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        Text("${b.appellation} · ${b.vintage}", fontSize = 10.5.sp, color = VincentColors.Muted)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${b.price} €", style = MonoNumber, color = VincentColors.Fg)
            Stars(b.rating)
        }
    }
}
