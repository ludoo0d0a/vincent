package fr.geoking.vincent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
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
import fr.geoking.vincent.screens.FavoritesScreen
import fr.geoking.vincent.screens.ImportExportScreen
import fr.geoking.vincent.screens.LogcatScreen
import fr.geoking.vincent.screens.LoginScreen
import fr.geoking.vincent.screens.RecentScreen
import fr.geoking.vincent.screens.SearchScreen
import fr.geoking.vincent.screens.SettingsScreen
import fr.geoking.vincent.screens.TastingsScreen
import fr.geoking.vincent.screens.ProducersScreen
import fr.geoking.vincent.screens.SuppliersScreen
import fr.geoking.vincent.debug.HttpDebugBar
import fr.geoking.vincent.debug.initHttpDebug
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.theme.VincentTheme

enum class Tab(val label: org.jetbrains.compose.resources.StringResource, val icon: ImageVector) {
    HOME(Res.string.tab_home, Icons.Filled.Home),
    CELLAR(Res.string.tab_cellar, Icons.Filled.GridView),
    BOTTLES(Res.string.tab_bottles, Icons.Filled.FormatListBulleted),
    SEARCH(Res.string.tab_search, Icons.Filled.Search),
}

/** A screen pushed above the tabbed home. Back pops the top of the stack. */
private sealed interface Dest {
    data class Detail(val bottle: Bottle) : Dest
    /** [placement] pre-fills the rack cell when adding from the cellar grid. */
    data class Add(val placement: RackPlacement? = null) : Dest
    data object Account : Dest
    data object Settings : Dest
    data object Recent : Dest
    data object Favorites : Dest
    data object Transfer : Dest
    data object Tastings : Dest
    data object Producers : Dest
    data object Suppliers : Dest
    data object Logcat : Dest
    /** AR view of the rack at [rackIndex]. */
    data class Ar(val rackIndex: Int) : Dest
}

@Composable
fun App() = VincentTheme {
    SideEffect { initHttpDebug() }
    var guest by remember { mutableStateOf(false) }
    val loggedIn = Auth.account != null || guest
    var tab by remember { mutableStateOf(Tab.HOME) }
    // Real navigation stack above the tabbed home; back pops one level.
    val stack = remember { mutableStateListOf<Dest>() }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    var googleLoading by remember { mutableStateOf(false) }
    val onSignIn = rememberGoogleSignIn(
        onLoading = { googleLoading = it },
        onError = { InternalLog.e("App", "Google Sign-in error: $it") },
        onResult = { if (it != null) { Auth.account = it; guest = false } }
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
                    onTab = { tab = it },
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                    onOpenRecent = { stack.add(Dest.Recent) },
                    onOpenFavorites = { stack.add(Dest.Favorites) },
                    onAdd = { stack.add(Dest.Add()) },
                    onAddToCell = { stack.add(Dest.Add(it)) },
                    onAccount = { stack.add(Dest.Account) },
                    onOpenAr = { stack.add(Dest.Ar(it)) },
                )

                is Dest.Detail -> BottleDetailScreen(
                    bottle = top.bottle,
                    onBack = ::pop,
                )

                is Dest.Add -> AddScreen(onClose = ::pop, initialPlacement = top.placement)

                Dest.Account -> AccountScreen(
                    onBack = ::pop,
                    onSignIn = onSignIn,
                    isLoading = googleLoading,
                    onOpenRecent = { stack.add(Dest.Recent) },
                    onOpenFavorites = { stack.add(Dest.Favorites) },
                    onOpenTransfer = { stack.add(Dest.Transfer) },
                    onOpenTastings = { stack.add(Dest.Tastings) },
                    onOpenProducers = { stack.add(Dest.Producers) },
                    onOpenSuppliers = { stack.add(Dest.Suppliers) },
                    onOpenSettings = { stack.add(Dest.Settings) },
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                    onSignOut = { Auth.signOut(); guest = false; stack.clear() },
                )

                Dest.Settings -> SettingsScreen(
                    onBack = ::pop,
                    onOpenLogcat = { stack.add(Dest.Logcat) },
                )

                Dest.Transfer -> ImportExportScreen(onBack = ::pop)

                Dest.Tastings -> TastingsScreen(onBack = ::pop)
                Dest.Producers -> ProducersScreen(onBack = ::pop)
                Dest.Suppliers -> SuppliersScreen(onBack = ::pop)
                Dest.Logcat -> LogcatScreen(onBack = ::pop)

                is Dest.Ar -> ArScreen(rackIndex = top.rackIndex, onBack = ::pop)

                Dest.Recent -> RecentScreen(
                    onBack = ::pop,
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                )

                Dest.Favorites -> FavoritesScreen(
                    onBack = ::pop,
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                )
            }
            HttpDebugBar(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 96.dp),
            )
        }
      }
    }
}

@Composable
private fun MainScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onOpenBottle: (Bottle) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenFavorites: () -> Unit,
    onAdd: () -> Unit,
    onAddToCell: (RackPlacement) -> Unit,
    onAccount: () -> Unit,
    onOpenAr: (Int) -> Unit,
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
            if (tab == Tab.HOME || tab == Tab.BOTTLES) {
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
            Tab.CELLAR -> CellarScreen(content, onOpenBottle = onOpenBottle, onAddToCell = onAddToCell, onOpenAr = onOpenAr)
            Tab.BOTTLES -> BottlesScreen(content, onOpenBottle = onOpenBottle, onOpenFavorites = onOpenFavorites)
            Tab.SEARCH -> SearchScreen(content)
        }
    }
}
