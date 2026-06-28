package com.engboost.dexmvp.transport

import android.content.Context
import java.io.File

object LocalDebugCaProvider {
    private const val PLACEHOLDER_MARKER = "REPLACE_THIS_FILE_WITH_REAL_LOCAL_CA_CERTIFICATE"

    fun prepareCaFile(context: Context): String {
        val resourceId = context.resources.getIdentifier("dexmvp_local_ca", "raw", context.packageName)
        require(resourceId != 0) {
            "Local HTTP/3 CA certificate resource is missing. Add app/src/debug/res/raw/dexmvp_local_ca.crt."
        }

        val caFile = File(context.cacheDir, "dexmvp-local-ca.crt")
        context.resources.openRawResource(resourceId).use { input ->
            caFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val content = caFile.readText()
        require(!content.contains(PLACEHOLDER_MARKER)) {
            "Local HTTP/3 CA certificate is not configured. Replace app/src/debug/res/raw/dexmvp_local_ca.crt with generated dexmvp-local-ca.crt."
        }
        return caFile.absolutePath
    }
}
