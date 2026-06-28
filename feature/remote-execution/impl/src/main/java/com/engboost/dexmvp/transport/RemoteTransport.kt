package com.engboost.dexmvp.transport

import java.io.File

interface RemoteTransport {
    suspend fun getString(url: String): String
    suspend fun download(url: String, destination: File): File
}

