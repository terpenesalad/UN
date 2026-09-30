package app.unreel.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.unreel.data.Rules
import app.unreel.data.Window

/** Dialog to enter any duration as hours + minutes. */
@Composable
fun DurationDialog(
    title: String,
    initialMinutes: Int,
    maxMinutes: Int = 24 * 60,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hours by remember { mutableStateOf(if (initialMinutes > 0) (initialMinutes / 60).toString() else "") }
    var minutes by remember { mutableStateOf(if (initialMinutes > 0) (initialMinutes % 60).toString() else "") }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    val valid = total in 1..maxMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { v -> hours = v.filter { it.isDigit() }.take(2) },
                        label = { Text("Hours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (valid) "= ${Rules.fmt(total)}" else "Enter between 1 minute and ${Rules.fmt(maxMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(total) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A row of preset chips plus "Custom". [value] 0 means none (shown only when [noneLabel] is set).
 */
@Composable
fun DurationPicker(
    value: Int,
    presets: List<Int>,
    noneLabel: String?,
    dialogTitle: String,
    maxMinutes: Int = 24 * 60,
    onPick: (Int) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    val isCustom = value > 0 && value !in presets

    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        if (noneLabel != null) {
            FilterChip(selected = value == 0, onClick = { onPick(0) }, label = { Text(noneLabel) })
            Spacer(Modifier.width(8.dp))
        }
        presets.forEach { p ->
            FilterChip(selected = value == p, onClick = { onPick(p) }, label = { Text(Rules.fmt(p)) })
            Spacer(Modifier.width(8.dp))
        }
        FilterChip(
            selected = isCustom,
            onClick = { showCustom = true },
            label = { Text(if (isCustom) "Custom: ${Rules.fmt(value)}" else "Custom…") },
        )
    }

    if (showCustom) {
        DurationDialog(
            title = dialogTitle,
            initialMinutes = value,
            maxMinutes = maxMinutes,
            onDismiss = { showCustom = false },
            onConfirm = {
                showCustom = false
                onPick(it)
            },
        )
    }
}

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

fun daysSummary(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Every day"
    (1..5).toSet() -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(" ") { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it - 1] }
}

/** Create or edit a blocked-hours window. */
@Composable
fun WindowDialog(initial: Window?, onDismiss: () -> Unit, onSave: (Window) -> Unit) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    var start by remember { mutableIntStateOf(initial?.startMin ?: 22 * 60) }
    var end by remember { mutableIntStateOf(initial?.endMin ?: 7 * 60) }
    var days by remember { mutableStateOf(initial?.days ?: (1..7).toSet()) }

    fun pick(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, is24).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add blocked time" else "Edit blocked time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("From", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { pick(start) { start = it } }) { Text(Rules.clock(start)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Until", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { pick(end) { end = it } }) { Text(Rules.clock(end)) }
                    }
                }
                if (end <= start) {
                    Text(
                        if (end == start) "Blocked all day." else "Runs overnight into the next morning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("On", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        val on = d in days
                        Surface(
                            onClick = { days = if (on) days - d else days + d },
                            shape = RoundedCornerShape(50),
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(DAY_LETTERS[d - 1], style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty(), onClick = { onSave(Window(start, end, days)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An installed app's icon, or nothing if it can't be loaded. */
@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(pkg) {
        try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}
