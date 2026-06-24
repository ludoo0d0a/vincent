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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.theme.VincentColors

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenBottle: (Bottle) -> Unit,
) {
    val favorites = Cellar.favorites

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.my_favorites), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(pluralStringResource(Res.plurals.vines_count, favorites.size, favorites.size), fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            favorites.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    pair.forEach { b -> BottleCard(b, Modifier.weight(1f)) { onOpenBottle(b) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(11.dp))
            }
            if (favorites.isEmpty()) {
                Text(
                    stringResource(Res.string.bottles_no_result_filter),
                    fontSize = 13.sp, color = VincentColors.Muted, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
