package com.engboost.dexmvp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class OkHttpRemoteTransport(
    private val httpClient: OkHttpClient = OkHttpClient(),
) : RemoteTransport {
    override suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("GET request failed: HTTP ${response.code}")
            }

            response.body?.string() ?: error("GET response is empty")
        }
    }

    override suspend fun download(url: String, destination: File): File = withContext(Dispatchers.IO) {
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
                error("Download request failed: HTTP ${response.code}")
            }

            val body = response.body ?: error("Download response is empty")
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        destination
    }
}

