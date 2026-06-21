package fr.geoking.vincent.debug

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.theme.VincentColors

@Composable
fun HttpDebugBar(modifier: Modifier = Modifier) {
    if (!HttpDebug.enabled) return

    var expanded by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val entries = HttpDebug.entries
    val selected = entries.firstOrNull { it.id == selectedId }

    val last = entries.firstOrNull()
    val statusTint = when (last?.statusCode) {
        in 200..299 -> VincentColors.Green
        in 400..599 -> VincentColors.Red
        null -> if (last?.error != null) VincentColors.Red else VincentColors.Amber
        else -> VincentColors.Amber
    }

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(if (expanded) 12.dp else 24.dp),
        color = VincentColors.Fg.copy(alpha = 0.92f),
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier
                    .then(if (expanded) Modifier.fillMaxWidth() else Modifier)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .background(statusTint, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "HTTP",
                        color = VincentColors.Surface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (expanded) {
                    Text(
                        "Debug réseau",
                        color = VincentColors.Surface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    last?.let {
                        Text(
                            "${it.method} ${it.statusCode ?: "—"} · ${it.durationMs}ms",
                            color = VincentColors.Surface.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        if (expanded) "▾" else "▸",
                        color = VincentColors.Surface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = VincentColors.Surface.copy(alpha = 0.15f))
                HttpDebug.apiKeyHint?.let { hint ->
                    Text(
                        hint,
                        color = VincentColors.Amber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Effacer",
                        color = VincentColors.Surface.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            HttpDebug.clear()
                            selectedId = null
                        },
                    )
                }
                Column(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            "Aucune requête encore.",
                            color = VincentColors.Surface.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        entries.forEach { e ->
                            val tint = when (e.statusCode) {
                                in 200..299 -> VincentColors.Green
                                in 400..599 -> VincentColors.Red
                                null -> VincentColors.Amber
                                else -> VincentColors.Amber
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedId = if (selectedId == e.id) null else e.id }
                                    .background(
                                        if (selectedId == e.id) VincentColors.Surface.copy(alpha = 0.12f)
                                        else Color.Transparent,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "${e.statusCode ?: "?"}",
                                    color = tint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        e.label,
                                        color = VincentColors.Surface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        e.url.take(80) + if (e.url.length > 80) "…" else "",
                                        color = VincentColors.Surface.copy(alpha = 0.55f),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Text(
                                    "${e.durationMs}ms",
                                    color = VincentColors.Surface.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                selected?.let { e ->
                    HorizontalDivider(color = VincentColors.Surface.copy(alpha = 0.15f))
                    Column(
                        Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DebugField("URL", e.url)
                        DebugField("Méthode", e.method)
                        e.requestBody?.let { DebugField("Payload", it) }
                        e.statusCode?.let { DebugField("Status", it.toString()) }
                        e.responseBody?.let { DebugField("Réponse", it) }
                        e.error?.let { DebugField("Erreur", it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugField(label: String, value: String) {
    Text(
        label.uppercase(),
        color = VincentColors.Surface.copy(alpha = 0.45f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        value,
        color = VincentColors.Surface.copy(alpha = 0.9f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 13.sp,
    )
}
