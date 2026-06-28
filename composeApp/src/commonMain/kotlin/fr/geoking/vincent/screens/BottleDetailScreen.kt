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
import androidx.compose.material.icons.filled.GridView
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
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.ai.foodPairer
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.bottlePriceCompareLinks
import fr.geoking.vincent.data.rememberLabelImageSaver
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.model.cellIndexFromSpot
import fr.geoking.vincent.model.cellSpotLabel
import fr.geoking.vincent.model.photo
import fr.geoking.vincent.model.thumbnailUri
import kotlinx.coroutines.launch
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.BottlePhotosRow
import fr.geoking.vincent.ui.BottleThumb
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.PlacementRackGrid
import fr.geoking.vincent.ui.Stars
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle

@Composable
fun BottleDetailScreen(bottle: Bottle, onBack: () -> Unit, onEdit: (Bottle) -> Unit) {
    val live = Cellar.bottle(bottle.id) ?: bottle
    val qty = live.quantity
    val fav = live.favorite
    val pairer = foodPairer()
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
                    Icons.Filled.Favorite,
                onClick = { Cellar.toggleFavorite(live.id) },
                tint = if (fav) VincentColors.Accent else VincentColors.Muted,
                    bg = if (fav) VincentColors.AccentSoft else VincentColors.Surface2,
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
                val sub = listOfNotNull(live.appellation.takeIf { it.isNotBlank() }, live.provenance.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                    Stars(live.rating)
                    Spacer(Modifier.width(7.dp))
                    val ratingStr = if (live.rating % 1.0 == 0.0) live.rating.toInt().toString() else live.rating.toString().replace('.', ',')
                    Text(stringResource(Res.string.detail_stars_count, ratingStr), fontSize = 22.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                }
            }
        }

        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(stringResource(Res.string.detail_photos)) {
                BottlePhotosRow(
                    photos = BottlePhotoKind.entries.associateWith { live.photo(it) },
                    onCapture = { kind -> pendingKind = kind; capture() },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Stat(stringResource(Res.string.detail_qty), "×$qty", Modifier.weight(1f))
                Stat(stringResource(Res.string.detail_value), "${live.price * qty} €", Modifier.weight(1f))
                Stat(stringResource(Res.string.detail_spot), live.cellarSpot, Modifier.weight(1f))
            }

            if (compareLinks.isNotEmpty()) {
                Section(stringResource(Res.string.detail_compare_prices)) {
                    Text(
                        stringResource(Res.string.detail_compare_desc),
                        fontSize = 11.sp,
                        color = VincentColors.Muted,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    compareLinks.chunked(3).forEachIndexed { index, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 8.dp else 7.dp),
                        ) {
                            row.forEach { link ->
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(link.url) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(11.dp),
                                ) {
                                    Text(link.label, fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
                                    Spacer(Modifier.width(3.dp))
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Description (provider overview)
            if (live.description.isNotBlank()) {
                Section(stringResource(Res.string.detail_description)) {
                    Text(live.description, fontSize = 12.5.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // Grape varieties
            if (live.grapes.isNotEmpty()) {
                Section(stringResource(Res.string.detail_grapes)) {
                    live.grapes.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                            rowItems.forEach { Pairing(it) }
                        }
                    }
                }
            }

            // Flavour profile (0–10 axes from the provider)
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

            // Pairings — Gemini can suggest more on demand.
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

            // Drink window
            if (live.drinkTo > 0 || live.maturity.isNotBlank()) {
                Section(stringResource(Res.string.detail_drink_peak)) {
                    if (live.drinkTo > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text("${live.drinkFrom}", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
                            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                                        .background(Brush.horizontalGradient(listOf(VincentColors.Amber, VincentColors.Green, VincentColors.Amber))),
                                )
                                Row(Modifier.fillMaxWidth()) {
                                    Spacer(Modifier.weight(live.drinkNow.coerceIn(0.02f, 0.95f)))
                                    Box(Modifier.size(13.dp).clip(RoundedCornerShape(50)).background(Color.White).border(3.dp, VincentColors.Green, RoundedCornerShape(50)))
                                    Spacer(Modifier.weight(1f - live.drinkNow.coerceIn(0.02f, 0.95f)))
                                }
                            }
                            Text("${live.drinkTo}", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
                        }
                    }
                    Text(live.tastingNotes.ifBlank { stringResource(Res.string.detail_no_notes) }, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    if (live.maturity.isNotBlank()) {
                        Text(live.maturity, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            // Provenance & purchase
            Section(stringResource(Res.string.detail_source_purchase)) {
                VCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Column {
                        InfoRow(Icons.Filled.Place, stringResource(Res.string.detail_source_label), live.provenance, divider = true)
                        InfoRow(Icons.Filled.Storefront, stringResource(Res.string.detail_merchant_label), live.merchant, divider = true)
                        InfoRow(Icons.Filled.CalendarMonth, stringResource(Res.string.detail_purchase_date_label), live.purchaseDate, divider = true)
                        InfoRow(Icons.Filled.LocalBar, stringResource(Res.string.detail_occasion_label), live.occasion, divider = false)
                    }
                }
            }

            // Location
            Section(stringResource(Res.string.detail_location_label)) {
                val placement = remember(live.cellarSpot) {
                    var found: Pair<Int, Int>? = null
                    val spot = live.cellarSpot.trim()
                    if (spot.isNotBlank() && spot != "—") {
                        for ((ri, rack) in Racks.all.withIndex()) {
                            val ci = cellIndexFromSpot(spot, rack.cols) ?: continue
                            if (ci in rack.cells.indices && rack.cells[ci].occupied) {
                                // Double check if it's actually the same bottle (best effort)
                                val cell = rack.cells[ci]
                                if (cell.color == live.color && cell.vintage == live.vintage.takeLast(2)) {
                                    found = ri to ci
                                    break
                                }
                            }
                        }
                    }
                    found
                }

                if (placement != null) {
                    val (ri, ci) = placement
                    val rack = Racks.all[ri]
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(VincentColors.AccentSoft).padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.GridView, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${rack.name} · ${cellSpotLabel(ci, rack.cols)}", color = VincentColors.Accent, fontWeight = FontWeight.W700, fontSize = 12.sp)
                        }
                        PlacementRackGrid(
                            rack = rack,
                            currentCell = ci,
                            showHint = false,
                        )
                    }
                } else {
                    Row(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(VincentColors.AccentSoft).padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(live.cellarSpot, color = VincentColors.Accent, fontWeight = FontWeight.W700, fontSize = 12.sp)
                    }
                }
            }

            // CTA
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
private fun IconChip(icon: ImageVector, onClick: () -> Unit, tint: Color = VincentColors.Fg, bg: Color = VincentColors.Surface2, contentDescription: String? = null) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    VCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), fontSize = 9.5.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        content()
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
