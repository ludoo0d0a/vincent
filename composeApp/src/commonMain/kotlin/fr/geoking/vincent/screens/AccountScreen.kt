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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.FeatureFlags
import fr.geoking.vincent.data.Auth
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.getAppVersion
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.SectionHeader
import fr.geoking.vincent.ui.VCard

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    isLoading: Boolean = false,
    errorMsg: String? = null,
    onOpenRecent: () -> Unit,
    onOpenBottles: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenBottle: (Bottle) -> Unit,
    onSignOut: () -> Unit,
) {
    val acc = Auth.account
    Column(Modifier.fillMaxSize().background(VincentColors.Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(VincentColors.Surface2).border(1.dp, VincentColors.Border, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back), modifier = Modifier.size(18.dp), tint = VincentColors.Fg) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(Res.string.my_account), fontSize = 20.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg)
                Text(
                    if (FeatureFlags.CLOUD_SYNC) stringResource(Res.string.sync_google)
                    else if (acc != null) stringResource(Res.string.connected_google) else stringResource(Res.string.local_data),
                    fontSize = 11.5.sp, color = VincentColors.Muted,
                )
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            // account card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(VincentColors.AccentDeep, VincentColors.Accent)))
                    .clickable(enabled = acc == null && !isLoading, onClick = onSignIn)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(acc?.initial ?: "?", color = Color.White, fontWeight = FontWeight.W800, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(acc?.name ?: stringResource(Res.string.guest), color = Color.White, fontWeight = FontWeight.W700, fontSize = 15.sp)
                    Text(acc?.email ?: stringResource(Res.string.connected_no_account), color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
                }
                GoogleG(22)
            }

            if (acc == null && errorMsg != null) {
                Text(
                    errorMsg,
                    color = VincentColors.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }

            Spacer(Modifier.height(11.dp))
            if (FeatureFlags.CLOUD_SYNC) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = VincentColors.Green, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(Res.string.backup), fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                            }
                            Text(stringResource(Res.string.up_to_date), fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                            Text(stringResource(Res.string.two_min_ago), fontSize = 10.5.sp, color = VincentColors.Green, fontWeight = FontWeight.W700)
                        }
                    }
                    VCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(Res.string.cloud_storage), fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                            Text("${Cellar.totalBottles()} / 500", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(VincentColors.Border)) {
                                Box(Modifier.fillMaxWidth((Cellar.totalBottles() / 500f).coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(4.dp)).background(VincentColors.Accent))
                            }
                        }
                    }
                }
            } else {
                // Local stats double as quick links into the bottle list.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VCard(Modifier.weight(1f).clickable(onClick = onOpenBottles)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.bottles_label).replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(11.dp))
                            }
                            Text("${Cellar.totalBottles()}", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                            Text(stringResource(Res.string.local_data), fontSize = 10.5.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                        }
                    }
                    VCard(Modifier.weight(1f).clickable(onClick = onOpenFavorites)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.bottles_filter_favorites), fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(11.dp))
                            }
                            Text("${Cellar.favorites.size}", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Accent, modifier = Modifier.padding(top = 5.dp))
                            Text(stringResource(Res.string.favorites_card_sub), fontSize = 10.5.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                        }
                    }
                }
            }

            // recent link
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable(onClick = onOpenRecent).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.last_added), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(13.dp))
            }

            SectionHeader(stringResource(Res.string.settings_section_app))
            AccountLink(stringResource(Res.string.settings_data_management), onOpenDataManagement)
            Spacer(Modifier.height(8.dp))
            AccountLink(stringResource(Res.string.settings_title), onOpenSettings)

            Spacer(Modifier.height(16.dp))
            if (acc != null) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                ) { Text(stringResource(Res.string.sign_out), fontWeight = FontWeight.W700, color = VincentColors.Accent) }
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(getAppVersion(), fontSize = 11.sp, color = VincentColors.Faint, fontWeight = FontWeight.W600)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccountLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(13.dp))
    }
}
