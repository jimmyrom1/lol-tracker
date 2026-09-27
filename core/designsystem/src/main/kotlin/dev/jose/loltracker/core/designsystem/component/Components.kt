package dev.jose.loltracker.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.jose.loltracker.core.designsystem.R
import dev.jose.loltracker.core.designsystem.theme.LocalResultColors
import dev.jose.loltracker.core.model.MatchResult
import dev.jose.loltracker.core.model.Queue
import dev.jose.loltracker.core.model.Role
import java.util.Locale

/** Icono del campeón. Si no hay imagen (sin conexión), muestra sus iniciales. */
@Composable
fun ChampionAvatar(iconUrl: String?, name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(size / 4)
    val initials: @Composable () -> Unit = {
        Box(
            Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(2).uppercase(Locale.ROOT), style = MaterialTheme.typography.titleSmall)
        }
    }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        if (iconUrl.isNullOrBlank()) {
            initials()
        } else {
            SubcomposeAsyncImage(
                model = iconUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { initials() },
                error = { initials() },
            )
        }
    }
}

@Composable
fun ResultBadge(result: MatchResult, modifier: Modifier = Modifier) {
    val colors = LocalResultColors.current
    val (bg, fg, label) = when (result) {
        MatchResult.WIN -> Triple(colors.win, colors.onWin, stringResource(R.string.result_win))
        MatchResult.LOSS -> Triple(colors.loss, colors.onLoss, stringResource(R.string.result_loss))
    }
    Text(
        text = label,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Últimos resultados como puntos de color (el más reciente a la izquierda). */
@Composable
fun RecentForm(results: List<MatchResult>, modifier: Modifier = Modifier) {
    val colors = LocalResultColors.current
    val description = results.joinToString(" ") { if (it == MatchResult.WIN) "V" else "D" }
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        results.forEach { result ->
            val win = result == MatchResult.WIN
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (win) colors.win else colors.loss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (win) "V" else "D",
                    color = if (win) colors.onWin else colors.onLoss,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun Role.label(): String = stringResource(
    when (this) {
        Role.TOP -> R.string.role_top
        Role.JUNGLE -> R.string.role_jungle
        Role.MID -> R.string.role_mid
        Role.ADC -> R.string.role_adc
        Role.SUPPORT -> R.string.role_support
    },
)

@Composable
fun Queue.label(): String = stringResource(
    when (this) {
        Queue.RANKED_SOLO -> R.string.queue_ranked_solo
        Queue.RANKED_FLEX -> R.string.queue_ranked_flex
        Queue.NORMAL -> R.string.queue_normal
        Queue.ARAM -> R.string.queue_aram
        Queue.OTHER -> R.string.queue_other
    },
)

/** "3,45" con el separador decimal del idioma del dispositivo. */
fun Double.format(decimals: Int = 1): String = String.format(Locale.getDefault(), "%.${decimals}f", this)

fun Double.percent(): String = String.format(Locale.getDefault(), "%.0f %%", this * 100)
