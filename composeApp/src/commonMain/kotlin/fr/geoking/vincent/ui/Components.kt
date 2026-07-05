package fr.geoking.vincent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.thumbnailUri

/** Bottle thumbnail: label photo when available, otherwise the vector bottle. */
@Composable
fun BottleThumb(
    bottle: Bottle,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val thumb = bottle.thumbnailUri()
    if (!thumb.isNullOrBlank()) {
        RemoteImage(
            url = thumb,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentDescription = "Étiquette ${bottle.domain}",
            contentScale = contentScale,
        )
    } else {
        // No photo → synthesize a label from the domain + vintage, like the HTML mockup.
        WineBottle(
            color = bottle.color,
            modifier = modifier,
            name = bottle.domain,
            appellation = bottle.appellation,
            vintage = bottle.vintage,
        )
    }
}

private fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount)
private fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)

/**
 * A wine bottle rendered with vector drawing: dark capsule neck, glass body with a
 * highlight sheen, and a cream paper label. Resolution-independent — size it with [Modifier].
 *
 * When [name] is provided and the bottle is drawn large enough, a synthetic label is
 * printed on the paper — domain (serif), appellation (small), vintage (mono) — mirroring
 * the HTML mockup's generated label so bottles without a photo still read as themselves.
 * Below the legibility threshold the label stays a plain crème rectangle with a hint line.
 */
@Composable
fun WineBottle(
    color: WineColor,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    name: String? = null,
    appellation: String? = null,
    vintage: String? = null,
) {
    val glass = color.glass
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val neckW = w * 0.40f
        val neckH = h * 0.24f
        val capH = neckH * 0.52f

        drawRoundRect(
            color = glass.darken(0.18f),
            topLeft = Offset(cx - neckW / 2f, 0f),
            size = Size(neckW, neckH),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        )
        drawRoundRect(
            color = glass.darken(0.42f),
            topLeft = Offset(cx - neckW / 2f, 0f),
            size = Size(neckW, capH),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        )

        val bodyTop = neckH * 0.82f
        val bodyH = h - bodyTop
        drawRoundRect(
            color = glass,
            topLeft = Offset(0f, bodyTop),
            size = Size(w, bodyH),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
        )
        drawRoundRect(
            color = glass.lighten(0.30f),
            topLeft = Offset(w * 0.12f, bodyTop + bodyH * 0.06f),
            size = Size(w * 0.14f, bodyH * 0.80f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        )

        if (showLabel) {
            val trimmedName = name?.trim().orEmpty()
            val trimmedAppellation = appellation?.trim().orEmpty()
            val trimmedVintage = vintage?.trim().orEmpty().takeIf { it.isNotEmpty() && it != "NM" }.orEmpty()
            // Only synthesize text when there's a name and the label is large enough to read.
            val printLabel = trimmedName.isNotEmpty() && w >= 44.dp.toPx()

            val lw = w * 0.76f
            val lh = bodyH * (if (printLabel) 0.50f else 0.44f)
            val lx = cx - lw / 2f
            val ly = h - lh - bodyH * 0.15f
            drawRoundRect(
                color = Color(0xFFF4EFE3),
                topLeft = Offset(lx, ly),
                size = Size(lw, lh),
                cornerRadius = CornerRadius(2f, 2f),
            )

            if (!printLabel) {
                drawRoundRect(
                    color = glass.darken(0.1f).copy(alpha = 0.5f),
                    topLeft = Offset(lx + lw * 0.18f, ly + lh * 0.34f),
                    size = Size(lw * 0.64f, lh * 0.10f),
                    cornerRadius = CornerRadius(1f, 1f),
                )
                return@Canvas
            }

            // --- Synthetic label text (domain / appellation / vintage) ---
            val ink = Color(0xFF3A241C)
            val inkSoft = Color(0xFF7A5444)
            val innerW = lw * 0.90f
            val innerX = cx - innerW / 2f
            val textConstraints = Constraints(maxWidth = innerW.toInt().coerceAtLeast(1))

            val nameLayout = measurer.measure(
                text = trimmedName.uppercase(),
                style = TextStyle(
                    color = ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.W800,
                    fontSize = (w * 0.155f).toSp(),
                    lineHeight = (w * 0.16f).toSp(),
                    letterSpacing = (w * 0.004f).toSp(),
                    textAlign = TextAlign.Center,
                ),
                constraints = textConstraints,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val appLayout = trimmedAppellation.takeIf { it.isNotEmpty() }?.let {
                measurer.measure(
                    text = it,
                    style = TextStyle(
                        color = inkSoft,
                        fontSize = (w * 0.115f).toSp(),
                        lineHeight = (w * 0.13f).toSp(),
                        textAlign = TextAlign.Center,
                    ),
                    constraints = textConstraints,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val vintageLayout = trimmedVintage.takeIf { it.isNotEmpty() }?.let {
                measurer.measure(
                    text = it,
                    style = TextStyle(
                        color = ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.W700,
                        fontSize = (w * 0.13f).toSp(),
                        textAlign = TextAlign.Center,
                    ),
                    constraints = textConstraints,
                    maxLines = 1,
                )
            }

            val gap = lh * 0.06f
            val blockH = nameLayout.size.height +
                (appLayout?.let { gap + it.size.height } ?: 0f) +
                (vintageLayout?.let { gap + it.size.height } ?: 0f)
            var cursorY = ly + (lh - blockH) / 2f
            drawText(nameLayout, topLeft = Offset(innerX, cursorY))
            cursorY += nameLayout.size.height
            appLayout?.let {
                cursorY += gap
                drawText(it, topLeft = Offset(innerX, cursorY))
                cursorY += it.size.height
            }
            vintageLayout?.let {
                cursorY += gap
                drawText(it, topLeft = Offset(innerX, cursorY))
            }
        }
    }
}

/** Coloured wine-type pill: a dot + label, tinted by [WineColor]. */
@Composable
fun ColorTag(color: WineColor, modifier: Modifier = Modifier, label: String = stringResource(color.label)) {
    Surface(
        color = color.tagBg,
        contentColor = color.tagFg,
        shape = RoundedCornerShape(7.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .background(color.glass, CircleShape),
            )
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.W700)
        }
    }
}

/** Simple star rating glyph row, e.g. "★★★★☆". */
@Composable
fun Stars(
    rating: Double,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFD69A3C),
    onClick: (() -> Unit)? = null,
) {
    val full = rating.toInt().coerceIn(0, 5)
    val text = buildString {
        repeat(full) { append('★') }
        repeat(5 - full) { append('☆') }
    }
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/** Coloured drink-window bar with a marker for the current position. */
@Composable
fun DrinkPeakBar(
    from: Int,
    to: Int,
    now: Float,
    modifier: Modifier = Modifier,
) {
    if (to <= 0) return
    val best = (from + to) / 2
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Meilleure année (milieu de la fenêtre) mise en avant en vert.
        Text("$best", style = MonoNumber, fontSize = 12.sp, fontWeight = FontWeight.W800, color = VincentColors.Green)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("$from", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(VincentColors.Amber, VincentColors.Green, VincentColors.Amber))),
                )
                Row(Modifier.fillMaxWidth()) {
                    val w = now.coerceIn(0.02f, 0.95f)
                    Spacer(Modifier.weight(w))
                    Box(
                        Modifier.size(13.dp).clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .border(3.dp, VincentColors.Green, RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.weight(1f - w))
                }
            }
            Text("$to", style = MonoNumber, fontSize = 10.sp, color = VincentColors.Muted)
        }
    }
}

private val ScreenPad = PaddingValues(horizontal = 16.dp)
fun screenPadding(): PaddingValues = ScreenPad
