package app.unreel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.unreel.data.Catalog
import app.unreel.data.CustomApps
import app.unreel.data.Mode
import app.unreel.data.Prefs
import app.unreel.data.Rule
import app.unreel.data.Rules
import app.unreel.data.Sites
import app.unreel.data.UsageStore
import app.unreel.data.Window
import app.unreel.data.nameOf

private val DAILY_PRESETS = listOf(15, 30, 45, 60, 90, 120)
private val SESSION_PRESETS = listOf(5, 10, 15, 20, 30, 45)
private val BREAK_PRESETS = listOf(5, 10, 15, 30, 60)

private fun looser(old: Int, new: Int) = old > 0 && (new == 0 || new > old)

/** Everything you can set for one app or website. */
@Composable
fun RuleEditor(id: String, gate: Gate, onBack: () -> Unit, onRemoved: () -> Unit) {
    BackHandler { onBack() }
    var rule by remember(id) { mutableStateOf(Rules.get(id)) }
    var editingWindow by remember { mutableStateOf<Int?>(null) } // index, or -1 for new
    val builtIn = Catalog.byId(id)
    val isSite = Sites.isSiteId(id)
    val isCustom = CustomApps.isCustomId(id)
    val iconPkg = when {
        isCustom -> CustomApps.pkgOf(id)
        builtIn != null -> builtIn.packages.first()
        else -> null
    }
    val usedToday = (UsageStore.seconds(id) / 60L).toInt()

    fun update(new: Rule, loosening: Boolean) {
        val apply = {
            Rules.set(id, new)
            rule = new
        }
        if (loosening) gate.loosen(apply) else apply()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            if (iconPkg != null) {
                AppIcon(iconPkg, 36.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(nameOf(id), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Rules.fmt(usedToday)} used today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (builtIn != null) {
            var block by remember(id) { mutableStateOf(Prefs.blockShortForm(builtIn.id)) }
            val wholeApp = builtIn.mode == Mode.WHOLE_APP
            SectionCard(if (wholeApp) "Block app" else "Short-form video") {
                SettingSwitch(
                    title = if (wholeApp) "Block ${builtIn.name}" else "Block ${builtIn.shortFormName}",
                    description = if (wholeApp) {
                        "${builtIn.name} is all short-form video, so the whole app stays closed while this is on."
                    } else {
                        "Closes the ${builtIn.shortFormName} viewer the moment it opens. Feed, stories and messages stay." +
                            if (builtIn.experimental) " (Beta: may miss some screens.)" else ""
                    },
                    checked = block,
                ) { on ->
                    if (on) {
                        Prefs.setBlockShortForm(builtIn.id, true)
                        block = true
                    } else {
                        gate.loosen {
                            Prefs.setBlockShortForm(builtIn.id, false)
                            block = false
                        }
                    }
                }
            }
        }

        SectionCard("Daily limit") {
            Text(
                "Total time allowed per day. Resets at midnight.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DurationPicker(
                value = rule.dailyMinutes,
                presets = DAILY_PRESETS,
                noneLabel = "None",
                dialogTitle = "Daily limit",
            ) { v ->
                if (v != rule.dailyMinutes) update(rule.copy(dailyMinutes = v), looser(rule.dailyMinutes, v))
            }
        }

        SectionCard("Session limit") {
            Text(
                "Longest you can use it in one go before a forced break. A session ends after 5 minutes away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DurationPicker(
                value = rule.sessionMinutes,
                presets = SESSION_PRESETS,
                noneLabel = "None",
                dialogTitle = "Session limit",
                maxMinutes = 12 * 60,
            ) { v ->
                if (v != rule.sessionMinutes) update(rule.copy(sessionMinutes = v), looser(rule.sessionMinutes, v))
            }
            if (rule.sessionMinutes > 0) {
                Text("Break length", style = MaterialTheme.typography.bodyLarge)
                DurationPicker(
                    value = rule.breakMinutes,
                    presets = BREAK_PRESETS,
                    noneLabel = null,
                    dialogTitle = "Break length",
                    maxMinutes = 12 * 60,
                ) { v ->
                    if (v != rule.breakMinutes) update(rule.copy(breakMinutes = v), v < rule.breakMinutes)
                }
            }
        }

        SectionCard("Blocked times") {
            Text(
                "Times of day when it's off completely, like bedtime or work hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rule.windows.forEachIndexed { i, w ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Rules.clock(w.startMin)} – ${Rules.clock(w.endMin)}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            daysSummary(w.days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editingWindow = i }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = {
                        update(rule.copy(windows = rule.windows.filterIndexed { j, _ -> j != i }), true)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
            OutlinedButton(onClick = { editingWindow = -1 }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add blocked time")
            }
        }

        if (isSite || isCustom) {
            TextButton(
                onClick = {
                    gate.loosen {
                        if (isSite) Sites.remove(Sites.nameOf(id)) else CustomApps.remove(CustomApps.pkgOf(id))
                        onRemoved()
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (isSite) "Remove this website" else "Remove this app")
            }
        } else if (!rule.isEmpty) {
            TextButton(onClick = { update(Rule(), true) }) { Text("Clear all limits") }
        }
        Spacer(Modifier.padding(8.dp))
    }

    val idx = editingWindow
    if (idx != null) {
        WindowDialog(
            initial = if (idx >= 0) rule.windows.getOrNull(idx) else null,
            onDismiss = { editingWindow = null },
            onSave = { w: Window ->
                editingWindow = null
                if (idx >= 0) {
                    update(rule.copy(windows = rule.windows.mapIndexed { j, old -> if (j == idx) w else old }), true)
                } else {
                    update(rule.copy(windows = rule.windows + w), false)
                }
            },
        )
    }
}
