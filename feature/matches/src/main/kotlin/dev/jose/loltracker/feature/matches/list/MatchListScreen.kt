package dev.jose.loltracker.feature.matches.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jose.loltracker.core.designsystem.component.ChampionAvatar
import dev.jose.loltracker.core.designsystem.component.EmptyState
import dev.jose.loltracker.core.designsystem.component.ResultBadge
import dev.jose.loltracker.core.designsystem.component.format
import dev.jose.loltracker.core.designsystem.component.label
import dev.jose.loltracker.core.model.Match
import dev.jose.loltracker.feature.matches.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun MatchListRoute(
    onAddMatch: () -> Unit,
    onOpenMatch: (Long) -> Unit,
    viewModel: MatchListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // El snackbar vive en el Scaffold de esta pantalla: así Material coloca el FAB encima de él
    // y el botón "Deshacer" no queda tapado.
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.matches_deleted)
    val undoLabel = stringResource(R.string.matches_undo)

    MatchListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onFilterChange = viewModel::setFilter,
        onAddMatch = onAddMatch,
        onOpenMatch = onOpenMatch,
        onDelete = { match ->
            viewModel.delete(match)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(
    state: MatchListUiState,
    snackbarHostState: SnackbarHostState,
    onFilterChange: (ResultFilter) -> Unit,
    onAddMatch: () -> Unit,
    onOpenMatch: (Long) -> Unit,
    onDelete: (Match) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.matches_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMatch,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.matches_add)) },
            )
        },
    ) { padding ->
        when (state) {
            MatchListUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            is MatchListUiState.Content -> Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.totalMatches > 0) FilterRow(state.filter, onFilterChange)
                when {
                    state.totalMatches == 0 -> EmptyState(
                        icon = Icons.Outlined.SportsEsports,
                        title = stringResource(R.string.matches_empty_title),
                        body = stringResource(R.string.matches_empty_body),
                    )

                    state.sections.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.SportsEsports,
                        title = stringResource(R.string.matches_empty_filtered_title),
                        body = stringResource(R.string.matches_empty_filtered_body),
                    )

                    else -> LazyColumn(
                        modifier = Modifier.testTag("match_list"),
                        // Espacio final para que el FAB no tape la última partida.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.sections.forEach { section ->
                            item(key = "header-${section.date}") { DayHeader(section.date, today) }
                            items(section.matches, key = { it.id }) { match ->
                                SwipeToDelete(onDelete = { onDelete(match) }) {
                                    MatchCard(match, state.championIcons[match.championId], onClick = { onOpenMatch(match.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: ResultFilter, onChange: (ResultFilter) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onChange(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                ResultFilter.ALL -> R.string.matches_filter_all
                                ResultFilter.WINS -> R.string.matches_filter_wins
                                ResultFilter.LOSSES -> R.string.matches_filter_losses
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val text = when (date) {
        today -> stringResource(R.string.matches_today)
        today.minusDays(1) -> stringResource(R.string.matches_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            .replaceFirstChar { it.uppercase() }
    }
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    // `remember` y no `rememberSwipeToDismissBoxState()`: esa versión es saveable y la LazyColumn
    // restauraría el estado "descartado" al deshacer (mismo key), borrando otra vez la partida.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, CardDefaults.shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.matches_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { content() }
}

@Composable
private fun MatchCard(match: Match, iconUrl: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChampionAvatar(iconUrl, match.championName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(match.championName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${match.role.label()} · ${match.queue.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.matches_cs, match.creepScore, match.csPerMinute.format()) +
                        " · " + stringResource(R.string.matches_duration, match.duration.toMinutes().toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                ResultBadge(match.result)
                Text(
                    stringResource(R.string.matches_kda, match.kills, match.deaths, match.assists),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    if (match.isPerfectKda) {
                        stringResource(R.string.matches_kda_perfect)
                    } else {
                        stringResource(R.string.matches_kda_ratio, match.kda.format(2))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
