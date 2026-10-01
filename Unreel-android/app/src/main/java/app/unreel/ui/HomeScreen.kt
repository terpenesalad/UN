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
import androidx.compose.foundation.layout.size
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

/** One day in the weekly chart: total screen time, and the part spent in tracked apps/sites. */
private data class DayUse(val label: String, val screen: Int, val tracked: Int)

private data class HomeSnapshot(
    val guardOn: Boolean,
    val screenToday: Int,
    val screenYesterday: Int,
    val unlocksToday: Int,
    val totalToday: Int,
    val blocksToday: Int,
    val week: List<DayUse>,
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
        val trackedMin = (tracked.sumOf { UsageStore.seconds(it.id, date.toString()) } / 60L).toInt()
        val screenMin = (UsageStore.seconds(UsageStore.SCREEN, date.toString()) / 60L).toInt()
        // Days before screen time was recorded only have tracked time; never show screen < tracked.
        DayUse(label, maxOf(screenMin, trackedMin), trackedMin)
    }
    return HomeSnapshot(
        guardOn = GuardStatus.isEnabled(context),
        screenToday = week.last().screen,
        screenYesterday = week[week.size - 2].screen,
        unlocksToday = UsageStore.unlocks(),
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

        ScreenTimeCard(snap.screenToday, snap.screenYesterday, snap.unlocksToday)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Tracked apps & sites", formatMinutes(snap.totalToday), Modifier.weight(1f))
            StatTile("Short-form blocked", snap.blocksToday.toString(), Modifier.weight(1f))
        }

        SectionCard("Screen time, last 7 days") {
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

/** Hero card: total unlocked screen time today, change vs yesterday, and unlock count. */
@Composable
private fun ScreenTimeCard(today: Int, yesterday: Int, unlocks: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Screen time today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatMinutes(today), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            val diff = today - yesterday
            val compare = when {
                yesterday == 0 -> "Phone unlocked and in use"
                diff == 0 -> "Same as yesterday so far"
                diff < 0 -> "${formatMinutes(-diff)} less than yesterday so far"
                else -> "${formatMinutes(diff)} more than all of yesterday"
            }
            Text(compare, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$unlocks unlock${if (unlocks == 1) "" else "s"} today",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * One bar per day = total screen time. The bottom segment (solid) is time in tracked
 * apps and sites; the top segment (light) is everything else. Same hue, two shades,
 * separated by a 2dp surface gap; legend below so identity isn't colour-alone.
 */
@Composable
private fun WeekChart(week: List<DayUse>) {
    val trackedColor = MaterialTheme.colorScheme.primary
    val otherColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val max = maxOf(1, week.maxOfOrNull { it.screen } ?: 1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            week.forEach {
                Text(
                    if (it.screen > 0) formatMinutes(it.screen) else "",
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
            val gap = 2.dp.toPx()
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            week.forEachIndexed { i, day ->
                val x = i * slot + (slot - barWidth) / 2f
                val total = if (day.screen == 0) 0f else maxOf(4f, size.height * day.screen / max)
                val trackedH = if (day.screen == 0) 0f else size.height * day.tracked / max
                if (total == 0f) {
                    drawRoundRect(otherColor, Offset(x, size.height - 4f), Size(barWidth, 4f), radius)
                    return@forEachIndexed
                }
                val otherH = total - trackedH
                if (otherH > gap) {
                    drawRoundRect(otherColor, Offset(x, size.height - total), Size(barWidth, otherH - gap), radius)
                }
                if (trackedH > 0f) {
                    drawRoundRect(trackedColor, Offset(x, size.height - trackedH), Size(barWidth, trackedH), radius)
                }
            }
        }
        Row {
            week.forEachIndexed { i, day ->
                Text(
                    day.label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == week.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(trackedColor, "Tracked apps & sites")
            LegendItem(otherColor, "Everything else")
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
