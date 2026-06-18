package com.engboost.remoteapi

import androidx.compose.runtime.Composable

interface RemoteFeature {
    val id: String
    val version: Int

    fun execute(input: RemoteInput): RemoteOutput
}

interface RemoteComposeFeature {
    val id: String
    val version: Int

    @Composable
    fun Content(input: RemoteInput, host: RemoteHost)
}

interface RemoteHost {
    fun emit(event: RemoteEvent)
}

object RemoteFeatureKind {
    const val OUTPUT = "output"
    const val COMPOSE = "compose"
}

data class RemoteInput(
    val text: String,
    val timestampMillis: Long,
)

data class RemoteOutput(
    val title: String,
    val message: String,
)

data class RemoteEvent(
    val type: String,
    val message: String,
)
