package dev.jose.loltracker.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.core.data.riot.RiotImportRepository
import dev.jose.loltracker.core.designsystem.component.ChampionAvatar
import dev.jose.loltracker.core.designsystem.component.EmptyState
import dev.jose.loltracker.core.designsystem.component.grouped
import dev.jose.loltracker.core.designsystem.component.message
import dev.jose.loltracker.core.designsystem.component.percent
import dev.jose.loltracker.core.designsystem.component.shortLabel
import dev.jose.loltracker.core.model.PlayerProfile
import dev.jose.loltracker.core.model.RankEntry
import dev.jose.loltracker.core.model.RankedQueue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProfileRoute(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onSync = { viewModel.sync() },
        onImportHistory = { viewModel.sync(RiotImportRepository.MAX_COUNT) },
        onSyncResultShown = viewModel::consumeSyncResult,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onImportHistory: () -> Unit,
    onSyncResultShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val result = state.syncResult
    val errorText = (result as? ImportResult.Failure)?.error?.message()
    LaunchedEffect(result) {
        when (result) {
            is ImportResult.Success -> snackbar.showSnackbar(
                resources.getQuantityString(R.plurals.profile_synced, result.imported, result.imported),
            )
            is ImportResult.Failure -> snackbar.showSnackbar(errorText.orEmpty())
            null -> return@LaunchedEffect
        }
        onSyncResultShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.profile_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val load = state.load) {
            ProfileLoad.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            ProfileLoad.NotConfigured -> EmptyState(
                icon = Icons.Outlined.AccountCircle,
                title = stringResource(R.string.profile_empty_title),
                body = stringResource(R.string.profile_empty_body),
                modifier = Modifier.padding(padding),
            )
            is ProfileLoad.Failed -> EmptyState(
                icon = Icons.Outlined.AccountCircle,
                title = stringResource(R.string.profile_error_title),
                body = load.error.message(),
                modifier = Modifier.padding(padding),
                action = { OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.profile_retry)) } },
            )
            is ProfileLoad.Loaded -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(load.profile)
                RankedQueue.entries.forEach { queue -> RankCard(queue, load.profile.ranks.firstOrNull { it.queue == queue }) }
                Masteries(load.profile, state)
                SyncCard(state, onSync, onImportHistory)
            }
        }
    }
}

@Composable
private fun Header(profile: PlayerProfile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChampionAvatar(profile.profileIconUrl, profile.riotId, size = 72.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(profile.riotId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.profile_level, profile.summonerLevel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RankCard(queue: RankedQueue, entry: RankEntry?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(if (queue == RankedQueue.SOLO) R.string.profile_solo else R.string.profile_flex),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.shortLabel(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (entry != null) {
                Text(
                    stringResource(R.string.profile_record, entry.wins, entry.losses, entry.winRate.percent()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Masteries(profile: PlayerProfile, state: ProfileUiState) {
    if (profile.topMasteries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profile_masteries), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        profile.topMasteries.forEach { mastery ->
            val champion = state.championsByKey[mastery.championKey]
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChampionAvatar(champion?.iconUrl, champion?.name ?: "?", size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(champion?.name ?: "#${mastery.championKey}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.profile_mastery_level, mastery.level), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.profile_mastery_points, mastery.points.grouped()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncCard(state: ProfileUiState, onSync: () -> Unit, onImportHistory: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_sync_title), style = MaterialTheme.typography.titleSmall)
            Text(
                state.lastSyncAt?.let { stringResource(R.string.profile_last_sync, it.format()) }
                    ?: stringResource(R.string.profile_never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.profile_sync_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isSyncing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.profile_syncing), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSync) { Text(stringResource(R.string.profile_sync_now)) }
                    OutlinedButton(onClick = onImportHistory) {
                        Text(pluralStringResource(R.plurals.profile_import_history, RiotImportRepository.MAX_COUNT, RiotImportRepository.MAX_COUNT))
                    }
                }
            }
        }
    }
}

private fun Instant.format(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
