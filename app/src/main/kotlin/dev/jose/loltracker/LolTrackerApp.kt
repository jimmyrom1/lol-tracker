package dev.jose.loltracker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jose.loltracker.feature.matches.navigation.MatchListDestination
import dev.jose.loltracker.feature.matches.navigation.matchesGraph
import dev.jose.loltracker.feature.draft.DraftDestination
import dev.jose.loltracker.feature.draft.draftGraph
import dev.jose.loltracker.feature.profile.ProfileDestination
import dev.jose.loltracker.feature.profile.profileGraph
import dev.jose.loltracker.feature.stats.StatsDestination
import dev.jose.loltracker.feature.stats.statsGraph
import kotlin.reflect.KClass

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    MATCHES(MatchListDestination, MatchListDestination::class, R.string.nav_matches, Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    STATS(StatsDestination, StatsDestination::class, R.string.nav_stats, Icons.Filled.QueryStats, Icons.Outlined.QueryStats),
    DRAFT(DraftDestination, DraftDestination::class, R.string.nav_draft, Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb),
    PROFILE(ProfileDestination, ProfileDestination::class, R.string.nav_profile, Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}

@Composable
fun LolTrackerApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // La barra inferior solo aparece en las pantallas principales, no en el formulario.
    val current = TopLevelDestination.entries.firstOrNull { top -> destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true }

    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                StartupMessage.Offline -> resources.getString(R.string.champions_offline)
                StartupMessage.KeyRejected -> resources.getString(R.string.sync_key_rejected)
                is StartupMessage.NewMatches -> resources.getQuantityString(R.plurals.sync_new_matches, message.count, message.count)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (current != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { top ->
                        val selected = top == current
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                            label = { Text(stringResource(top.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MatchListDestination,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
        ) {
            matchesGraph(navController)
            statsGraph()
            draftGraph()
            profileGraph()
        }
    }
}
