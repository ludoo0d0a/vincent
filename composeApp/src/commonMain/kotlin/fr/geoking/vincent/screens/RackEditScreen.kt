package fr.geoking.vincent.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.model.RackFormat
import fr.geoking.vincent.theme.MonoNumber
import fr.geoking.vincent.theme.VincentColors

@Composable
fun RackEditScreen(
    rackIndex: Int,
    onBack: () -> Unit,
    onSwitchedToRack: (Int) -> Unit = {},
) {
    val rack = Racks.all.getOrNull(rackIndex) ?: run {
        onBack()
        return
    }

    var name by remember { mutableStateOf(rack.name) }
    var cols by remember { mutableIntStateOf(rack.cols) }
    var rows by remember { mutableIntStateOf(rack.rows) }
    var staggered by remember { mutableStateOf(rack.staggered) }
    var staggerOffset by remember { mutableStateOf(rack.staggerOffset) }
    var format by remember { mutableStateOf(rack.format) }
    val canDelete = Racks.all.size > 1

    Column(
        Modifier
            .fillMaxSize()
            .background(VincentColors.Bg)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VincentColors.Surface2)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    modifier = Modifier.size(18.dp),
                    tint = VincentColors.Fg
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(Res.string.cellar_edit_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W800,
                color = VincentColors.Fg
            )
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.cellar_edit_name), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Layout format picker.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.cellar_edit_format),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = VincentColors.Fg,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatPill(
                        label = stringResource(Res.string.cellar_edit_format_grid),
                        selected = format == RackFormat.GRID,
                        onClick = { format = RackFormat.GRID },
                        modifier = Modifier.weight(1f),
                    )
                    FormatPill(
                        label = stringResource(Res.string.cellar_edit_format_x),
                        selected = format == RackFormat.X,
                        onClick = {
                            // X bins group 2×2 cells, so dimensions must be even.
                            if (cols % 2 != 0) cols = (cols + 1).coerceAtMost(10)
                            if (rows % 2 != 0) rows = (rows + 1).coerceAtMost(12)
                            cols = cols.coerceAtLeast(2)
                            rows = rows.coerceAtLeast(2)
                            format = RackFormat.X
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (format == RackFormat.X) {
                Stepper(stringResource(Res.string.cellar_edit_square_cols), cols / 2, 1..5) { cols = it * 2 }
                Stepper(stringResource(Res.string.cellar_edit_square_rows), rows / 2, 1..6) { rows = it * 2 }
            } else {
                Stepper(stringResource(Res.string.cellar_edit_cols), cols, 1..10) { cols = it }
                Stepper(stringResource(Res.string.cellar_edit_rows), rows, 1..12) { rows = it }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.cellar_edit_staggered),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                            color = VincentColors.Fg
                        )
                        Text(
                            stringResource(Res.string.cellar_edit_staggered_desc),
                            fontSize = 10.5.sp,
                            color = VincentColors.Muted
                        )
                    }
                    Switch(checked = staggered, onCheckedChange = { staggered = it })
                }

                if (staggered) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.cellar_edit_stagger_offset),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = VincentColors.Fg
                            )
                            Text(
                                stringResource(Res.string.cellar_edit_stagger_offset_desc),
                                fontSize = 10.5.sp,
                                color = VincentColors.Muted
                            )
                        }
                        Switch(checked = staggerOffset, onCheckedChange = { staggerOffset = it })
                    }
                }
            }

            Text(
                if (format == RackFormat.X) {
                    stringResource(Res.string.cellar_edit_capacity_x, (cols / 2) * (rows / 2), cols * rows)
                } else {
                    stringResource(Res.string.cellar_edit_capacity, cols * rows)
                },
                fontSize = 11.sp,
                color = VincentColors.Muted
            )

            Spacer(Modifier.height(8.dp))

            // Manage: clone or delete this rack.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val newIdx = Racks.duplicate(rackIndex)
                        onSwitchedToRack(newIdx)
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.cellar_edit_duplicate))
                }
                OutlinedButton(
                    onClick = {
                        Racks.remove(rackIndex)
                        val nextIdx = rackIndex.coerceAtMost(Racks.all.lastIndex)
                        onSwitchedToRack(nextIdx)
                        onBack()
                    },
                    enabled = canDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VincentColors.Red),
                ) {
                    Text(stringResource(Res.string.cellar_edit_delete))
                }
            }

            Button(
                onClick = {
                    Racks.update(
                        rackIndex,
                        rack.resized(cols, rows, staggered, format, staggerOffset)
                            .copy(name = name.ifBlank { rack.name }),
                    )
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent, contentColor = Color.White),
            ) {
                Text(stringResource(Res.string.cellar_edit_save), fontWeight = FontWeight.W700)
            }
        }
    }
}

@Composable
private fun FormatPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) VincentColors.Accent else VincentColors.Surface2)
            .border(
                1.dp,
                if (selected) VincentColors.Accent else VincentColors.Border,
                RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.W700,
            color = if (selected) Color.White else VincentColors.Muted,
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
        StepBtn("−") { if (value > range.first) onChange(value - 1) }
        Text("$value", Modifier.width(36.dp), style = MonoNumber, color = VincentColors.Fg, textAlign = TextAlign.Center)
        StepBtn("+") { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(VincentColors.Surface2)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 17.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
    }
}
