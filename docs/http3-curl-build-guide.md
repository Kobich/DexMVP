# HTTP/3 curl Build Guide

Цель: получить папку:

```text
third_party/curl-android
```

С ней проект сможет собрать `native-http3` с реальным `libcurl`:

```powershell
.\scripts\verify-native-http3-curl.ps1
```

## Коротко: что делать

Есть два нормальных пути.

### Путь 1 — попросить готовый bundle

Это лучший рабочий вариант.

Попросить у команды инфраструктуры/security:

```text
Android libcurl with HTTP/3 support
ABI: arm64-v8a and x86_64
headers: include/curl/curl.h
libraries: libcurl.so and all dependent .so files if they are dynamic
versions: curl, ngtcp2, nghttp3, TLS backend
licenses
```

Когда дадут файлы, разложить так:

```text
third_party/curl-android/
  include/curl/curl.h
  libs/arm64-v8a/libcurl.so
  libs/x86_64/libcurl.so
```

Если рядом с `libcurl.so` есть зависимости, например TLS/QUIC `.so`, положить их в ту же ABI-папку:

```text
third_party/curl-android/libs/arm64-v8a/*.so
third_party/curl-android/libs/x86_64/*.so
```

Проверить:

```powershell
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
```

### Путь 2 — собрать самим через vcpkg

Это самый понятный способ для самостоятельной сборки на машине с интернетом.

Нужны:

- Git;
- Android SDK;
- Android NDK;
- PowerShell;
- доступ в интернет.

Команды:

```powershell
git clone https://github.com/microsoft/vcpkg C:\work\vcpkg
cd C:\work\vcpkg
.\bootstrap-vcpkg.bat
```

Указать NDK:

```powershell
$env:ANDROID_NDK_HOME="C:\Users\RED.DOT\AppData\Local\Android\Sdk\ndk\28.2.13676358"
```

Собрать под физическое устройство:

```powershell
.\vcpkg.exe install "curl[http3]:arm64-android"
```

Собрать под emulator:

```powershell
.\vcpkg.exe install "curl[http3]:x64-android"
```

Импортировать в проект:

```powershell
cd C:\Users\RED.DOT\AndroidStudioProjects\DexMVP
.\scripts\import-curl-from-vcpkg.ps1 -VcpkgRoot C:\work\vcpkg
```

Если собран пока только emulator ABI `x64-android`, импортировать так:

```powershell
.\scripts\import-curl-from-vcpkg.ps1 `
  -VcpkgRoot C:\Users\RED.DOT\Downloads\vcpkg-master\vcpkg-master `
  -Triplets x64-android
```

Проверить:

```powershell
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
```

Если в bundle есть только `x86_64`, проверять так:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

## Если vcpkg собрал `.a`, а не `.so`

Это нормальный результат для vcpkg Android.

Проект поддерживает оба варианта:

```text
libcurl.so
libcurl.a
```

Если есть `libcurl.a`, рядом должны быть статические зависимости:

```text
libngtcp2_crypto_ossl.a
libngtcp2.a
libnghttp3.a
libssl.a
libcrypto.a
libz.a
```

Скрипт `import-curl-from-vcpkg.ps1` копирует их автоматически.

Для static vcpkg build `native-http3` собирается с minSdk 26 в curl-enabled режиме. Это нужно, потому что часть Android fortified socket symbols, например `__sendto_chk`, есть в NDK libc начиная с API 26. Обычная сборка без curl остаётся на minSdk 24.

## Почему нужен не обычный curl

Нужен curl с HTTP/3.

Официальная документация curl говорит, что для HTTP/3 через `ngtcp2` нужны:

- `ngtcp2`;
- `nghttp3`;
- TLS library с QUIC support.

Также HTTP/3 в curl работает для `https://` URL, потому что HTTP/3 идёт поверх QUIC/TLS.

## Что переносить на закрытый ПК

После успешной проверки перенести:

```text
DexMVP/
  third_party/curl-android/
  docs/
  scripts/
  app/
  feature/
  native-http3/
  remote-module/
  server/
```

На закрытом ПК проверить:

```powershell
.\scripts\verify-project.ps1
.\scripts\verify-native-http3.ps1
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
```

## Источники

- curl HTTP/3: https://curl.se/docs/http3.html
- curl install/vcpkg: https://curl.se/docs/install.html
- vcpkg Android triplets: https://learn.microsoft.com/en-us/vcpkg/users/platforms/android
- vcpkg curl package: https://vcpkg.io/en/package/curl.html
