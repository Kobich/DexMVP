package com.engboost.dexmvp.loader

import android.content.Context
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteFeature
import dalvik.system.DexClassLoader
import java.io.File

class DexModuleLoader(
    private val context: Context,
) {
    fun loadOutputFeature(manifest: RemoteModuleManifest, feature: RemoteFeatureManifest, artifact: File): RemoteFeature {
        val instance = loadInstance(manifest, feature, artifact)
        return instance as? RemoteFeature
            ?: error("${feature.entryPoint} does not implement RemoteFeature")
    }

    fun loadComposeFeature(manifest: RemoteModuleManifest, feature: RemoteFeatureManifest, artifact: File): RemoteComposeFeature {
        val instance = loadInstance(manifest, feature, artifact)
        return instance as? RemoteComposeFeature
            ?: error("${feature.entryPoint} does not implement RemoteComposeFeature")
    }

    private fun loadInstance(manifest: RemoteModuleManifest, feature: RemoteFeatureManifest, artifact: File): Any {
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
        val entryClass = classLoader.loadClass(feature.entryPoint)
        return entryClass.getDeclaredConstructor().newInstance()
    }
}
