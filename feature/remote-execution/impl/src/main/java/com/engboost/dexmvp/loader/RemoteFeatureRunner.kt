package com.engboost.dexmvp.loader

import com.engboost.remoteapi.RemoteInput
import com.engboost.remoteapi.RemoteOutput
import java.io.File

class RemoteFeatureRunner(
    private val loader: DexModuleLoader,
    private val hostApiVersion: Int = HOST_API_VERSION,
) {
    fun run(manifest: RemoteModuleManifest, artifact: File, input: RemoteInput): RemoteOutput {
        require(manifest.minHostApi <= hostApiVersion) {
            "Module requires host API ${manifest.minHostApi}, current host API is $hostApiVersion"
        }

        val feature = loader.load(manifest, artifact)
        require(feature.id == manifest.moduleId) {
            "Loaded feature id ${feature.id} does not match manifest id ${manifest.moduleId}"
        }
        require(feature.version == manifest.version) {
            "Loaded feature version ${feature.version} does not match manifest version ${manifest.version}"
        }

        return feature.execute(input)
    }

    companion object {
        const val HOST_API_VERSION = 1
    }
}

