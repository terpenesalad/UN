package app.unreel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.unreel.data.Rules
import app.unreel.data.Sites
import app.unreel.guard.SiteMatcher

/** Websites section of the Limits screen. Tapping a site opens its settings. */
@Composable
fun WebsitesCard(onOpen: (String) -> Unit) {
    var sites by remember { mutableStateOf(Sites.list()) }
    var input by remember { mutableStateOf("") }

    fun add(raw: String) {
        val site = SiteMatcher.normalize(raw).substringBefore('/')
        if (site.contains('.') && !site.contains(' ')) {
            Sites.add(site)
            sites = Sites.list()
            input = ""
        }
    }

    SectionCard("Websites") {
        Text(
            "Limits for sites you visit in a browser (Firefox, Brave, Chrome, Samsung Internet, Edge, Opera, " +
                "DuckDuckGo). When time's up, the browser switches to a new Google tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        sites.forEach { site ->
            val id = Sites.id(site)
            TrackedRow(
                iconPkg = null,
                name = site,
                subtitle = Rules.summary(Rules.get(id)),
                id = id,
                onClick = { onOpen(id) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Add a site, e.g. reddit.com") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(input) }),
            )
            IconButton(onClick = { add(input) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add site")
            }
        }

        val quick = Sites.suggestions.filter { it !in sites }
        if (quick.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                quick.forEach { s ->
                    AssistChip(onClick = { add(s) }, label = { Text("+ $s") })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}
