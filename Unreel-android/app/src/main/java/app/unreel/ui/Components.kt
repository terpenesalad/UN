package app.unreel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.unreel.data.Prefs
import kotlinx.coroutines.delay

const val STRICT_WAIT_SECONDS = 60

fun formatMinutes(m: Int): String = if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"

/**
 * Strict mode: any change that loosens a rule goes through [loosen],
 * which makes the user wait before confirming.
 */
class Gate {
    var pending by mutableStateOf<(() -> Unit)?>(null)

    fun loosen(action: () -> Unit) {
        if (Prefs.strictMode) pending = action else action()
    }
}

@Composable
fun rememberGate(): Gate = remember { Gate() }

@Composable
fun GateDialog(gate: Gate) {
    val action = gate.pending ?: return
    var left by remember(action) { mutableIntStateOf(STRICT_WAIT_SECONDS) }
    LaunchedEffect(action) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    AlertDialog(
        onDismissRequest = { gate.pending = null },
        title = { Text("Strict mode is on") },
        text = {
            Text(
                if (left > 0) "Take a breath. You can confirm this change in $left seconds."
                else "Still want to make this change?"
            )
        },
        confirmButton = {
            TextButton(
                enabled = left == 0,
                onClick = {
                    action()
                    gate.pending = null
                },
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = { gate.pending = null }) { Text("Keep it") }
        },
    )
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
