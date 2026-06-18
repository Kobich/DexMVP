package com.engboost.dexmvp.loader

import android.content.Context
import com.engboost.remoteapi.RemoteComposeFeature
import com.engboost.remoteapi.RemoteInput
import com.engboost.remoteapi.RemoteOutput
import java.io.File

class RemoteModuleRepository(
    context: Context,
    serverBaseUrl: String,
) {
    private val apiClient = ManifestApiClient(serverBaseUrl)
    private val downloader = ArtifactDownloader()
    private val storage = ModuleStorage(context)
    private val verifier = Sha256Verifier()
    private val runner = RemoteFeatureRunner(DexModuleLoader(context))

    suspend fun fetchManifest(): RemoteModuleManifest {
        val manifest = apiClient.fetchActiveManifest()
        require(manifest.minHostApi <= RemoteFeatureRunner.HOST_API_VERSION) {
            "Module requires host API ${manifest.minHostApi}, current host API is ${RemoteFeatureRunner.HOST_API_VERSION}"
        }
        return manifest
    }

    suspend fun downloadAndVerify(manifest: RemoteModuleManifest): File {
        val tempFile = storage.tempArtifactFile(manifest)
        downloader.download(manifest.artifactUrl, tempFile)
        verifier.requireMatches(tempFile, manifest.sha256)
        return storage.commitVerifiedArtifact(tempFile, manifest)
    }

    fun run(manifest: RemoteModuleManifest, artifact: File, input: RemoteInput): RemoteOutput {
        val feature = manifest.features.firstOrNull()
            ?: error("Manifest does not contain features")
        return runOutput(manifest, feature, artifact, input)
    }

    fun runOutput(manifest: RemoteModuleManifest, feature: RemoteFeatureManifest, artifact: File, input: RemoteInput): RemoteOutput {
        return runner.runOutput(manifest, feature, artifact, input)
    }

    fun loadCompose(manifest: RemoteModuleManifest, feature: RemoteFeatureManifest, artifact: File): RemoteComposeFeature {
        return runner.loadCompose(manifest, feature, artifact)
    }
}
