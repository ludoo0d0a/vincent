package fr.geoking.vincent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.model.WineColor

private fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount)
private fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)

/**
 * A wine bottle rendered with vector drawing: dark capsule neck, glass body with a
 * highlight sheen, and a cream paper label. Resolution-independent — size it with [Modifier].
 */
@Composable
fun WineBottle(
    color: WineColor,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val glass = color.glass
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
            val lw = w * 0.76f
            val lh = bodyH * 0.44f
            val lx = cx - lw / 2f
            val ly = h - lh - bodyH * 0.15f
            drawRoundRect(
                color = Color(0xFFF4EFE3),
                topLeft = Offset(lx, ly),
                size = Size(lw, lh),
                cornerRadius = CornerRadius(2f, 2f),
            )
            drawRoundRect(
                color = glass.darken(0.1f).copy(alpha = 0.5f),
                topLeft = Offset(lx + lw * 0.18f, ly + lh * 0.34f),
                size = Size(lw * 0.64f, lh * 0.10f),
                cornerRadius = CornerRadius(1f, 1f),
            )
        }
    }
}

/** Coloured wine-type pill: a dot + label, tinted by [WineColor]. */
@Composable
fun ColorTag(color: WineColor, modifier: Modifier = Modifier, label: String = color.label) {
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
fun Stars(rating: Double, modifier: Modifier = Modifier, color: Color = Color(0xFFD69A3C)) {
    val full = rating.toInt().coerceIn(0, 5)
    val text = buildString {
        repeat(full) { append('★') }
        repeat(5 - full) { append('☆') }
    }
    Text(text, color = color, fontSize = 11.sp, modifier = modifier)
}

private val ScreenPad = PaddingValues(horizontal = 16.dp)
fun screenPadding(): PaddingValues = ScreenPad
