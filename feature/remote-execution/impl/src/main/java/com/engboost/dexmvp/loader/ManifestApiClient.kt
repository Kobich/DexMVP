package com.engboost.dexmvp.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class ManifestApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun fetchActiveManifest(): RemoteModuleManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/modules/active")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Manifest request failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: error("Manifest response is empty")
            json.decodeFromString(RemoteModuleManifest.serializer(), body)
        }
    }
}

