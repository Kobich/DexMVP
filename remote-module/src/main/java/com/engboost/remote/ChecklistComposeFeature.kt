package com.engboost.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteEvent
import com.engboost.remoteapi.RemoteHost
import com.engboost.remoteapi.RemoteInput

class ChecklistComposeFeature : RemoteComposeFeature {
    override val id: String = "checklist-compose"
    override val version: Int = 1

    @Composable
    override fun Content(input: RemoteInput, host: RemoteHost) {
        var serverReady by remember { mutableStateOf(true) }
        var artifactVerified by remember { mutableStateOf(true) }
        var composeLoaded by remember { mutableStateOf(false) }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Remote Checklist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                ChecklistRow("Server manifest loaded", serverReady) {
                    serverReady = it
                    host.emit(RemoteEvent(type = "checklist", message = "Server item = $it"))
                }
                ChecklistRow("Artifact hash verified", artifactVerified) {
                    artifactVerified = it
                    host.emit(RemoteEvent(type = "checklist", message = "Hash item = $it"))
                }
                ChecklistRow("Remote Compose rendered", composeLoaded) {
                    composeLoaded = it
                    host.emit(RemoteEvent(type = "checklist", message = "Compose item = $it"))
                }
                Text("Input timestamp: ${input.timestampMillis}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun ChecklistRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

