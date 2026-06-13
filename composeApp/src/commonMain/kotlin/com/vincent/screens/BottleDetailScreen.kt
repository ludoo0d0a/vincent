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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincent.data.Cellar
import com.vincent.model.Bottle
import com.vincent.theme.MonoNumber
import com.vincent.theme.VincentColors
import com.vincent.ui.ColorTag
import com.vincent.ui.Stars
import com.vincent.ui.VCard
import com.vincent.ui.WineBottle

@Composable
fun BottleDetailScreen(bottle: Bottle, onBack: () -> Unit) {
    val live = Cellar.bottle(bottle.id)
    val qty = live?.quantity ?: bottle.quantity
    val fav = live?.favorite ?: bottle.favorite
    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        // Top actions
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconChip(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            IconChip(
                Icons.Filled.Favorite,
                onClick = { Cellar.toggleFavorite(bottle.id) },
                tint = if (fav) VincentColors.Accent else VincentColors.Muted,
                bg = if (fav) VincentColors.AccentSoft else VincentColors.Surface2,
            )
        }

        // Hero
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF6EAEA), Color(0xFFEFD9DC))))
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            WineBottle(bottle.color, Modifier.size(width = 62.dp, height = 150.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                ColorTag(bottle.color, label = "${bottle.color.label} · ${bottle.category.label}")
                Spacer(Modifier.height(7.dp))
                Text("${bottle.domain} ${bottle.vintage}", fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text("${bottle.appellation} · ${bottle.provenance}", fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                    Stars(bottle.rating)
                    Spacer(Modifier.width(7.dp))
                    Text(bottle.rating.toString().replace('.', ','), fontSize = 22.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                }
            }
        }

        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Stat("Quantité", "×$qty", Modifier.weight(1f))
                Stat("Valeur", "${bottle.price * qty} €", Modifier.weight(1f))
                Stat("Casier", bottle.cellarSpot, Modifier.weight(1f))
            }

            // Pairings
            if (bottle.pairings.isNotEmpty()) {
                Section("Accords mets-vin") {
                    bottle.pairings.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                            rowItems.forEach { Pairing(it) }
                        }
                    }
                }
            }

            // Drink window
            if (bottle.drinkTo > 0) {
                Section("Apogée — à boire") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("${bottle.drinkFrom}", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Box(
                                Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                                    .background(Brush.horizontalGradient(listOf(VincentColors.Amber, VincentColors.Green, VincentColors.Amber))),
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(bottle.drinkNow.coerceIn(0.02f, 0.95f)))
                                Box(Modifier.size(13.dp).clip(RoundedCornerShape(50)).background(Color.White).border(3.dp, VincentColors.Green, RoundedCornerShape(50)))
                                Spacer(Modifier.weight(1f - bottle.drinkNow.coerceIn(0.02f, 0.95f)))
                            }
                        }
                        Text("${bottle.drinkTo}", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
                    }
                    if (bottle.tastingNotes.isNotEmpty()) {
                        Text(bottle.tastingNotes, fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            // Provenance & purchase
            Section("Provenance & achat") {
                VCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Column {
                        InfoRow(Icons.Filled.Place, "Provenance", bottle.provenance, divider = true)
                        InfoRow(Icons.Filled.Storefront, "Caviste", bottle.merchant, divider = true)
                        InfoRow(Icons.Filled.CalendarMonth, "Acheté le", bottle.purchaseDate, divider = true)
                        InfoRow(Icons.Filled.LocalBar, "Occasion", bottle.occasion, divider = false)
                    }
                }
            }

            // Location
            Section("Emplacement") {
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(VincentColors.AccentSoft).padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.GridView, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cave A · Rangée ${bottle.cellarSpot.take(1)} · Case ${bottle.cellarSpot.drop(1)}", color = VincentColors.Accent, fontWeight = FontWeight.W700, fontSize = 12.sp)
                }
            }

            // CTA
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = { Cellar.adjustQuantity(bottle.id, -1) }, modifier = Modifier.weight(1f)) { Text("Servir −1") }
                Button(
                    onClick = { Cellar.adjustQuantity(bottle.id, +1) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
                ) { Text("Ajouter +1") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IconChip(icon: ImageVector, onClick: () -> Unit, tint: Color = VincentColors.Fg, bg: Color = VincentColors.Surface2) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    VCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), fontSize = 9.5.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        content()
    }
}

@Composable
private fun Pairing(label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocalBar, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, divider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = VincentColors.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(label, fontSize = 12.sp, color = VincentColors.Muted, fontWeight = FontWeight.W500)
            }
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(VincentColors.Border))
    }
}
