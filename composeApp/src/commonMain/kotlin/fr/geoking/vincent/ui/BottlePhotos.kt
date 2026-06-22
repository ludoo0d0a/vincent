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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.geoking.vincent.model.BottlePhotoKind
import fr.geoking.vincent.theme.VincentColors

/** Three photo slots (bouteille / étiquette / dos) — tap photo to view, icon to retake, empty slot to capture. */
@Composable
fun BottlePhotosRow(
    photos: Map<BottlePhotoKind, String?>,
    onCapture: (BottlePhotoKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewing by remember { mutableStateOf<Pair<BottlePhotoKind, String>?>(null) }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BottlePhotoKind.entries.forEach { kind ->
            BottlePhotoSlot(
                kind = kind,
                uri = photos[kind],
                onCapture = { onCapture(kind) },
                onView = { uri -> viewing = kind to uri },
                modifier = Modifier.weight(1f),
            )
        }
    }

    viewing?.let { (kind, uri) ->
        PhotoViewerDialog(
            uri = uri,
            label = kind.label,
            onDismiss = { viewing = null },
        )
    }
}

@Composable
private fun BottlePhotoSlot(
    kind: BottlePhotoKind,
    uri: String?,
    onCapture: () -> Unit,
    onView: (String) -> Unit,
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
                .then(if (!hasPhoto) Modifier.clickable(onClick = onCapture) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPhoto) {
                RemoteImage(
                    url = uri,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onView(uri) },
                    contentDescription = kind.label,
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VincentColors.Accent.copy(alpha = 0.92f))
                        .clickable(onClick = onCapture)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Reprendre la photo",
                        tint = VincentColors.Surface,
                        modifier = Modifier.height(12.dp),
                    )
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

@Composable
private fun PhotoViewerDialog(
    uri: String,
    label: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = uri,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentDescription = label,
                contentScale = ContentScale.Fit,
            )
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.W700, color = Color.White)
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                )
            }
        }
    }
}
