package dev.jose.loltracker.feature.draft

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DraftDestination

fun NavGraphBuilder.draftGraph() {
    composable<DraftDestination> { DraftRoute() }
}
