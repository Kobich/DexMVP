package com.engboost.nativehttp3;

public final class NativeHttp3UnavailableException extends IllegalStateException {
    public NativeHttp3UnavailableException(String message) {
        super(message);
    }

    public NativeHttp3UnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
