package com.engboost.server.modules

import com.engboost.server.security.Sha256
import kotlinx.serialization.Serializable
import java.io.File

class ModuleRegistry(
    private val baseUrl: String = System.getenv("DEX_SERVER_BASE_URL") ?: "http://10.0.2.2:8080",
) {
    private val moduleId = "hello"
    private val version = 1
    private val artifact = resolveArtifactFile()

    fun activeManifest(): ServerModuleManifest {
        require(artifact.exists()) {
            "Remote artifact is missing. Build it first: ./gradlew.bat :remote-module:assembleDebug"
        }

        return ServerModuleManifest(
            moduleId = moduleId,
            version = version,
            hostApiVersion = 1,
            minHostApi = 1,
            entryPoint = "com.engboost.remote.HelloRemoteFeature",
            artifactUrl = "${baseUrl.trimEnd('/')}/api/v1/modules/$moduleId/$version/artifact",
            sha256 = Sha256.calculate(artifact),
            signature = "",
        )
    }

    fun artifactFile(requestedModuleId: String?, requestedVersion: Int?): File? {
        if (requestedModuleId != moduleId || requestedVersion != version || !artifact.exists()) {
            return null
        }
        return artifact
    }

    private fun resolveArtifactFile(): File {
        val overridePath = System.getenv("DEX_REMOTE_ARTIFACT")
        if (!overridePath.isNullOrBlank()) {
            return File(overridePath)
        }

        val candidates = listOf(
            File("../remote-module/build/outputs/apk/debug/remote-module-debug.apk"),
            File("remote-module/build/outputs/apk/debug/remote-module-debug.apk"),
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}

@Serializable
data class ServerModuleManifest(
    val moduleId: String,
    val version: Int,
    val hostApiVersion: Int,
    val minHostApi: Int,
    val entryPoint: String,
    val artifactUrl: String,
    val sha256: String,
    val signature: String,
)

