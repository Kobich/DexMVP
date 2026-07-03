# HTTP/3 curl Build Guide

Цель: собрать на Windows через `vcpkg` Android-бандл `libcurl` с HTTP/3 и положить его в проект так, чтобы прошла сборка `native-http3`.

Итоговая папка должна выглядеть так:

```text
third_party/curl-android/
  include/curl/curl.h
  libs/arm64-v8a/libcurl.so OR libcurl.a
  libs/x86_64/libcurl.so OR libcurl.a
```

`arm64-v8a` нужен для физического устройства. `x86_64` нужен для большинства Android emulator на Windows.

## 1. Что поставить

На машине с интернетом нужны:

- Git;
- Visual Studio Build Tools или Visual Studio с C++ workload;
- Android Studio с Android SDK и Android NDK;
- PowerShell;
- свободное место под сборку `vcpkg`.

Важно: Android NDK должен быть установлен локально. `vcpkg` берет Android toolchain через переменную `ANDROID_NDK_HOME`.

## 2. Подготовить vcpkg

Пример ниже использует `C:\work\vcpkg`. Можно выбрать другой путь, но дальше надо подставлять свой.

```powershell
git clone https://github.com/microsoft/vcpkg C:\work\vcpkg
cd C:\work\vcpkg
.\bootstrap-vcpkg.bat
```

Указать путь к NDK:

```powershell
$env:ANDROID_NDK_HOME="C:\Users\RED.DOT\AppData\Local\Android\Sdk\ndk\28.2.13676358"
```

Проверить, что путь реальный:

```powershell
Test-Path "$env:ANDROID_NDK_HOME\source.properties"
```

Если команда вернула `False`, открой Android Studio и установи NDK через `SDK Manager -> SDK Tools -> NDK`.

## 3. Собрать curl с HTTP/3

Собрать ABI для физического устройства:

```powershell
cd C:\work\vcpkg
.\vcpkg.exe install "curl[http3]:arm64-android"
```

Собрать ABI для emulator:

```powershell
.\vcpkg.exe install "curl[http3]:x64-android"
```

Что делает `curl[http3]`: включает HTTP/3 через `ngtcp2`, `nghttp3` и OpenSSL. Это важно, потому что обычный `curl` без feature `http3` для этой задачи не подходит.

Соответствие `vcpkg` triplet и Android ABI:

```text
arm64-android -> arm64-v8a
x64-android   -> x86_64
```

## 4. Импортировать результат в проект

Вернуться в корень проекта:

```powershell
cd C:\Users\RED.DOT\AndroidStudioProjects\DexMVP
```

Импортировать обе сборки:

```powershell
.\scripts\import-curl-from-vcpkg.ps1 -VcpkgRoot C:\work\vcpkg
```

Скрипт копирует:

- headers из `installed\<triplet>\include`;
- `libcurl.so`, если vcpkg собрал shared library;
- `libcurl.a`, если vcpkg собрал static library;
- остальные `.so` и `.a` зависимости из `lib` и `debug\lib`.

Для этого проекта оба варианта нормальны: `libcurl.so` и `libcurl.a` поддерживаются в `native-http3/src/main/cpp/CMakeLists.txt`.

Если нужен только emulator, можно импортировать только `x64-android`:

```powershell
.\scripts\import-curl-from-vcpkg.ps1 `
  -VcpkgRoot C:\work\vcpkg `
  -Triplets x64-android
```

## 5. Проверить layout

Для полного набора ABI:

```powershell
.\scripts\check-curl-android-layout.ps1
```

Для emulator-only набора:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64
```

Ожидаемый результат: `OK: curl Android bundle layout is valid.`

## 6. Проверить сборку native-http3 с curl

Для полного набора ABI:

```powershell
.\scripts\verify-native-http3-curl.ps1
```

Для emulator-only набора:

```powershell
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

Скрипт включает:

```text
nativeHttp3.enableCmake=true
nativeHttp3.enableCurl=true
nativeHttp3.curlRootDir=<путь к third_party/curl-android>
nativeHttp3.abis=<ABI list>
```

Если сборка прошла, проект собрал `native-http3` с реальным `libcurl`.

## 7. Что переносить на закрытую машину

После успешной проверки перенести вместе с проектом папку:

```text
third_party/curl-android
```

На закрытой машине проверить:

```powershell
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
```

Если там есть только emulator ABI:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

## Частые проблемы

### `ANDROID_NDK_HOME` не задан

Задать переменную в текущем PowerShell:

```powershell
$env:ANDROID_NDK_HOME="C:\path\to\Android\Sdk\ndk\<version>"
```

Для постоянной настройки:

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_NDK_HOME", "C:\path\to\Android\Sdk\ndk\<version>", "User")
```

После этого открыть новый PowerShell.

### vcpkg собрал `.a`, а не `.so`

Это нормально. Android triplet в `vcpkg` часто дает static libraries. Проект поддерживает static `libcurl.a`, если рядом есть статические зависимости:

```text
libngtcp2_crypto_ossl.a
libngtcp2.a
libnghttp3.a
libssl.a
libcrypto.a
libz.a
```

`import-curl-from-vcpkg.ps1` копирует эти файлы автоматически, если они есть в установленном triplet.

### Ошибка по minSdk или fortified symbols

В curl-enabled режиме модуль `native-http3` использует `minSdk 26`. Это уже настроено в `native-http3/build.gradle.kts`. Обычная сборка без curl остается на `minSdk 24`.

### Нужен не весь набор ABI

Для локальной проверки можно собирать только `x64-android` и запускать:

```powershell
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

Для физического устройства нужен `arm64-android` и ABI `arm64-v8a`.

## Источники

- curl HTTP/3: https://curl.se/docs/http3.html
- curl install: https://curl.se/docs/install.html
- vcpkg Android: https://learn.microsoft.com/en-us/vcpkg/users/platforms/android
- vcpkg curl package: https://vcpkg.io/en/package/curl.html
