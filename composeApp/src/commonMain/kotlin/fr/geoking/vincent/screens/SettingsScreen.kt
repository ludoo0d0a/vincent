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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.SUPPORTED_LANGUAGES
import fr.geoking.vincent.data.Settings
import fr.geoking.vincent.data.Updater
import fr.geoking.vincent.data.XWinesData
import fr.geoking.vincent.debug.InternalLog
import kotlinx.coroutines.launch
import fr.geoking.vincent.getAppVersion
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.SectionHeader

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogcat: () -> Unit,
) {
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

            SectionHeader(stringResource(Res.string.settings_section_app))
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

            SectionHeader(stringResource(Res.string.settings_section_data_sources))
            XWinesRow()

            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(getAppVersion(), fontSize = 11.sp, color = VincentColors.Faint, fontWeight = FontWeight.W600)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun XWinesRow() {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val notConfigured = stringResource(Res.string.settings_xwines_not_configured)
    val genericError = stringResource(Res.string.settings_xwines_error)
    val downloading = XWinesData.isDownloading
    val count = XWinesData.count
    val updatedLabel = XWinesData.updatedAtLabel

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.settings_xwines_title), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
            val status = when {
                error != null -> error!!
                updatedLabel.isBlank() -> stringResource(Res.string.settings_xwines_never)
                else -> stringResource(Res.string.settings_xwines_count, count) +
                    "  ·  " + stringResource(Res.string.settings_xwines_updated, updatedLabel)
            }
            Spacer(Modifier.height(2.dp))
            Text(status, fontSize = 11.sp, fontWeight = FontWeight.W500, color = if (error != null) VincentColors.Accent else VincentColors.Faint)
        }
        Spacer(Modifier.width(12.dp))
        val actionLabel = when {
            downloading -> stringResource(Res.string.settings_xwines_downloading)
            updatedLabel.isBlank() -> stringResource(Res.string.settings_xwines_download)
            else -> stringResource(Res.string.settings_xwines_update)
        }
        Box(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (downloading) VincentColors.Surface2 else VincentColors.AccentSoft)
                .border(1.dp, if (downloading) VincentColors.Border else VincentColors.Accent, RoundedCornerShape(10.dp))
                .clickable(enabled = !downloading) {
                    error = null
                    scope.launch {
                        val result = XWinesData.update()
                        result.exceptionOrNull()?.let { e ->
                            error = if (e.message?.contains("not configured") == true) notConfigured else genericError
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (downloading) {
                CircularProgressIndicator(Modifier.size(15.dp), color = VincentColors.Accent, strokeWidth = 2.dp)
            } else {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.W700, color = VincentColors.Accent)
            }
        }
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
