package dev.jose.loltracker.feature.stats

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object StatsDestination

fun NavGraphBuilder.statsGraph() {
    composable<StatsDestination> { StatsRoute() }
}
