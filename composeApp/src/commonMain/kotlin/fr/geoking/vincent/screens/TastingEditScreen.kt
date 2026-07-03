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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Tasting
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.formatShortDateTime

@Composable
fun TastingEditScreen(
    bottle: Bottle,
    onClose: () -> Unit,
    tastingId: String? = null
) {
    val existing = tastingId?.let { id -> Tastings.all.firstOrNull { it.id == id } }

    var date by remember { mutableStateOf(existing?.date ?: "") }
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var rating by remember { mutableStateOf(existing?.rating ?: 0.0) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    LaunchedEffect(Unit) {
        if (date.isBlank()) {
            date = formatShortDateTime(System.currentTimeMillis())
        }
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Text(stringResource(Res.string.detail_tasting_title), fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Spacer(Modifier.width(38.dp))
        }

        Column(Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text(stringResource(Res.string.detail_tasting_date)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(stringResource(Res.string.detail_tasting_place)) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(Res.string.detail_rating), fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { i ->
                    val star = i + 1
                    Icon(
                        if (rating >= star) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = Color(0xFFD69A3C),
                        modifier = Modifier.size(32.dp).clickable { rating = star.toDouble() }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(Res.string.detail_tasting_notes)) },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }

        Button(
            onClick = {
                val t = Tasting(
                    id = tastingId ?: "tasting-${System.currentTimeMillis()}",
                    bottleId = bottle.id,
                    wineName = bottle.domain,
                    date = date,
                    rating = rating,
                    notes = notes,
                    color = bottle.color,
                    vintage = bottle.vintage,
                    place = place
                )
                Tastings.save(t)

                // Update bottle rating (average)
                val bottleTastings = Tastings.all.filter { it.bottleId == bottle.id }
                val avg = if (bottleTastings.isEmpty()) 0.0 else bottleTastings.map { it.rating }.average()
                Cellar.updateBottle(bottle.copy(rating = avg))

                onClose()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
        ) {
            Text(stringResource(Res.string.detail_tasting_save), fontWeight = FontWeight.W700)
        }
    }
}
