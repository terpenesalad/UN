package app.unreel.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.unreel.data.Prefs
import app.unreel.data.UsageStore
import app.unreel.filter.DnsFilterService
import app.unreel.guard.SiteMatcher

@Composable
fun SettingsScreen(gate: Gate) {
    val context = LocalContext.current
    var strict by remember { mutableStateOf(Prefs.strictMode) }
    var browserShortForm by remember { mutableStateOf(Prefs.browserShortForm) }
    var sites by remember { mutableStateOf(Prefs.blockedSites.sorted()) }
    var dnsOn by remember { mutableStateOf(Prefs.dnsFilter) }
    var newSite by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DnsFilterService.start(context)
            Prefs.dnsFilter = true
            dnsOn = true
        }
    }

    fun addSite() {
        val cleaned = SiteMatcher.normalize(newSite)
        if (cleaned.isNotBlank() && cleaned.contains('.') && !cleaned.contains(' ')) {
            Prefs.blockedSites = Prefs.blockedSites + cleaned
            sites = Prefs.blockedSites.sorted()
            newSite = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionCard("Strict mode") {
            SettingSwitch(
                title = "Pause before loosening",
                description = "Turning off a block, raising a limit or removing a site makes you wait " +
                    "$STRICT_WAIT_SECONDS seconds first. The urge usually passes.",
                checked = strict,
            ) { on ->
                if (on) {
                    Prefs.strictMode = true
                    strict = true
                } else {
                    gate.loosen {
                        Prefs.strictMode = false
                        strict = false
                    }
                }
            }
        }

        SectionCard("Browsers") {
            SettingSwitch(
                title = "Block short-form sites",
                description = "Stops YouTube Shorts, Instagram and Facebook Reels, and TikTok in Chrome, Firefox, " +
                    "Samsung Internet, Edge, Brave, Opera and DuckDuckGo.",
                checked = browserShortForm,
            ) { on ->
                if (on) {
                    Prefs.browserShortForm = true
                    browserShortForm = true
                } else {
                    gate.loosen {
                        Prefs.browserShortForm = false
                        browserShortForm = false
                    }
                }
            }
            HorizontalDivider()
            Text("Blocked websites", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSite,
                    onValueChange = { newSite = it },
                    placeholder = { Text("e.g. reddit.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addSite() }),
                )
                IconButton(onClick = { addSite() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add site")
                }
            }
            if (sites.isEmpty()) {
                Text(
                    "No websites blocked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sites.forEach { site ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(site, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = {
                        gate.loosen {
                            Prefs.blockedSites = Prefs.blockedSites - site
                            sites = Prefs.blockedSites.sorted()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove $site")
                    }
                }
            }
        }

        SectionCard("Adult content filter") {
            SettingSwitch(
                title = "Filter adult websites",
                description = "Sends DNS lookups (website names only, not your traffic) to Cloudflare for " +
                    "Families, which blocks adult and malware sites in every app and browser. Android shows " +
                    "a key icon while it's on. Only one VPN can run at a time.",
                checked = dnsOn,
            ) { on ->
                if (on) {
                    val consent = VpnService.prepare(context)
                    if (consent != null) {
                        vpnPermission.launch(consent)
                    } else {
                        DnsFilterService.start(context)
                        Prefs.dnsFilter = true
                        dnsOn = true
                    }
                } else {
                    gate.loosen {
                        DnsFilterService.stop(context)
                        Prefs.dnsFilter = false
                        dnsOn = false
                    }
                }
            }
        }

        SectionCard("Your data") {
            Text(
                "Everything Unreel knows stays on this phone. No account, no servers, no tracking.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset statistics") }
        }

        Column(Modifier.padding(bottom = 8.dp)) {
            Text(
                "Unreel 1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset statistics?") },
            text = { Text("This clears your usage history and blocked counts. Settings stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    // Resetting also clears today's usage, which would lift limits.
                    gate.loosen { UsageStore.reset() }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}
