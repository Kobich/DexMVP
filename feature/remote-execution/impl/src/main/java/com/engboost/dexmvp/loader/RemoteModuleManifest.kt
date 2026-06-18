package com.engboost.dexmvp.loader

import kotlinx.serialization.Serializable

@Serializable
data class RemoteModuleManifest(
    val moduleId: String,
    val version: Int,
    val hostApiVersion: Int,
    val minHostApi: Int,
    val artifactUrl: String,
    val sha256: String,
    val signature: String = "",
    val features: List<RemoteFeatureManifest>,
)

@Serializable
data class RemoteFeatureManifest(
    val id: String,
    val title: String,
    val kind: String,
    val version: Int,
    val entryPoint: String,
)
