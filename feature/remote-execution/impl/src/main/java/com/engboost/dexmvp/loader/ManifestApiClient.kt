package com.engboost.dexmvp.loader

import com.engboost.dexmvp.transport.RemoteTransport
import kotlinx.serialization.json.Json

class ManifestApiClient(
    private val baseUrl: String,
    private val transport: RemoteTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchActiveManifest(): RemoteModuleManifest {
        val body = transport.getString("${baseUrl.trimEnd('/')}/api/v1/modules/active")
        return json.decodeFromString(RemoteModuleManifest.serializer(), body)
    }
}
