package app.unreel.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.unreel.data.Prefs
import app.unreel.data.allTracked
import app.unreel.data.UsageStore
import app.unreel.guard.GuardStatus
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private data class AppToday(val name: String, val minutes: Int, val limit: Int)

private data class HomeSnapshot(
    val guardOn: Boolean,
    val totalToday: Int,
    val blocksToday: Int,
    val week: List<Pair<String, Int>>,
    val perApp: List<AppToday>,
)

private fun takeSnapshot(context: Context): HomeSnapshot {
    val today = LocalDate.now()
    val tracked = allTracked()
    val perApp = tracked.map {
        AppToday(it.name, (UsageStore.seconds(it.id, today.toString()) / 60L).toInt(), Prefs.limitMinutes(it.id))
    }
    val week = (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val seconds = tracked.sumOf { UsageStore.seconds(it.id, date.toString()) }
        label to (seconds / 60L).toInt()
    }
    return HomeSnapshot(
        guardOn = GuardStatus.isEnabled(context),
        totalToday = perApp.sumOf { it.minutes },
        blocksToday = UsageStore.blocks(),
        week = week,
        perApp = perApp,
    )
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var snap by remember { mutableStateOf(takeSnapshot(context)) }
    var showDisclosure by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            snap = takeSnapshot(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Unreel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Your friends, minus the endless scroll.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (snap.guardOn) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Protection is on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Short-form video is blocked. Messages, stories and posts still work.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Protection is off", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Unreel needs its accessibility service to spot Reels and Shorts and enforce your limits.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { showDisclosure = true }) { Text("Turn on protection") }
                    Text(
                        "Switch greyed out? Android restricts this for apps installed outside the Play Store. " +
                            "Open App info, tap ⋮ in the corner, choose \"Allow restricted settings\", then try again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Open App info") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Social time today", formatMinutes(snap.totalToday), Modifier.weight(1f))
            StatTile("Short-form blocked", snap.blocksToday.toString(), Modifier.weight(1f))
        }

        SectionCard("Last 7 days") {
            WeekChart(snap.week)
        }

        SectionCard("Today by app and site") {
            snap.perApp.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (row.limit > 0) "${formatMinutes(row.minutes)} of ${formatMinutes(row.limit)}"
                            else formatMinutes(row.minutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (row.limit > 0) {
                        LinearProgressIndicator(
                            progress = { (row.minutes.toFloat() / row.limit).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Before you turn this on") },
            text = {
                Text(
                    "Unreel uses Android's Accessibility Service to see which app is on screen and whether a " +
                        "Reels or Shorts viewer is open, so it can close it and enforce your time limits.\n\n" +
                        "It reads screen structure only to make that decision. Nothing you see or type is recorded, " +
                        "stored or sent anywhere. Unreel has no servers.\n\n" +
                        "On the next screen, find Unreel under Installed apps and turn it on."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekChart(week: List<Pair<String, Int>>) {
    val barColor = MaterialTheme.colorScheme.primary
    val max = maxOf(1, week.maxOfOrNull { it.second } ?: 1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            week.forEach {
                Text(
                    if (it.second > 0) formatMinutes(it.second) else "",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val slot = size.width / week.size
            val barWidth = slot * 0.5f
            week.forEachIndexed { i, (_, minutes) ->
                val h = maxOf(4f, size.height * minutes / max)
                val isToday = i == week.lastIndex
                drawRoundRect(
                    color = if (isToday) barColor else barColor.copy(alpha = 0.4f),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
        }
        Row {
            week.forEach {
                Text(
                    it.first,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
