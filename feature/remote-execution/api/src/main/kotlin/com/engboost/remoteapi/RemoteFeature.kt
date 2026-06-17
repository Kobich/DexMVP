package com.engboost.remoteapi

interface RemoteFeature {
    val id: String
    val version: Int

    fun execute(input: RemoteInput): RemoteOutput
}

data class RemoteInput(
    val text: String,
    val timestampMillis: Long,
)

data class RemoteOutput(
    val title: String,
    val message: String,
)

