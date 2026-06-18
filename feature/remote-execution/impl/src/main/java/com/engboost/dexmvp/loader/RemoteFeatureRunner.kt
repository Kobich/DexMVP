package com.engboost.dexmvp.loader

import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteInput
import com.engboost.remoteapi.RemoteOutput
import java.io.File

class RemoteFeatureRunner(
    private val loader: DexModuleLoader,
    private val hostApiVersion: Int = HOST_API_VERSION,
) {
    fun runOutput(manifest: RemoteModuleManifest, featureManifest: RemoteFeatureManifest, artifact: File, input: RemoteInput): RemoteOutput {
        requireCompatible(manifest)

        val feature = loader.loadOutputFeature(manifest, featureManifest, artifact)
        requireMatches(feature.id, feature.version, featureManifest)

        return feature.execute(input)
    }

    fun loadCompose(manifest: RemoteModuleManifest, featureManifest: RemoteFeatureManifest, artifact: File): RemoteComposeFeature {
        requireCompatible(manifest)

        val feature = loader.loadComposeFeature(manifest, featureManifest, artifact)
        requireMatches(feature.id, feature.version, featureManifest)

        return feature
    }

    private fun requireCompatible(manifest: RemoteModuleManifest) {
        require(manifest.minHostApi <= hostApiVersion) {
            "Module requires host API ${manifest.minHostApi}, current host API is $hostApiVersion"
        }
    }

    private fun requireMatches(featureId: String, featureVersion: Int, featureManifest: RemoteFeatureManifest) {
        require(featureId == featureManifest.id) {
            "Loaded feature id $featureId does not match manifest id ${featureManifest.id}"
        }
        require(featureVersion == featureManifest.version) {
            "Loaded feature version $featureVersion does not match manifest version ${featureManifest.version}"
        }
    }

    companion object {
        const val HOST_API_VERSION = 1
    }
}
