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
import org.jetbrains.compose.resources.pluralStringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.FeatureFlags
import fr.geoking.vincent.data.Auth
import fr.geoking.vincent.data.rememberGoogleSignIn
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.versionInfo
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.ui.SectionHeader
import fr.geoking.vincent.ui.VCard

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenTastings: () -> Unit = {},
    onOpenProducers: () -> Unit = {},
    onOpenSuppliers: () -> Unit = {},
    onOpenBottle: (Bottle) -> Unit,
    onSignOut: () -> Unit,
) {
    val acc = Auth.account
    val signIn = rememberGoogleSignIn { account -> if (account != null) Auth.account = account }

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
                    .then(if (acc == null) Modifier.clickable(onClick = signIn) else Modifier)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(acc?.initial ?: "?", color = Color.White, fontWeight = FontWeight.W800, fontSize = 18.sp)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(acc?.name ?: stringResource(Res.string.guest), color = Color.White, fontWeight = FontWeight.W700, fontSize = 15.sp)
                    Text(acc?.email ?: stringResource(Res.string.connected_no_account), color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
                }
                GoogleG(22)
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
                // Cloud sync not wired — show honest local stats instead.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(Res.string.bottles_label).replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                            Text("${Cellar.totalBottles()}", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                            Text(stringResource(Res.string.local_data), fontSize = 10.5.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                        }
                    }
                    VCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(Res.string.references), fontSize = 10.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                            Text("${Cellar.references()}", fontSize = 14.sp, fontWeight = FontWeight.W800, color = VincentColors.Fg, modifier = Modifier.padding(top = 5.dp))
                            Text(stringResource(Res.string.distinct_wines), fontSize = 10.5.sp, color = VincentColors.Muted, fontWeight = FontWeight.W600)
                        }
                    }
                }
            }

            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(VincentColors.Surface).border(1.dp, VincentColors.Border, RoundedCornerShape(13.dp)).clickable(onClick = onOpenTransfer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.export_csv_label), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.W600, color = VincentColors.Fg)
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = VincentColors.Faint, modifier = Modifier.size(13.dp))
            }

            SectionHeader(stringResource(Res.string.ploc_data_title))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountLink(stringResource(Res.string.ploc_tastings), onOpenTastings)
                AccountLink(stringResource(Res.string.ploc_producers), onOpenProducers)
                AccountLink(stringResource(Res.string.ploc_suppliers), onOpenSuppliers)
            }

            SectionHeader(stringResource(Res.string.my_favorites), pluralStringResource(Res.plurals.vines_count, Cellar.favorites.size, Cellar.favorites.size)) {
                onOpenFavorites()
            }
            AccountLink(stringResource(Res.string.my_favorites), onOpenFavorites)

            if (acc != null) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(
                        stringResource(Res.string.sign_out),
                        fontWeight = FontWeight.W700,
                        color = VincentColors.Accent
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                versionInfo,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 11.sp,
                color = VincentColors.Faint,
                fontWeight = FontWeight.W600,
            )
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
