package com.engboost.dexmvp.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ArtifactDownloader(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(url: String, destination: File): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        if (destination.exists()) {
            destination.delete()
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Artifact request failed: HTTP ${response.code}")
            }

            val body = response.body ?: error("Artifact response is empty")
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        destination
    }
}

