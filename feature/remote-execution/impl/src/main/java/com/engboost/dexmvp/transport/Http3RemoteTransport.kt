package com.engboost.dexmvp.transport

import com.engboost.nativehttp3.NativeHttp3Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class Http3RemoteTransport(
    private val client: NativeHttp3Client = NativeHttp3Client(),
) : RemoteTransport {
    override suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        client.getString(url)
    }

    override suspend fun download(url: String, destination: File): File = withContext(Dispatchers.IO) {
        client.download(url, destination)
    }
}
