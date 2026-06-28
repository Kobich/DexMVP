package com.engboost.dexmvp.transport

import java.io.File

class FallbackRemoteTransport(
    private val primary: RemoteTransport,
    private val fallback: RemoteTransport,
) : RemoteTransport {
    override suspend fun getString(url: String): String {
        return runCatching { primary.getString(url) }
            .getOrElse { fallback.getString(url) }
    }

    override suspend fun download(url: String, destination: File): File {
        return runCatching { primary.download(url, destination) }
            .getOrElse { fallback.download(url, destination) }
    }
}

