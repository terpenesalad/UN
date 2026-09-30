package app.unreel.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.unreel.data.Catalog
import app.unreel.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val pkg: String, val label: String)

/** Every app with a launcher icon, except Unreel and the built-in social apps (they're listed already). */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val builtIn = Catalog.apps.flatMap { it.packages }.toSet()
    // Home-screen apps are excluded: limiting the launcher would lock you out of your phone.
    val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        .map { it.activityInfo.packageName }
        .toSet()
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { (pkg, _) -> pkg != context.packageName && pkg !in builtIn && pkg !in launchers }
        .map { (pkg, label) -> InstalledApp(pkg, label) }
        .sortedBy { it.label.lowercase() }
}

/**
 * Full-screen, searchable, multi-select list of the phone's apps. Shown in place of the
 * Limits screen (not as a pop-up), with the Add button in the header so it's always visible.
 */
@Composable
fun AppPickerScreen(onCancel: () -> Unit, onAdd: (List<InstalledApp>) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmLeave by remember { mutableStateOf(false) }
    val already = remember { Prefs.customApps }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { loadInstalledApps(context) }
    }

    fun add() {
        onAdd(apps.orEmpty().filter { it.pkg in selected })
    }

    fun leave() {
        if (selected.isEmpty()) onCancel() else confirmLeave = true
    }

    BackHandler { leave() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
            Text(
                "Choose apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Button(enabled = selected.isNotEmpty(), onClick = { add() }) {
                Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val list = apps
        if (list == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val shown = list.filter {
                query.isBlank() || it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.pkg }) { app ->
                    val added = app.pkg in already
                    val checked = added || app.pkg in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !added) {
                                selected = if (app.pkg in selected) selected - app.pkg else selected + app.pkg
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            if (added) {
                                Text(
                                    "Already added",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Checkbox(checked = checked, onCheckedChange = null, enabled = !added)
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Add ${selected.size} selected app${if (selected.size == 1) "" else "s"}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    add()
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onCancel()
                }) { Text("Discard") }
            },
        )
    }
}
