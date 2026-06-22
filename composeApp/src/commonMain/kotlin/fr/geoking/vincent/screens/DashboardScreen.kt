package fr.geoking.vincent.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.BrandAvatar
import fr.geoking.vincent.ui.ScreenHeader
import fr.geoking.vincent.ui.SectionHeader
import fr.geoking.vincent.ui.VCard

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenBottle: (fr.geoking.vincent.model.Bottle) -> Unit,
    onAccount: () -> Unit,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = stringResource(Res.string.my_cellar),
            subtitle = stringResource(Res.string.cellar_subtitle),
            trailing = { Box(Modifier.clickable(onClick = onAccount)) { BrandAvatar("L") } },
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            HeroStat()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(stringResource(Res.string.avg_price), "${Cellar.averagePrice()} €", Modifier.weight(1f))
                MiniStat(stringResource(Res.string.this_month), "+${Cellar.addedThisMonth}", Modifier.weight(1f), suffix = stringResource(Res.string.additions))
            }
            SectionHeader(stringResource(Res.string.breakdown_by_color), stringResource(Res.string.details))
            BreakdownCard()
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HeroStat() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(VincentColors.AccentDeep, VincentColors.Accent)))
            .padding(18.dp),
    ) {
        Column {
            Text(stringResource(Res.string.estimated_value_label), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.W600)
            Text("${Cellar.estimatedValue()} €", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.W800)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroFig("${Cellar.totalBottles()}", stringResource(Res.string.bottles_label))
                HeroFig("${Cellar.references()}", stringResource(Res.string.references_label))
                HeroFig("${Cellar.readyToDrink()}", stringResource(Res.string.ready_to_drink))
            }
        }
    }
}

@Composable
private fun HeroFig(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, suffix: String? = null) {
    VCard(modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = VincentColors.Muted, fontSize = 10.5.sp, fontWeight = FontWeight.W600)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = VincentColors.Fg, fontSize = 19.sp, fontWeight = FontWeight.W800)
                if (suffix != null) {
                    Text(" $suffix", color = VincentColors.Green, fontSize = 11.sp, fontWeight = FontWeight.W600, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard() {
    VCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Donut(Cellar.breakdown())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Cellar.breakdown().forEach { b ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(end = 7.dp).size(11.dp).clip(CircleShape).background(b.color.glass))
                            Text(stringResource(b.color.label), fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                        }
                        Text("${b.percent}%", fontSize = 11.5.sp, color = VincentColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(data: List<fr.geoking.vincent.model.ColorBreakdown>) {
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            var start = -90f
            val stroke = 16.dp.toPx()
            val inset = stroke / 2f
            data.forEach { seg ->
                val sweep = seg.percent / 100f * 360f
                drawArc(
                    color = seg.color.glass,
                    startAngle = start,
                    sweepAngle = sweep - 2f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${Cellar.totalBottles()}", fontSize = 17.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
            Text(stringResource(Res.string.btl), fontSize = 9.sp, color = VincentColors.Muted)
        }
    }
}
