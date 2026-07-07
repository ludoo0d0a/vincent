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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.data.WineEnrichment
import fr.geoking.vincent.data.maturityText
import fr.geoking.vincent.data.provenanceText
import fr.geoking.vincent.data.sugarLevel
import fr.geoking.vincent.data.rememberLabelImageSaver
import fr.geoking.vincent.getCurrentYear
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.model.SugarLevel
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.effectiveDrinkFrom
import fr.geoking.vincent.model.effectiveDrinkNow
import fr.geoking.vincent.model.effectiveDrinkTo
import fr.geoking.vincent.model.hasDrinkWindow
import fr.geoking.vincent.model.photo
import fr.geoking.vincent.model.thumbnailUri
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.AlcoholQuickPicker
import fr.geoking.vincent.ui.BottlePhotosRow
import fr.geoking.vincent.ui.BottleThumb
import fr.geoking.vincent.ui.CollapsibleSection
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.DrinkPeakBar
import fr.geoking.vincent.ui.GrapeChipEditor
import fr.geoking.vincent.ui.QuickChipPicker
import fr.geoking.vincent.ui.QuickRegionPicker
import fr.geoking.vincent.ui.Stars
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.VintageQuickPicker
import fr.geoking.vincent.ui.WineBottle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleEditScreen(
    bottle: Bottle,
    onClose: () -> Unit,
    onOpenTastings: (Bottle) -> Unit = {},
) {
    val live = Cellar.bottle(bottle.id) ?: bottle
    val scope = rememberCoroutineScope()
    val labelSaver = rememberLabelImageSaver()
    var photos by remember(live.id) {
        mutableStateOf(BottlePhotoKind.entries.associateWith { live.photo(it) })
    }
    var pendingKind by remember { mutableStateOf<BottlePhotoKind?>(null) }
    val capture = rememberPhotoCapture { bytes ->
        val kind = pendingKind ?: return@rememberPhotoCapture
        scope.launch {
            val path = labelSaver.save(bytes, live.id, kind)
            photos = photos + (kind to path)
            pendingKind = null
        }
    }

    var domain by remember(live.id) { mutableStateOf(live.domain) }
    var appellation by remember(live.id) { mutableStateOf(live.appellation) }
    var color by remember(live.id) { mutableStateOf(live.color) }
    var category by remember(live.id) { mutableStateOf(live.category) }
    var provenance by remember(live.id) { mutableStateOf(live.provenance) }
    var vintage by remember(live.id) { mutableStateOf(if (live.vintage == "NM") "NM" else live.vintage) }
    var price by remember(live.id) { mutableStateOf(if (live.price > 0) live.price.toString() else "") }
    var alcohol by remember(live.id) { mutableStateOf(live.alcoholLevel) }
    var sugar by remember(live.id) { mutableStateOf(live.sugarLevel) }
    var grapes by remember(live.id) { mutableStateOf(live.grapes) }
    var merchant by remember(live.id) { mutableStateOf(live.merchant) }
    var purchaseDate by remember(live.id) { mutableStateOf(live.purchaseDate) }
    var occasion by remember(live.id) { mutableStateOf(live.occasion) }
    var agingPotential by remember(live.id) {
        mutableStateOf(if (live.agingPotential > 0) live.agingPotential.toString() else "")
    }
    var drinkFrom by remember(live.id) {
        mutableStateOf(if (live.drinkFrom > 0) live.drinkFrom.toString() else "")
    }
    var drinkTo by remember(live.id) {
        mutableStateOf(if (live.drinkTo > 0) live.drinkTo.toString() else "")
    }
    var enrichment by remember(live.id) { mutableStateOf<WineEnrichment?>(null) }

    // Picking a cellar/catalogue suggestion fills the identity fields (and photos/enrichment when available).
    fun applySuggestion(s: WineSuggestion) {
        if (s.domain.isNotBlank()) domain = s.domain
        if (s.appellation.isNotBlank()) appellation = s.appellation
        s.color?.let { color = it }
        s.category?.let { category = it }
        if (s.vintage.isNotBlank()) vintage = s.vintage
        s.price?.let { price = it.toString() }
        s.bottle?.let { b ->
            photos = BottlePhotoKind.entries.associateWith { b.photo(it) }
            agingPotential = if (b.agingPotential > 0) b.agingPotential.toString() else ""
            alcohol = b.alcoholLevel
            sugar = b.sugarLevel
        }
        val extId = s.externalId
        if (extId != null) scope.launch { enrichment = WineDataSource.enrich(s.externalSource, extId) }
        else enrichment = null
    }

    val defaultDomain = stringResource(Res.string.add_default_domain)
    val categoryFallback = stringResource(category.label)

    val bottleTastings = remember(live.id, Tastings.all.toList()) {
        Tastings.all.filter { it.bottleId == live.id }
    }

    val draft = remember(
        domain, appellation, color, category, provenance, vintage, price, alcohol, sugar, grapes,
        merchant, purchaseDate, occasion, agingPotential, drinkFrom, drinkTo, photos, enrichment, defaultDomain, categoryFallback,
    ) {
        val vintageYear = vintage.trim().toIntOrNull()
        val enr = enrichment
        val parsedFrom = drinkFrom.filter { it.isDigit() }.toIntOrNull()
        val parsedTo = drinkTo.filter { it.isDigit() }.toIntOrNull()
        val dFrom = parsedFrom
            ?: enr?.drinkFromYears?.let { resolveGrapemindsDrinkYear(it, vintageYear) }
            ?: 0
        val dTo = parsedTo
            ?: enr?.drinkToYears?.let { resolveGrapemindsDrinkYear(it, vintageYear) }
            ?: 0
        live.copy(
            domain = domain.trim().ifBlank { defaultDomain },
            appellation = appellation.trim().ifBlank { categoryFallback },
            color = color,
            category = category,
            provenance = provenance.trim().ifBlank { enr?.provenanceText().orEmpty() },
            vintage = vintage.trim().ifBlank { "NM" },
            price = price.filter { it.isDigit() }.toIntOrNull() ?: 0,
            alcoholLevel = alcohol,
            sugarLevel = enr?.sugarLevel() ?: sugar,
            grapes = grapes.ifEmpty { enr?.grapes.orEmpty() },
            merchant = merchant.trim().ifBlank { "—" },
            purchaseDate = purchaseDate.trim().ifBlank { live.purchaseDate },
            occasion = occasion.trim(),
            agingPotential = agingPotential.filter { it.isDigit() }.toIntOrNull() ?: 0,
            drinkFrom = dFrom,
            drinkTo = dTo,
            tastingNotes = enr?.tastingNotes ?: live.tastingNotes,
            description = enr?.description ?: live.description,
            pairingNotes = enr?.pairingText ?: live.pairingNotes,
            flavorProfile = enr?.flavorProfile ?: live.flavorProfile,
            maturity = enr?.maturityText() ?: live.maturity,
            photoBottle = photos[BottlePhotoKind.BOTTLE],
            photoLabel = photos[BottlePhotoKind.LABEL],
            photoBack = photos[BottlePhotoKind.BACK],
        )
    }

    val hasPhotos = photos.values.any { !it.isNullOrBlank() }
    var photosExpanded by remember(live.id) { mutableStateOf(hasPhotos) }
    var grapesExpanded by remember(live.id) { mutableStateOf(grapes.isNotEmpty()) }
    var peakExpanded by remember(live.id) {
        mutableStateOf(live.drinkTo > 0 || live.drinkFrom > 0 || live.agingPotential > 0 || live.maturity.isNotBlank())
    }
    var sourceExpanded by remember(live.id) { mutableStateOf(true) }
    var priceExpanded by remember(live.id) { mutableStateOf(live.price > 0) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg)
            }
            Text(stringResource(Res.string.edit_bottle), fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Spacer(Modifier.width(38.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            // Hero — same structure as bottle detail
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFF6EAEA), Color(0xFFEFD9DC))))
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (draft.thumbnailUri() != null) {
                    BottleThumb(draft, Modifier.size(width = 62.dp, height = 150.dp))
                } else {
                    WineBottle(
                        color = color,
                        modifier = Modifier.size(width = 62.dp, height = 150.dp),
                        name = domain,
                        appellation = appellation,
                        vintage = vintage,
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Aperçu identique à l'écran « voir bouteille », en lecture seule (lié au draft → mis à jour en direct).
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    ColorTag(color, label = "${stringResource(color.label)} · ${stringResource(category.label)}")
                    Spacer(Modifier.height(7.dp))
                    val title = listOfNotNull(
                        draft.domain.takeIf { it.isNotBlank() },
                        draft.vintage.takeIf { it != "NM" },
                    ).joinToString(" ")
                    Text(
                        title.ifBlank { stringResource(Res.string.add_default_domain) },
                        fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg,
                    )
                    val sub = listOfNotNull(
                        draft.appellation.takeIf { it.isNotBlank() && it.trim() != draft.domain.trim() },
                        draft.provenance.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                        Stars(draft.rating)
                    }
                    if (draft.quantity > 1 || draft.price > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            if (draft.quantity > 1) {
                                HeroBadge(stringResource(Res.string.detail_qty), "×${draft.quantity}")
                            }
                            if (draft.price > 0) {
                                HeroBadge(stringResource(Res.string.detail_value), "${draft.price * draft.quantity} €")
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Identité du vin — pleine largeur, avec autocomplete cave + catalogue.
                AutocompleteField(
                    label = stringResource(Res.string.add_field_domain_name),
                    value = domain,
                    onChange = { domain = it },
                    onPick = ::applySuggestion,
                )
                AutocompleteField(
                    label = stringResource(Res.string.add_field_appellation_label),
                    value = appellation,
                    onChange = { appellation = it },
                    onPick = ::applySuggestion,
                )
                QuickChipPicker(
                    label = stringResource(Res.string.add_field_color),
                    quickOptions = listOf(WineColor.RED, WineColor.WHITE, WineColor.ROSE),
                    allOptions = WineColor.entries,
                    selected = color,
                    labelOf = { stringResource(it.label) },
                    onSelect = { color = it },
                )
                QuickRegionPicker(
                    label = stringResource(Res.string.add_field_category),
                    selectedCategory = category,
                    selectedProvenance = provenance,
                    categoryLabelOf = { stringResource(it.label) },
                    onSelectCategory = { category = it; provenance = "" },
                    onSelectRegion = { region ->
                        provenance = region
                        category = categoryFromRegion(region)
                    },
                )
                VintageQuickPicker(selected = vintage, showLabel = true, onSelect = { vintage = it })

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

                CollapsibleSection(
                    title = stringResource(Res.string.detail_grapes),
                    expanded = grapesExpanded,
                    onToggle = { grapesExpanded = !grapesExpanded },
                ) {
                    Box(Modifier.padding(top = 6.dp)) {
                        GrapeChipEditor(grapes = grapes, onChange = { grapes = it })
                    }
                }

                CollapsibleSection(
                    title = stringResource(Res.string.detail_drink_peak),
                    expanded = peakExpanded,
                    onToggle = { peakExpanded = !peakExpanded },
                ) {
                    VCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EditField(
                                    stringResource(Res.string.detail_drink_from),
                                    drinkFrom,
                                    numeric = true,
                                    modifier = Modifier.weight(1f),
                                ) { drinkFrom = it.filter { c -> c.isDigit() } }
                                EditField(
                                    stringResource(Res.string.detail_drink_to),
                                    drinkTo,
                                    numeric = true,
                                    modifier = Modifier.weight(1f),
                                ) { drinkTo = it.filter { c -> c.isDigit() } }
                            }
                            EditField(
                                stringResource(Res.string.add_field_aging_potential),
                                agingPotential,
                                numeric = true,
                            ) { agingPotential = it }
                            if (draft.hasDrinkWindow()) {
                                DrinkPeakBar(
                                    from = draft.effectiveDrinkFrom(),
                                    to = draft.effectiveDrinkTo(),
                                    now = draft.effectiveDrinkNow(getCurrentYear()),
                                )
                            }
                            if (draft.maturity.isNotBlank()) {
                                Text(draft.maturity, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp)
                            }
                        }
                    }
                }

                CollapsibleSection(
                    title = stringResource(Res.string.detail_source_purchase),
                    expanded = sourceExpanded,
                    onToggle = { sourceExpanded = !sourceExpanded },
                ) {
                    VCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (live.cellarSpot.isNotBlank()) {
                                ReadOnlyInfo(Icons.Filled.Place, stringResource(Res.string.detail_spot), live.cellarSpot)
                            }
                            EditField(stringResource(Res.string.detail_source_label), provenance) { provenance = it }
                            AlcoholQuickPicker(
                                label = stringResource(Res.string.detail_alcohol_label),
                                selected = alcohol,
                                onSelect = { alcohol = it },
                            )
                            SugarChipRow(sugar) { sugar = it }
                            EditField(stringResource(Res.string.detail_merchant_label), merchant) { merchant = it }
                            DateField(stringResource(Res.string.detail_purchase_date_label), purchaseDate) { showDatePicker = true }
                            EditField(stringResource(Res.string.detail_occasion_label), occasion) { occasion = it }
                        }
                    }
                }

                CollapsibleSection(
                    title = stringResource(Res.string.detail_price),
                    expanded = priceExpanded,
                    onToggle = { priceExpanded = !priceExpanded },
                ) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.add_field_price_label), fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }

                Text(stringResource(Res.string.detail_tasting), fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                VCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTastings(draft) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.LocalBar, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.detail_tasting_open), fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                            Text(
                                if (bottleTastings.isEmpty()) stringResource(Res.string.detail_tasting_empty)
                                else pluralStringResource(Res.plurals.tastings_subtitle, bottleTastings.size, bottleTastings.size),
                                fontSize = 11.sp, color = VincentColors.Muted,
                            )
                        }
                        Text("›", fontSize = 18.sp, color = VincentColors.Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Button(
            onClick = {
                Cellar.updateBottle(draft)
                onClose()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
        ) {
            Text(stringResource(Res.string.save), fontWeight = FontWeight.W700)
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { purchaseDate = epochMillisToDateLabel(it) }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.back)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    numeric: Boolean = false,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun DateField(label: String, value: String, onClick: () -> Unit) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = VincentColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole read-only field opens the picker.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick))
    }
}

/** Convert an epoch-millis instant (UTC) to a dd/MM/yyyy label without extra date deps. */
private fun epochMillisToDateLabel(millis: Long): String {
    val z = millis.floorDiv(86_400_000L) + 719468L
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    fun pad(n: Long) = n.toString().padStart(2, '0')
    return "${pad(d)}/${pad(m)}/$year"
}

@Composable
private fun ReadOnlyInfo(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = VincentColors.Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 12.sp, color = VincentColors.Muted, fontWeight = FontWeight.W500)
        }
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
    }
}

@Composable
private fun SugarChipRow(selected: SugarLevel, onSelect: (SugarLevel) -> Unit) {
    Column {
        Text(
            stringResource(Res.string.add_field_sugar),
            fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SugarLevel.entries.forEach { level ->
                val on = level == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) VincentColors.Accent else VincentColors.Surface2)
                        .border(1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(10.dp))
                        .clickable { onSelect(level) }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                ) {
                    Text(
                        stringResource(level.label),
                        fontSize = 12.sp, fontWeight = FontWeight.W600,
                        color = if (on) Color.White else VincentColors.Fg,
                    )
                }
            }
        }
    }
}

private fun categoryFromRegion(region: String): WineCategory {
    val v = region.lowercase()
    return when {
        "bourgogne" in v || "burgundy" in v -> WineCategory.BOURGOGNE
        "rhône" in v || "rhone" in v -> WineCategory.RHONE
        "provence" in v -> WineCategory.PROVENCE
        "loire" in v -> WineCategory.LOIRE
        "champagne" in v -> WineCategory.CHAMPAGNE
        else -> WineCategory.BORDEAUX
    }
}

private fun resolveGrapemindsDrinkYear(raw: Int, vintageYear: Int?): Int {
    if (raw <= 0) return 0
    if (raw >= 1900) return raw
    return vintageYear?.plus(raw) ?: 0
}
