package com.engboost.dexmvp.loader

import android.content.Context
import java.io.File

class ModuleStorage(
    context: Context,
) {
    private val modulesDir = File(context.filesDir, "remote-modules")

    fun tempArtifactFile(manifest: RemoteModuleManifest): File {
        modulesDir.mkdirs()
        return File(modulesDir, "${manifest.moduleId}-${manifest.version}.download")
    }

    fun finalArtifactFile(manifest: RemoteModuleManifest): File {
        modulesDir.mkdirs()
        return File(modulesDir, "${manifest.moduleId}-${manifest.version}.apk")
    }

    fun commitVerifiedArtifact(tempFile: File, manifest: RemoteModuleManifest): File {
        val finalFile = finalArtifactFile(manifest)
        if (finalFile.exists()) {
            finalFile.setWritable(true)
            finalFile.delete()
        }

        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()

        finalFile.setReadable(true, true)
        finalFile.setExecutable(false, false)
        finalFile.setWritable(false, false)
        return finalFile
    }
}

