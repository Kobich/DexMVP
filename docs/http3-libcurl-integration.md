# HTTP/3 libcurl Integration

This document describes the next integration point after the JNI skeleton.

Source/acquisition decision: `docs/http3-curl-source-strategy.md`.
Build/import steps: `docs/http3-curl-build-guide.md`.

## Current State

The project now has three HTTP/3 levels:

```text
default build
  -> no native .so
  -> OkHttp fallback still works

native skeleton build
  -> builds libnative-http3.so
  -> JNI loads
  -> libcurl is still disabled

native libcurl build
  -> links libnative-http3.so against prebuilt libcurl.so
  -> intended path for real HTTP/3 requests
```

## Gradle Flags

Default project verification:

```powershell
.\scripts\verify-project.ps1
```

JNI skeleton verification:

```powershell
.\scripts\verify-native-http3.ps1
```

libcurl verification:

```powershell
.\scripts\verify-native-http3-curl.ps1
```

By default the script uses:

```text
third_party/curl-android
```

Custom path:

```powershell
.\scripts\verify-native-http3-curl.ps1 -CurlRootDir C:\path\to\curl-android
```

## Expected libcurl Layout

`CurlRootDir` must have this structure:

```text
third_party/curl-android/
  include/
    curl/
      curl.h
  libs/
    arm64-v8a/
      libcurl.so OR libcurl.a
    armeabi-v7a/
      libcurl.so OR libcurl.a
    x86/
      libcurl.so
    x86_64/
      libcurl.so
```

If only one ABI is needed later, Gradle can be restricted with `abiFilters`. For now the project keeps all default debug ABIs.

For local emulator-only curl checks:

```powershell
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

The curl-enabled native build uses minSdk 26 because static vcpkg curl may reference Android libc symbols unavailable in API 24.

Layout-only check:

```powershell
.\scripts\check-curl-android-layout.ps1
```

## Required libcurl Capabilities

The prebuilt `libcurl.so` must be built with:

- HTTP/3 support;
- QUIC backend, for example `ngtcp2`/`nghttp3` or another Android-compatible backend;
- TLS backend suitable for Android;
- shared library output for each target ABI.

## Runtime Flow

```text
Http3RemoteTransport
  -> NativeHttp3Client.getString/download
    -> JNI nativeGetString/nativeDownload
      -> libcurl with CURL_HTTP_VERSION_3ONLY
```

If curl is not linked, JNI throws `NativeHttp3UnavailableException`. In `HTTP3_PREFERRED` mode the app then falls back to OkHttp.

## What Is Not Solved Yet

- building or vendoring libcurl itself;
- certificate strategy for IP-only HTTPS;
- NGINX HTTP/3 endpoint;
- runtime diagnostics for UDP-blocked networks;
- restricting APK ABIs for production size.

## Closed Infrastructure Workflow

On an internet-enabled machine:

1. Prepare or receive the `curl-android` bundle.
2. Put it into `third_party/curl-android`.
3. Run `.\scripts\check-curl-android-layout.ps1`.
4. Run `.\scripts\verify-native-http3-curl.ps1`.
5. Pack the project together with `third_party/curl-android`.

On the closed machine:

1. Unpack the project.
2. Run `.\scripts\verify-project.ps1`.
3. Run `.\scripts\verify-native-http3.ps1`.
4. Run `.\scripts\verify-native-http3-curl.ps1` only if `third_party/curl-android` contains real `include/` and `libs/`.
