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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.getCurrentYear
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.ai.PriceSearchResult
import fr.geoking.vincent.ai.foodPairer
import fr.geoking.vincent.ai.priceSearcher
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.bottlePriceCompareLinks
import fr.geoking.vincent.data.rememberLabelImageSaver
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.model.effectiveDrinkFrom
import fr.geoking.vincent.model.effectiveDrinkNow
import fr.geoking.vincent.model.effectiveDrinkTo
import fr.geoking.vincent.model.hasDrinkWindow
import fr.geoking.vincent.model.photo
import fr.geoking.vincent.model.thumbnailUri
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.BottlePhotosRow
import fr.geoking.vincent.ui.BottleThumb
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.DrinkPeakBar
import fr.geoking.vincent.ui.Stars
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle

@Composable
fun BottleDetailScreen(bottle: Bottle, onBack: () -> Unit, onEdit: (Bottle) -> Unit, onTasting: (Bottle, String?) -> Unit, onMove: (Bottle) -> Unit) {
    val live = Cellar.bottle(bottle.id) ?: bottle
    val qty = live.quantity
    val fav = live.favorite
    val pairer = foodPairer()
    val searcher = priceSearcher()
    val scope = rememberCoroutineScope()
    val labelSaver = rememberLabelImageSaver()
    var pendingKind by remember { mutableStateOf<BottlePhotoKind?>(null) }
    val capture = rememberPhotoCapture { bytes ->
        val kind = pendingKind ?: return@rememberPhotoCapture
        scope.launch {
            val path = labelSaver.save(bytes, live.id, kind)
            Cellar.updatePhoto(live.id, kind, path)
            pendingKind = null
        }
    }
    var suggested by remember { mutableStateOf<List<String>>(emptyList()) }
    var pairingBusy by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PriceSearchResult>>(emptyList()) }

    val uriHandler = LocalUriHandler.current
    val compareLinks = remember(live.domain, live.vintage, live.appellation) { bottlePriceCompareLinks(live) }
    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        // Top actions
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack, contentDescription = stringResource(Res.string.back))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconChip(
                    Icons.Filled.Edit,
                    onClick = { onEdit(live) },
                    contentDescription = stringResource(Res.string.edit_bottle),
                )
                IconChip(
                    if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    onClick = { Cellar.toggleFavorite(live.id) },
                    tint = VincentColors.Accent,
                    bg = if (fav) VincentColors.AccentSoft else VincentColors.Surface2,
                    borderColor = VincentColors.Accent,
                )
            }
        }

        // Hero
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF6EAEA), Color(0xFFEFD9DC))))
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (live.thumbnailUri() != null) {
                BottleThumb(live, Modifier.size(width = 62.dp, height = 150.dp))
            } else {
                WineBottle(live.color, Modifier.size(width = 62.dp, height = 150.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                ColorTag(live.color, label = "${stringResource(live.color.label)} · ${stringResource(live.category.label)}")
                Spacer(Modifier.height(7.dp))
                val title = listOfNotNull(live.domain.takeIf { it.isNotBlank() }, live.vintage.takeIf { it != "NM" }).joinToString(" ")
                Text(title.ifBlank { stringResource(Res.string.add_default_domain) }, fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                val sub = listOfNotNull(
                    live.appellation.takeIf { it.isNotBlank() && it.trim() != live.domain.trim() },
                    live.provenance.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                    Stars(live.rating, onClick = { onTasting(live, null) })
                    if (live.rating > 0.0) {
                        Spacer(Modifier.width(7.dp))
                        val ratingStr = if (live.rating % 1.0 == 0.0) live.rating.toInt().toString() else live.rating.toString().replace('.', ',')
                        Text(stringResource(Res.string.detail_stars_count, ratingStr), fontSize = 22.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                    }
                }
                if (qty > 1 || live.price > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        if (qty > 1) {
                            HeroBadge(stringResource(Res.string.detail_qty), "×$qty")
                        }
                        if (live.price > 0) {
                            HeroBadge(stringResource(Res.string.detail_value), "${live.price * qty} €")
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val photos = BottlePhotoKind.entries.associateWith { live.photo(it) }
            val hasPhotos = photos.values.any { !it.isNullOrBlank() }
            var photosExpanded by remember(live.id) { mutableStateOf(hasPhotos) }
            LaunchedEffect(hasPhotos) {
                if (hasPhotos) photosExpanded = true
            }
            CollapsibleSection(
                title = stringResource(Res.string.detail_photos),
                expanded = photosExpanded,
                onToggle = { photosExpanded = !photosExpanded },
            ) {
                BottlePhotosRow(
                    photos = photos,
                    onCapture = { kind -> pendingKind = kind; capture() },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (live.grapes.isNotEmpty()) {
                Section(stringResource(Res.string.detail_grapes)) {
                    live.grapes.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                            rowItems.forEach { Pairing(it) }
                        }
                    }
                }
            }

            Section(stringResource(Res.string.detail_source_purchase)) {
                VCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Column {
                        if (live.cellarSpot.isNotBlank()) {
                            InfoRow(Icons.Filled.Place, stringResource(Res.string.detail_spot), live.cellarSpot, divider = true)
                        }
                        InfoRow(Icons.Filled.Place, stringResource(Res.string.detail_source_label), live.provenance, divider = true)
                        if (live.alcoholLevel > 0.0) {
                            InfoRow(Icons.Filled.LocalBar, stringResource(Res.string.detail_alcohol_label), "${live.alcoholLevel}%", divider = true)
                        }
                        InfoRow(Icons.Filled.LocalBar, stringResource(Res.string.detail_sugar_label), stringResource(live.sugarLevel.label), divider = true)
                        InfoRow(Icons.Filled.Storefront, stringResource(Res.string.detail_merchant_label), live.merchant, divider = true)
                        InfoRow(Icons.Filled.CalendarMonth, stringResource(Res.string.detail_purchase_date_label), live.purchaseDate, divider = true)
                        InfoRow(Icons.Filled.LocalBar, stringResource(Res.string.detail_occasion_label), live.occasion, divider = false)
                    }
                }
            }

            Section(stringResource(Res.string.detail_compare_prices)) {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                searchResults = emptyList()
                                scope.launch {
                                    searcher.search(live)
                                        .onStart { searching = true }
                                        .onCompletion { searching = false }
                                        .collect { res ->
                                            searchResults = searchResults + res
                                        }
                                }
                            },
                            modifier = Modifier.height(34.dp),
                            enabled = !searching,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VincentColors.Accent),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            if (searching) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = VincentColors.Accent)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.detail_price_auto), fontSize = 11.sp, fontWeight = FontWeight.W700)
                        }

                        compareLinks.forEach { link ->
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(link.url) },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text(link.label, fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                                }
                        }
                    }

                    if (searching || searchResults.isNotEmpty()) {
                        VCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                if (searching) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = VincentColors.Muted)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(Res.string.detail_price_searching), fontSize = 11.sp, color = VincentColors.Muted)
                                    }
                                }
                                searchResults.forEach { res ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(res.label, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                                            if (res.isFound) {
                                                Text(res.url, fontSize = 10.sp, color = VincentColors.Muted, maxLines = 1)
                                            } else {
                                                Text(stringResource(Res.string.detail_price_not_found), fontSize = 10.sp, color = VincentColors.Muted)
                                            }
                                        }
                                        if (res.isFound) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${res.price} €", fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    stringResource(Res.string.detail_price_update),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.W700,
                                                    color = VincentColors.Accent,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(VincentColors.AccentSoft)
                                                        .clickable { Cellar.updateBottle(live.copy(price = res.price)) }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (res != searchResults.last() || searching) {
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(VincentColors.Border).padding(vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val bottleTastings = remember(live.id, Tastings.all.toList()) {
                Tastings.all.filter { it.bottleId == live.id }.sortedByDescending { it.date }
            }
            Section(stringResource(Res.string.detail_tasting)) {
                if (bottleTastings.isEmpty()) {
                    Text(
                        stringResource(Res.string.detail_tasting_empty),
                        fontSize = 12.sp,
                        color = VincentColors.Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        bottleTastings.forEach { tasting ->
                            VCard(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onTasting(live, tasting.id) },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tasting.date, fontSize = 11.sp, color = VincentColors.Muted)
                                            if (tasting.place.isNotBlank()) {
                                                Text(tasting.place, fontSize = 11.sp, color = VincentColors.Muted)
                                            }
                                        }
                                        Stars(tasting.rating)
                                    }
                                    if (tasting.notes.isNotEmpty()) {
                                        Text(tasting.notes, fontSize = 12.sp, color = VincentColors.Fg, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onTasting(live, null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Text(stringResource(Res.string.detail_tasting_add), fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
                }
            }

            if (live.description.isNotBlank()) {
                Section(stringResource(Res.string.detail_description)) {
                    Text(live.description, fontSize = 12.5.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            live.flavorProfile?.let { fp ->
                Section(stringResource(Res.string.detail_flavor_profile)) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlavorBar(stringResource(Res.string.detail_fp_sweetness), fp.sweetness)
                        FlavorBar(stringResource(Res.string.detail_fp_acidity), fp.acidity)
                        FlavorBar(stringResource(Res.string.detail_fp_tannins), fp.tannins)
                        FlavorBar(stringResource(Res.string.detail_fp_alcohol), fp.alcohol)
                        FlavorBar(stringResource(Res.string.detail_fp_body), fp.body)
                        FlavorBar(stringResource(Res.string.detail_fp_finish), fp.finish)
                    }
                }
            }

            Section(stringResource(Res.string.detail_pairings_title)) {
                if (live.pairingNotes.isNotBlank()) {
                    Text(live.pairingNotes, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                }
                val all = (live.pairings + suggested).distinct()
                all.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                        rowItems.forEach { Pairing(it) }
                    }
                }
                OutlinedButton(
                    onClick = {
                        pairingBusy = true
                        scope.launch {
                            val res = pairer.pairings(live)
                            if (res.isNotEmpty()) suggested = res
                            pairingBusy = false
                        }
                    },
                    enabled = !pairingBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(40.dp),
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (pairingBusy) stringResource(Res.string.detail_analyzing) else stringResource(Res.string.detail_suggest_ai), fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
                }
            }

            if (live.hasDrinkWindow() || live.maturity.isNotBlank()) {
                val drinkFrom = live.effectiveDrinkFrom()
                val drinkTo = live.effectiveDrinkTo()
                val drinkNow = live.effectiveDrinkNow(getCurrentYear())
                Section(stringResource(Res.string.detail_drink_peak)) {
                    if (live.hasDrinkWindow()) {
                        DrinkPeakBar(
                            from = drinkFrom,
                            to = drinkTo,
                            now = drinkNow,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(live.tastingNotes.ifBlank { stringResource(Res.string.detail_no_notes) }, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    if (live.maturity.isNotBlank()) {
                        Text(live.maturity, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = { Cellar.adjustQuantity(live.id, -1) }, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.detail_serve_minus)) }
                Button(
                    onClick = { Cellar.adjustQuantity(live.id, +1) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
                ) { Text(stringResource(Res.string.detail_add_plus)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeroBadge(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label.uppercase(), fontSize = 9.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun IconChip(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = VincentColors.Fg,
    bg: Color = VincentColors.Surface2,
    borderColor: Color = VincentColors.Border,
    contentDescription: String? = null,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        content()
    }
}

@Composable
private fun CollapsibleSection(
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

/** A 0–10 aroma/structure axis rendered as a labelled progress bar. */
@Composable
private fun FlavorBar(label: String, value: Int) {
    val v = value.coerceIn(0, 10)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = VincentColors.Muted, modifier = Modifier.width(78.dp))
        Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(VincentColors.Border)) {
            Box(Modifier.fillMaxWidth(v / 10f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(VincentColors.Accent))
        }
        Spacer(Modifier.width(8.dp))
        Text("$v", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
    }
}

@Composable
private fun Pairing(label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocalBar, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, divider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = VincentColors.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(label, fontSize = 12.sp, color = VincentColors.Muted, fontWeight = FontWeight.W500)
            }
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(VincentColors.Border))
    }
}
