plugins {
    alias(libs.plugins.android.library)
}

val nativeHttp3CmakeEnabled = providers.gradleProperty("nativeHttp3.enableCmake")
    .map(String::toBoolean)
    .getOrElse(false)
val nativeHttp3CurlEnabled = providers.gradleProperty("nativeHttp3.enableCurl")
    .map(String::toBoolean)
    .getOrElse(false)
val nativeHttp3CurlRootDir = providers.gradleProperty("nativeHttp3.curlRootDir").orNull
val nativeHttp3Abis = providers.gradleProperty("nativeHttp3.abis")
    .map { value -> value.split(",").map(String::trim).filter(String::isNotEmpty) }
    .getOrElse(emptyList())

android {
    namespace = "com.engboost.nativehttp3"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = if (nativeHttp3CurlEnabled) 26 else 24

        if (nativeHttp3Abis.isNotEmpty()) {
            ndk {
                abiFilters += nativeHttp3Abis
            }
        }

        if (nativeHttp3CmakeEnabled) {
            externalNativeBuild {
                cmake {
                    arguments += "-DNATIVE_HTTP3_ENABLE_CURL=${if (nativeHttp3CurlEnabled) "ON" else "OFF"}"
                    nativeHttp3CurlRootDir?.let {
                        arguments += "-DNATIVE_HTTP3_CURL_ROOT=$it"
                    }
                    cppFlags += "-std=c++17"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    if (nativeHttp3CmakeEnabled) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    if (nativeHttp3CurlEnabled && nativeHttp3CurlRootDir != null) {
        sourceSets {
            getByName("main") {
                jniLibs.directories.add("$nativeHttp3CurlRootDir/libs")
            }
        }
    }
}
