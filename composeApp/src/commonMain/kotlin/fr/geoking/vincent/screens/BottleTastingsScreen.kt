package fr.geoking.vincent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.DataScreenHeader
import fr.geoking.vincent.ui.Stars
import fr.geoking.vincent.ui.VCard
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

/** Dedicated screen listing the tastings recorded for a single [bottle]. */
@Composable
fun BottleTastingsScreen(
    bottle: Bottle,
    onBack: () -> Unit,
    onTasting: (Bottle, String?) -> Unit,
) {
    val live = Cellar.bottle(bottle.id) ?: bottle
    val bottleTastings = remember(live.id, Tastings.all.toList()) {
        Tastings.all.filter { it.bottleId == live.id }.sortedByDescending { it.date }
    }

    fun deleteTasting(id: String) {
        Tastings.delete(id)
        val remaining = Tastings.all.filter { it.bottleId == live.id }
        val avg = if (remaining.isEmpty()) 0.0 else remaining.map { it.rating }.average()
        Cellar.updateBottle((Cellar.bottle(live.id) ?: live).copy(rating = avg))
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg)) {
        DataScreenHeader(
            title = stringResource(Res.string.detail_tasting_title),
            subtitle = pluralStringResource(Res.plurals.tastings_subtitle, bottleTastings.size, bottleTastings.size),
            onBack = onBack,
        )

        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val subtitle = listOfNotNull(
                live.domain.takeIf { it.isNotBlank() },
                live.vintage.takeIf { it != "NM" },
            ).joinToString(" ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Muted, modifier = Modifier.padding(top = 4.dp))
            }

            if (bottleTastings.isEmpty()) {
                Text(
                    stringResource(Res.string.detail_tasting_empty),
                    fontSize = 12.sp,
                    color = VincentColors.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                bottleTastings.forEach { tasting ->
                    VCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier.weight(1f).clickable { onTasting(live, tasting.id) },
                                ) {
                                    Text(tasting.date, fontSize = 11.sp, color = VincentColors.Muted)
                                    if (tasting.place.isNotBlank()) {
                                        Text(tasting.place, fontSize = 11.sp, color = VincentColors.Muted)
                                    }
                                }
                                Stars(tasting.rating)
                                Spacer(Modifier.size(10.dp))
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(Res.string.detail_tasting_delete),
                                    tint = VincentColors.Muted,
                                    modifier = Modifier.size(20.dp).clickable { deleteTasting(tasting.id) },
                                )
                            }
                            if (tasting.notes.isNotEmpty()) {
                                Text(
                                    tasting.notes,
                                    fontSize = 12.sp,
                                    color = VincentColors.Fg,
                                    modifier = Modifier.padding(top = 4.dp).clickable { onTasting(live, tasting.id) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = { onTasting(live, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.detail_tasting_add), fontWeight = FontWeight.W700)
        }
    }
}
