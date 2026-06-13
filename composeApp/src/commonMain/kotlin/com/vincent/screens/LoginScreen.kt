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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincent.theme.VincentColors

@Composable
fun LoginScreen(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF7EEEF), VincentColors.Bg)),
        ),
    ) {
        // Hero
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(70.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(VincentColors.Accent, VincentColors.AccentDeep))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.LocalBar, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("Vincent", fontSize = 30.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            Text(
                "Votre cave dans la poche. Synchronisée, sauvegardée, partout avec vous.",
                fontSize = 13.sp, color = VincentColors.Muted, textAlign = TextAlign.Center, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 8.dp).width(240.dp),
            )
        }

        // Bottom actions
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                Perk(Icons.Filled.CloudUpload, "Stockage cloud")
                Perk(Icons.Filled.Favorite, "Favoris")
                Perk(Icons.Filled.Devices, "Multi-appareils")
            }
            // Google button
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onContinue),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                GoogleG()
                Spacer(Modifier.width(11.dp))
                Text("Continuer avec Google", fontSize = 14.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
            }
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onContinue), contentAlignment = Alignment.Center) {
                Text("Continuer sans compte", fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Muted)
            }
            Text(
                "En continuant, vous acceptez les conditions. Vos données restent privées et chiffrées.",
                fontSize = 10.sp, color = VincentColors.Faint, textAlign = TextAlign.Center, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Perk(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(VincentColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.W600, color = VincentColors.Muted)
    }
}

/** Minimal multi-colour Google "G" badge (no external asset). */
@Composable
fun GoogleG(size: Int = 20) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text("G", fontSize = (size * 0.78f).sp, fontWeight = FontWeight.W800, color = Color(0xFF4285F4))
    }
}
