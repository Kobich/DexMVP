package com.engboost.dexmvp.remoteexecution.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.engboost.dexmvp.loader.RemoteModuleManifest
import com.engboost.dexmvp.loader.RemoteModuleRepository
import com.engboost.remoteapi.RemoteInput
import kotlinx.coroutines.launch
import java.io.File

private data class RemoteExecutionDemoState(
    val serverUrl: String = "http://10.0.2.2:8080",
    val isBusy: Boolean = false,
    val manifest: RemoteModuleManifest? = null,
    val artifactPath: String? = null,
    val resultTitle: String? = null,
    val resultMessage: String? = null,
    val error: String? = null,
    val events: List<String> = listOf("Ready"),
)

@Composable
fun RemoteExecutionDemoScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(RemoteExecutionDemoState()) }

    fun appendEvent(message: String) {
        state = state.copy(events = (listOf(message) + state.events).take(8))
    }

    fun repository(): RemoteModuleRepository {
        return RemoteModuleRepository(context, state.serverUrl)
    }

    fun runAsync(label: String, block: suspend () -> RemoteExecutionDemoState) {
        scope.launch {
            state = state.copy(isBusy = true, error = null)
            appendEvent("$label started")
            try {
                state = block().copy(isBusy = false, error = null)
                appendEvent("$label complete")
            } catch (error: Throwable) {
                state = state.copy(isBusy = false, error = error.message ?: error.toString())
                appendEvent("$label failed")
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Dex Remote MVP",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = { state = state.copy(serverUrl = it) },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !state.isBusy,
                    onClick = {
                        runAsync("Check") {
                            val manifest = repository().fetchManifest()
                            state.copy(manifest = manifest, resultTitle = null, resultMessage = null)
                        }
                    },
                ) {
                    Text("Check")
                }

                Button(
                    enabled = !state.isBusy,
                    onClick = {
                        runAsync("Download") {
                            val manifest = state.manifest ?: repository().fetchManifest()
                            val artifact = repository().downloadAndVerify(manifest)
                            state.copy(manifest = manifest, artifactPath = artifact.absolutePath)
                        }
                    },
                ) {
                    Text("Download")
                }

                OutlinedButton(
                    enabled = !state.isBusy,
                    onClick = {
                        runAsync("Run") {
                            val manifest = state.manifest ?: error("Check or Download module first")
                            val artifactPath = state.artifactPath ?: error("Download module first")
                            val output = repository().run(
                                manifest = manifest,
                                artifact = File(artifactPath),
                                input = RemoteInput(
                                    text = "Host call from DexMVP",
                                    timestampMillis = System.currentTimeMillis(),
                                ),
                            )
                            state.copy(resultTitle = output.title, resultMessage = output.message)
                        }
                    },
                ) {
                    Text("Run")
                }
            }

            state.error?.let { ErrorCard(it) }
            state.manifest?.let { ManifestCard(it, state.artifactPath) }
            ResultCard(state.resultTitle, state.resultMessage)
            EventLogCard(state.events)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Error", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ManifestCard(manifest: RemoteModuleManifest, artifactPath: String?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Active Module", fontWeight = FontWeight.SemiBold)
            InfoLine("moduleId", manifest.moduleId)
            InfoLine("version", manifest.version.toString())
            InfoLine("minHostApi", manifest.minHostApi.toString())
            InfoLine("entryPoint", manifest.entryPoint)
            InfoLine("sha256", manifest.sha256)
            InfoLine("artifact", artifactPath ?: "not downloaded")
        }
    }
}

@Composable
private fun ResultCard(title: String?, message: String?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Execution Result", fontWeight = FontWeight.SemiBold)
            Text(title ?: "No result yet", style = MaterialTheme.typography.titleMedium)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EventLogCard(events: List<String>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Event Log", fontWeight = FontWeight.SemiBold)
            events.forEach { event ->
                Text(event, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
