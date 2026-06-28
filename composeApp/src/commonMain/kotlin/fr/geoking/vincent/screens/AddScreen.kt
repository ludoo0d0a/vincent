package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import fr.geoking.vincent.ai.AiUsage
import fr.geoking.vincent.ai.PriceEstimate
import fr.geoking.vincent.ai.priceEstimator
import fr.geoking.vincent.ai.rememberBarcodeScanner
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.ai.wineRecognizer
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.model.photo
import fr.geoking.vincent.model.thumbnailUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.barcodeLookup
import fr.geoking.vincent.data.rememberLabelImageSaver
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackPlacement
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.cellIndexFromSpot
import fr.geoking.vincent.model.cellSpotLabel
import fr.geoking.vincent.model.rowLabel
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.BottlePhotosRow
import fr.geoking.vincent.ui.BottleThumb
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.SpeechTextInput
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle

// One "Identifier" screen handles BOTH barcode and label; plus voice and manual.
private enum class AddMode(val label: org.jetbrains.compose.resources.StringResource) {
    IDENTIFY(Res.string.add_mode_identify),
    VOICE(Res.string.add_mode_voice),
    MANUAL(Res.string.add_mode_manual)
}

private sealed interface ScanMessage {
    data object OffSuccessPhoto : ScanMessage
    data object OffSuccess : ScanMessage
    data class NotFound(val code: String) : ScanMessage
}

@Composable
fun AddScreen(onClose: () -> Unit, initialPlacement: RackPlacement? = null) {
    // Opened from an empty rack cell → start on the manual form with the spot pre-filled.
    var mode by remember { mutableStateOf(if (initialPlacement != null) AddMode.MANUAL else AddMode.IDENTIFY) }
    val recognizer = wineRecognizer()
    val estimator = priceEstimator()
    val scope = rememberCoroutineScope()
    var aiBottle by remember { mutableStateOf<Bottle?>(null) }
    var aiPrice by remember { mutableStateOf<PriceEstimate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var manualBottle by remember { mutableStateOf<Bottle?>(null) }
    // Optional placement chosen in the manual wizard: (rackIndex, cellIndex).
    var manualPlacement by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var manualSeed by remember {
        mutableStateOf(initialPlacement?.let { p ->
            val rack = Racks.all.getOrNull(p.rackIndex)
            ManualSeed(
                spot = rack?.let { p.spotLabel(it.cols) }.orEmpty(),
                placeRack = p.rackIndex,
                placeCell = p.cellIndex,
            )
        })
    }
    var scanMsg by remember { mutableStateOf<ScanMessage?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    // Barcode → Open Food Facts lookup → prefill the manual form (vintage/price stay
    // for the user to complete, since EANs rarely encode them).
    val lookup = remember { barcodeLookup() }
    val labelSaver = rememberLabelImageSaver()
    var capturedLabelUri by remember { mutableStateOf<String?>(null) }
    val scanBarcode = rememberBarcodeScanner { code ->
        if (code != null) {
            busy = true
            scope.launch {
                val info = lookup.byBarcode(code)
                busy = false
                manualSeed = if (info != null) {
                    scanMsg = if (info.imageUrl != null) ScanMessage.OffSuccessPhoto else ScanMessage.OffSuccess
                    ManualSeed(
                        domain = info.brand.ifBlank { info.name },
                        appellation = if (info.brand.isNotBlank()) info.name else "",
                        imageUrl = info.imageUrl,
                    )
                } else {
                    scanMsg = ScanMessage.NotFound(code)
                    ManualSeed()
                }
                mode = AddMode.MANUAL
            }
        }
    }
    // Label capture: snap the label/bottle with the system camera (full-res) and let
    // the vision model read it → Gemini fromImage. No hardcoded recognition result.
    val startCapture = rememberPhotoCapture { bytes ->
        busy = true
        aiError = null
        scope.launch {
            val bottleId = "new-${Cellar.references()}-${System.currentTimeMillis()}"
            val imagePath = labelSaver.save(bytes, bottleId, BottlePhotoKind.LABEL)
            capturedLabelUri = imagePath
            val outcome = recognizer.fromImage(bytes)
            aiBottle = outcome.bottle?.copy(
                id = bottleId,
                photoLabel = imagePath,
                source = AddSource.SCAN,
            )
            aiPrice = aiBottle?.let { estimator.estimate(it) }
            aiError = outcome.error
            busy = false
        }
    }
    val identify: () -> Unit = { startCapture() }
    var transcript by remember { mutableStateOf("") }
    val onVoiceDictationEnd: (String) -> Unit = { text ->
        busy = true
        aiError = null
        scope.launch {
            val outcome = recognizer.fromText(text)
            val b = outcome.bottle
            aiBottle = b
            aiError = outcome.error
            busy = false
            if (b != null) {
                val est = estimator.estimate(b)
                aiPrice = est
                manualSeed = ManualSeed(
                    domain = b.domain,
                    appellation = b.appellation,
                    color = b.color,
                    category = b.category,
                    vintage = if (b.vintage == "NM") "" else b.vintage,
                    price = (est?.amountEur ?: b.price.takeIf { it > 0 })?.toString() ?: "",
                )
                mode = AddMode.MANUAL
            }
        }
    }
    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Text(stringResource(Res.string.add_bottle), fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Spacer(Modifier.width(38.dp))
        }

        // mode selector
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AddMode.entries.forEach { m ->
                val on = m == mode
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) VincentColors.Surface else Color.Transparent)
                        .clickable {
                            mode = m
                            if (m != AddMode.IDENTIFY && m != AddMode.VOICE) aiError = null
                        }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(m.label), fontSize = 12.sp, fontWeight = FontWeight.W700, color = if (on) VincentColors.Accent else VincentColors.Muted) }
            }
        }

        scanMsg?.let { msg ->
            Text(
                when (msg) {
                    ScanMessage.OffSuccessPhoto -> stringResource(Res.string.add_scan_off_success_photo)
                    ScanMessage.OffSuccess -> stringResource(Res.string.add_scan_off_success)
                    is ScanMessage.NotFound -> stringResource(Res.string.add_scan_barcode_not_found, msg.code)
                },
                fontSize = 11.5.sp, color = VincentColors.Muted, lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Remaining daily AI allowance, reported by the proxy after each call.
        AiUsage.quota?.let { q ->
            Text(
                stringResource(Res.string.ai_quota_remaining, q.remaining, q.limit),
                fontSize = 11.sp,
                fontWeight = FontWeight.W600,
                color = if (q.remaining == 0) VincentColors.Red else VincentColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            when (mode) {
                AddMode.IDENTIFY -> ScanPane(
                    color = aiBottle?.color ?: WineColor.RED,
                    bottle = aiBottle,
                    title = aiBottle?.let { "${it.domain} ${it.vintage}" }.orEmpty(),
                    subtitle = aiBottle?.let { "${it.appellation} · ${stringResource(it.color.label)}" }.orEmpty(),
                    priceLabel = aiPrice?.let { "≈ ${it.amountEur} € · ${it.source}" },
                    busy = busy,
                    hasResult = aiBottle != null,
                    errorMsg = aiError,
                    onIdentify = identify,
                    onScanBarcode = scanBarcode,
                )
                AddMode.VOICE -> VoicePane(
                    transcript = transcript,
                    onTranscriptChange = { transcript = it },
                    onDictationEnd = onVoiceDictationEnd,
                    parsed = aiBottle,
                    priceLabel = aiPrice?.let { "≈ ${it.amountEur} € · ${it.source}" },
                    errorMsg = aiError,
                    busy = busy,
                )
                AddMode.MANUAL -> ManualPane(
                    seed = manualSeed,
                    onBottle = { b, place -> manualBottle = b; manualPlacement = place },
                )
            }
        }

        // Only enabled once we have a real bottle: AI-recognised (scan/photo/voice)
        // or a manually filled form. No more hardcoded fallback.
        val ready: Bottle? = if (mode == AddMode.MANUAL) manualBottle
            else aiBottle?.let { it.copy(price = aiPrice?.amountEur ?: it.price, photoLabel = capturedLabelUri ?: it.photoLabel) }
        val buttonLabel = when {
            ready != null -> if (mode == AddMode.MANUAL) stringResource(Res.string.add_to_cellar) else stringResource(Res.string.add_confirm)
            busy -> stringResource(Res.string.add_analyzing)
            mode == AddMode.IDENTIFY -> stringResource(Res.string.add_barcode_required)
            mode == AddMode.VOICE -> stringResource(Res.string.add_dictate_required)
            else -> stringResource(Res.string.add_confirm)
        }
        Button(
            onClick = {
                ready?.let { b ->
                    Cellar.addBottle(b)
                    // Place into the chosen empty rack cell, if any.
                    if (mode == AddMode.MANUAL) manualPlacement?.let { (ri, ci) ->
                        Racks.all.getOrNull(ri)?.let { r ->
                            Racks.update(ri, r.replaceCell(ci, RackCell(rowLabel(ci / r.cols), true, b.color, b.category, b.vintage, b.price)))
                        }
                    }
                    onClose()
                }
            },
            enabled = ready != null && !busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VincentColors.Accent,
                contentColor = Color.White,
                disabledContainerColor = VincentColors.Surface2,
                disabledContentColor = VincentColors.Faint,
            ),
        ) {
            Text(buttonLabel, fontWeight = FontWeight.W700)
        }
    }
}

/** Values pushed into the manual form, e.g. from a barcode lookup or cellar suggestion. */
private data class ManualSeed(
    val domain: String = "",
    val appellation: String = "",
    val color: WineColor = WineColor.RED,
    val category: WineCategory = WineCategory.BORDEAUX,
    val vintage: String = "",
    val price: String = "",
    val spot: String = "",
    val placeRack: Int? = null,
    val placeCell: Int? = null,
    val imageUrl: String? = null,
)

/** Manual entry — search cellar + form; emits a Bottle (or null while the name is empty). */
@Composable
private fun ManualPane(seed: ManualSeed?, onBottle: (Bottle?, Pair<Int, Int>?) -> Unit) {
    var domain by remember { mutableStateOf("") }
    var appellation by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(WineColor.RED) }
    var category by remember { mutableStateOf(WineCategory.BORDEAUX) }
    var vintage by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var spot by remember(seed) { mutableStateOf(seed?.spot.orEmpty()) }
    var placeRack by remember(seed) { mutableStateOf(seed?.placeRack) }
    var placeCell by remember(seed) { mutableStateOf(seed?.placeCell) }
    var photos by remember { mutableStateOf<Map<BottlePhotoKind, String?>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    val draftId = remember { "new-${Cellar.references()}-${System.currentTimeMillis()}" }
    val labelSaver = rememberLabelImageSaver()
    val scope = rememberCoroutineScope()
    var pendingKind by remember { mutableStateOf<BottlePhotoKind?>(null) }
    val capture = rememberPhotoCapture { bytes ->
        val kind = pendingKind ?: return@rememberPhotoCapture
        scope.launch {
            val path = labelSaver.save(bytes, draftId, kind)
            photos = photos + (kind to path)
            pendingKind = null
        }
    }

    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    val cellarSize = Cellar.bottles.size
    val suggestions = remember(debouncedQuery, cellarSize) { Cellar.search(debouncedQuery) }

    fun applyFromCellar(b: Bottle) {
        domain = b.domain
        appellation = b.appellation
        color = b.color
        category = b.category
        vintage = if (b.vintage == "NM") "" else b.vintage
        price = if (b.price > 0) b.price.toString() else ""
        photos = BottlePhotoKind.entries.associateWith { b.photo(it) }
        searchQuery = ""
        debouncedQuery = ""
    }

    LaunchedEffect(seed) {
        seed?.let {
            domain = it.domain; appellation = it.appellation; color = it.color; category = it.category
            vintage = it.vintage; price = it.price
            if (it.imageUrl != null) photos = photos + (BottlePhotoKind.LABEL to it.imageUrl)
            if (it.spot.isNotBlank()) spot = it.spot
            if (it.placeRack != null && it.placeCell != null) {
                placeRack = it.placeRack
                placeCell = it.placeCell
            }
        }
    }

    val defaultDomain = stringResource(Res.string.add_default_domain)
    val todayLabel = stringResource(Res.string.add_today)
    val justNowLabel = stringResource(Res.string.add_just_now)
    val categoryFallback = stringResource(category.label)

    // When the spot label matches an occupied cell, treat it as the bottle's current rack position.
    val currentPlacement = remember(spot, placeRack, placeCell) {
        val fromSelection = placeRack?.let { r -> placeCell?.let { c -> r to c } }
        if (fromSelection != null) {
            val rack = Racks.all.getOrNull(fromSelection.first)
            if (rack != null && rack.cells.getOrNull(fromSelection.second)?.occupied == true) fromSelection else null
        } else {
            val label = spot.trim()
            if (label.isBlank()) null
            else {
                var found: Pair<Int, Int>? = null
                for ((ri, rack) in Racks.all.withIndex()) {
                    val ci = cellIndexFromSpot(label, rack.cols) ?: continue
                    if (ci in rack.cells.indices && rack.cells[ci].occupied) {
                        found = ri to ci
                        break
                    }
                }
                found
            }
        }
    }

    LaunchedEffect(domain, appellation, color, category, vintage, price, qty, spot, placeRack, placeCell, photos, defaultDomain, todayLabel, justNowLabel, categoryFallback) {
        val placement = placeRack?.let { r -> placeCell?.let { c -> r to c } }
        onBottle(
            Bottle(
                id = draftId,
                domain = domain.trim().ifBlank { defaultDomain },
                appellation = appellation.trim().ifBlank { categoryFallback },
                color = color,
                category = category,
                vintage = vintage.trim().ifBlank { "NM" },
                price = price.filter { it.isDigit() }.toIntOrNull() ?: 0,
                quantity = qty.filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                rating = 0.0,
                cellarSpot = spot.trim().uppercase().ifBlank { "—" },
                provenance = "",
                merchant = "—",
                purchaseDate = todayLabel,
                occasion = "",
                source = AddSource.MANUAL,
                addedLabel = justNowLabel,
                photoBottle = photos[BottlePhotoKind.BOTTLE],
                photoLabel = photos[BottlePhotoKind.LABEL],
                photoBack = photos[BottlePhotoKind.BACK],
            ),
            placement,
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(Res.string.add_photos_optional),
            fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(start = 2.dp),
        )
        BottlePhotosRow(
            photos = BottlePhotoKind.entries.associateWith { photos[it] },
            onCapture = { kind -> pendingKind = kind; capture() },
        )
        Text(
            stringResource(Res.string.add_search_cellar),
            fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(start = 2.dp),
        )
        SearchField(
            placeholder = stringResource(Res.string.add_search_placeholder),
            value = searchQuery,
            onValueChange = { searchQuery = it },
        )
        if (debouncedQuery.length >= 2) {
            if (suggestions.isEmpty()) {
                Text(
                    stringResource(Res.string.add_empty_cellar),
                    fontSize = 11.5.sp, color = VincentColors.Muted,
                    modifier = Modifier.padding(start = 2.dp),
                )
            } else {
                suggestions.forEach { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VincentColors.Surface)
                            .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                            .clickable { applyFromCellar(b) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WineBottle(b.color, Modifier.size(width = 16.dp, height = 32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.domain, fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                            Text("${b.appellation} · ${b.vintage}", fontSize = 11.sp, color = VincentColors.Muted)
                        }
                        Text(stringResource(Res.string.add_reuse), fontSize = 10.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
                    }
                }
            }
        }

        Field(stringResource(Res.string.add_field_domain_name), domain) { domain = it }
        Field(stringResource(Res.string.add_field_appellation_label), appellation) { appellation = it }
        ChipRow(stringResource(Res.string.add_field_color), WineColor.entries, color, { stringResource(it.label) }) { color = it }
        ChipRow(stringResource(Res.string.add_field_category), WineCategory.entries, category, { stringResource(it.label) }) { category = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(Res.string.add_field_vintage_label), vintage, Modifier.weight(1f), numeric = true) { vintage = it }
            Field(stringResource(Res.string.add_field_price_label), price, Modifier.weight(1f), numeric = true) { price = it }
        }
        Field(stringResource(Res.string.add_field_quantity), qty, numeric = true) { qty = it }
        PlacementSection(
            placeRack = placeRack,
            placeCell = placeCell,
            currentRack = currentPlacement?.first,
            currentCell = currentPlacement?.second,
            initialOpen = placeRack != null && placeCell != null,
            onClear = { placeRack = null; placeCell = null; spot = "" },
            onPick = { ri, ci, label -> placeRack = ri; placeCell = ci; spot = label },
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Optional placement wizard: pick a rack, then an empty spot (or leave unplaced). */
@Composable
private fun PlacementSection(
    placeRack: Int?,
    placeCell: Int?,
    currentRack: Int? = null,
    currentCell: Int? = null,
    initialOpen: Boolean = false,
    onClear: () -> Unit,
    onPick: (Int, Int, String) -> Unit,
) {
    var open by remember { mutableStateOf(initialOpen) }
    var browse by remember(placeRack) { mutableStateOf(placeRack ?: 0) }
    val placed = placeRack != null && placeCell != null
    val placedLabel = if (placed) {
        val r = Racks.all.getOrNull(placeRack!!)
        if (r != null) "${r.name} · ${cellSpotLabel(placeCell!!, r.cols)}" else "—"
    } else null
    val placedSpot = if (placed) {
        Racks.all.getOrNull(placeRack!!)?.let { cellSpotLabel(placeCell!!, it.cols) }
    } else null

    Column {
        Text(
            stringResource(Res.string.add_placement_optional), fontSize = 11.sp, color = VincentColors.Muted,
            fontWeight = FontWeight.W600, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                .clickable { open = !open }.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                placedLabel ?: stringResource(Res.string.add_placement_placeholder),
                fontSize = 13.sp, fontWeight = FontWeight.W600,
                color = if (placed) VincentColors.Accent else VincentColors.Muted,
                modifier = Modifier.weight(1f),
            )
            if (placed) {
                Text(stringResource(Res.string.add_placement_remove), fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent,
                    modifier = Modifier.clickable { onClear() })
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Racks.all.forEachIndexed { i, r ->
                    val on = i == browse
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (on) VincentColors.Accent else VincentColors.Surface2)
                            .border(1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(10.dp))
                            .clickable { browse = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(r.name, fontSize = 12.sp, fontWeight = FontWeight.W600, color = if (on) Color.White else VincentColors.Fg) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val rack = Racks.all.getOrNull(browse)
            val freeCells = rack?.cells?.mapIndexed { i, c -> i to c }?.filter { !it.second.occupied }.orEmpty()
            val hasEmpty = freeCells.isNotEmpty()
            if (rack == null || !hasEmpty) {
                Text(stringResource(Res.string.add_placement_none_free), fontSize = 11.5.sp, color = VincentColors.Muted, modifier = Modifier.padding(start = 2.dp))
            } else {
                PlacementRackGrid(
                    rack = rack,
                    selectedCell = if (browse == placeRack) placeCell else null,
                    currentCell = if (browse == currentRack) currentCell else null,
                    onPick = { ci ->
                        val label = cellSpotLabel(ci, rack.cols)
                        onPick(browse, ci, label)
                    },
                )
                if (placed && placedSpot != null) {
                    val rackName = Racks.all.getOrNull(placeRack!!)?.name.orEmpty()
                    Text(
                        stringResource(Res.string.add_placement_chosen_format, rackName, placedSpot),
                        fontSize = 10.5.sp,
                        color = VincentColors.Faint,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                    )
                }
            }
        }
    }
}

private enum class PlacementCellHighlight {
    Occupied,
    Available,
    Selected,
    Current,
}

/** Visual rack grid for manual placement — tap an empty cell to select it. */
@Composable
private fun PlacementRackGrid(
    rack: Rack,
    selectedCell: Int?,
    currentCell: Int?,
    onPick: (Int) -> Unit,
) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            rack.cells.chunked(rack.cols).forEachIndexed { rowIndex, rowCells ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        rowLabel(rowIndex),
                        fontSize = 10.sp,
                        color = VincentColors.Faint,
                        modifier = Modifier.width(14.dp),
                    )
                    val shiftRight = rack.staggered && rowIndex % 2 == 1
                    if (shiftRight) Spacer(Modifier.weight(0.5f))
                    rowCells.forEachIndexed { colIndex, cell ->
                        val gi = rowIndex * rack.cols + colIndex
                        val highlight = when {
                            gi == selectedCell && !cell.occupied -> PlacementCellHighlight.Selected
                            gi == currentCell && cell.occupied -> PlacementCellHighlight.Current
                            !cell.occupied -> PlacementCellHighlight.Available
                            else -> PlacementCellHighlight.Occupied
                        }
                        PlacementGridCell(
                            cell = cell,
                            spotLabel = cellSpotLabel(gi, rack.cols),
                            highlight = highlight,
                            onClick = { if (!cell.occupied) onPick(gi) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rack.staggered && !shiftRight) Spacer(Modifier.weight(0.5f))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.add_placement_tap_free),
                fontSize = 11.sp,
                color = VincentColors.Muted,
                fontWeight = FontWeight.W500,
            )
        }
    }
}

@Composable
private fun PlacementGridCell(
    cell: RackCell,
    spotLabel: String,
    highlight: PlacementCellHighlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (highlight) {
        PlacementCellHighlight.Available,
        PlacementCellHighlight.Selected,
        -> {
            val selected = highlight == PlacementCellHighlight.Selected
            Box(
                modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) VincentColors.Accent else VincentColors.AccentSoft)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = VincentColors.Accent,
                        shape = RoundedCornerShape(7.dp),
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        spotLabel,
                        style = MonoNumber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W700,
                        color = VincentColors.Accent,
                    )
                }
            }
        }
        PlacementCellHighlight.Current -> {
            val wineBorder = cell.color?.glass ?: VincentColors.Accent
            val tint = lerp(Color.White, cell.color?.glass ?: VincentColors.Accent, 0.22f)
            Box(
                modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint)
                    .border(3.dp, wineBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(2.dp)
                        .border(2.dp, VincentColors.Accent, RoundedCornerShape(6.dp)),
                )
                Text(
                    stringResource(Res.string.add_placement_current),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.W800,
                    color = VincentColors.Accent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(VincentColors.AccentSoft)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        PlacementCellHighlight.Occupied -> {
            val wineBorder = cell.color?.glass ?: VincentColors.Border
            val tint = lerp(Color.White, cell.color?.glass ?: VincentColors.Accent, 0.22f)
            Box(
                modifier
                    .aspectRatio(1f)
                    .alpha(0.35f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint)
                    .border(2.dp, wineBorder, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
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
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { opt ->
                val on = opt == selected
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (on) VincentColors.Accent else VincentColors.Surface2)
                        .border(1.dp, if (on) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(10.dp))
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                ) {
                    Text(
                        labelOf(opt), fontSize = 12.sp, fontWeight = FontWeight.W600,
                        color = if (on) Color.White else VincentColors.Fg,
                    )
                }
            }
        }
    }
}

// Inside "Identifier": two distinct capture methods, toggled — barcode (Open Food
// Facts, auto-detect) vs label photo (AI). Mirrors the two scan screens in the mockup.
private enum class IdentifyMethod(val label: org.jetbrains.compose.resources.StringResource) {
    BARCODE(Res.string.add_method_barcode),
    LABEL(Res.string.add_method_label)
}

@Composable
private fun ScanPane(
    color: WineColor,
    bottle: Bottle?,
    title: String,
    subtitle: String,
    priceLabel: String?,
    busy: Boolean,
    hasResult: Boolean,
    errorMsg: String? = null,
    onIdentify: () -> Unit,
    onScanBarcode: (() -> Unit)? = null,
) {
    var method by remember { mutableStateOf(IdentifyMethod.BARCODE) }
    val label = method == IdentifyMethod.LABEL
    Column(Modifier.fillMaxSize()) {
        // Viewfinder + loading animation overlay. Frame adapts to the chosen method.
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF15151B)))),
            contentAlignment = Alignment.Center,
        ) {
            if (label) {
                // Portrait frame around the bottle/label.
                Box(Modifier.size(width = 170.dp, height = 240.dp), contentAlignment = Alignment.Center) {
                    WineBottle(if (hasResult) color else WineColor.RED, Modifier.size(width = 72.dp, height = 165.dp))
                    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                        Box(Modifier.align(a).size(28.dp).border(3.dp, Color.White, RoundedCornerShape(8.dp)))
                    }
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color(0xFF7BE6A8)))
                }
            } else {
                // Landscape frame with a barcode glyph.
                Box(Modifier.size(width = 232.dp, height = 132.dp), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(3, 2, 4, 2, 3, 2, 2, 4, 3, 2, 4, 2, 3, 2, 2, 3, 2, 4).forEach { w ->
                            Box(Modifier.width(w.dp).height(56.dp).background(Color.White.copy(alpha = 0.92f)))
                        }
                    }
                    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                        Box(Modifier.align(a).size(28.dp).border(3.dp, Color.White, RoundedCornerShape(8.dp)))
                    }
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color(0xFF7BE6A8)))
                }
            }
            if (busy) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (label) stringResource(Res.string.add_analyzing_label) else stringResource(Res.string.add_searching_off),
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.W700,
                        )
                    }
                }
            } else {
                Text(
                    if (label) stringResource(Res.string.add_frame_label) else stringResource(Res.string.add_frame_barcode),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.W600,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
        }

        // Result (clear) — only once the AI returned a bottle.
        Spacer(Modifier.height(12.dp))
        if (hasResult) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VincentColors.Surface)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (bottle != null && bottle.thumbnailUri() != null) {
                    BottleThumb(bottle, Modifier.size(width = 30.dp, height = 54.dp))
                } else {
                    WineBottle(color, Modifier.size(width = 30.dp, height = 54.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(Res.string.add_identified_ai), fontSize = 9.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
                    }
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 3.dp))
                    Text(subtitle, fontSize = 12.sp, color = VincentColors.Muted)
                    if (priceLabel != null) {
                        Text(priceLabel, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Green, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        } else if (!busy && errorMsg != null) {
            Text(
                errorMsg,
                fontSize = 12.5.sp, color = VincentColors.Red, lineHeight = 17.sp,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }

        // Method toggle (Code-barres | Étiquette) — clearly separates the two flows.
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(VincentColors.Surface2)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            IdentifyMethod.entries.forEach { m ->
                val on = m == method
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) VincentColors.Surface else Color.Transparent)
                        .clickable(enabled = !busy) { method = m }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(m.label), fontSize = 12.sp, fontWeight = FontWeight.W700, color = if (on) VincentColors.Accent else VincentColors.Muted) }
            }
        }

        // Single action, scoped to the active method.
        Spacer(Modifier.height(10.dp))
        val onAction = if (label) onIdentify else (onScanBarcode ?: onIdentify)
        Row(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                .background(if (busy) VincentColors.AccentSoft else VincentColors.Accent)
                .clickable(enabled = !busy, onClick = onAction),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (label) Icons.Filled.AutoAwesome else Icons.Filled.QrCodeScanner,
                contentDescription = null,
                tint = if (busy) VincentColors.Accent else Color.White, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (label) stringResource(Res.string.add_photo_capture) else stringResource(Res.string.add_barcode_scan),
                fontSize = 13.sp, fontWeight = FontWeight.W700, color = if (busy) VincentColors.Accent else Color.White,
            )
        }
    }
}

@Composable
private fun VoicePane(
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    onDictationEnd: (String) -> Unit,
    parsed: Bottle?,
    priceLabel: String?,
    errorMsg: String? = null,
    busy: Boolean = false,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        SpeechTextInput(
            value = transcript,
            onValueChange = onTranscriptChange,
            onDictationEnd = onDictationEnd,
            placeholder = stringResource(Res.string.add_voice_placeholder),
            title = stringResource(Res.string.add_voice_title),
            subtitle = stringResource(Res.string.add_voice_subtitle),
        )

        Spacer(Modifier.height(14.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = VincentColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.add_voice_analyzing), fontSize = 12.sp, color = VincentColors.Muted)
            }
        } else if (errorMsg != null && parsed == null) {
            Text(errorMsg, fontSize = 12.sp, color = VincentColors.Red, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth())
        }
        if (parsed != null) {
            ParsedField(stringResource(Res.string.add_parsed_domain), parsed.domain)
            ParsedField(stringResource(Res.string.add_parsed_vintage), parsed.vintage, mono = true)
            ParsedFieldTag(stringResource(Res.string.add_parsed_color), parsed.color)
            if (priceLabel != null) ParsedField(stringResource(Res.string.add_parsed_estimated_price), priceLabel)
        }
    }
}

@Composable
private fun ParsedField(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(11.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
        if (mono) Text(value, style = MonoNumber, color = VincentColors.Fg)
        else Text(value, fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
    }
}

@Composable
private fun ParsedFieldTag(label: String, color: WineColor) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(11.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(11.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
        ColorTag(color)
    }
}
