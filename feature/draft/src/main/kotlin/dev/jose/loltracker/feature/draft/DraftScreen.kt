package dev.jose.loltracker.feature.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jose.loltracker.core.designsystem.component.ChampionAvatar
import dev.jose.loltracker.core.designsystem.component.ChampionPickerDialog
import dev.jose.loltracker.core.designsystem.component.label
import dev.jose.loltracker.core.designsystem.component.message
import dev.jose.loltracker.core.designsystem.component.percent
import dev.jose.loltracker.core.designsystem.component.shortLabel
import dev.jose.loltracker.core.designsystem.theme.LocalResultColors
import dev.jose.loltracker.core.domain.PickSuggestion
import dev.jose.loltracker.core.domain.Record
import dev.jose.loltracker.core.model.LiveGame
import dev.jose.loltracker.core.model.LivePlayer
import dev.jose.loltracker.core.model.Role

private enum class PickerTarget { ENEMY, ALLY }

@Composable
fun DraftRoute(viewModel: DraftViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DraftScreen(
        state = state,
        actions = DraftActions(
            onRole = viewModel::setRole,
            onAddEnemy = viewModel::addEnemy,
            onRemoveEnemy = viewModel::removeEnemy,
            onAddAlly = viewModel::addAlly,
            onRemoveAlly = viewModel::removeAlly,
            onClear = viewModel::clear,
            onFindLiveGame = viewModel::findLiveGame,
        ),
    )
}

data class DraftActions(
    val onRole: (Role?) -> Unit = {},
    val onAddEnemy: (String) -> Unit = {},
    val onRemoveEnemy: (String) -> Unit = {},
    val onAddAlly: (String) -> Unit = {},
    val onRemoveAlly: (String) -> Unit = {},
    val onClear: () -> Unit = {},
    val onFindLiveGame: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftScreen(state: DraftUiState, actions: DraftActions) {
    var picker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    picker?.let { target ->
        ChampionPickerDialog(
            champions = state.champions.values.sortedBy { it.name },
            excluded = state.query.allies + state.query.enemies,
            onPick = {
                if (target == PickerTarget.ENEMY) actions.onAddEnemy(it.id) else actions.onAddAlly(it.id)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.draft_title)) },
                actions = {
                    if (state.query.enemies.isNotEmpty() || state.query.allies.isNotEmpty()) {
                        TextButton(onClick = actions.onClear) { Text(stringResource(R.string.draft_clear)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LiveGameCard(state, actions.onFindLiveGame)

            Section(stringResource(R.string.draft_role)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.query.role == null, onClick = { actions.onRole(null) }, label = { Text(stringResource(R.string.draft_any_role)) })
                    Role.entries.forEach { role ->
                        FilterChip(selected = state.query.role == role, onClick = { actions.onRole(role) }, label = { Text(role.label()) })
                    }
                }
            }
            ChampionRow(stringResource(R.string.draft_enemies), state.query.enemies, 5, state, actions.onRemoveEnemy) { picker = PickerTarget.ENEMY }
            ChampionRow(stringResource(R.string.draft_allies), state.query.allies, 4, state, actions.onRemoveAlly) { picker = PickerTarget.ALLY }

            Suggestions(state)
            Insights(state)
        }
    }
}

@Composable
private fun LiveGameCard(state: DraftUiState, onFind: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.draft_live_title), style = MaterialTheme.typography.titleSmall)
            when (val live = state.live) {
                LiveGameState.Idle -> Text(stringResource(R.string.draft_live_hint), style = MaterialTheme.typography.bodySmall)
                LiveGameState.Searching -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.draft_live_searching), style = MaterialTheme.typography.bodyMedium)
                }
                LiveGameState.NotInGame -> Text(stringResource(R.string.draft_live_not_in_game), style = MaterialTheme.typography.bodyMedium)
                is LiveGameState.Failed -> Text(live.error.message(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                is LiveGameState.Found -> LivePlayers(live.game, state)
            }
            if (state.live != LiveGameState.Searching) {
                Button(onClick = onFind) {
                    Icon(Icons.Outlined.Sensors, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.draft_live_button))
                }
            }
        }
    }
}

@Composable
private fun LivePlayers(game: LiveGame, state: DraftUiState) {
    val myTeam = game.me?.teamId
    val teams = game.players.groupBy { it.teamId == myTeam }
    listOf(true to R.string.draft_live_my_team, false to R.string.draft_live_enemy_team).forEach { (mine, title) ->
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        teams[mine].orEmpty().forEach { player -> LivePlayerRow(player, state) }
    }
}

@Composable
private fun LivePlayerRow(player: LivePlayer, state: DraftUiState) {
    val champion = state.championsByKey[player.championKey]
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChampionAvatar(champion?.iconUrl, champion?.name ?: "?", size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                player.riotId.substringBefore('#').ifBlank { champion?.name.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (player.isMe) FontWeight.Bold else FontWeight.Normal,
            )
            val rank = player.soloRank
            Text(
                rank.shortLabel() + (rank?.let { " · " + stringResource(R.string.draft_live_winrate, it.winRate.percent(), it.games) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                player.mastery?.let { stringResource(R.string.draft_live_mastery, it.level) } ?: stringResource(R.string.draft_live_first_time),
                style = MaterialTheme.typography.labelMedium,
            )
            // Contra los rivales: tu historial frente a ese campeón.
            champion?.let { state.liveRecords[it.id] }?.takeIf { it.games > 0 }?.let { record ->
                RecordText(stringResource(R.string.draft_you_vs, record.wins, record.losses), record)
            }
        }
    }
}

@Composable
private fun ChampionRow(title: String, ids: Set<String>, max: Int, state: DraftUiState, onRemove: (String) -> Unit, onAdd: () -> Unit) {
    Section(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ids.forEach { id ->
                val champion = state.champions[id]
                InputChip(
                    selected = false,
                    onClick = { onRemove(id) },
                    label = { Text(champion?.name ?: id) },
                    avatar = { ChampionAvatar(champion?.iconUrl, champion?.name ?: id, size = 24.dp) },
                    trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.draft_remove), Modifier.size(16.dp)) },
                )
            }
            if (ids.size < max) {
                AssistChip(
                    onClick = onAdd,
                    label = { Text(stringResource(R.string.draft_add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun Suggestions(state: DraftUiState) {
    Section(stringResource(R.string.draft_suggestions)) {
        when {
            state.historySize == 0 -> Text(stringResource(R.string.draft_no_history), style = MaterialTheme.typography.bodyMedium)
            state.advice.picks.isEmpty() -> Text(stringResource(R.string.draft_no_picks), style = MaterialTheme.typography.bodyMedium)
            else -> {
                state.advice.picks.forEach { SuggestionRow(it, state) }
                Text(
                    stringResource(R.string.draft_sample, state.advice.sampleSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(pick: PickSuggestion, state: DraftUiState) {
    val champion = state.champions[pick.championId]
    val name = champion?.name ?: pick.championId
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChampionAvatar(champion?.iconUrl, name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.draft_record, pick.overall.wins, pick.overall.losses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pick.vsEnemies.forEach { (enemy, record) ->
                    RecordText(stringResource(R.string.draft_vs, record.wins, record.losses, state.champions[enemy]?.name ?: enemy), record)
                }
                pick.withAllies.forEach { (ally, record) ->
                    RecordText(stringResource(R.string.draft_with, record.wins, record.losses, state.champions[ally]?.name ?: ally), record)
                }
            }
            Text(pick.score.percent(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Insights(state: DraftUiState) {
    val enemies = state.advice.enemies.filter { it.record.games > 0 }
    val allies = state.advice.allies.filter { it.record.games > 0 }
    if (enemies.isEmpty() && allies.isEmpty()) return
    Section(stringResource(R.string.draft_insights)) {
        enemies.forEach { insight ->
            RecordText(
                stringResource(R.string.draft_insight_vs, state.champions[insight.championId]?.name ?: insight.championId, insight.record.wins, insight.record.losses),
                insight.record,
            )
        }
        allies.forEach { insight ->
            RecordText(
                stringResource(R.string.draft_insight_with, state.champions[insight.championId]?.name ?: insight.championId, insight.record.wins, insight.record.losses),
                insight.record,
            )
        }
    }
}

/** Verde (azul) si vas por encima del 50 %, rojo si vas por debajo. */
@Composable
private fun RecordText(text: String, record: Record) {
    val colors = LocalResultColors.current
    val color = when {
        record.wins * 2 > record.games -> colors.onWin
        record.wins * 2 < record.games -> colors.onLoss
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
