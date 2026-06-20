package fr.geoking.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.theme.VincentColors

/** Three photo slots (bouteille / étiquette / dos) — tap to capture or replace. */
@Composable
fun BottlePhotosRow(
    photos: Map<BottlePhotoKind, String?>,
    onCapture: (BottlePhotoKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BottlePhotoKind.entries.forEach { kind ->
            BottlePhotoSlot(
                kind = kind,
                uri = photos[kind],
                onClick = { onCapture(kind) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottlePhotoSlot(
    kind: BottlePhotoKind,
    uri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPhoto = !uri.isNullOrBlank()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(108.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VincentColors.Surface2)
                .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPhoto) {
                RemoteImage(
                    url = uri,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = kind.label,
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VincentColors.Accent.copy(alpha = 0.92f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = VincentColors.Surface, modifier = Modifier.height(12.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = VincentColors.Muted, modifier = Modifier.height(22.dp))
                    Text("Ajouter", fontSize = 10.sp, fontWeight = FontWeight.W600, color = VincentColors.Muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            kind.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.W700,
            color = if (hasPhoto) VincentColors.Fg else VincentColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
