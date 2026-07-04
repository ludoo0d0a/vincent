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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import fr.geoking.vincent.ai.AiUsage
import fr.geoking.vincent.ai.PriceEstimate
import fr.geoking.vincent.ai.priceEstimator
import fr.geoking.vincent.ai.rememberBarcodeScanner
import fr.geoking.vincent.ai.rememberDictation
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.WineDataSource
import fr.geoking.vincent.data.WineEnrichment
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
import fr.geoking.vincent.ui.PlacementCellHighlight
import fr.geoking.vincent.ui.PlacementRackGrid
import fr.geoking.vincent.ui.SpeechTextInput
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle

// One "Identifier" screen handles BOTH barcode and label; plus voice and manual.
private enum class AddMode(val label: org.jetbrains.compose.resources.StringResource) {
    PHOTO(Res.string.add_mode_photo),
    SCAN(Res.string.add_mode_scan),
    VOICE(Res.string.add_mode_voice),
    MANUAL(Res.string.add_mode_manual)
}

private sealed interface ScanMessage {
    data object OffSuccessPhoto : ScanMessage
    data object OffSuccess : ScanMessage
    data class NotFound(val code: String) : ScanMessage
}

@Composable
fun AddScreen(onClose: () -> Unit, initialPlacement: RackPlacement? = null, editingBottle: Bottle? = null) {
    // Opened from an empty rack cell → start on the manual form with the spot pre-filled.
    var mode by remember { mutableStateOf(if (initialPlacement != null || editingBottle != null) AddMode.MANUAL else AddMode.PHOTO) }
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
        mutableStateOf(
            editingBottle?.let { b ->
                var foundRack: Int? = null
                var foundCell: Int? = null
                if (b.cellarSpot.isNotBlank()) {
                    for ((ri, rack) in Racks.all.withIndex()) {
                        val ci = cellIndexFromSpot(b.cellarSpot, rack.cols)
                        if (ci != null && ci in rack.cells.indices && rack.cells[ci].occupied) {
                            foundRack = ri
                            foundCell = ci
                            break
                        }
                    }
                }
                ManualSeed(
                    domain = b.domain,
                    appellation = b.appellation,
                    color = b.color,
                    category = b.category,
                    vintage = if (b.vintage == "NM") "" else b.vintage,
                    price = if (b.price > 0) b.price.toString() else "",
                    agingPotential = if (b.agingPotential > 0) b.agingPotential.toString() else "",
                    alcohol = if (b.alcoholLevel > 0.0) b.alcoholLevel.toString() else "",
                    sugar = b.sugarLevel,
                    grapes = b.grapes,
                    spot = b.cellarSpot,
                    placeRack = foundRack,
                    placeCell = foundCell,
                    photos = BottlePhotoKind.entries.associateWith { b.photo(it) }
                )
            } ?: initialPlacement?.let { p ->
                val rack = Racks.all.getOrNull(p.rackIndex)
                ManualSeed(
                    spot = rack?.let { p.spotLabel(it.cols) }.orEmpty(),
                    placeRack = p.rackIndex,
                    placeCell = p.cellIndex,
                )
            }
        )
    }
    var scanMsg by remember { mutableStateOf<ScanMessage?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    // Barcode → wine data providers (Open Food Facts, GWDB…) → prefill the manual
    // form (vintage/price stay for the user to complete, since EANs rarely encode them).
    val labelSaver = rememberLabelImageSaver()
    var capturedLabelUri by remember { mutableStateOf<String?>(null) }
    val scanBarcode = rememberBarcodeScanner { code ->
        if (code != null) {
            busy = true
            scope.launch {
                val info = WineDataSource.byBarcode(code)
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
    // Conversation used to complete the parsed bottle by discussing with the assistant.
    var voiceChat by remember { mutableStateOf<List<VoiceChatMsg>>(emptyList()) }
    var chatBusy by remember { mutableStateOf(false) }
    val assistantDone = stringResource(Res.string.add_voice_assistant_done)

    // Pre-fill the manual form from the current (possibly edited) voice or scan result.
    fun seedManualFromCurrent() {
        aiBottle?.let { b ->
            val eff = aiPrice?.amountEur ?: b.price
            manualSeed = ManualSeed(
                domain = b.domain,
                appellation = b.appellation,
                color = b.color,
                category = b.category,
                vintage = if (b.vintage == "NM") "" else b.vintage,
                price = eff.takeIf { it > 0 }?.toString() ?: "",
                agingPotential = b.agingPotential.takeIf { it > 0 }?.toString() ?: "",
                alcohol = b.alcoholLevel.takeIf { it > 0.0 }?.toString() ?: "",
                sugar = b.sugarLevel,
                grapes = b.grapes,
                imageUrl = capturedLabelUri, // Legacy field fallback if needed
                photos = BottlePhotoKind.entries.associateWith { b.photo(it) }.toMutableMap().apply {
                    if (capturedLabelUri != null) put(BottlePhotoKind.LABEL, capturedLabelUri)
                }
            )
        }
    }

    val onVoiceDictationEnd: (String) -> Unit = { text ->
        busy = true
        aiError = null
        voiceChat = emptyList()
        scope.launch {
            val outcome = recognizer.fromText(text)
            val b = outcome.bottle
            aiBottle = b
            aiError = outcome.error
            busy = false
            if (b != null) {
                // Stay on the voice summary; the user completes missing data by tapping
                // a field or chatting, and only switches to the manual form on demand.
                aiPrice = estimator.estimate(b)
                seedManualFromCurrent()
            }
        }
    }
    // Inline edits on the summary update the parsed bottle directly.
    val onVoiceBottleChange: (Bottle) -> Unit = { b -> aiBottle = b }
    // Editing the price by hand overrides the estimate.
    val onVoicePriceChange: (Int) -> Unit = { p ->
        aiBottle = aiBottle?.copy(price = p)
        aiPrice = null
    }
    val onSwitchToManual: () -> Unit = {
        seedManualFromCurrent()
        mode = AddMode.MANUAL
    }
    val onSendChat: (String) -> Unit = { msg ->
        val current = aiBottle
        if (current != null && msg.isNotBlank()) {
            voiceChat = voiceChat + VoiceChatMsg(fromUser = true, text = msg)
            chatBusy = true
            scope.launch {
                val outcome = recognizer.refine(current, msg)
                outcome.bottle?.let { b ->
                    aiBottle = b
                    // Keep an explicit price; otherwise try to (re)estimate the missing one.
                    aiPrice = if (b.price > 0) null else estimator.estimate(b)
                }
                val reply = outcome.reply ?: outcome.error ?: assistantDone
                voiceChat = voiceChat + VoiceChatMsg(fromUser = false, text = reply)
                chatBusy = false
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
                            if (m != AddMode.PHOTO && m != AddMode.SCAN && m != AddMode.VOICE) aiError = null
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
                AddMode.PHOTO -> ScanPane(
                    isLabel = true,
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
                    onSwitchToManual = onSwitchToManual,
                )
                AddMode.SCAN -> ScanPane(
                    isLabel = false,
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
                    onSwitchToManual = onSwitchToManual,
                )
                AddMode.VOICE -> VoicePane(
                    transcript = transcript,
                    onTranscriptChange = { transcript = it },
                    onDictationEnd = onVoiceDictationEnd,
                    parsed = aiBottle,
                    effectivePrice = aiPrice?.amountEur ?: aiBottle?.price ?: 0,
                    priceSource = aiPrice?.source,
                    errorMsg = aiError,
                    busy = busy,
                    chat = voiceChat,
                    chatBusy = chatBusy,
                    onBottleChange = onVoiceBottleChange,
                    onPriceChange = onVoicePriceChange,
                    onSendChat = onSendChat,
                    onSwitchToManual = onSwitchToManual,
                )
                AddMode.MANUAL -> ManualPane(
                    seed = manualSeed,
                    onBottle = { b, place -> manualBottle = b; manualPlacement = place },
                )
            }
        }

        // Only enabled once we have a real bottle: AI-recognised (scan/photo/voice)
        // or a manually filled form. No more hardcoded fallback.
        val ready: Bottle? = if (mode == AddMode.MANUAL) manualBottle?.let { if (editingBottle != null) it.copy(id = editingBottle.id) else it }
            else aiBottle?.let { it.copy(price = aiPrice?.amountEur ?: it.price, photoLabel = capturedLabelUri ?: it.photoLabel) }
        val buttonLabel = when {
            ready != null -> when {
                editingBottle != null -> stringResource(Res.string.save_changes)
                mode == AddMode.MANUAL -> stringResource(Res.string.add_to_cellar)
                else -> stringResource(Res.string.add_confirm)
            }
            busy -> stringResource(Res.string.add_analyzing)
            mode == AddMode.PHOTO -> stringResource(Res.string.add_barcode_required)
            mode == AddMode.SCAN -> stringResource(Res.string.add_barcode_required)
            mode == AddMode.VOICE -> stringResource(Res.string.add_dictate_required)
            else -> stringResource(Res.string.add_confirm)
        }
        Button(
            onClick = {
                ready?.let { b ->
                    if (editingBottle != null) {
                        Cellar.updateBottle(b)
                    } else {
                        Cellar.addBottle(b)
                    }
                    // Place into the chosen empty rack cell, if any. The new cell is
                    // marked selected (and any prior selection cleared) so the cellar
                    // grid reopens on this freshly-added bottle.
                    if (mode == AddMode.MANUAL) manualPlacement?.let { (ri, ci) ->
                        Racks.all.getOrNull(ri)?.let { r ->
                            val placed = r.copy(
                                cells = r.cells.mapIndexed { idx, c ->
                                    when {
                                        idx == ci -> RackCell(rowLabel(ci / r.cols), true, b.color, b.category, b.vintage, b.price, selected = true)
                                        c.selected -> c.copy(selected = false)
                                        else -> c
                                    }
                                },
                            )
                            Racks.update(ri, placed)
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
    val agingPotential: String = "",
    val alcohol: String = "",
    val sugar: fr.geoking.vincent.model.SugarLevel = fr.geoking.vincent.model.SugarLevel.SEC,
    val grapes: List<String> = emptyList(),
    val spot: String = "",
    val placeRack: Int? = null,
    val placeCell: Int? = null,
    val imageUrl: String? = null,
    val photos: Map<BottlePhotoKind, String?> = emptyMap(),
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
    var agingPotential by remember { mutableStateOf("") }
    var alcohol by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf(fr.geoking.vincent.model.SugarLevel.SEC) }
    var grapes by remember { mutableStateOf<List<String>>(emptyList()) }
    var qty by remember { mutableStateOf("1") }
    var spot by remember(seed) { mutableStateOf(seed?.spot.orEmpty()) }
    var placeRack by remember(seed) { mutableStateOf(seed?.placeRack) }
    var placeCell by remember(seed) { mutableStateOf(seed?.placeCell) }
    var photos by remember { mutableStateOf<Map<BottlePhotoKind, String?>>(emptyMap()) }
    // Rich detail (drink window, tasting notes, pairings) fetched when a catalogue suggestion is picked.
    var enrichment by remember(seed) { mutableStateOf<WineEnrichment?>(null) }
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
            alcohol = if (b.alcoholLevel > 0.0) b.alcoholLevel.toString() else ""
            sugar = b.sugarLevel
        }
        // Catalogue pick: pull drink window + tasting notes in the background (best-effort).
        val extId = s.externalId
        if (extId != null) scope.launch { enrichment = WineDataSource.enrich(s.externalSource, extId) }
        else enrichment = null
    }

    LaunchedEffect(seed) {
        seed?.let {
            domain = it.domain; appellation = it.appellation; color = it.color; category = it.category
            vintage = it.vintage; price = it.price; agingPotential = it.agingPotential
            alcohol = it.alcohol; sugar = it.sugar; grapes = it.grapes
            if (it.imageUrl != null) photos = photos + (BottlePhotoKind.LABEL to it.imageUrl)
            if (it.photos.isNotEmpty()) photos = photos + it.photos
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

    LaunchedEffect(domain, appellation, color, category, vintage, price, agingPotential, alcohol, sugar, grapes, qty, spot, placeRack, placeCell, photos, enrichment, defaultDomain, todayLabel, justNowLabel, categoryFallback) {
        val placement = placeRack?.let { r -> placeCell?.let { c -> r to c } }
        // grapeminds enrichment: turn ageing offsets into absolute years (needs a numeric vintage)
        // and fold tasting notes + pairing prose into the free-text notes field.
        val vintageYear = vintage.trim().toIntOrNull()
        val enr = enrichment
        val dFrom = enr?.drinkFromYears?.let { off -> vintageYear?.plus(off) } ?: 0
        val dTo = enr?.drinkToYears?.let { off -> vintageYear?.plus(off) } ?: 0
        val maturityText = enr?.let {
            buildString {
                if (it.maturity.isNotBlank()) append(it.maturity)
                if (it.young.isNotBlank()) { if (isNotEmpty()) append("\n\n"); append("Jeune : ${it.young}") }
                if (it.ripe.isNotBlank()) { if (isNotEmpty()) append("\n\n"); append("À maturité : ${it.ripe}") }
                if (it.storage.isNotBlank()) { if (isNotEmpty()) append("\n\n"); append("Conservation : ${it.storage}") }
            }
        }.orEmpty()
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
                agingPotential = agingPotential.filter { it.isDigit() }.toIntOrNull() ?: 0,
                alcoholLevel = alcohol.replace(',', '.').toDoubleOrNull() ?: 0.0,
                sugarLevel = sugar,
                cellarSpot = spot.trim().uppercase().ifBlank { "—" },
                provenance = "",
                merchant = "—",
                purchaseDate = todayLabel,
                occasion = "",
                drinkFrom = dFrom,
                drinkTo = dTo,
                tastingNotes = enr?.tastingNotes.orEmpty(),
                description = enr?.description.orEmpty(),
                pairingNotes = enr?.pairingText.orEmpty(),
                grapes = enr?.grapes ?: grapes,
                flavorProfile = enr?.flavorProfile,
                maturity = maturityText,
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
        ChipRow(stringResource(Res.string.add_field_color), WineColor.entries, color, { stringResource(it.label) }) { color = it }
        ChipRow(stringResource(Res.string.add_field_category), WineCategory.entries, category, { stringResource(it.label) }) { category = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(Res.string.add_field_vintage_label), vintage, Modifier.weight(1f), numeric = true) { vintage = it }
            Field(stringResource(Res.string.add_field_price_label), price, Modifier.weight(1f), numeric = true) { price = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(Res.string.add_field_alcohol), alcohol, Modifier.weight(1f), numeric = true) { alcohol = it }
            Field(stringResource(Res.string.add_field_quantity), qty, Modifier.weight(1f), numeric = true) { qty = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(Res.string.add_field_aging_potential), agingPotential, Modifier.weight(1f), numeric = true) { agingPotential = it }
        }
        ChipRow(stringResource(Res.string.add_field_sugar), fr.geoking.vincent.model.SugarLevel.entries, sugar, { stringResource(it.label) }) { sugar = it }
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

/** A wine candidate surfaced by [AutocompleteField], from the cellar or the wine catalogue. */
private data class WineSuggestion(
    val domain: String,
    val appellation: String,
    val color: WineColor? = null,
    val category: WineCategory? = null,
    val vintage: String = "",
    val price: Int? = null,
    /** Set when the candidate is an existing cellar bottle (lets us reuse its photos). */
    val bottle: Bottle? = null,
    /** Provider/source label shown on the right of the row (null for cellar matches). */
    val source: String? = null,
    /** Catalogue id + provider, when the candidate can be enriched (drink window, notes…). */
    val externalId: String? = null,
    val externalSource: String? = null,
)

/** Suggestions for the add form: local cellar bottles first, then the wine catalogue. */
private suspend fun searchWineSuggestions(query: String): List<WineSuggestion> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val local = Cellar.search(q).map { b ->
        WineSuggestion(
            domain = b.domain,
            appellation = b.appellation,
            color = b.color,
            category = b.category,
            vintage = if (b.vintage == "NM") "" else b.vintage,
            price = b.price.takeIf { it > 0 },
            bottle = b,
        )
    }
    val catalog = runCatching { WineDataSource.search(q) }.getOrDefault(emptyList()).map { p ->
        WineSuggestion(
            domain = p.brand.ifBlank { p.name },
            appellation = (if (p.brand.isNotBlank()) p.name else p.region.orEmpty()).ifBlank { p.name },
            vintage = p.vintage.orEmpty(),
            source = p.source,
            externalId = p.externalId,
            externalSource = p.externalSource,
        )
    }
    return (local + catalog)
        .filter { it.domain.isNotBlank() || it.appellation.isNotBlank() }
        .distinctBy { it.domain.lowercase() to it.appellation.lowercase() }
        .take(8)
}

/**
 * A text field that proposes cellar + catalogue matches as the user types. Picking a
 * suggestion fills the whole form (domain, appellation, colour, vintage, price, photos).
 */
@Composable
private fun AutocompleteField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
    onPick: (WineSuggestion) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var debounced by remember { mutableStateOf(value) }
    var suggestions by remember { mutableStateOf<List<WineSuggestion>>(emptyList()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        delay(280)
        debounced = value
    }
    LaunchedEffect(debounced, focused) {
        suggestions = if (focused && debounced.trim().length >= 2) searchWineSuggestions(debounced) else emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(label, fontSize = 12.sp) },
            modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        if (focused && suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VincentColors.Surface)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)),
            ) {
                suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(s)
                                suggestions = emptyList()
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WineBottle(s.color ?: WineColor.RED, Modifier.size(width = 14.dp, height = 28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.domain.ifBlank { s.appellation },
                                fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                s.appellation.takeIf { it.isNotBlank() && it != s.domain },
                                s.vintage.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) {
                                Text(sub, fontSize = 11.sp, color = VincentColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(
                            s.source ?: stringResource(Res.string.add_reuse),
                            fontSize = 9.5.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
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


@Composable
private fun ScanPane(
    isLabel: Boolean,
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
    onSwitchToManual: () -> Unit,
) {
    val label = isLabel
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
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(VincentColors.Surface2)
                        .border(1.dp, VincentColors.Border, RoundedCornerShape(10.dp))
                        .clickable(onClick = onSwitchToManual),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.add_voice_switch_manual), tint = VincentColors.Accent, modifier = Modifier.size(16.dp))
                }
            }
        } else if (!busy && errorMsg != null) {
            Text(
                errorMsg,
                fontSize = 12.5.sp, color = VincentColors.Red, lineHeight = 17.sp,
                modifier = Modifier.padding(vertical = 14.dp),
            )
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

/** A single message in the "complete by discussion" conversation. */
private data class VoiceChatMsg(val fromUser: Boolean, val text: String)

/** Editable fields surfaced on the voice summary. */
private enum class VoiceField { DOMAIN, APPELLATION, COLOR, VINTAGE, PRICE, AGING, ALCOHOL, SUGAR }

@Composable
private fun VoicePane(
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    onDictationEnd: (String) -> Unit,
    parsed: Bottle?,
    effectivePrice: Int,
    priceSource: String?,
    errorMsg: String? = null,
    busy: Boolean = false,
    chat: List<VoiceChatMsg>,
    chatBusy: Boolean,
    onBottleChange: (Bottle) -> Unit,
    onPriceChange: (Int) -> Unit,
    onSendChat: (String) -> Unit,
    onSwitchToManual: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (parsed == null) {
            // Step 1 — dictate, then parse. Once parsed, we keep the user on the summary.
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
            } else if (errorMsg != null) {
                Text(errorMsg, fontSize = 12.sp, color = VincentColors.Red, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth())
            }
        } else {
            // Step 2 — summary with editable / missing fields + the discussion assistant.
            VoiceSummary(
                parsed = parsed,
                effectivePrice = effectivePrice,
                priceSource = priceSource,
                onBottleChange = onBottleChange,
                onPriceChange = onPriceChange,
                onSwitchToManual = onSwitchToManual,
            )
            Spacer(Modifier.height(18.dp))
            VoiceDiscussion(messages = chat, busy = chatBusy, onSend = onSendChat)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VoiceSummary(
    parsed: Bottle,
    effectivePrice: Int,
    priceSource: String?,
    onBottleChange: (Bottle) -> Unit,
    onPriceChange: (Int) -> Unit,
    onSwitchToManual: () -> Unit,
) {
    var editing by remember { mutableStateOf<VoiceField?>(null) }

    fun missing(f: VoiceField): Boolean = when (f) {
        VoiceField.DOMAIN -> parsed.domain.isBlank()
        VoiceField.APPELLATION -> parsed.appellation.isBlank() || parsed.appellation == "—"
        VoiceField.COLOR -> false
        VoiceField.VINTAGE -> parsed.vintage.isBlank() || parsed.vintage == "NM"
        VoiceField.PRICE -> effectivePrice <= 0
        VoiceField.AGING -> parsed.agingPotential <= 0
        VoiceField.ALCOHOL -> parsed.alcoholLevel <= 0.0
        VoiceField.SUGAR -> false
    }
    val anyMissing = VoiceField.entries.any { missing(it) }

    fun toggle(f: VoiceField) { editing = if (editing == f) null else f }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.add_voice_summary_title), fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            }
            // "Edit" → switch to the pre-filled manual entry.
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(VincentColors.Surface2)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onSwitchToManual).padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.add_voice_switch_manual), fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
            }
        }
        if (anyMissing) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(Res.string.add_voice_complete_hint),
                fontSize = 11.sp, color = VincentColors.Muted, lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SummaryRow(
            label = stringResource(Res.string.add_parsed_domain),
            value = parsed.domain,
            missing = missing(VoiceField.DOMAIN),
            editing = editing == VoiceField.DOMAIN,
            onToggle = { toggle(VoiceField.DOMAIN) },
        ) {
            InlineEditField(parsed.domain) { onBottleChange(parsed.copy(domain = it)) }
        }
        SummaryRow(
            label = stringResource(Res.string.add_parsed_appellation),
            value = parsed.appellation.takeIf { it.isNotBlank() && it != "—" }.orEmpty(),
            missing = missing(VoiceField.APPELLATION),
            editing = editing == VoiceField.APPELLATION,
            onToggle = { toggle(VoiceField.APPELLATION) },
        ) {
            InlineEditField(parsed.appellation.takeIf { it != "—" }.orEmpty()) { onBottleChange(parsed.copy(appellation = it)) }
        }
        SummaryRow(
            label = stringResource(Res.string.add_parsed_color),
            valueContent = { ColorTag(parsed.color) },
            missing = false,
            editing = editing == VoiceField.COLOR,
            onToggle = { toggle(VoiceField.COLOR) },
        ) {
            ChipRow("", WineColor.entries, parsed.color, { stringResource(it.label) }) {
                onBottleChange(parsed.copy(color = it)); editing = null
            }
        }
        SummaryRow(
            label = stringResource(Res.string.add_parsed_vintage),
            value = parsed.vintage.takeIf { it != "NM" && it.isNotBlank() }.orEmpty(),
            mono = true,
            missing = missing(VoiceField.VINTAGE),
            editing = editing == VoiceField.VINTAGE,
            onToggle = { toggle(VoiceField.VINTAGE) },
        ) {
            InlineEditField(parsed.vintage.takeIf { it != "NM" }.orEmpty(), numeric = true) {
                onBottleChange(parsed.copy(vintage = it.trim().ifBlank { "NM" }))
            }
        }
        SummaryRow(
            label = stringResource(Res.string.add_parsed_estimated_price),
            value = if (effectivePrice > 0) "≈ $effectivePrice €" + (priceSource?.let { " · $it" } ?: "") else "",
            missing = missing(VoiceField.PRICE),
            editing = editing == VoiceField.PRICE,
            onToggle = { toggle(VoiceField.PRICE) },
        ) {
            InlineEditField(effectivePrice.takeIf { it > 0 }?.toString().orEmpty(), numeric = true) {
                onPriceChange(it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0)
            }
        }
        SummaryRow(
            label = stringResource(Res.string.add_parsed_aging_potential),
            value = if (parsed.agingPotential > 0) stringResource(Res.string.aging_potential_years, parsed.agingPotential) else "",
            missing = missing(VoiceField.AGING),
            editing = editing == VoiceField.AGING,
            onToggle = { toggle(VoiceField.AGING) },
        ) {
            InlineEditField(parsed.agingPotential.takeIf { it > 0 }?.toString().orEmpty(), numeric = true) {
                onBottleChange(parsed.copy(agingPotential = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0))
            }
        }
        SummaryRow(
            label = stringResource(Res.string.add_field_alcohol),
            value = if (parsed.alcoholLevel > 0.0) "${parsed.alcoholLevel} %" else "",
            missing = missing(VoiceField.ALCOHOL),
            editing = editing == VoiceField.ALCOHOL,
            onToggle = { toggle(VoiceField.ALCOHOL) },
        ) {
            InlineEditField(parsed.alcoholLevel.takeIf { it > 0.0 }?.toString().orEmpty(), numeric = true) {
                onBottleChange(parsed.copy(alcoholLevel = it.replace(',', '.').toDoubleOrNull() ?: 0.0))
            }
        }
        SummaryRow(
            label = stringResource(Res.string.add_field_sugar),
            valueContent = { Text(stringResource(parsed.sugarLevel.label), fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg) },
            missing = false,
            editing = editing == VoiceField.SUGAR,
            onToggle = { toggle(VoiceField.SUGAR) },
        ) {
            ChipRow("", fr.geoking.vincent.model.SugarLevel.entries, parsed.sugarLevel, { stringResource(it.label) }) {
                onBottleChange(parsed.copy(sugarLevel = it)); editing = null
            }
        }
        if (parsed.grapes.isNotEmpty()) {
            SummaryRow(
                label = stringResource(Res.string.detail_grapes),
                value = parsed.grapes.joinToString(", "),
                missing = false,
                editing = false,
                onToggle = {},
            ) {}
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    missing: Boolean,
    editing: Boolean,
    onToggle: () -> Unit,
    value: String = "",
    mono: Boolean = false,
    valueContent: (@Composable () -> Unit)? = null,
    editor: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(11.dp))
            .background(VincentColors.Surface)
            .border(1.dp, if (editing) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(11.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
            when {
                editing -> Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.add_voice_field_done), tint = VincentColors.Accent, modifier = Modifier.size(16.dp))
                missing -> Text(stringResource(Res.string.add_voice_missing), fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Red)
                valueContent != null -> valueContent()
                mono -> Text(value, style = MonoNumber, color = VincentColors.Fg)
                else -> Text(value, fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            }
        }
        if (editing) {
            Spacer(Modifier.height(8.dp))
            editor()
        }
    }
}

@Composable
private fun InlineEditField(initial: String, numeric: Boolean = false, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VoiceDiscussion(
    messages: List<VoiceChatMsg>,
    busy: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val startDictation = rememberDictation(
        onText = { draft = it },
        onLevel = {},
        onListening = { listening = it },
    )

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.add_voice_discuss_title),
            fontSize = 11.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = 2.dp).fillMaxWidth(),
        )
        messages.forEach { ChatBubble(it) }
        if (busy) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = VincentColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.add_voice_analyzing), fontSize = 12.sp, color = VincentColors.Muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.add_voice_discuss_placeholder), fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (listening) VincentColors.Accent else VincentColors.AccentSoft)
                    .clickable(enabled = !busy, onClick = startDictation),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = stringResource(Res.string.ui_speech_speak),
                    tint = if (listening) Color.White else VincentColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            val canSend = draft.isNotBlank() && !busy
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (canSend) VincentColors.Accent else VincentColors.Surface2)
                    .clickable(enabled = canSend) { onSend(draft.trim()); draft = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.add_voice_send),
                    tint = if (canSend) Color.White else VincentColors.Faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: VoiceChatMsg) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            msg.text,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = if (msg.fromUser) Color.White else VincentColors.Fg,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (msg.fromUser) VincentColors.Accent else VincentColors.Surface)
                .then(if (msg.fromUser) Modifier else Modifier.border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
