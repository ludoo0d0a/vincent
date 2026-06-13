package com.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vincent.theme.VincentColors

/** Top header used on every primary screen: title + optional subtitle + trailing slot. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500,
                    color = VincentColors.Muted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        if (action != null) {
            Text(action, fontSize = 12.sp, fontWeight = FontWeight.W600, color = VincentColors.Accent)
        }
    }
}

/** A bordered white card matching the mockups (hairline border, no heavy shadow). */
@Composable
fun VCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(16.dp)),
    ) { content() }
}

/** Circular avatar with a single initial on the brand gradient. */
@Composable
fun BrandAvatar(initial: String, sizeDp: Int = 34) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(VincentColors.Accent, Color0xFF5C1822)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.W700, fontSize = (sizeDp * 0.42f).sp)
    }
}

private val Color0xFF5C1822 = VincentColors.AccentDeep
