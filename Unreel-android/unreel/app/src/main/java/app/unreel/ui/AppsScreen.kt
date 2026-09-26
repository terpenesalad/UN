package app.unreel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.unreel.data.Catalog
import app.unreel.data.Mode
import app.unreel.data.Prefs
import app.unreel.data.SocialApp

private val LIMIT_OPTIONS = listOf(0, 15, 30, 45, 60, 90, 120)

@Composable
fun AppsScreen(gate: Gate) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Apps", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Choose what to block and set daily limits. Everything else in these apps keeps working.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Catalog.apps.forEach { AppCard(it, gate) }
    }
}

@Composable
private fun AppCard(app: SocialApp, gate: Gate) {
    var block by remember { mutableStateOf(Prefs.blockShortForm(app.id)) }
    var limit by remember { mutableIntStateOf(Prefs.limitMinutes(app.id)) }
    val wholeApp = app.mode == Mode.WHOLE_APP

    SectionCard(app.name) {
        if (app.experimental) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "Beta: detection may miss some screens",
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        SettingSwitch(
            title = if (wholeApp) "Block ${app.name}" else "Block ${app.shortFormName}",
            description = if (wholeApp) {
                "${app.name} is all short-form video, so the whole app stays closed while this is on."
            } else {
                "Closes the ${app.shortFormName} viewer the moment it opens. Feed, stories and messages stay."
            },
            checked = block,
        ) { on ->
            if (on) {
                Prefs.setBlockShortForm(app.id, true)
                block = true
            } else {
                gate.loosen {
                    Prefs.setBlockShortForm(app.id, false)
                    block = false
                }
            }
        }

        Text("Daily limit", style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIMIT_OPTIONS.forEach { option ->
                FilterChip(
                    selected = limit == option,
                    onClick = {
                        if (option != limit) {
                            val loosening = option == 0 || (limit != 0 && option > limit)
                            val apply: () -> Unit = {
                                Prefs.setLimitMinutes(app.id, option)
                                limit = option
                            }
                            if (loosening) gate.loosen(apply) else apply()
                        }
                    },
                    label = { Text(if (option == 0) "None" else formatMinutes(option)) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
