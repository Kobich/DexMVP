package com.engboost.nativehttp3;

import java.io.File;

public final class NativeHttp3Client {
    private static final String NATIVE_LOAD_ERROR = loadNativeLibrary();

    private final NativeHttp3Config config;

    public NativeHttp3Client() {
        this(new NativeHttp3Config());
    }

    public NativeHttp3Client(NativeHttp3Config config) {
        this.config = config;
    }

    public boolean isAvailable() {
        return isNativeLayerLoaded() && nativeIsCurlEnabled();
    }

    public boolean isNativeLayerLoaded() {
        return NATIVE_LOAD_ERROR == null;
    }

    public String engineInfo() {
        if (NATIVE_LOAD_ERROR != null) {
            return "native-http3 JNI is not loaded; config=" + config + "; error=" + NATIVE_LOAD_ERROR;
        }

        return nativeEngineInfo() + "; config=" + config;
    }

    public String getString(String url) {
        requireNativeLayer(url);
        return nativeGetString(
            url,
            config.connectTimeoutMillis,
            config.readTimeoutMillis,
            config.verifyTls,
            config.caFilePath
        );
    }

    public File download(String url, File destination) {
        requireNativeLayer(url);
        nativeDownload(
            url,
            destination.getAbsolutePath(),
            config.connectTimeoutMillis,
            config.readTimeoutMillis,
            config.verifyTls,
            config.caFilePath
        );
        return destination;
    }

    private void requireNativeLayer(String url) {
        if (NATIVE_LOAD_ERROR == null) {
            return;
        }

        throw new NativeHttp3UnavailableException(
            "HTTP/3 native layer is not loaded for url=" + url
                + ". Native layer loaded=" + isNativeLayerLoaded()
                + ". Engine=" + engineInfo()
        );
    }

    private static String loadNativeLibrary() {
        try {
            System.loadLibrary("native-http3");
            return null;
        } catch (UnsatisfiedLinkError error) {
            return error.getMessage();
        }
    }

    private static native String nativeEngineInfo();

    private static native boolean nativeIsCurlEnabled();

    private static native String nativeGetString(
        String url,
        long connectTimeoutMillis,
        long readTimeoutMillis,
        boolean verifyTls,
        String caFilePath
    );

    private static native void nativeDownload(
        String url,
        String destinationPath,
        long connectTimeoutMillis,
        long readTimeoutMillis,
        boolean verifyTls,
        String caFilePath
    );
}
