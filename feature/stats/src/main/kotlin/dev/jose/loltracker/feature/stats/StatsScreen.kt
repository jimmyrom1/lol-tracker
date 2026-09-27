package dev.jose.loltracker.feature.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jose.loltracker.core.designsystem.component.ChampionAvatar
import dev.jose.loltracker.core.designsystem.component.EmptyState
import dev.jose.loltracker.core.designsystem.component.RecentForm
import dev.jose.loltracker.core.designsystem.component.StatCard
import dev.jose.loltracker.core.designsystem.component.format
import dev.jose.loltracker.core.designsystem.component.label
import dev.jose.loltracker.core.designsystem.component.percent
import dev.jose.loltracker.core.designsystem.theme.LocalResultColors
import dev.jose.loltracker.core.domain.ChampionStats
import dev.jose.loltracker.core.domain.PlayerStats
import dev.jose.loltracker.core.domain.RoleStats
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue

@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(state, onQueueChange = viewModel::selectQueue)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: StatsUiState, onQueueChange: (Queue?) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) }) { padding ->
        when (state) {
            StatsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            is StatsUiState.Content -> if (state.availableQueues.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.QueryStats,
                    title = stringResource(R.string.stats_empty_title),
                    body = stringResource(R.string.stats_empty_body),
                    modifier = Modifier.padding(padding),
                )
            } else {
                StatsContent(state, onQueueChange, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun StatsContent(state: StatsUiState.Content, onQueueChange: (Queue?) -> Unit, modifier: Modifier) {
    val stats = state.stats
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.availableQueues.size > 1) {
            item { QueueFilter(state.availableQueues, state.queue, onQueueChange) }
        }
        item { Summary(stats) }
        item { Streak(stats) }
        item { SectionTitle(stringResource(R.string.stats_by_role)) }
        items(stats.byRole, key = { "role-${it.role}" }) { RoleRow(it) }
        item { SectionTitle(stringResource(R.string.stats_by_champion)) }
        items(stats.byChampion, key = { "champ-${it.championId}" }) { ChampionRow(it, state.championIcons[it.championId]) }
    }
}

@Composable
private fun QueueFilter(queues: List<Queue>, selected: Queue?, onChange: (Queue?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onChange(null) }, label = { Text(stringResource(R.string.stats_all_queues)) })
        queues.forEach { queue ->
            FilterChip(selected = selected == queue, onClick = { onChange(queue) }, label = { Text(queue.label()) })
        }
    }
}

@Composable
private fun Summary(stats: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = stringResource(R.string.stats_games),
                value = stats.games.toString(),
                supporting = stringResource(R.string.stats_record, stats.wins, stats.losses),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.stats_win_rate),
                value = stats.winRate.percent(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = stringResource(R.string.stats_kda),
                value = stats.averageKda.format(2),
                supporting = stringResource(R.string.stats_kda_supporting),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.stats_cs),
                value = stats.averageCsPerMinute.format(1),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Streak(stats: PlayerStats) {
    val streak = stats.currentStreak ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.stats_streak_title))
        Text(
            pluralStringResource(
                if (streak.result == MatchResult.WIN) R.plurals.stats_streak_wins else R.plurals.stats_streak_losses,
                streak.length,
                streak.length,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.stats_recent_form),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RecentForm(stats.recentForm)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun RoleRow(role: RoleStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(role.role.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.stats_games_count, role.games, role.games) + " · " + role.winRate.percent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WinRateBar(role.winRate)
    }
}

@Composable
private fun ChampionRow(champion: ChampionStats, iconUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChampionAvatar(iconUrl, champion.championName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    champion.championName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(champion.winRate.percent(), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(
                    R.string.stats_champion_line,
                    pluralStringResource(R.plurals.stats_games_count, champion.games, champion.games),
                    champion.averageKda.format(2),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WinRateBar(champion.winRate)
        }
    }
}

@Composable
private fun WinRateBar(winRate: Double) {
    val colors = LocalResultColors.current
    LinearProgressIndicator(
        progress = { winRate.toFloat() },
        color = colors.win,
        trackColor = colors.loss,
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}
