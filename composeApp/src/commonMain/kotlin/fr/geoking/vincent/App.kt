package fr.geoking.vincent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import fr.geoking.vincent.data.Auth
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.screens.AccountScreen
import fr.geoking.vincent.screens.AddScreen
import fr.geoking.vincent.screens.BottleDetailScreen
import fr.geoking.vincent.screens.BottlesScreen
import fr.geoking.vincent.screens.CellarScreen
import fr.geoking.vincent.screens.DashboardScreen
import fr.geoking.vincent.screens.ImportExportScreen
import fr.geoking.vincent.screens.LoginScreen
import fr.geoking.vincent.screens.RecentScreen
import fr.geoking.vincent.screens.SearchScreen
import fr.geoking.vincent.theme.VincentColors
import fr.geoking.vincent.theme.VincentTheme

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Filled.Home),
    CELLAR("Casiers", Icons.Filled.GridView),
    BOTTLES("Bouteilles", Icons.Filled.FormatListBulleted),
    SEARCH("Recherche", Icons.Filled.Search),
}

/** A screen pushed above the tabbed home. Back pops the top of the stack. */
private sealed interface Dest {
    data class Detail(val bottle: Bottle) : Dest
    /** [spot] pre-fills the rack location when adding from an empty cell. */
    data class Add(val spot: String? = null) : Dest
    data object Account : Dest
    data object Recent : Dest
    data object Transfer : Dest
}

@Composable
fun App() = VincentTheme {
    var guest by remember { mutableStateOf(false) }
    val loggedIn = Auth.account != null || guest
    var tab by remember { mutableStateOf(Tab.HOME) }
    // Real navigation stack above the tabbed home; back pops one level.
    val stack = remember { mutableStateListOf<Dest>() }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    // System back button: while screens are stacked, pop instead of exiting.
    NavBackHandler(enabled = loggedIn && stack.isNotEmpty()) { pop() }

    Surface(color = VincentColors.Bg, modifier = Modifier.fillMaxSize()) {
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
                    onAdd = { stack.add(Dest.Add()) },
                    onAddToCell = { stack.add(Dest.Add(it)) },
                    onAccount = { stack.add(Dest.Account) },
                )

                is Dest.Detail -> BottleDetailScreen(
                    bottle = top.bottle,
                    onBack = ::pop,
                )

                is Dest.Add -> AddScreen(onClose = ::pop, initialSpot = top.spot)

                Dest.Account -> AccountScreen(
                    onBack = ::pop,
                    onOpenRecent = { stack.add(Dest.Recent) },
                    onOpenTransfer = { stack.add(Dest.Transfer) },
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
                    onSignOut = { Auth.signOut(); guest = false; stack.clear() },
                )

                Dest.Transfer -> ImportExportScreen(onBack = ::pop)

                Dest.Recent -> RecentScreen(
                    onBack = ::pop,
                    onOpenBottle = { stack.add(Dest.Detail(it)) },
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
    onAdd: () -> Unit,
    onAddToCell: (String) -> Unit,
    onAccount: () -> Unit,
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
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
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
                ) { Icon(Icons.Filled.Add, contentDescription = "Ajouter une bouteille") }
            }
        },
    ) { inner ->
        val content = Modifier.padding(inner)
        when (tab) {
            Tab.HOME -> DashboardScreen(content, onOpenBottle = onOpenBottle, onAccount = onAccount)
            Tab.CELLAR -> CellarScreen(content, onOpenBottle = onOpenBottle, onAddToCell = onAddToCell)
            Tab.BOTTLES -> BottlesScreen(content, onOpenBottle = onOpenBottle)
            Tab.SEARCH -> SearchScreen(content)
        }
    }
}
