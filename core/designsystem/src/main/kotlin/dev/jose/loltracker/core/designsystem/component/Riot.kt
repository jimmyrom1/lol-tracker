package dev.jose.loltracker.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jose.loltracker.core.designsystem.R
import dev.jose.loltracker.core.model.Champion
import dev.jose.loltracker.core.model.RankEntry
import dev.jose.loltracker.core.model.RiotError
import java.text.Normalizer

@Composable
fun RiotError.message(): String = stringResource(
    when (this) {
        RiotError.NOT_CONFIGURED -> R.string.riot_err_not_configured
        RiotError.MISSING_API_KEY -> R.string.riot_err_missing_key
        RiotError.INVALID_API_KEY -> R.string.riot_err_invalid_key
        RiotError.ACCOUNT_NOT_FOUND -> R.string.riot_err_not_found
        RiotError.RATE_LIMITED -> R.string.riot_err_rate_limited
        RiotError.NETWORK -> R.string.riot_err_network
    },
)

/** 390649 → "390.649" con el separador del idioma activo (se recompone si cambia el idioma). */
@Composable
fun Int.grouped(): String {
    val locale = LocalConfiguration.current.locales[0]
    return String.format(locale, "%,d", this)
}

/** "SILVER" → "Plata". */
@Composable
fun tierLabel(tier: String): String = stringResource(
    when (tier.uppercase()) {
        "IRON" -> R.string.tier_iron
        "BRONZE" -> R.string.tier_bronze
        "SILVER" -> R.string.tier_silver
        "GOLD" -> R.string.tier_gold
        "PLATINUM" -> R.string.tier_platinum
        "EMERALD" -> R.string.tier_emerald
        "DIAMOND" -> R.string.tier_diamond
        "MASTER" -> R.string.tier_master
        "GRANDMASTER" -> R.string.tier_grandmaster
        "CHALLENGER" -> R.string.tier_challenger
        else -> R.string.tier_unranked
    },
)

/** "Plata IV · 23 LP" ("Maestro · 120 LP" en las ligas sin división). */
@Composable
fun RankEntry?.shortLabel(): String {
    if (this == null) return stringResource(R.string.tier_unranked)
    val division = if (division.isBlank() || tier.uppercase() in setOf("MASTER", "GRANDMASTER", "CHALLENGER")) "" else " $division"
    return "${tierLabel(tier)}$division · $leaguePoints LP"
}

/** Selector de campeón con buscador; ignora tildes, espacios y apóstrofos ("kaisa" → Kai'Sa). */
@Composable
fun ChampionPickerDialog(
    champions: List<Champion>,
    onPick: (Champion) -> Unit,
    onDismiss: () -> Unit,
    excluded: Set<String> = emptySet(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, champions, excluded) {
        val needle = query.normalizedForSearch()
        champions.filter { it.id !in excluded && it.name.normalizedForSearch().contains(needle) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("picker_search"),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { champion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(champion) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ChampionAvatar(champion.iconUrl, champion.name, size = 36.dp)
                            Text(champion.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) } },
    )
}

fun String.normalizedForSearch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
