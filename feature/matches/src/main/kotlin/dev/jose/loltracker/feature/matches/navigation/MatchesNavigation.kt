package dev.jose.loltracker.feature.matches.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.jose.loltracker.feature.matches.edit.MatchEditRoute
import dev.jose.loltracker.feature.matches.edit.MatchEditViewModel
import dev.jose.loltracker.feature.matches.list.MatchListRoute
import kotlinx.serialization.Serializable

@Serializable
data object MatchListDestination

/** `matchId = 0` crea una partida nueva. El nombre debe coincidir con [MatchEditViewModel.MATCH_ID_ARG]. */
@Serializable
data class MatchEditDestination(val matchId: Long = 0L)

fun NavController.navigateToMatchEdit(matchId: Long = 0L) = navigate(MatchEditDestination(matchId))

fun NavGraphBuilder.matchesGraph(navController: NavController) {
    composable<MatchListDestination> {
        MatchListRoute(
            onAddMatch = { navController.navigateToMatchEdit() },
            onOpenMatch = { id -> navController.navigateToMatchEdit(id) },
        )
    }
    composable<MatchEditDestination> {
        MatchEditRoute(onDone = { navController.popBackStack() })
    }
}
