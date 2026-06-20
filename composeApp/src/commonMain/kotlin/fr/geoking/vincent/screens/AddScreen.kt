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
import androidx.compose.material.icons.filled.Mic
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
import fr.geoking.vincent.ai.PriceEstimate
import fr.geoking.vincent.ai.priceEstimator
import fr.geoking.vincent.ai.rememberBarcodeScanner
import fr.geoking.vincent.ai.rememberDictation
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.ai.wineRecognizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.barcodeLookup
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.RackCell
import fr.geoking.vincent.model.RackPlacement
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.rowLabel
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.RemoteImage
import fr.geoking.vincent.ui.WineBottle

// One "Identifier" screen handles BOTH barcode and label; plus voice and manual.
private enum class AddMode(val label: String) { IDENTIFY("Identifier"), VOICE("Voix"), MANUAL("Manuel") }

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
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    // Barcode → Open Food Facts lookup → prefill the manual form (vintage/price stay
    // for the user to complete, since EANs rarely encode them).
    val lookup = remember { barcodeLookup() }
    val scanBarcode = rememberBarcodeScanner { code ->
        if (code != null) {
            busy = true
            scope.launch {
                val info = lookup.byBarcode(code)
                busy = false
                manualSeed = if (info != null) {
                    scanMsg = if (info.imageUrl != null) {
                        "Prérempli depuis Open Food Facts — photo d'étiquette disponible."
                    } else {
                        "Prérempli depuis Open Food Facts — complétez millésime et prix."
                    }
                    ManualSeed(
                        domain = info.brand.ifBlank { info.name },
                        appellation = if (info.brand.isNotBlank()) info.name else "",
                        imageUrl = info.imageUrl,
                    )
                } else {
                    scanMsg = "Code-barres $code introuvable — complétez à la main ou utilisez la photo."
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
            val outcome = recognizer.fromImage(bytes)
            aiBottle = outcome.bottle
            aiPrice = aiBottle?.let { estimator.estimate(it) }
            aiError = outcome.error
            busy = false
        }
    }
    val identify: () -> Unit = { startCapture() }
    // Voice dictation: real SpeechRecognizer → transcript → Gemini parsing on final result.
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    val startDictation = rememberDictation(
        onText = { transcript = it },
        onLevel = { level = it },
        onListening = { l ->
            listening = l
            if (!l && transcript.isNotBlank()) {
                busy = true
                aiError = null
                scope.launch {
                    val outcome = recognizer.fromText(transcript)
                    val b = outcome.bottle
                    aiBottle = b
                    aiError = outcome.error
                    busy = false
                    // Land on the editable review form, pre-filled from the dictation.
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
        },
    )
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
            ) { Icon(Icons.Filled.Close, contentDescription = "Fermer", modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Text("Ajouter une bouteille", fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
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
                ) { Text(m.label, fontSize = 12.sp, fontWeight = FontWeight.W700, color = if (on) VincentColors.Accent else VincentColors.Muted) }
            }
        }

        if (scanMsg != null) {
            Text(
                scanMsg!!,
                fontSize = 11.5.sp, color = VincentColors.Muted, lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            when (mode) {
                AddMode.IDENTIFY -> ScanPane(
                    color = aiBottle?.color ?: WineColor.RED,
                    title = aiBottle?.let { "${it.domain} ${it.vintage}" }.orEmpty(),
                    subtitle = aiBottle?.let { "${it.appellation} · ${it.color.label}" }.orEmpty(),
                    priceLabel = aiPrice?.let { "≈ ${it.amountEur} € · ${it.source}" },
                    busy = busy,
                    hasResult = aiBottle != null,
                    errorMsg = aiError,
                    onIdentify = identify,
                    onScanBarcode = scanBarcode,
                )
                AddMode.VOICE -> VoicePane(
                    transcript = transcript,
                    listening = listening,
                    level = level,
                    parsed = aiBottle,
                    priceLabel = aiPrice?.let { "≈ ${it.amountEur} € · ${it.source}" },
                    errorMsg = aiError,
                    busy = busy,
                    onMic = startDictation,
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
            else aiBottle?.let { it.copy(price = aiPrice?.amountEur ?: it.price) }
        val buttonLabel = when {
            ready != null -> if (mode == AddMode.MANUAL) "Ajouter à la cave" else "Confirmer l'ajout"
            busy -> "Analyse en cours…"
            mode == AddMode.IDENTIFY -> "Photo ou code-barres requis"
            mode == AddMode.VOICE -> "Dictez une bouteille"
            else -> "Confirmer l'ajout"
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
    // Placement chosen via the wizard (rack, empty cell). Null = not placed.
    var placeRack by remember(seed) { mutableStateOf(seed?.placeRack) }
    var placeCell by remember(seed) { mutableStateOf(seed?.placeCell) }
    var imageUrl by remember(seed) { mutableStateOf(seed?.imageUrl) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    // Reading bottles.size keeps suggestions in sync when the cellar changes.
    val cellarSize = Cellar.bottles.size
    val suggestions = remember(debouncedQuery, cellarSize) { Cellar.search(debouncedQuery) }

    fun applyFromCellar(b: Bottle) {
        domain = b.domain
        appellation = b.appellation
        color = b.color
        category = b.category
        vintage = if (b.vintage == "NM") "" else b.vintage
        price = if (b.price > 0) b.price.toString() else ""
        searchQuery = ""
        debouncedQuery = ""
    }

    // Apply a prefill (barcode lookup, cellar suggestion, or an empty-cell tap).
    LaunchedEffect(seed) {
        seed?.let {
            domain = it.domain; appellation = it.appellation; color = it.color; category = it.category
            vintage = it.vintage; price = it.price; imageUrl = it.imageUrl
            if (it.spot.isNotBlank()) spot = it.spot
            if (it.placeRack != null && it.placeCell != null) {
                placeRack = it.placeRack
                placeCell = it.placeCell
            }
        }
    }

    // All fields optional — a bottle can be saved with no name/colour/vintage/spot.
    LaunchedEffect(domain, appellation, color, category, vintage, price, qty, spot, placeRack, placeCell) {
        val placement = placeRack?.let { r -> placeCell?.let { c -> r to c } }
        onBottle(
            Bottle(
                id = "new-${Cellar.references()}",
                domain = domain.trim().ifBlank { "Bouteille" },
                appellation = appellation.trim().ifBlank { category.label },
                color = color,
                category = category,
                vintage = vintage.trim().ifBlank { "NM" },
                price = price.filter { it.isDigit() }.toIntOrNull() ?: 0,
                quantity = qty.filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                rating = 0.0,
                cellarSpot = spot.trim().uppercase().ifBlank { "—" },
                provenance = "",
                merchant = "—",
                purchaseDate = "Aujourd'hui",
                occasion = "",
                source = AddSource.MANUAL,
                addedLabel = "à l'instant",
            ),
            placement,
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            RemoteImage(
                url = imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VincentColors.Surface2)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(14.dp)),
                contentDescription = "Étiquette Open Food Facts",
            )
        }
        Text(
            "Rechercher dans la cave",
            fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(start = 2.dp),
        )
        SearchField(
            placeholder = "Domaine, appellation…",
            value = searchQuery,
            onValueChange = { searchQuery = it },
        )
        if (debouncedQuery.length >= 2) {
            if (suggestions.isEmpty()) {
                Text(
                    "Aucune bouteille en cave — saisissez un nouveau vin ci-dessous.",
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
                        Text("Réutiliser", fontSize = 10.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
                    }
                }
            }
        }

        Field("Domaine / nom", domain) { domain = it }
        Field("Appellation", appellation) { appellation = it }
        ChipRow("Couleur", WineColor.entries, color, { it.label }) { color = it }
        ChipRow("Catégorie", WineCategory.entries, category, { it.label }) { category = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Millésime", vintage, Modifier.weight(1f), numeric = true) { vintage = it }
            Field("Prix (€)", price, Modifier.weight(1f), numeric = true) { price = it }
        }
        Field("Quantité", qty, numeric = true) { qty = it }
        PlacementSection(
            placeRack = placeRack,
            placeCell = placeCell,
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
    initialOpen: Boolean = false,
    onClear: () -> Unit,
    onPick: (Int, Int, String) -> Unit,
) {
    var open by remember { mutableStateOf(initialOpen) }
    var browse by remember(placeRack) { mutableStateOf(placeRack ?: 0) }
    val placed = placeRack != null && placeCell != null
    val placedLabel = if (placed) {
        val r = Racks.all.getOrNull(placeRack!!)
        if (r != null) "${r.name} · ${rowLabel(placeCell!! / r.cols)}${placeCell!! % r.cols + 1}" else "—"
    } else null

    Column {
        Text(
            "Emplacement (optionnel)", fontSize = 11.sp, color = VincentColors.Muted,
            fontWeight = FontWeight.W600, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                .clickable { open = !open }.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                placedLabel ?: "Sans emplacement — touchez pour placer",
                fontSize = 13.sp, fontWeight = FontWeight.W600,
                color = if (placed) VincentColors.Fg else VincentColors.Muted,
                modifier = Modifier.weight(1f),
            )
            if (placed) {
                Text("Retirer", fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent,
                    modifier = Modifier.clickable { onClear() })
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            // 1) choose a rack
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
            // 2) choose an empty spot in that rack
            val rack = Racks.all.getOrNull(browse)
            val empties = rack?.cells?.indices?.filter { !rack.cells[it].occupied }.orEmpty()
            if (rack == null || empties.isEmpty()) {
                Text("Aucun emplacement libre ici.", fontSize = 11.5.sp, color = VincentColors.Muted, modifier = Modifier.padding(start = 2.dp))
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    empties.forEach { ci ->
                        val label = "${rowLabel(ci / rack.cols)}${ci % rack.cols + 1}"
                        val sel = browse == placeRack && ci == placeCell
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(if (sel) VincentColors.Accent else VincentColors.AccentSoft)
                                .clickable { onPick(browse, ci, label); open = false }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) { Text(label, style = MonoNumber, color = if (sel) Color.White else VincentColors.Accent) }
                    }
                }
            }
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
    labelOf: (T) -> String,
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

@Composable
private fun ScanPane(
    color: WineColor,
    title: String,
    subtitle: String,
    priceLabel: String?,
    busy: Boolean,
    hasResult: Boolean,
    errorMsg: String? = null,
    onIdentify: () -> Unit,
    onScanBarcode: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        // Viewfinder + loading animation overlay.
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF15151B)))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 170.dp, height = 240.dp), contentAlignment = Alignment.Center) {
                WineBottle(if (hasResult) color else WineColor.RED, Modifier.size(width = 72.dp, height = 165.dp))
                listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                    Box(Modifier.align(a).size(28.dp).border(3.dp, Color.White, RoundedCornerShape(8.dp)))
                }
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color(0xFF7BE6A8)))
            }
            if (busy) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Analyse de l'étiquette…", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.W700)
                    }
                }
            } else {
                Text(
                    "Scannez le code-barres ou cadrez l'étiquette",
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
                WineBottle(color, Modifier.size(width = 30.dp, height = 54.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("IDENTIFIÉ PAR L'IA", fontSize = 9.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
                    }
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 3.dp))
                    Text(subtitle, fontSize = 12.sp, color = VincentColors.Muted)
                    if (priceLabel != null) {
                        Text(priceLabel, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Green, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        } else if (!busy) {
            if (errorMsg != null) {
                Text(
                    errorMsg,
                    fontSize = 12.5.sp, color = VincentColors.Red, lineHeight = 17.sp,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                Text(
                    "Choisissez une méthode d'identification ci-dessous.",
                    fontSize = 12.5.sp, color = VincentColors.Muted,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }

        // Two clearly-separated actions: barcode (secondary) vs label→AI (primary).
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onScanBarcode != null) {
                Row(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(14.dp))
                        .clickable(enabled = !busy, onClick = onScanBarcode),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Code-barres", fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
                }
            }
            Row(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (busy) VincentColors.AccentSoft else VincentColors.Accent)
                    .clickable(enabled = !busy, onClick = onIdentify),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = if (busy) VincentColors.Accent else Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Étiquette · IA", fontSize = 13.sp, fontWeight = FontWeight.W700, color = if (busy) VincentColors.Accent else Color.White)
            }
        }
    }
}

@Composable
private fun VoicePane(
    transcript: String,
    listening: Boolean,
    level: Float,
    parsed: Bottle?,
    priceLabel: String?,
    errorMsg: String? = null,
    busy: Boolean = false,
    onMic: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (listening) "● Écoute…" else "Dictée vocale", color = VincentColors.Accent, fontWeight = FontWeight.W700, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        Text("Dictez votre bouteille", fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        Text("« domaine, millésime, couleur, quantité, casier »", fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 3.dp))

        // live waveform — driven by the microphone level while listening
        Row(
            Modifier.height(80.dp).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(18, 40, 66, 30, 78, 48, 80, 34, 60, 22, 50, 74, 28, 44, 16).forEach { base ->
                val h = (base * (0.35f + 0.65f * (if (listening) level else 0f))).coerceIn(6f, 80f)
                Box(Modifier.width(5.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(VincentColors.Accent.copy(alpha = if (listening) 0.9f else 0.4f)))
            }
        }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(16.dp)).padding(15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                transcript.ifBlank { "Touchez le micro et dictez votre bouteille…" },
                fontSize = 15.sp,
                color = if (transcript.isBlank()) VincentColors.Faint else VincentColors.Fg,
                lineHeight = 22.sp,
            )
        }

        Box(
            Modifier.padding(top = 14.dp).size(64.dp).clip(RoundedCornerShape(50))
                .background(if (listening) VincentColors.Accent else VincentColors.AccentSoft).clickable(onClick = onMic),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Mic, contentDescription = "Parler", tint = if (listening) Color.White else VincentColors.Accent, modifier = Modifier.size(28.dp)) }

        Spacer(Modifier.height(14.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = VincentColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Analyse de la dictée…", fontSize = 12.sp, color = VincentColors.Muted)
            }
        } else if (errorMsg != null && parsed == null) {
            Text(errorMsg, fontSize = 12.sp, color = VincentColors.Red, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth())
        }
        if (parsed != null) {
            ParsedField("Domaine", parsed.domain)
            ParsedField("Millésime", parsed.vintage, mono = true)
            ParsedFieldTag("Couleur", parsed.color)
            if (priceLabel != null) ParsedField("Prix estimé", priceLabel)
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
