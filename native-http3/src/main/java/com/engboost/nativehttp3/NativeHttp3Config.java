package com.engboost.nativehttp3;

public final class NativeHttp3Config {
    public final long connectTimeoutMillis;
    public final long readTimeoutMillis;
    public final boolean verifyTls;
    public final String caFilePath;

    public NativeHttp3Config() {
        this(15_000L, 30_000L, true, "");
    }

    public NativeHttp3Config(
        long connectTimeoutMillis,
        long readTimeoutMillis,
        boolean verifyTls
    ) {
        this(connectTimeoutMillis, readTimeoutMillis, verifyTls, "");
    }

    public NativeHttp3Config(
        long connectTimeoutMillis,
        long readTimeoutMillis,
        boolean verifyTls,
        String caFilePath
    ) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.verifyTls = verifyTls;
        this.caFilePath = caFilePath;
    }

    @Override
    public String toString() {
        return "NativeHttp3Config{"
            + "connectTimeoutMillis=" + connectTimeoutMillis
            + ", readTimeoutMillis=" + readTimeoutMillis
            + ", verifyTls=" + verifyTls
            + ", caFilePath=" + caFilePath
            + '}';
    }
}
