package com.engboost.dexmvp.loader

import java.io.File
import java.security.MessageDigest

class Sha256Verifier {
    fun requireMatches(file: File, expectedSha256: String) {
        val actual = calculate(file)
        require(actual.equals(expectedSha256, ignoreCase = true)) {
            "SHA-256 mismatch: expected $expectedSha256, actual $actual"
        }
    }

    fun calculate(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

