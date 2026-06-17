package com.engboost.dexmvp.loader

import kotlinx.serialization.Serializable

@Serializable
data class RemoteModuleManifest(
    val moduleId: String,
    val version: Int,
    val hostApiVersion: Int,
    val minHostApi: Int,
    val entryPoint: String,
    val artifactUrl: String,
    val sha256: String,
    val signature: String = "",
)

