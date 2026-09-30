package app.unreel.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.unreel.data.Catalog
import app.unreel.data.CustomApps
import app.unreel.data.Prefs
import app.unreel.data.Rules
import app.unreel.data.Sites
import app.unreel.data.UsageStore
import app.unreel.data.nameOf
import java.util.Date

/** Full-screen stop shown when a rule kicks in. */
class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APP = "app"
        const val EXTRA_REASON = "reason"
        const val EXTRA_UNTIL = "until"
        const val REASON_LIMIT = "limit"
        const val REASON_APP = "app_blocked"
        const val REASON_SCHEDULE = "schedule"
        const val REASON_BREAK = "break"

        fun intent(context: Context, id: String, reason: String, until: Long = 0L): Intent =
            Intent(context, BlockedActivity::class.java)
                .putExtra(EXTRA_APP, id)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_UNTIL, until)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getStringExtra(EXTRA_APP) ?: ""
        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_LIMIT
        val until = intent.getLongExtra(EXTRA_UNTIL, 0L)
        val isSite = Sites.isSiteId(id)
        val name = if (id.isEmpty()) "This app" else nameOf(id)
        val untilText = if (until > 0L) DateFormat.getTimeFormat(this).format(Date(until)) else ""

        val title: String
        val body: String
        when (reason) {
            REASON_APP -> {
                title = "$name is blocked"
                body = "$name is all short-form video, so Unreel keeps it closed. You can change this in the Limits tab."
            }
            REASON_SCHEDULE -> {
                title = "$name is off right now"
                body = "You've blocked $name at this time of day. It's available again at $untilText."
            }
            REASON_BREAK -> {
                title = "Time for a break"
                body = "You've reached your ${Rules.fmt(Rules.get(id).sessionMinutes)} session limit for $name. " +
                    "It's available again at $untilText."
            }
            else -> {
                title = "That's your $name time for today"
                body = "You set a daily limit of ${Rules.fmt(Prefs.limitMinutes(id))}. It resets at midnight." +
                    if (isSite) " Other websites still work." else ""
            }
        }

        val extra = Prefs.extraMinutes
        val maxExt = Prefs.maxExtensions
        val extAllowed = maxExt == 0 || UsageStore.extensionsUsed(id) < maxExt
        val onMore: (() -> Unit)? =
            if (reason == REASON_LIMIT && id.isNotEmpty() && extra > 0 && extAllowed && !Prefs.strictMode) {
                {
                    UsageStore.addBonus(id, extra)
                    UsageStore.addExtension(id)
                    reopen(id)
                    finish()
                }
            } else {
                null
            }

        // For a website the browser underneath is on a neutral page, so just close this screen.
        val onClose: () -> Unit = if (isSite) ({ finish() }) else ({ goHome() })

        setContent {
            UnreelTheme {
                BlockedScreen(
                    title = title,
                    body = body,
                    closeLabel = if (isSite) "Close" else "Back to home screen",
                    moreLabel = "$extra more minutes",
                    onClose = onClose,
                    onMore = onMore,
                )
            }
        }
    }

    /** Reopens the app after "more time". For a website, closing returns to the browser. */
    private fun reopen(id: String) {
        val pkgs = when {
            CustomApps.isCustomId(id) -> listOf(CustomApps.pkgOf(id))
            else -> Catalog.byId(id)?.packages ?: emptyList()
        }
        val launch = pkgs.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
private fun BlockedScreen(
    title: String,
    body: String,
    closeLabel: String,
    moreLabel: String,
    onClose: () -> Unit,
    onMore: (() -> Unit)?,
) {
    BackHandler { onClose() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(closeLabel) }
            if (onMore != null) {
                TextButton(onClick = onMore) { Text(moreLabel) }
            }
        }
    }
}
