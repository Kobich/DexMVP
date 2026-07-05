package com.engboost.dexmvp.transport

import android.content.Context
import java.io.File

object CaddyCaProvider {
    fun prepareCaFile(context: Context): String {
        val resourceId = context.resources.getIdentifier("dexmvp_root_ca", "raw", context.packageName)
        require(resourceId != 0) {
            "Caddy root CA resource is missing. Generate app/src/main/res/raw/dexmvp_root_ca.crt."
        }

        val caFile = File(context.cacheDir, "dexmvp-root-ca.crt")
        context.resources.openRawResource(resourceId).use { input ->
            caFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return caFile.absolutePath
    }
}
