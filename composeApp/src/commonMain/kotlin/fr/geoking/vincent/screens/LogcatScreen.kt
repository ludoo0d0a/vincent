package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.debug.InternalLog
import fr.geoking.vincent.debug.LogLevel
import fr.geoking.vincent.theme.VincentColors
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.debug_internal_logs

@Composable
fun LogcatScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        // Toolbar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(VincentColors.Surface2)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp),
                    tint = VincentColors.Fg
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(Res.string.debug_internal_logs),
                Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.W800,
                color = VincentColors.Fg
            )
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(VincentColors.Surface2)
                    .clickable { InternalLog.clear() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear",
                    modifier = Modifier.size(18.dp),
                    tint = VincentColors.Red
                )
            }
        }

        // Search field
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VincentColors.Surface2)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = VincentColors.Muted
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Rechercher…",
                        color = VincentColors.Muted,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = VincentColors.Fg, fontSize = 14.sp),
                    cursorBrush = SolidColor(VincentColors.Fg),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear search",
                    modifier = Modifier.size(18.dp).clickable { query = "" },
                    tint = VincentColors.Muted
                )
            }
        }

        HorizontalDivider(color = VincentColors.Border)

        val filtered = InternalLog.entries.filter { entry ->
            query.isBlank() ||
                entry.message.contains(query, ignoreCase = true) ||
                entry.tag.contains(query, ignoreCase = true)
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (InternalLog.entries.isEmpty()) "Aucun log" else "Aucun résultat",
                    color = VincentColors.Muted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { entry ->
                    LogEntryRow(entry)
                    HorizontalDivider(color = VincentColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: fr.geoking.vincent.debug.LogEntry) {
    val tint = when (entry.level) {
        LogLevel.INFO -> VincentColors.Green
        LogLevel.WARN -> VincentColors.Amber
        LogLevel.ERROR -> VincentColors.Red
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                entry.level.name.take(1),
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                entry.tag,
                color = VincentColors.Fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatTime(entry.timestampMs),
                color = VincentColors.Muted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            entry.message,
            color = VincentColors.Fg.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
        entry.throwable?.let {
            Text(
                it.stackTraceToString(),
                color = VincentColors.Red.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60)) % 24
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
