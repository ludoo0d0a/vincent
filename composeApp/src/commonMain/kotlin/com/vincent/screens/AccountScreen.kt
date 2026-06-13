package com.vincent.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincent.data.Cellar
import com.vincent.model.Bottle
import com.vincent.theme.VincentColors
import com.vincent.ui.SectionHeader
import com.vincent.ui.VCard

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenBottle: (Bottle) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Mon compte", fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text("Synchronisé avec Google", fontSize = 11.5.sp, color = VincentColors.Muted)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            // account card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(VincentColors.AccentDeep, VincentColors.Accent))).padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("L", color = Color.White, fontWeight = FontWeight.W800, fontSize = 18.sp)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ludovic V.", color = Color.White, fontWeight = FontWeight.W700, fontSize = 15.sp)
                    Text("l••••@gmail.com", color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
                }
                GoogleG(22)
            }

            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = VincentColors.Green, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Sauvegarde", fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                        }
                        Text("À jour", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                        Text("il y a 2 min", fontSize = 10.5.sp, color = VincentColors.Green, fontWeight = FontWeight.W700)
                    }
                }
                VCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Stockage cloud", fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                        Text("${Cellar.totalBottles()} / 500", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(VincentColors.Border)) {
                            Box(Modifier.fillMaxWidth((Cellar.totalBottles() / 500f).coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(4.dp)).background(VincentColors.Accent))
                        }
                    }
                }
            }

            // recent link
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable(onClick = onOpenRecent).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dernières bouteilles ajoutées", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(13.dp))
            }

            SectionHeader("Mes favoris", "${Cellar.favorites.size} vins")
            Cellar.favorites.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    pair.forEach { b -> BottleCard(b, Modifier.weight(1f)) { onOpenBottle(b) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(11.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
