package dev.jose.loltracker.feature.matches.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jose.loltracker.core.data.riot.DataDragonImages
import dev.jose.loltracker.core.designsystem.component.ChampionAvatar
import dev.jose.loltracker.core.designsystem.component.GameIcon
import dev.jose.loltracker.core.designsystem.component.ResultBadge
import dev.jose.loltracker.core.designsystem.component.StatCard
import dev.jose.loltracker.core.designsystem.component.grouped
import dev.jose.loltracker.core.designsystem.component.label
import dev.jose.loltracker.core.designsystem.component.percent
import dev.jose.loltracker.core.designsystem.theme.LocalResultColors
import dev.jose.loltracker.core.model.MatchDetail
import dev.jose.loltracker.core.model.ParticipantStats
import dev.jose.loltracker.core.model.TeamObjectives
import dev.jose.loltracker.core.model.TimelineSummary
import dev.jose.loltracker.feature.matches.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.max

@Composable
fun MatchDetailRoute(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: MatchDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == MatchDetailUiState.NotFound) onBack() }
    MatchDetailScreen(state, onBack = onBack, onEdit = onEdit, onRetryTimeline = viewModel::retryTimeline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(state: MatchDetailUiState, onBack: () -> Unit, onEdit: (Long) -> Unit, onRetryTimeline: () -> Unit) {
    val content = state as? MatchDetailUiState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.match?.championName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.edit_back))
                    }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = { onEdit(content.match.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.detail_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (content == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(content)
            val detail = content.detail
            val me = detail?.me
            if (detail == null || me == null) {
                Text(
                    stringResource(R.string.detail_no_riot_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StatsGrid(me)
                Build(me, content.patchVersion)
                Timeline(detail.timeline, content.timeline, content.championNames, onRetryTimeline)
                Teams(detail, content)
            }
            if (content.match.notes.isNotBlank()) {
                SectionTitle(stringResource(R.string.edit_notes))
                Text(content.match.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Header(content: MatchDetailUiState.Content) {
    val match = content.match
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChampionAvatar(content.championIcons[match.championId], match.championName, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.matches_kda, match.kills, match.deaths, match.assists),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${match.role.label()} · ${match.queue.label()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                match.playedAt.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)) +
                    " · " + stringResource(R.string.matches_duration, match.duration.toMinutes().toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ResultBadge(match.result)
    }
}

@Composable
private fun StatsGrid(me: ParticipantStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.detail_damage), me.damageToChampions.grouped(), Modifier.weight(1f))
            StatCard(stringResource(R.string.detail_gold), me.gold.grouped(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                stringResource(R.string.detail_kill_participation),
                me.killParticipation?.percent() ?: "–",
                Modifier.weight(1f),
            )
            StatCard(
                stringResource(R.string.detail_vision),
                me.visionScore.toString(),
                Modifier.weight(1f),
                supporting = stringResource(R.string.detail_wards, me.wardsPlaced),
            )
        }
    }
}

@Composable
private fun Build(me: ParticipantStats, patchVersion: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.detail_build))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            me.summonerSpells.forEach { spell ->
                GameIcon(patchVersion?.let { DataDragonImages.summonerSpell(it, spell) }, null, size = 32.dp)
            }
            Spacer(Modifier.width(10.dp))
            me.items.forEach { item ->
                GameIcon(
                    url = if (item == 0 || patchVersion == null) null else DataDragonImages.item(patchVersion, item),
                    contentDescription = null,
                    size = 32.dp,
                )
            }
        }
    }
}

@Composable
private fun Timeline(summary: TimelineSummary?, state: TimelineState, names: Map<String, String>, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.detail_timeline))
        when {
            summary != null -> {
                GoldChart(summary.teamGoldDiff)
                val opponent = summary.laneOpponentChampion?.let { names[it] ?: it }
                if (opponent != null) {
                    summary.laneCsDiffAt10?.let {
                        LaneLine(stringResource(R.string.detail_cs_at_10, signed(it), opponent), it)
                    }
                    summary.laneGoldDiffAt15?.let {
                        LaneLine(stringResource(R.string.detail_gold_at_15, signed(it), opponent), it)
                    }
                }
            }
            state is TimelineState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.detail_timeline_error),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.detail_retry)) }
            }
            else -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun LaneLine(text: String, value: Int) {
    val colors = LocalResultColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (value >= 0) colors.onWin else colors.onLoss,
    )
}

/**
 * Diferencia de oro de tu equipo minuto a minuto: por encima de la línea vais ganando.
 * Se dibuja a mano con Canvas; para una sola serie no compensa una librería de gráficas.
 */
@Composable
private fun GoldChart(diff: List<Int>) {
    if (diff.size < 2) return
    val colors = LocalResultColors.current
    val axis = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.primary
    val maxAbs = max(diff.maxOf { abs(it) }, 1000)
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
        ) {
            val midY = size.height / 2
            fun x(i: Int) = i * size.width / (diff.size - 1)
            fun y(v: Int) = midY - v / maxAbs.toFloat() * (midY - 8.dp.toPx())
            // Área por encima (ventaja) y por debajo (desventaja) del eje.
            for (i in 0 until diff.size - 1) {
                val color = if (diff[i] + diff[i + 1] >= 0) colors.win else colors.loss
                val area = Path().apply {
                    moveTo(x(i), midY)
                    lineTo(x(i), y(diff[i]))
                    lineTo(x(i + 1), y(diff[i + 1]))
                    lineTo(x(i + 1), midY)
                    close()
                }
                drawPath(area, color.copy(alpha = 0.6f))
            }
            drawLine(axis, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                moveTo(x(0), y(diff[0]))
                diff.indices.drop(1).forEach { lineTo(x(it), y(diff[it])) }
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.detail_gold_final, signed(diff.last())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("${diff.size - 1}'", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Teams(detail: MatchDetail, content: MatchDetailUiState.Content) {
    val me = detail.me ?: return
    val maxDamage = detail.participants.maxOf { it.damageToChampions }.coerceAtLeast(1)
    val myTeam = listOf(me) + detail.allies
    val teams = listOf(
        Triple(R.string.detail_my_team, myTeam, detail.teams.firstOrNull { it.teamId == me.teamId }),
        Triple(R.string.detail_enemy_team, detail.enemies, detail.teams.firstOrNull { it.teamId != me.teamId }),
    )
    teams.forEach { (title, players, objectives) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(title), Modifier.weight(1f))
                objectives?.let { ObjectivesLine(it) }
            }
            players.forEach { p -> PlayerRow(p, isMe = p.puuid == me.puuid, maxDamage, content) }
        }
    }
}

@Composable
private fun ObjectivesLine(o: TeamObjectives) {
    Text(
        stringResource(R.string.detail_objectives, o.towers, o.dragons, o.barons),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayerRow(p: ParticipantStats, isMe: Boolean, maxDamage: Int, content: MatchDetailUiState.Content) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChampionAvatar(content.championIcons[p.championId], content.championNames[p.championId] ?: p.championId, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.riotId.substringBefore('#').ifBlank { content.championNames[p.championId] ?: p.championId },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DamageBar(p.damageToChampions / maxDamage.toFloat())
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${p.kills}/${p.deaths}/${p.assists}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${p.creepScore} CS · ${p.damageToChampions.grouped()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DamageBar(fraction: Float) {
    Box(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

@Composable
private fun signed(value: Int) = if (value > 0) "+${value.grouped()}" else value.grouped()
