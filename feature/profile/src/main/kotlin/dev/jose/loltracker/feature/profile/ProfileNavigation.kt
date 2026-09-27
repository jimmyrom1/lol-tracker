package dev.jose.loltracker.feature.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProfileDestination

fun NavGraphBuilder.profileGraph() {
    composable<ProfileDestination> { ProfileRoute() }
}
