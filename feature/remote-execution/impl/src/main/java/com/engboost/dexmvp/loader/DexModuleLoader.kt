package com.engboost.dexmvp.loader

import android.content.Context
import com.engboost.remoteapi.RemoteFeature
import dalvik.system.DexClassLoader
import java.io.File

class DexModuleLoader(
    private val context: Context,
) {
    fun load(manifest: RemoteModuleManifest, artifact: File): RemoteFeature {
        require(artifact.exists()) { "Artifact does not exist: ${artifact.absolutePath}" }
        require(!artifact.canWrite()) { "Artifact must be read-only before loading" }

        val optimizedDir = File(context.codeCacheDir, "remote-dex/${manifest.moduleId}-${manifest.version}")
        optimizedDir.mkdirs()

        val classLoader = DexClassLoader(
            artifact.absolutePath,
            optimizedDir.absolutePath,
            null,
            context.classLoader,
        )
        val entryClass = classLoader.loadClass(manifest.entryPoint)
        val instance = entryClass.getDeclaredConstructor().newInstance()
        return instance as? RemoteFeature
            ?: error("${manifest.entryPoint} does not implement RemoteFeature")
    }
}

