package app.unreel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.unreel.data.Catalog
import app.unreel.data.CustomApps
import app.unreel.data.Mode
import app.unreel.data.Prefs
import app.unreel.data.Rules
import app.unreel.data.UsageStore

@Composable
fun AppsScreen(gate: Gate) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val current = editing
    if (current != null) {
        RuleEditor(current, gate, onBack = { editing = null }, onRemoved = { editing = null })
        return
    }

    var showPicker by rememberSaveable { mutableStateOf(false) }
    if (showPicker) {
        AppPickerScreen(
            onCancel = { showPicker = false },
            onAdd = { picked ->
                picked.forEach { app ->
                    CustomApps.add(app.pkg, app.label)
                    val id = CustomApps.id(app.pkg)
                    if (Rules.get(id).isEmpty) Prefs.setLimitMinutes(id, 30)
                }
                showPicker = false
                if (picked.size == 1) editing = CustomApps.id(picked.first().pkg)
            },
        )
        return
    }

    val custom = CustomApps.list()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Limits", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tap anything to set a daily limit, session limit with breaks, or blocked times.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Social apps") {
            Catalog.apps.forEach { app ->
                val blockNote = when {
                    !Prefs.blockShortForm(app.id) -> null
                    app.mode == Mode.WHOLE_APP -> "Blocked"
                    else -> "${app.shortFormName} blocked"
                }
                val summary = Rules.summary(Rules.get(app.id))
                TrackedRow(
                    iconPkg = app.packages.first(),
                    name = app.name,
                    subtitle = listOfNotNull(blockNote, summary.takeIf { it != "No limits" || blockNote == null })
                        .joinToString(" · "),
                    id = app.id,
                    onClick = { editing = app.id },
                )
            }
        }

        WebsitesCard(onOpen = { editing = it })

        SectionCard("Your apps") {
            if (custom.isEmpty()) {
                Text(
                    "Add any app on your phone, like games, Discord, Netflix or X.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            custom.forEach { pkg ->
                val id = CustomApps.id(pkg)
                TrackedRow(
                    iconPkg = pkg,
                    name = Prefs.appLabel(pkg),
                    subtitle = Rules.summary(Rules.get(id)),
                    id = id,
                    onClick = { editing = id },
                )
            }
            OutlinedButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add apps")
            }
        }
    }
}

/** One app or site in a list: icon, name, rule summary, today's time. */
@Composable
fun TrackedRow(
    iconPkg: String?,
    name: String,
    subtitle: String,
    id: String,
    onClick: () -> Unit,
    iconSize: Dp = 36.dp,
    letter: String? = null,
) {
    val used = (UsageStore.seconds(id) / 60L).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPkg != null) {
            AppIcon(iconPkg, iconSize)
            Spacer(Modifier.width(12.dp))
        } else if (letter != null) {
            Box(
                Modifier
                    .size(iconSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Rules.fmt(used),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
