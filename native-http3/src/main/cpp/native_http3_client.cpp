#include <jni.h>
#include <cstdio>
#include <string>

#if NATIVE_HTTP3_ENABLE_CURL
#include <curl/curl.h>
#endif

namespace {

void throwUnavailable(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("com/engboost/nativehttp3/NativeHttp3UnavailableException");
    if (exceptionClass == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
        return;
    }

    env->ThrowNew(exceptionClass, message.c_str());
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

#if NATIVE_HTTP3_ENABLE_CURL

size_t writeToString(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* output = static_cast<std::string*>(userdata);
    output->append(ptr, size * nmemb);
    return size * nmemb;
}

size_t writeToFile(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* file = static_cast<FILE*>(userdata);
    return std::fwrite(ptr, size, nmemb, file);
}

void configureCurl(
    CURL* curl,
    const std::string& url,
    jlong connectTimeoutMillis,
    jlong readTimeoutMillis,
    jboolean verifyTls,
    const std::string& caFilePath,
    char* errorBuffer
) {
    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, errorBuffer);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, static_cast<long>(connectTimeoutMillis));
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, static_cast<long>(readTimeoutMillis));
    curl_easy_setopt(curl, CURLOPT_HTTP_VERSION, CURL_HTTP_VERSION_3ONLY);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, verifyTls ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, verifyTls ? 2L : 0L);
    if (verifyTls && !caFilePath.empty()) {
        curl_easy_setopt(curl, CURLOPT_CAINFO, caFilePath.c_str());
    }
}

bool isHttpSuccess(CURL* curl) {
    long responseCode = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &responseCode);
    return responseCode >= 200 && responseCode < 300;
}

std::string curlErrorMessage(CURLcode code, CURL* curl) {
    long responseCode = 0;
    long verifyResult = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &responseCode);
    curl_easy_getinfo(curl, CURLINFO_SSL_VERIFYRESULT, &verifyResult);
    return "libcurl HTTP/3 request failed. curlCode=" + std::to_string(code)
        + ", responseCode=" + std::to_string(responseCode)
        + ", sslVerifyResult=" + std::to_string(verifyResult)
        + ", message=" + curl_easy_strerror(code);
}

#endif

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_engboost_nativehttp3_NativeHttp3Client_nativeEngineInfo(
    JNIEnv* env,
    jclass /* clazz */
) {
#if NATIVE_HTTP3_ENABLE_CURL
    return env->NewStringUTF(curl_version());
#else
    return env->NewStringUTF("native-http3 JNI loaded; libcurl HTTP/3 is not linked");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_engboost_nativehttp3_NativeHttp3Client_nativeIsCurlEnabled(
    JNIEnv* /* env */,
    jclass /* clazz */
) {
#if NATIVE_HTTP3_ENABLE_CURL
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_engboost_nativehttp3_NativeHttp3Client_nativeGetString(
    JNIEnv* env,
    jclass /* clazz */,
    jstring url,
    jlong connectTimeoutMillis,
    jlong readTimeoutMillis,
    jboolean verifyTls,
    jstring caFilePath
) {
#if NATIVE_HTTP3_ENABLE_CURL
    curl_global_init(CURL_GLOBAL_DEFAULT);
    CURL* curl = curl_easy_init();
    if (curl == nullptr) {
        throwUnavailable(env, "libcurl initialization failed");
        return nullptr;
    }

    std::string output;
    char errorBuffer[CURL_ERROR_SIZE] = {0};
    std::string caPath = toString(env, caFilePath);
    configureCurl(
        curl,
        toString(env, url),
        connectTimeoutMillis,
        readTimeoutMillis,
        verifyTls,
        caPath,
        errorBuffer
    );
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeToString);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &output);

    CURLcode code = curl_easy_perform(curl);
    if (code != CURLE_OK || !isHttpSuccess(curl)) {
        std::string message = curlErrorMessage(code, curl);
        if (errorBuffer[0] != '\0') {
            message += ", detail=";
            message += errorBuffer;
        }
        message += ", caFilePath=" + caPath;
        curl_easy_cleanup(curl);
        throwUnavailable(env, message);
        return nullptr;
    }

    curl_easy_cleanup(curl);
    return env->NewStringUTF(output.c_str());
#else
    throwUnavailable(
        env,
        "libcurl HTTP/3 is not linked. Build with -PnativeHttp3.enableCurl=true and provide curlRootDir."
    );
    return nullptr;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_engboost_nativehttp3_NativeHttp3Client_nativeDownload(
    JNIEnv* env,
    jclass /* clazz */,
    jstring url,
    jstring destinationPath,
    jlong connectTimeoutMillis,
    jlong readTimeoutMillis,
    jboolean verifyTls,
    jstring caFilePath
) {
#if NATIVE_HTTP3_ENABLE_CURL
    curl_global_init(CURL_GLOBAL_DEFAULT);
    CURL* curl = curl_easy_init();
    if (curl == nullptr) {
        throwUnavailable(env, "libcurl initialization failed");
        return;
    }

    std::string path = toString(env, destinationPath);
    FILE* file = std::fopen(path.c_str(), "wb");
    if (file == nullptr) {
        curl_easy_cleanup(curl);
        throwUnavailable(env, "Cannot open destination file for HTTP/3 download: " + path);
        return;
    }

    char errorBuffer[CURL_ERROR_SIZE] = {0};
    std::string caPath = toString(env, caFilePath);
    configureCurl(
        curl,
        toString(env, url),
        connectTimeoutMillis,
        readTimeoutMillis,
        verifyTls,
        caPath,
        errorBuffer
    );
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeToFile);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, file);

    CURLcode code = curl_easy_perform(curl);
    std::fclose(file);
    if (code != CURLE_OK || !isHttpSuccess(curl)) {
        std::string message = curlErrorMessage(code, curl);
        if (errorBuffer[0] != '\0') {
            message += ", detail=";
            message += errorBuffer;
        }
        message += ", caFilePath=" + caPath;
        curl_easy_cleanup(curl);
        throwUnavailable(env, message);
        return;
    }

    curl_easy_cleanup(curl);
#else
    throwUnavailable(
        env,
        "libcurl HTTP/3 is not linked. Build with -PnativeHttp3.enableCurl=true and provide curlRootDir."
    );
#endif
}
