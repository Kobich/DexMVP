package com.engboost.dexmvp.transport

import android.content.Context
import com.engboost.nativehttp3.NativeHttp3Client

data class TransportDiagnostics(
    val mode: TransportMode,
    val transport: String,
    val tlsVerification: String,
    val nativeLayer: String,
    val caFilePath: String,
    val engine: String,
)

object TransportDiagnosticsProvider {
    fun inspect(mode: TransportMode, context: Context): TransportDiagnostics {
        return when (mode) {
            TransportMode.HTTP_FALLBACK -> TransportDiagnostics(
                mode = mode,
                transport = "OkHttp HTTP fallback",
                tlsVerification = "OkHttp default",
                nativeLayer = "not used",
                caFilePath = "not used",
                engine = "not used",
            )

            TransportMode.HTTP3_PREFERRED,
            TransportMode.HTTP3_ONLY -> {
                val configResult = runCatching { RemoteTransportFactory.localHttp3Config(context) }
                val config = configResult.getOrNull()
                val client = NativeHttp3Client(config ?: RemoteTransportFactory.localHttp3Config())
                TransportDiagnostics(
                    mode = mode,
                    transport = "libcurl HTTP/3 via JNI",
                    tlsVerification = if (config != null) {
                        "enabled with local debug CA"
                    } else {
                        "CA not configured: ${configResult.exceptionOrNull()?.message}"
                    },
                    nativeLayer = if (client.isNativeLayerLoaded()) "loaded" else "not loaded",
                    caFilePath = config?.caFilePath.orEmpty(),
                    engine = client.engineInfo(),
                )
            }
        }
    }
}
