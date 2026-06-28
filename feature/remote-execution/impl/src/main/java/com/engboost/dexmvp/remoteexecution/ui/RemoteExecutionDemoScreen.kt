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
import com.engboost.dexmvp.loader.RemoteFeatureManifest
import com.engboost.dexmvp.loader.RemoteModuleManifest
import com.engboost.dexmvp.loader.RemoteModuleRepository
import com.engboost.dexmvp.transport.TransportDiagnostics
import com.engboost.dexmvp.transport.TransportDiagnosticsProvider
import com.engboost.dexmvp.transport.RemoteTransportFactory
import com.engboost.dexmvp.transport.TransportMode
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteEvent
import com.engboost.remoteapi.RemoteFeatureKind
import com.engboost.remoteapi.RemoteHost
import com.engboost.remoteapi.RemoteInput
import kotlinx.coroutines.launch
import java.io.File

private sealed interface RemoteExecutionRoute {
    data object Home : RemoteExecutionRoute
    data class Feature(val title: String) : RemoteExecutionRoute
}

private data class RemoteExecutionDemoState(
    val serverUrl: String = "http://10.0.2.2:8080",
    val transportMode: TransportMode = TransportMode.HTTP_FALLBACK,
    val route: RemoteExecutionRoute = RemoteExecutionRoute.Home,
    val isBusy: Boolean = false,
    val manifest: RemoteModuleManifest? = null,
    val artifactPath: String? = null,
    val selectedFeature: RemoteFeatureManifest? = null,
    val composeFeature: RemoteComposeFeature? = null,
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
        state = state.copy(events = (listOf(message) + state.events).take(10))
    }

    fun repository(): RemoteModuleRepository {
        return RemoteModuleRepository(
            context = context,
            serverBaseUrl = state.serverUrl,
            transport = RemoteTransportFactory.create(state.transportMode, context),
        )
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

    val remoteHost = remember {
        object : RemoteHost {
            override fun emit(event: RemoteEvent) {
                appendEvent("${event.type}: ${event.message}")
            }
        }
    }

    when (val route = state.route) {
        RemoteExecutionRoute.Home -> {
            RemoteExecutionHomeScreen(
                state = state,
                onServerUrlChange = { state = state.copy(serverUrl = it) },
                onTransportModeChange = { state = state.copy(transportMode = it) },
                onCheck = {
                    runAsync("Check") {
                        val manifest = repository().fetchManifest()
                        state.copy(
                            manifest = manifest,
                            selectedFeature = null,
                            composeFeature = null,
                            resultTitle = null,
                            resultMessage = null,
                        )
                    }
                },
                onDownload = {
                    runAsync("Download") {
                        val manifest = state.manifest ?: repository().fetchManifest()
                        val artifact = repository().downloadAndVerify(manifest)
                        state.copy(manifest = manifest, artifactPath = artifact.absolutePath)
                    }
                },
                onOpen = { feature ->
                    runAsync("Open ${feature.id}") {
                        val manifest = state.manifest ?: error("Check module first")
                        val artifactPath = state.artifactPath ?: error("Download module first")
                        val artifact = File(artifactPath)
                        if (feature.kind == RemoteFeatureKind.COMPOSE) {
                            val composeFeature = repository().loadCompose(manifest, feature, artifact)
                            state.copy(
                                route = RemoteExecutionRoute.Feature(feature.title),
                                selectedFeature = feature,
                                composeFeature = composeFeature,
                                resultTitle = null,
                                resultMessage = null,
                            )
                        } else {
                            val output = repository().runOutput(
                                manifest = manifest,
                                feature = feature,
                                artifact = artifact,
                                input = demoInput(),
                            )
                            state.copy(
                                route = RemoteExecutionRoute.Feature(feature.title),
                                selectedFeature = feature,
                                composeFeature = null,
                                resultTitle = output.title,
                                resultMessage = output.message,
                            )
                        }
                    }
                },
            )
        }

        is RemoteExecutionRoute.Feature -> {
            RemoteFeatureScreen(
                title = route.title,
                feature = state.selectedFeature,
                composeFeature = state.composeFeature,
                resultTitle = state.resultTitle,
                resultMessage = state.resultMessage,
                events = state.events,
                host = remoteHost,
                onBack = {
                    state = state.copy(
                        route = RemoteExecutionRoute.Home,
                        selectedFeature = null,
                        composeFeature = null,
                        resultTitle = null,
                        resultMessage = null,
                    )
                    appendEvent("Back to feature list")
                },
            )
        }
    }
}

@Composable
private fun RemoteExecutionHomeScreen(
    state: RemoteExecutionDemoState,
    onServerUrlChange: (String) -> Unit,
    onTransportModeChange: (TransportMode) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onOpen: (RemoteFeatureManifest) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val transportDiagnostics = remember(state.transportMode) {
        TransportDiagnosticsProvider.inspect(state.transportMode, appContext)
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
                onValueChange = onServerUrlChange,
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TransportModeCard(
                selected = state.transportMode,
                enabled = !state.isBusy,
                onSelected = onTransportModeChange,
            )

            TransportDiagnosticsCard(transportDiagnostics)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.isBusy, onClick = onCheck) {
                    Text("Check")
                }

                Button(enabled = !state.isBusy, onClick = onDownload) {
                    Text("Download")
                }
            }

            state.error?.let { ErrorCard(it) }
            state.manifest?.let { manifest ->
                ManifestCard(manifest, state.artifactPath)
                FeatureListCard(
                    features = manifest.features,
                    isBusy = state.isBusy,
                    canOpen = state.artifactPath != null,
                    onOpen = onOpen,
                )
            }
            EventLogCard(state.events)
        }
    }
}

@Composable
private fun TransportDiagnosticsCard(diagnostics: TransportDiagnostics) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Transport Diagnostics", fontWeight = FontWeight.SemiBold)
            InfoLine("mode", diagnostics.mode.label)
            InfoLine("transport", diagnostics.transport)
            InfoLine("tls", diagnostics.tlsVerification)
            InfoLine("native", diagnostics.nativeLayer)
            InfoLine("ca", diagnostics.caFilePath)
            InfoLine("engine", diagnostics.engine, maxLines = 6)
        }
    }
}

@Composable
private fun TransportModeCard(
    selected: TransportMode,
    enabled: Boolean,
    onSelected: (TransportMode) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Transport", fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportMode.entries.forEach { mode ->
                    val isSelected = mode == selected
                    if (isSelected) {
                        Button(
                            enabled = enabled,
                            onClick = { onSelected(mode) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(mode.label)
                        }
                    } else {
                        OutlinedButton(
                            enabled = enabled,
                            onClick = { onSelected(mode) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(mode.label)
                        }
                    }
                }
            }
            Text(
                text = when (selected) {
                    TransportMode.HTTP_FALLBACK -> "Uses current OkHttp transport."
                    TransportMode.HTTP3_PREFERRED -> "Tries local libcurl HTTP/3 first, then falls back to OkHttp."
                    TransportMode.HTTP3_ONLY -> "Uses local libcurl HTTP/3 only."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RemoteFeatureScreen(
    title: String,
    feature: RemoteFeatureManifest?,
    composeFeature: RemoteComposeFeature?,
    resultTitle: String?,
    resultMessage: String?,
    events: List<String>,
    host: RemoteHost,
    onBack: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    feature?.let {
                        Text(
                            "${it.kind} | ${it.entryPoint}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }

            if (composeFeature != null) {
                RemoteComposeContent(feature = composeFeature, host = host)
            } else {
                ResultCard(resultTitle, resultMessage)
            }

            EventLogCard(events)
        }
    }
}

private fun demoInput(): RemoteInput {
    return RemoteInput(
        text = "Host call from DexMVP",
        timestampMillis = System.currentTimeMillis(),
    )
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
            Text("Remote Artifact", fontWeight = FontWeight.SemiBold)
            InfoLine("moduleId", manifest.moduleId)
            InfoLine("version", manifest.version.toString())
            InfoLine("minHostApi", manifest.minHostApi.toString())
            InfoLine("features", manifest.features.size.toString())
            InfoLine("sha256", manifest.sha256)
            InfoLine("artifact", artifactPath ?: "not downloaded")
        }
    }
}

@Composable
private fun FeatureListCard(
    features: List<RemoteFeatureManifest>,
    isBusy: Boolean,
    canOpen: Boolean,
    onOpen: (RemoteFeatureManifest) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Remote Features", fontWeight = FontWeight.SemiBold)
            features.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feature.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${feature.kind} | ${feature.entryPoint}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        enabled = !isBusy && canOpen,
                        onClick = { onOpen(feature) },
                    ) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteComposeContent(
    feature: RemoteComposeFeature,
    host: RemoteHost,
) {
    feature.Content(input = demoInput(), host = host)
}

@Composable
private fun ResultCard(title: String?, message: String?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Output Feature Result", fontWeight = FontWeight.SemiBold)
            Text(title ?: "No output feature result yet", style = MaterialTheme.typography.titleMedium)
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
private fun InfoLine(label: String, value: String, maxLines: Int = 2) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
