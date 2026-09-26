package app.unreel.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import app.unreel.data.Prefs
import app.unreel.data.SocialApp
import app.unreel.data.UsageStore

/** Full-screen stop shown when a limit is reached or a blocked app is opened. */
class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APP = "app"
        const val EXTRA_REASON = "reason"
        const val REASON_LIMIT = "limit"
        const val REASON_APP = "app_blocked"

        fun intent(context: Context, appId: String, reason: String): Intent =
            Intent(context, BlockedActivity::class.java)
                .putExtra(EXTRA_APP, appId)
                .putExtra(EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = Catalog.byId(intent.getStringExtra(EXTRA_APP))
        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_LIMIT

        val onMore: (() -> Unit)? =
            if (app != null && reason == REASON_LIMIT && !Prefs.strictMode) {
                {
                    UsageStore.addBonus(app.id, 5)
                    val launch = app.packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
                    if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
            } else {
                null
            }

        setContent {
            UnreelTheme {
                BlockedScreen(app, reason, onHome = { goHome() }, onMore = onMore)
            }
        }
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
private fun BlockedScreen(app: SocialApp?, reason: String, onHome: () -> Unit, onMore: (() -> Unit)?) {
    BackHandler { onHome() }
    val name = app?.name ?: "This app"
    val limit = app?.let { Prefs.limitMinutes(it.id) } ?: 0

    val title = if (reason == BlockedActivity.REASON_APP) "$name is blocked" else "That's your $name time for today"
    val body = if (reason == BlockedActivity.REASON_APP) {
        "$name is all short-form video, so Unreel keeps it closed. You can change this in Unreel's Apps tab."
    } else {
        "You set a daily limit of ${formatMinutes(limit)}. It resets at midnight. " +
            "Messages from friends will still be there tomorrow."
    }

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
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Back to home screen") }
            if (onMore != null) {
                TextButton(onClick = onMore) { Text("5 more minutes") }
            }
        }
    }
}
