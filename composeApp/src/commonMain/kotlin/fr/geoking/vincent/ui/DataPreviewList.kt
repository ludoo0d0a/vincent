package fr.geoking.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.theme.VincentColors
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.data_preview_empty
import vincent.composeapp.generated.resources.data_preview_next
import vincent.composeapp.generated.resources.data_preview_page
import vincent.composeapp.generated.resources.data_preview_prev
import vincent.composeapp.generated.resources.data_preview_title

private const val DefaultPageSize = 20

/**
 * Lightweight paginated preview of stored rows (20 per page).
 * Renders only the current page so large referentials stay cheap inside a scroll column.
 */
@Composable
fun <T> DataPreviewList(
    items: List<T>,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    secondary: ((T) -> String?)? = null,
    pageSize: Int = DefaultPageSize,
) {
    var page by remember { mutableIntStateOf(0) }
    val total = items.size
    val pageCount = if (total == 0) 1 else ((total + pageSize - 1) / pageSize)
    val safePage = page.coerceIn(0, pageCount - 1)
    val from = safePage * pageSize
    val to = (from + pageSize).coerceAtMost(total)

    Column(modifier.fillMaxWidth()) {
        SectionHeader(stringResource(Res.string.data_preview_title))
        VCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (total == 0) {
                    Text(
                        stringResource(Res.string.data_preview_empty),
                        fontSize = 12.5.sp,
                        color = VincentColors.Muted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    for (i in from until to) {
                        val item = items[i]
                        val sub = secondary?.invoke(item)?.takeIf { it.isNotBlank() }
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                label(item),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = VincentColors.Fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (sub != null) {
                                Text(
                                    sub,
                                    fontSize = 11.sp,
                                    color = VincentColors.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (i < to - 1) {
                            Spacer(Modifier.fillMaxWidth().height(1.dp).background(VincentColors.Border))
                        }
                    }
                }

                if (total > pageSize) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { page = (safePage - 1).coerceAtLeast(0) },
                            enabled = safePage > 0,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(Res.string.data_preview_prev), fontSize = 12.sp)
                        }
                        Text(
                            stringResource(Res.string.data_preview_page, safePage + 1, pageCount),
                            fontSize = 11.5.sp,
                            color = VincentColors.Muted,
                        )
                        TextButton(
                            onClick = { page = (safePage + 1).coerceAtMost(pageCount - 1) },
                            enabled = safePage < pageCount - 1,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(Res.string.data_preview_next), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
