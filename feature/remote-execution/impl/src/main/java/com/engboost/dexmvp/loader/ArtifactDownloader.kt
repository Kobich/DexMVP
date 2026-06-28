package com.engboost.dexmvp.loader

import com.engboost.dexmvp.transport.RemoteTransport
import java.io.File

class ArtifactDownloader(
    private val transport: RemoteTransport,
) {
    suspend fun download(url: String, destination: File): File {
        return transport.download(url, destination)
    }
}
