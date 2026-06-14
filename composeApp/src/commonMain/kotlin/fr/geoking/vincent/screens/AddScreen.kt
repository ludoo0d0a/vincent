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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import fr.geoking.vincent.ai.rememberDictation
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.ai.wineRecognizer
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
import fr.geoking.vincent.model.AddSource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineCategory
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.ColorTag
import fr.geoking.vincent.ui.WineBottle

private enum class AddMode(val label: String) { SCAN("Scan"), PHOTO("Photo"), VOICE("Voix"), MANUAL("Manuel") }

@Composable
fun AddScreen(onClose: () -> Unit) {
    var mode by remember { mutableStateOf(AddMode.SCAN) }
    val recognizer = wineRecognizer()
    val estimator = priceEstimator()
    val scope = rememberCoroutineScope()
    var aiBottle by remember { mutableStateOf<Bottle?>(null) }
    var aiPrice by remember { mutableStateOf<PriceEstimate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var manualBottle by remember { mutableStateOf<Bottle?>(null) }
    // Scan & Photo both snap the label/bottle with the system camera (full-res) and let
    // the vision model read it → Gemini fromImage. No hardcoded recognition result.
    val startCapture = rememberPhotoCapture { bytes ->
        busy = true
        scope.launch {
            aiBottle = recognizer.fromImage(bytes)
            aiPrice = aiBottle?.let { estimator.estimate(it) }
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
                scope.launch {
                    aiBottle = recognizer.fromText(transcript)
                    aiPrice = aiBottle?.let { estimator.estimate(it) }
                    busy = false
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
                        .clickable { mode = m }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(m.label, fontSize = 12.sp, fontWeight = FontWeight.W700, color = if (on) VincentColors.Accent else VincentColors.Muted) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            when (mode) {
                AddMode.SCAN, AddMode.PHOTO -> ScanPane(
                    photo = mode == AddMode.PHOTO,
                    color = aiBottle?.color ?: WineColor.RED,
                    title = aiBottle?.let { "${it.domain} ${it.vintage}" } ?: "En attente d'identification",
                    subtitle = aiBottle?.let { "${it.appellation} · ${it.color.label}" } ?: "Cadrez l'étiquette puis touchez l'IA",
                    priceLabel = aiPrice?.let { "≈ ${it.amountEur} € · ${it.source}" },
                    busy = busy,
                    onIdentify = identify,
                )
                AddMode.VOICE -> VoicePane(
                    transcript = transcript,
                    listening = listening,
                    level = level,
                    parsed = aiBottle,
                    priceLabel = aiPrice?.let { "≈ ${it.amountEur} € · ${it.source}" },
                    onMic = startDictation,
                )
                AddMode.MANUAL -> ManualPane(onBottle = { manualBottle = it })
            }
        }

        // Only enabled once we have a real bottle: AI-recognised (scan/photo/voice)
        // or a manually filled form. No more hardcoded fallback.
        val ready: Bottle? = if (mode == AddMode.MANUAL) manualBottle
            else aiBottle?.let { it.copy(price = aiPrice?.amountEur ?: it.price) }
        Button(
            onClick = { ready?.let { Cellar.addBottle(it); onClose() } },
            enabled = ready != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VincentColors.Accent,
                contentColor = Color.White,
                disabledContainerColor = VincentColors.Surface2,
                disabledContentColor = VincentColors.Faint,
            ),
        ) {
            Text(
                if (mode == AddMode.MANUAL) "Ajouter à la cave" else "Confirmer l'ajout",
                fontWeight = FontWeight.W700,
            )
        }
    }
}

/** Manual entry — a real form; emits a Bottle (or null while the name is empty). */
@Composable
private fun ManualPane(onBottle: (Bottle?) -> Unit) {
    var domain by remember { mutableStateOf("") }
    var appellation by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(WineColor.RED) }
    var category by remember { mutableStateOf(WineCategory.BORDEAUX) }
    var vintage by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var spot by remember { mutableStateOf("") }

    LaunchedEffect(domain, appellation, color, category, vintage, price, qty, spot) {
        onBottle(
            if (domain.isBlank()) null
            else Bottle(
                id = "new-${Cellar.references()}",
                domain = domain.trim(),
                appellation = appellation.trim().ifBlank { category.label },
                color = color,
                category = category,
                vintage = vintage.trim().ifBlank { "NM" },
                price = price.filter { it.isDigit() }.toIntOrNull() ?: 0,
                quantity = qty.filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                rating = 0.0,
                cellarSpot = spot.trim().uppercase(),
                provenance = "",
                merchant = "—",
                purchaseDate = "Aujourd'hui",
                occasion = "",
                source = AddSource.MANUAL,
                addedLabel = "à l'instant",
            )
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Field("Domaine / nom", domain) { domain = it }
        Field("Appellation", appellation) { appellation = it }
        ChipRow("Couleur", WineColor.entries, color, { it.label }) { color = it }
        ChipRow("Catégorie", WineCategory.entries, category, { it.label }) { category = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Millésime", vintage, Modifier.weight(1f), numeric = true) { vintage = it }
            Field("Prix (€)", price, Modifier.weight(1f), numeric = true) { price = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Quantité", qty, Modifier.weight(1f), numeric = true) { qty = it }
            Field("Casier", spot, Modifier.weight(1f)) { spot = it }
        }
        Spacer(Modifier.height(4.dp))
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
    photo: Boolean,
    color: WineColor,
    title: String,
    subtitle: String,
    priceLabel: String?,
    busy: Boolean,
    onIdentify: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF15151B)))),
            contentAlignment = Alignment.Center,
        ) {
            // viewfinder frame
            Box(Modifier.size(width = 180.dp, height = 260.dp), contentAlignment = Alignment.Center) {
                WineBottle(WineColor.RED, Modifier.size(width = 74.dp, height = 170.dp))
                listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                    Box(Modifier.align(a).size(30.dp).border(3.dp, Color.White, RoundedCornerShape(8.dp)))
                }
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color(0xFF7BE6A8)))
            }
            Text(
                if (photo) "Prenez la bouteille en photo" else "Cadrez l'étiquette — reconnaissance auto",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.W600,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
            )
            // detected card
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(16.dp)).background(VincentColors.Surface).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WineBottle(color, Modifier.size(width = 28.dp, height = 50.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (busy) "ANALYSE…" else "IDENTIFIER AVEC L'IA", fontSize = 9.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent)
                    }
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg, modifier = Modifier.padding(top = 2.dp))
                    Text(subtitle, fontSize = 11.sp, color = VincentColors.Muted)
                    if (priceLabel != null) {
                        Text(priceLabel, fontSize = 11.sp, fontWeight = FontWeight.W700, color = VincentColors.Green, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(VincentColors.Accent).clickable(onClick = onIdentify),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AutoAwesome, contentDescription = "Identifier", tint = Color.White, modifier = Modifier.size(20.dp)) }
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
