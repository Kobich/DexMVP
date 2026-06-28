cmake_minimum_required(VERSION 3.22.1)

project(native_http3)

option(NATIVE_HTTP3_ENABLE_CURL "Build native-http3 against prebuilt libcurl HTTP/3" OFF)
set(NATIVE_HTTP3_CURL_ROOT "" CACHE PATH "Root directory with include/curl and libs/<abi>/libcurl.so")

add_library(
    native-http3
    SHARED
    native_http3_client.cpp
)

find_library(log-lib log)

target_compile_definitions(
    native-http3
    PRIVATE
    NATIVE_HTTP3_ENABLE_CURL=$<BOOL:${NATIVE_HTTP3_ENABLE_CURL}>
)

if (NATIVE_HTTP3_ENABLE_CURL)
    if (NOT NATIVE_HTTP3_CURL_ROOT)
        message(FATAL_ERROR "NATIVE_HTTP3_CURL_ROOT is required when NATIVE_HTTP3_ENABLE_CURL=ON")
    endif()

    set(NATIVE_HTTP3_CURL_INCLUDE_DIR "${NATIVE_HTTP3_CURL_ROOT}/include")
    set(NATIVE_HTTP3_CURL_SHARED_LIBRARY "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libcurl.so")
    set(NATIVE_HTTP3_CURL_STATIC_LIBRARY "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libcurl.a")

    if (NOT EXISTS "${NATIVE_HTTP3_CURL_INCLUDE_DIR}/curl/curl.h")
        message(FATAL_ERROR "curl/curl.h not found: ${NATIVE_HTTP3_CURL_INCLUDE_DIR}/curl/curl.h")
    endif()

    if (EXISTS "${NATIVE_HTTP3_CURL_SHARED_LIBRARY}")
        add_library(curl SHARED IMPORTED)
        set_target_properties(
            curl
            PROPERTIES
            IMPORTED_LOCATION "${NATIVE_HTTP3_CURL_SHARED_LIBRARY}"
        )

        target_link_libraries(
            native-http3
            curl
        )
    elseif (EXISTS "${NATIVE_HTTP3_CURL_STATIC_LIBRARY}")
        add_library(curl STATIC IMPORTED)
        set_target_properties(
            curl
            PROPERTIES
            IMPORTED_LOCATION "${NATIVE_HTTP3_CURL_STATIC_LIBRARY}"
        )

        set(NATIVE_HTTP3_STATIC_DEPENDENCY_LIBS
            "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libngtcp2_crypto_ossl.a"
            "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libngtcp2.a"
            "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libnghttp3.a"
            "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libssl.a"
            "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libcrypto.a"
            "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libz.a"
        )

        foreach (NATIVE_HTTP3_STATIC_LIB ${NATIVE_HTTP3_STATIC_DEPENDENCY_LIBS})
            if (NOT EXISTS "${NATIVE_HTTP3_STATIC_LIB}")
                message(FATAL_ERROR "Static curl dependency not found for ${ANDROID_ABI}: ${NATIVE_HTTP3_STATIC_LIB}")
            endif()
        endforeach()

        target_compile_definitions(
            native-http3
            PRIVATE
            CURL_STATICLIB=1
        )

        target_link_libraries(
            native-http3
            curl
            ${NATIVE_HTTP3_STATIC_DEPENDENCY_LIBS}
        )
    else()
        message(FATAL_ERROR "libcurl not found for ${ANDROID_ABI}. Expected ${NATIVE_HTTP3_CURL_SHARED_LIBRARY} or ${NATIVE_HTTP3_CURL_STATIC_LIBRARY}")
    endif()

    target_include_directories(
        native-http3
        PRIVATE
        "${NATIVE_HTTP3_CURL_INCLUDE_DIR}"
    )

endif()

target_link_libraries(
    native-http3
    ${log-lib}
)
