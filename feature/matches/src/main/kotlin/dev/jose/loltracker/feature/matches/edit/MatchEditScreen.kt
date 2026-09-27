package dev.jose.loltracker.feature.matches.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import dev.jose.loltracker.core.designsystem.component.ChampionAvatar
import dev.jose.loltracker.core.designsystem.component.label
import dev.jose.loltracker.core.domain.MatchField
import dev.jose.loltracker.core.domain.MatchValidator
import dev.jose.loltracker.core.domain.ValidationError
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import dev.jose.loltracker.feature.matches.R
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MatchEditRoute(onDone: () -> Unit, viewModel: MatchEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val champions by viewModel.champions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notFound = stringResource(R.string.edit_not_found)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == MatchEditEvent.NotFound) Toast.makeText(context, notFound, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    MatchEditScreen(
        state = state,
        champions = champions,
        actions = MatchEditActions(
            onBack = onDone,
            onSave = viewModel::save,
            onChampion = viewModel::onChampionSelected,
            onRole = viewModel::onRoleSelected,
            onQueue = viewModel::onQueueSelected,
            onResult = viewModel::onResultSelected,
            onKills = viewModel::onKillsChange,
            onDeaths = viewModel::onDeathsChange,
            onAssists = viewModel::onAssistsChange,
            onCreepScore = viewModel::onCreepScoreChange,
            onDuration = viewModel::onDurationChange,
            onDate = viewModel::onDateSelected,
            onNotes = viewModel::onNotesChange,
        ),
    )
}

/** Agrupa los callbacks para que la firma del composable no tenga quince parámetros. */
data class MatchEditActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onChampion: (Champion) -> Unit = {},
    val onRole: (Role) -> Unit = {},
    val onQueue: (Queue) -> Unit = {},
    val onResult: (MatchResult) -> Unit = {},
    val onKills: (String) -> Unit = {},
    val onDeaths: (String) -> Unit = {},
    val onAssists: (String) -> Unit = {},
    val onCreepScore: (String) -> Unit = {},
    val onDuration: (String) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onNotes: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchEditScreen(
    state: MatchEditUiState,
    champions: List<Champion>,
    actions: MatchEditActions,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.edit_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val form = state.form
        val errors = state.errors
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChampionPicker(
                selected = form.champion,
                champions = champions,
                error = errors[MatchField.CHAMPION]?.let { stringResource(R.string.error_champion_required) },
                onSelect = actions.onChampion,
            )

            Section(stringResource(R.string.edit_result)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MatchResult.entries.forEachIndexed { index, result ->
                        SegmentedButton(
                            selected = form.result == result,
                            onClick = { actions.onResult(result) },
                            shape = SegmentedButtonDefaults.itemShape(index, MatchResult.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    if (result == MatchResult.WIN) {
                                        dev.jose.loltracker.core.designsystem.R.string.result_win
                                    } else {
                                        dev.jose.loltracker.core.designsystem.R.string.result_loss
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.edit_role)) {
                ChipGroup(Role.entries, form.role, actions.onRole) { it.label() }
            }
            Section(stringResource(R.string.edit_queue)) {
                ChipGroup(Queue.entries, form.queue, actions.onQueue) { it.label() }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(R.string.edit_kills, form.kills, errors[MatchField.KILLS], MatchValidator.STAT_RANGE, actions.onKills, Modifier.weight(1f))
                NumberField(R.string.edit_deaths, form.deaths, errors[MatchField.DEATHS], MatchValidator.STAT_RANGE, actions.onDeaths, Modifier.weight(1f))
                NumberField(R.string.edit_assists, form.assists, errors[MatchField.ASSISTS], MatchValidator.STAT_RANGE, actions.onAssists, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(R.string.edit_cs, form.creepScore, errors[MatchField.CREEP_SCORE], MatchValidator.CREEP_SCORE_RANGE, actions.onCreepScore, Modifier.weight(1f))
                NumberField(R.string.edit_duration, form.durationMinutes, errors[MatchField.DURATION], MatchValidator.DURATION_MINUTES_RANGE, actions.onDuration, Modifier.weight(1f))
            }

            DateField(form.playedAt, zone, errors[MatchField.PLAYED_AT], actions.onDate)

            OutlinedTextField(
                value = form.notes,
                onValueChange = actions.onNotes,
                label = { Text(stringResource(R.string.edit_notes)) },
                placeholder = { Text(stringResource(R.string.edit_notes_hint)) },
                supportingText = { Text("${form.notes.length} / ${MatchEditViewModel.NOTES_MAX_LENGTH}") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = actions.onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("save_button"),
            ) { Text(stringResource(R.string.edit_save)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun <T> ChipGroup(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun NumberField(
    labelRes: Int,
    value: String,
    error: ValidationError?,
    range: IntRange,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(labelRes)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        isError = error != null,
        supportingText = error?.let { { Text(errorText(it, range)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.testTag("field_$label"),
    )
}

@Composable
private fun errorText(error: ValidationError, range: IntRange = 0..0): String = when (error) {
    ValidationError.REQUIRED -> stringResource(R.string.error_required)
    ValidationError.NOT_A_NUMBER -> stringResource(R.string.error_not_a_number)
    ValidationError.OUT_OF_RANGE -> stringResource(R.string.error_out_of_range, range.first, range.last)
    ValidationError.IN_THE_FUTURE -> stringResource(R.string.error_in_the_future)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChampionPicker(selected: Champion?, champions: List<Champion>, error: String?, onSelect: (Champion) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable(selected?.id) { mutableStateOf(selected?.name.orEmpty()) }
    val matches = remember(query, champions) {
        val needle = query.normalized()
        champions.filter { it.name.normalized().contains(needle) }.take(8)
    }
    // El icono se resuelve contra el catálogo por si la partida se guardó sin él.
    val icon = selected?.let { s -> s.iconUrl.ifBlank { champions.firstOrNull { it.id == s.id }?.iconUrl } }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                ChampionAvatar(icon, selected.name, size = 56.dp)
                Spacer(Modifier.width(12.dp))
            }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        expanded = true
                    },
                    label = { Text(stringResource(R.string.edit_champion)) },
                    placeholder = { Text(stringResource(R.string.edit_champion_search)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .testTag("champion_field"),
                )
                ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
                    matches.forEach { champion ->
                        DropdownMenuItem(
                            leadingIcon = { ChampionAvatar(champion.iconUrl, champion.name, size = 32.dp) },
                            text = { Text(champion.name) },
                            onClick = {
                                onSelect(champion)
                                query = champion.name
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        if (champions.isEmpty()) {
            Text(
                stringResource(R.string.edit_champion_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(playedAt: Instant, zone: ZoneId, error: ValidationError?, onDate: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val date = playedAt.atZone(zone).toLocalDate()

    Box {
        OutlinedTextField(
            value = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_date)) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            isError = error != null,
            supportingText = error?.let { { Text(errorText(it)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        // Capa transparente encima: un campo de solo lectura no recibe clics por sí mismo.
        Box(Modifier.matchParentSize().clickable { open = true })
    }

    if (open) {
        // El DatePicker trabaja con milisegundos en UTC a medianoche.
        val today = LocalDate.now(zone)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.edit_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.edit_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

/** "lee sin" encuentra "Lee Sin" y "kaisa" encuentra "Kai'Sa". */
private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
