package fr.geoking.vincent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

@Composable
actual fun ProvideAppLocale(content: @Composable () -> Unit) {
    // Settings.setLanguage already updated Locale.getDefault(), which Android's
    // LocaleList.getDefault() (and thus Compose's Locale.current and the resource
    // environment) tracks. Re-keying on the chosen language re-runs the whole
    // subtree so every stringResource() re-resolves against the new locale.
    key(Settings.language) {
        content()
    }
}
