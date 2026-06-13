package com.vincent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vincent.model.Bottle
import com.vincent.screens.AccountScreen
import com.vincent.screens.AddScreen
import com.vincent.screens.BottleDetailScreen
import com.vincent.screens.BottlesScreen
import com.vincent.screens.CellarScreen
import com.vincent.screens.DashboardScreen
import com.vincent.screens.LoginScreen
import com.vincent.screens.RecentScreen
import com.vincent.screens.SearchScreen
import com.vincent.theme.VincentColors
import com.vincent.theme.VincentTheme

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Filled.Home),
    CELLAR("Casiers", Icons.Filled.GridView),
    BOTTLES("Bouteilles", Icons.Filled.FormatListBulleted),
    SEARCH("Recherche", Icons.Filled.Search),
}

private enum class Overlay { ADD, ACCOUNT, RECENT }

@Composable
fun App() = VincentTheme {
    var loggedIn by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var detail by remember { mutableStateOf<Bottle?>(null) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    Surface(color = VincentColors.Bg, modifier = Modifier.fillMaxSize()) {
        when {
            !loggedIn -> LoginScreen(onContinue = { loggedIn = true })

            detail != null -> BottleDetailScreen(
                bottle = detail!!,
                onBack = { detail = null },
            )

            overlay == Overlay.ADD -> AddScreen(onClose = { overlay = null })

            overlay == Overlay.ACCOUNT -> AccountScreen(
                onBack = { overlay = null },
                onOpenRecent = { overlay = Overlay.RECENT },
                onOpenBottle = { detail = it; overlay = null },
            )

            overlay == Overlay.RECENT -> RecentScreen(
                onBack = { overlay = Overlay.ACCOUNT },
                onOpenBottle = { detail = it; overlay = null },
            )

            else -> MainScaffold(
                tab = tab,
                onTab = { tab = it },
                onOpenBottle = { detail = it },
                onAdd = { overlay = Overlay.ADD },
                onAccount = { overlay = Overlay.ACCOUNT },
            )
        }
    }
}

@Composable
private fun MainScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onOpenBottle: (Bottle) -> Unit,
    onAdd: () -> Unit,
    onAccount: () -> Unit,
) {
    Scaffold(
        containerColor = VincentColors.Bg,
        bottomBar = {
            NavigationBar(containerColor = VincentColors.Surface) {
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
            Tab.CELLAR -> CellarScreen(content, onOpenBottle = onOpenBottle)
            Tab.BOTTLES -> BottlesScreen(content, onOpenBottle = onOpenBottle)
            Tab.SEARCH -> SearchScreen(content)
        }
    }
}
