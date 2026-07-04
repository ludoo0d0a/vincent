package fr.geoking.vincent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.*
import fr.geoking.vincent.data.Auth
import fr.geoking.vincent.data.ProvideAppLocale
import fr.geoking.vincent.data.UpdateState
import fr.geoking.vincent.data.rememberGoogleSignIn
import fr.geoking.vincent.debug.InternalLog
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.RackPlacement
import fr.geoking.vincent.screens.AccountScreen
import fr.geoking.vincent.screens.AddScreen
import fr.geoking.vincent.screens.ArScreen
import fr.geoking.vincent.screens.BottleDetailScreen
import fr.geoking.vincent.screens.BottlesScreen
import fr.geoking.vincent.screens.CellarScreen
import fr.geoking.vincent.screens.DashboardScreen
import fr.geoking.vincent.screens.WinesManagementScreen
import fr.geoking.vincent.screens.RacksManagementScreen
import fr.geoking.vincent.screens.LogcatScreen
import fr.geoking.vincent.screens.LoginScreen
import fr.geoking.vincent.screens.RecentScreen
import fr.geoking.vincent.screens.SettingsScreen
import fr.geoking.vincent.screens.DataManagementScreen
import fr.geoking.vincent.screens.RegionsManagementScreen
import fr.geoking.vincent.screens.TastingEditScreen
import fr.geoking.vincent.screens.TastingsScreen
import fr.geoking.vincent.screens.ProducersScreen
import fr.geoking.vincent.screens.RackEditScreen
import fr.geoking.vincent.screens.SuppliersScreen
import fr.geoking.vincent.debug.HttpDebugBar
import fr.geoking.vincent.debug.initHttpDebug
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.theme.VincentTheme

enum class Tab(val label: org.jetbrains.compose.resources.StringResource, val icon: ImageVector) {
    HOME(Res.string.tab_home, Icons.Filled.Home),
    CELLAR(Res.string.tab_cellar, Icons.Filled.GridView),
    BOTTLES(Res.string.tab_bottles, Icons.Filled.FormatListBulleted),
}

/** A screen pushed above the tabbed home. Back pops the top of the stack. */
private sealed interface Dest {
    data class Detail(val bottle: Bottle) : Dest
    /** [placement] pre-fills the rack cell when adding from the cellar grid. */
    data class Add(val placement: RackPlacement? = null) : Dest
    data class Edit(val bottle: Bottle) : Dest
    data object Account : Dest
    data object Settings : Dest
    data object DataManagement : Dest
    data object Recent : Dest
    data object WinesManagement : Dest
    data object RacksManagement : Dest
    data object RegionsManagement : Dest
    data object Tastings : Dest
    data object Producers : Dest
    data object Suppliers : Dest
    data object Logcat : Dest
    /** AR view of the rack at [rackIndex]. */
    data class Ar(val rackIndex: Int) : Dest
    /** Edit the rack at [rackIndex]. */
    data class RackEdit(val rackIndex: Int) : Dest
    data class TastingEdit(val bottle: Bottle, val tastingId: String? = null) : Dest
    data class Placement(val bottle: Bottle) : Dest
}

@Composable
fun App() = VincentTheme {
    SideEffect { initHttpDebug() }
    var guest by remember { mutableStateOf(false) }
    val loggedIn = Auth.account != null || guest
    var tab by remember { mutableStateOf(Tab.HOME) }
    // When opening the Bottles tab from the account favourites card, pre-select the
    // favourites chip; a direct tab tap always shows every bottle.
    var bottlesFavOnly by remember { mutableStateOf(false) }
    var bottlesFiltersVisible by remember { mutableStateOf(false) }
    var cellarRackIdx by remember { mutableStateOf(0) }
    // Real navigation stack above the tabbed home; back pops one level.
    val stack = remember { mutableStateListOf<Dest>() }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val onSignIn = rememberGoogleSignIn(
        onLoading = { googleLoading = it; if (it) googleError = null },
        onError = { googleError = it; InternalLog.e("App", "Google Sign-in error: $it") },
        onResult = { if (it != null) { Auth.account = it; guest = false; googleError = null } }
    )

    // System back button: while screens are stacked, pop instead of exiting.
    NavBackHandler(enabled = loggedIn && stack.isNotEmpty()) { pop() }

    Surface(color = VincentColors.Bg, modifier = Modifier.fillMaxSize()) {
      // Applies the user's chosen language to all string resources below.
      ProvideAppLocale {
        // No fullscreen: keep all content within the system bars so the top
        // back button and the bottom navigation are never hidden behind them.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            if (!loggedIn) {
                LoginScreen(onGuest = { guest = true })
            } else when (val top = stack.lastOrNull()) {
                null -> MainScaffold(
                    tab = tab,
                    onTab = { t ->
                        if (t == Tab.BOTTLES) bottlesFavOnly = false
                        tab = t
                    },
                    bottlesFavOnly = bottlesFavOnly,
                    bottlesFiltersVisible = bottlesFiltersVisible,
                    onBottlesFiltersVisible = { bottlesFiltersVisible = it },
                    cellarRackIdx = cellarRackIdx,
                    onCellarRackIdxChange = { cellarRackIdx = it },
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                    onEditBottle = { stack.add(Dest.Edit(it)) },
                    onOpenRecent = { stack.add(Dest.Recent) },
                    onAdd = { stack.add(Dest.Add()) },
                    onAddToCell = { stack.add(Dest.Add(it)) },
                    onAccount = { stack.add(Dest.Account) },
                    onOpenDataManagement = { stack.add(Dest.DataManagement) },
                    onOpenAr = { stack.add(Dest.Ar(it)) },
                    onEditRack = { stack.add(Dest.RackEdit(it)) },
                )

                is Dest.Detail -> BottleDetailScreen(
                    bottle = top.bottle,
                    onBack = { stack.clear() },
                    onEdit = { stack.add(Dest.Edit(it)) },
                    onTasting = { bottle, tastingId -> stack.add(Dest.TastingEdit(bottle, tastingId)) },
                    onMove = { stack.add(Dest.Placement(it)) },
                )

                is Dest.Add -> AddScreen(onClose = { stack.clear() }, initialPlacement = top.placement)

                is Dest.Edit -> AddScreen(onClose = { stack.clear() }, editingBottle = top.bottle)

                Dest.Account -> AccountScreen(
                    onBack = { stack.clear() },
                    onSignIn = onSignIn,
                    isLoading = googleLoading,
                    errorMsg = googleError,
                    onOpenRecent = { stack.add(Dest.Recent) },
                    onOpenBottles = { bottlesFavOnly = false; tab = Tab.BOTTLES; stack.clear() },
                    onOpenFavorites = { bottlesFavOnly = true; tab = Tab.BOTTLES; stack.clear() },
                    onOpenDataManagement = { stack.add(Dest.DataManagement) },
                    onOpenSettings = { stack.add(Dest.Settings) },
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                    onSignOut = { Auth.signOut(); guest = false; stack.clear() },
                )

                Dest.Settings -> SettingsScreen(
                    onBack = { stack.clear() },
                    onOpenLogcat = { stack.add(Dest.Logcat) },
                    onOpenDataManagement = { stack.add(Dest.DataManagement) },
                )

                Dest.DataManagement -> DataManagementScreen(
                    onBack = ::pop,
                    onOpenWines = { stack.add(Dest.WinesManagement) },
                    onOpenRacks = { stack.add(Dest.RacksManagement) },
                    onOpenTastings = { stack.add(Dest.Tastings) },
                    onOpenProducers = { stack.add(Dest.Producers) },
                    onOpenSuppliers = { stack.add(Dest.Suppliers) },
                    onOpenRegions = { stack.add(Dest.RegionsManagement) },
                )

                Dest.WinesManagement -> WinesManagementScreen(onBack = ::pop)
                Dest.RacksManagement -> RacksManagementScreen(onBack = ::pop)
                Dest.RegionsManagement -> RegionsManagementScreen(onBack = ::pop)

                Dest.Tastings -> TastingsScreen(onBack = ::pop)
                Dest.Producers -> ProducersScreen(onBack = ::pop)
                Dest.Suppliers -> SuppliersScreen(onBack = ::pop)
                Dest.Logcat -> LogcatScreen(onBack = { stack.clear() })

                is Dest.Ar -> ArScreen(rackIndex = top.rackIndex, onBack = { stack.clear() })

                is Dest.RackEdit -> RackEditScreen(
                    rackIndex = top.rackIndex,
                    onBack = { stack.clear() },
                    onSwitchedToRack = { index ->
                        cellarRackIdx = index
                        tab = Tab.CELLAR
                    }
                )

                is Dest.TastingEdit -> TastingEditScreen(
                    bottle = top.bottle,
                    tastingId = top.tastingId,
                    onClose = { stack.removeAt(stack.lastIndex) }
                )

                is Dest.Placement -> AddScreen(
                    onClose = { stack.removeAt(stack.lastIndex) },
                    editingBottle = top.bottle
                )

                Dest.Recent -> RecentScreen(
                    onBack = { stack.clear() },
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                )
            }
            HttpDebugBar(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 96.dp),
            )
            // Non-blocking overlay shown while a flexible update downloads.
            if (UpdateState.downloading) {
                UpdateDownloadBanner(
                    progress = UpdateState.progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                )
            }
        }
      }
    }
}

/**
 * Compact, non-blocking banner shown while a flexible in-app update downloads.
 * It floats above content and never intercepts touches, so the app stays usable.
 */
@Composable
private fun UpdateDownloadBanner(progress: Float?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = VincentColors.Surface,
        contentColor = VincentColors.Fg,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(22.dp),
                    color = VincentColors.Accent,
                    trackColor = VincentColors.AccentSoft,
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = VincentColors.Accent,
                    trackColor = VincentColors.AccentSoft,
                    strokeWidth = 2.5.dp,
                )
            }
            Text(
                text = stringResource(Res.string.update_downloading),
                style = MaterialTheme.typography.bodyMedium,
                color = VincentColors.Muted,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    bottlesFavOnly: Boolean,
    bottlesFiltersVisible: Boolean,
    onBottlesFiltersVisible: (Boolean) -> Unit,
    cellarRackIdx: Int,
    onCellarRackIdxChange: (Int) -> Unit,
    onOpenBottle: (Bottle) -> Unit,
    onEditBottle: (Bottle) -> Unit,
    onOpenRecent: () -> Unit,
    onAdd: () -> Unit,
    onAddToCell: (RackPlacement) -> Unit,
    onAccount: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenAr: (Int) -> Unit,
    onEditRack: (Int) -> Unit,
) {
    Scaffold(
        containerColor = VincentColors.Bg,
        // Root already applies system-bar insets; don't add them again here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = VincentColors.Surface,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        icon = { Icon(t.icon, contentDescription = stringResource(t.label)) },
                        label = { Text(stringResource(t.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VincentColors.Accent,
                            selectedTextColor = VincentColors.Accent,
                            indicatorColor = VincentColors.AccentSoft,
                            unselectedIconColor = VincentColors.Faint,
                            unselectedTextColor = VincentColors.Faint,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME || (tab == Tab.BOTTLES && !bottlesFiltersVisible)) {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = VincentColors.Accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add_bottle)) }
            }
        },
    ) { inner ->
        val content = Modifier.padding(inner)
        when (tab) {
            Tab.HOME -> DashboardScreen(content, onOpenBottle = onOpenBottle, onOpenRecent = onOpenRecent, onAccount = onAccount)
            Tab.CELLAR -> CellarScreen(
                content,
                rackIdx = cellarRackIdx,
                onRackIdxChange = onCellarRackIdxChange,
                onOpenBottle = onOpenBottle,
                onEditBottle = onEditBottle,
                onAddToCell = onAddToCell,
                onOpenAr = onOpenAr,
                onEditRack = onEditRack,
                onOpenDataManagement = onOpenDataManagement
            )
            Tab.BOTTLES -> BottlesScreen(
                content,
                onOpenBottle = onOpenBottle,
                initialFavoritesOnly = bottlesFavOnly,
                onOpenDataManagement = onOpenDataManagement,
                onFiltersVisible = onBottlesFiltersVisible,
            )
        }
    }
}
