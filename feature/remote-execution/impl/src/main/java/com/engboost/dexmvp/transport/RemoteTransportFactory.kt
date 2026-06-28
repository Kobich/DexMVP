package com.engboost.dexmvp.transport

import android.content.Context
import com.engboost.nativehttp3.NativeHttp3Client
import com.engboost.nativehttp3.NativeHttp3Config

object RemoteTransportFactory {
    fun create(mode: TransportMode, context: Context? = null): RemoteTransport {
        return when (mode) {
            TransportMode.HTTP_FALLBACK -> OkHttpRemoteTransport()
            TransportMode.HTTP3_ONLY -> localHttp3Transport(context)
            TransportMode.HTTP3_PREFERRED -> FallbackRemoteTransport(
                primary = localHttp3Transport(context),
                fallback = OkHttpRemoteTransport(),
            )
        }
    }

    private fun localHttp3Transport(context: Context?): Http3RemoteTransport {
        return Http3RemoteTransport(
            client = NativeHttp3Client(
                localHttp3Config(context),
            ),
        )
    }

    fun localHttp3Config(context: Context? = null): NativeHttp3Config {
        val caFilePath = context?.let(LocalDebugCaProvider::prepareCaFile).orEmpty()
        return NativeHttp3Config(
            15_000,
            30_000,
            true,
            caFilePath,
        )
    }
}
