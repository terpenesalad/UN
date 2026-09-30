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
import kotlinx.coroutines.delay
import app.unreel.guard.GuardStatus
import app.unreel.guard.GuardService
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
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
    var extraMinutes by remember { mutableIntStateOf(Prefs.extraMinutes) }
    var maxExtensions by remember { mutableIntStateOf(Prefs.maxExtensions) }
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

        SectionCard("\"More time\" button") {
            Text(
                "Shown when you hit a daily limit (hidden in strict mode).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Extra time per tap", style = MaterialTheme.typography.bodyLarge)
            DurationPicker(
                value = extraMinutes,
                presets = listOf(1, 5, 10, 15, 30),
                noneLabel = "Off",
                dialogTitle = "Extra time per tap",
                maxMinutes = 120,
            ) { v ->
                val apply = {
                    Prefs.extraMinutes = v
                    extraMinutes = v
                }
                if (v > extraMinutes) gate.loosen(apply) else apply()
            }
            if (extraMinutes > 0) {
                Text("Times per day, per app or site", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(1, 2, 3, 5, 0).forEach { n ->
                        FilterChip(
                            selected = maxExtensions == n,
                            onClick = {
                                val apply = {
                                    Prefs.maxExtensions = n
                                    maxExtensions = n
                                }
                                val looser = n == 0 || (maxExtensions != 0 && n > maxExtensions)
                                if (looser) gate.loosen(apply) else apply()
                            },
                            label = { Text(if (n == 0) "Unlimited" else n.toString()) },
                        )
                        Spacer(Modifier.width(8.dp))
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

        TroubleshootingCard()

        SectionCard("Your data") {
            Text(
                "Everything Unreel knows stays on this phone. No account, no servers, no tracking.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset statistics") }
        }

        Column(Modifier.padding(bottom = 8.dp)) {
            Text(
                "Unreel 1.2.1",
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

/** Shows whether protection is running and what Unreel last read from a browser's address bar. */
@Composable
private fun TroubleshootingCard() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            now = System.currentTimeMillis()
        }
    }
    val running = remember(now) { GuardStatus.isEnabled(context) }
    val url = GuardService.lastBrowserUrl
    val at = GuardService.lastBrowserAt
    val pkg = GuardService.lastBrowserPkg
    val site = GuardService.lastBrowserSite

    SectionCard("Troubleshooting") {
        Text(
            if (running) "Protection is running." else "Protection is off. Turn it on from the Today tab.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Last address read from a browser", style = MaterialTheme.typography.bodyLarge)
        if (url == null || at == 0L) {
            Text(
                "Nothing yet. Open a website in your browser for a few seconds, then come back here. " +
                    "If this stays empty, Unreel can't read that browser's address bar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val secs = ((now - at) / 1000L).coerceAtLeast(0L)
            val ago = if (secs < 60) "${secs}s ago" else "${secs / 60}m ago"
            Text(url, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "In ${pkg ?: "?"} · via ${GuardService.lastBrowserHow ?: "?"} · $ago\n" +
                    if (site != null) "Matched your limit for $site, so time is being counted."
                    else "Didn't match any website in your Limits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
