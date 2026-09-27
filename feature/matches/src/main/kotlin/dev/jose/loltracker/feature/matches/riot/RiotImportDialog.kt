package dev.jose.loltracker.feature.matches.riot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jose.loltracker.core.data.riot.ImportError
import dev.jose.loltracker.core.data.riot.ImportResult
import dev.jose.loltracker.feature.matches.R

@Composable
fun RiotImportDialog(onDismiss: () -> Unit, viewModel: RiotImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RiotImportDialogContent(
        state = state,
        onRiotIdChange = viewModel::onRiotIdChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onImport = viewModel::import,
        onDismiss = onDismiss,
    )
}

@Composable
fun RiotImportDialogContent(
    state: RiotImportUiState,
    onRiotIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val done = state.result is ImportResult.Success
    AlertDialog(
        onDismissRequest = { if (!state.isImporting) onDismiss() },
        title = { Text(stringResource(R.string.riot_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.riot_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = state.riotId,
                    onValueChange = onRiotIdChange,
                    label = { Text(stringResource(R.string.riot_id)) },
                    placeholder = { Text("jimmyrom#uarra") },
                    isError = state.riotIdError,
                    supportingText = if (state.riotIdError) {
                        { Text(stringResource(R.string.riot_id_error)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !state.isImporting,
                    modifier = Modifier.fillMaxWidth().testTag("riot_id"),
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(stringResource(R.string.riot_api_key)) },
                    placeholder = { Text("RGAPI-…") },
                    supportingText = {
                        Text(stringResource(if (state.hasBuiltInKey) R.string.riot_api_key_built_in else R.string.riot_api_key_help))
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !state.isImporting,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.isImporting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.riot_importing))
                    }
                    state.result != null -> ResultText(state.result)
                }
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.riot_close)) }
            } else {
                TextButton(onClick = onImport, enabled = !state.isImporting, modifier = Modifier.testTag("riot_import")) {
                    Text(stringResource(R.string.riot_import))
                }
            }
        },
        dismissButton = {
            if (!done) TextButton(onClick = onDismiss, enabled = !state.isImporting) { Text(stringResource(R.string.edit_cancel)) }
        },
    )
}

@Composable
private fun ResultText(result: ImportResult) {
    when (result) {
        is ImportResult.Success -> Text(
            buildString {
                append(pluralStringResource(R.plurals.riot_imported, result.imported, result.imported))
                if (result.alreadyImported > 0) {
                    append(' ')
                    append(pluralStringResource(R.plurals.riot_already, result.alreadyImported, result.alreadyImported))
                }
                if (result.skipped > 0) {
                    append(' ')
                    append(pluralStringResource(R.plurals.riot_skipped, result.skipped, result.skipped))
                }
            },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("riot_result"),
        )

        is ImportResult.Failure -> Text(
            stringResource(
                when (result.error) {
                    ImportError.MISSING_API_KEY -> R.string.riot_error_missing_key
                    ImportError.INVALID_API_KEY -> R.string.riot_error_invalid_key
                    ImportError.ACCOUNT_NOT_FOUND -> R.string.riot_error_not_found
                    ImportError.RATE_LIMITED -> R.string.riot_error_rate_limited
                    ImportError.NETWORK -> R.string.riot_error_network
                },
            ),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("riot_result"),
        )
    }
}
