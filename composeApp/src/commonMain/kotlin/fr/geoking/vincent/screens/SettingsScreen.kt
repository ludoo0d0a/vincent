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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.ai.GemmaModel
import fr.geoking.vincent.ai.GemmaModelState
import fr.geoking.vincent.data.SUPPORTED_LANGUAGES
import fr.geoking.vincent.data.Settings
import fr.geoking.vincent.data.Updater
import fr.geoking.vincent.debug.InternalLog
import fr.geoking.vincent.getAppVersion
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.SectionHeader
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogcat: () -> Unit,
    onOpenDataManagement: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var geminiDraft by remember { mutableStateOf(Settings.geminiApiKey) }
    var hfDraft by remember { mutableStateOf(Settings.huggingFaceToken) }
    var showGeminiKey by remember { mutableStateOf(false) }
    val gemmaState = GemmaModel.state

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(Res.string.settings_insert_demo_data_confirm_title)) },
            text = {
                Text(
                    stringResource(Res.string.settings_insert_demo_data_confirm_message),
                    fontSize = 13.sp,
                    color = VincentColors.Fg,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        scope.launch {
                            fr.geoking.vincent.data.Cellar.seedDemoData()
                            fr.geoking.vincent.data.Racks.seedDemoData()
                            fr.geoking.vincent.data.Settings.setDemoDataSeeded(true)
                            showSuccessDialog = true
                        }
                    },
                ) {
                    Text(stringResource(Res.string.cellar_action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(Res.string.cellar_action_cancel))
                }
            },
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text(stringResource(Res.string.settings_insert_demo_data_success_title)) },
            text = {
                Text(
                    stringResource(Res.string.settings_insert_demo_data_success_message),
                    fontSize = 13.sp,
                    color = VincentColors.Fg,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showSuccessDialog = false },
                ) {
                    Text(stringResource(Res.string.cellar_dismiss))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(Res.string.settings_language_section))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageRow(
                    label = stringResource(Res.string.settings_language_system),
                    selected = Settings.language.isBlank(),
                    onClick = { Settings.setLanguage("") },
                )
                SUPPORTED_LANGUAGES.forEach { lang ->
                    LanguageRow(
                        label = lang.nativeName,
                        selected = Settings.language == lang.tag,
                        onClick = { Settings.setLanguage(lang.tag) },
                    )
                }
            }

            SectionHeader(stringResource(Res.string.settings_section_ai))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(Res.string.settings_gemma_title), fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(stringResource(Res.string.settings_gemma_desc), fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 16.sp)
                val statusLabel = when (val s = gemmaState) {
                    is GemmaModelState.Missing -> stringResource(Res.string.settings_gemma_missing)
                    is GemmaModelState.Ready -> stringResource(Res.string.settings_gemma_ready)
                    is GemmaModelState.Downloading -> stringResource(Res.string.settings_gemma_downloading, (s.progress * 100).toInt())
                    is GemmaModelState.Error -> stringResource(Res.string.settings_gemma_error, s.message)
                }
                Text(
                    statusLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                    color = when (gemmaState) {
                        is GemmaModelState.Ready -> VincentColors.Green
                        is GemmaModelState.Error -> VincentColors.Red
                        else -> VincentColors.Muted
                    },
                )
                if (gemmaState is GemmaModelState.Downloading) {
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(VincentColors.Surface2),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(gemmaState.progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(VincentColors.Accent),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (gemmaState) {
                        is GemmaModelState.Missing, is GemmaModelState.Error -> {
                            SettingsActionChip(stringResource(Res.string.settings_gemma_download)) {
                                scope.launch { GemmaModel.download() }
                            }
                        }
                        is GemmaModelState.Downloading -> {
                            Text(
                                stringResource(Res.string.settings_gemma_downloading, (gemmaState.progress * 100).toInt()),
                                fontSize = 12.sp,
                                color = VincentColors.Muted,
                            )
                        }
                        is GemmaModelState.Ready -> {
                            SettingsActionChip(stringResource(Res.string.settings_gemma_delete), danger = true) {
                                GemmaModel.delete()
                            }
                        }
                    }
                }
                SettingsSecretField(
                    label = stringResource(Res.string.settings_hf_token),
                    placeholder = stringResource(Res.string.settings_hf_token_hint),
                    value = hfDraft,
                    onChange = { hfDraft = it },
                    onSave = { Settings.setHuggingFaceToken(hfDraft) },
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface)
                    .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(Res.string.settings_gemini_title), fontSize = 13.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(stringResource(Res.string.settings_gemini_desc), fontSize = 12.sp, color = VincentColors.Muted, lineHeight = 16.sp)
                SettingsToggle(
                    label = stringResource(Res.string.settings_gemini_fallback),
                    checked = Settings.geminiFallbackEnabled,
                    onCheckedChange = { Settings.setGeminiFallbackEnabled(it) },
                )
                OutlinedTextField(
                    value = geminiDraft,
                    onValueChange = { geminiDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.settings_gemini_key), fontSize = 12.sp) },
                    placeholder = { Text(stringResource(Res.string.settings_gemini_key_hint), fontSize = 12.sp) },
                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VincentColors.Accent,
                        unfocusedBorderColor = VincentColors.Border,
                        focusedTextColor = VincentColors.Fg,
                        unfocusedTextColor = VincentColors.Fg,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionChip(stringResource(Res.string.settings_gemini_key_saved)) {
                        Settings.setGeminiApiKey(geminiDraft)
                        showGeminiKey = false
                    }
                    if (Settings.geminiApiKey.isNotBlank() || geminiDraft.isNotBlank()) {
                        SettingsActionChip(stringResource(Res.string.settings_gemini_key_clear), danger = true) {
                            geminiDraft = ""
                            Settings.setGeminiApiKey("")
                        }
                    }
                    SettingsActionChip(if (showGeminiKey) "•••" else "ABC") {
                        showGeminiKey = !showGeminiKey
                    }
                }
            }

            SectionHeader(stringResource(Res.string.settings_section_app))
            SettingsLink(stringResource(Res.string.settings_data_management), onOpenDataManagement)
            Spacer(Modifier.height(8.dp))
            SettingsLink(stringResource(Res.string.settings_insert_demo_data)) {
                showConfirmDialog = true
            }
            Spacer(Modifier.height(8.dp))
            SettingsLink(stringResource(Res.string.update_check)) { Updater.checkForUpdate(true) }
            Spacer(Modifier.height(8.dp))
            SettingsToggle(
                label = stringResource(Res.string.settings_internal_logs_toggle),
                checked = InternalLog.enabled,
                onCheckedChange = { InternalLog.enabled = it },
            )
            if (InternalLog.enabled) {
                Spacer(Modifier.height(8.dp))
                SettingsLink(stringResource(Res.string.settings_internal_logs_view), onOpenLogcat)
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(getAppVersion(), fontSize = 11.sp, color = VincentColors.Faint, fontWeight = FontWeight.W600)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSecretField(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label, fontSize = 12.sp) },
            placeholder = { Text(placeholder, fontSize = 12.sp) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VincentColors.Accent,
                unfocusedBorderColor = VincentColors.Border,
                focusedTextColor = VincentColors.Fg,
                unfocusedTextColor = VincentColors.Fg,
            ),
        )
        SettingsActionChip(stringResource(Res.string.settings_gemini_key_saved), onClick = onSave)
    }
}

@Composable
private fun SettingsActionChip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (danger) VincentColors.AccentSoft else VincentColors.Surface2)
            .border(1.dp, if (danger) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            color = if (danger) VincentColors.Accent else VincentColors.Fg,
        )
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (selected) VincentColors.AccentSoft else VincentColors.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) VincentColors.Accent else VincentColors.Border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = if (selected) VincentColors.Accent else VincentColors.Fg)
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = VincentColors.Accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VincentColors.Accent,
                uncheckedThumbColor = VincentColors.Faint,
                uncheckedTrackColor = VincentColors.Surface2,
                uncheckedBorderColor = VincentColors.Border,
            ),
        )
    }
}
