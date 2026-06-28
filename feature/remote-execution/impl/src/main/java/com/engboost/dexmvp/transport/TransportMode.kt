package com.engboost.dexmvp.transport

enum class TransportMode(
    val label: String,
) {
    HTTP_FALLBACK("HTTP fallback"),
    HTTP3_PREFERRED("HTTP/3 preferred"),
    HTTP3_ONLY("HTTP/3 only"),
}

