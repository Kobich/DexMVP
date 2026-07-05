# native-http3

Native HTTP/3 transport backend for the Android client.

## Назначение

Модуль изолирует native/JNI часть от feature-модуля загрузки DEX/APK.

Схема:

```text
feature:remote-execution:impl
  -> Http3RemoteTransport
    -> native-http3
      -> JNI
        -> libcurl + ngtcp2/nghttp3 + OpenSSL
```

## Текущее состояние

Модуль уже используется для локального HTTP/3 стенда:

- Java API стабилен для `Http3RemoteTransport`;
- CMake подключается только при Gradle-флаге `nativeHttp3.enableCmake=true`;
- curl backend подключается только при Gradle-флаге `nativeHttp3.enableCurl=true`;
- JNI методы `nativeEngineInfo()`, `nativeGetString()` и `nativeDownload()` вызывают libcurl;
- TLS verification включён; `CAINFO` по умолчанию пустой, поэтому transport полагается на default trust libcurl/OpenSSL;
- без native/curl flags обычная сборка и Gradle sync остаются рабочими.

Подтверждённый локальный сценарий:

```text
Android app
  -> libcurl HTTP/3 via JNI
  -> NGINX HTTP/3 in WSL :8443
  -> Ktor on Windows :8080
```

## Проверка без curl backend

Проверяет, что NDK/CMake/JNI слой собирается отдельно от curl:

```powershell
.\scripts\verify-native-http3.ps1
```

Прямая Gradle-команда:

```powershell
.\gradlew.bat :native-http3:assembleDebug "-PnativeHttp3.enableCmake=true"
```

## Проверка с libcurl HTTP/3

Нужен импортированный bundle `third_party/curl-android`.

```powershell
.\scripts\check-curl-android-layout.ps1 -Abis x86_64
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

Сборка и установка приложения с HTTP/3 backend:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
.\scripts\install-http3-apk.ps1
```

## Важно для закрытой инфраструктуры

Исходники модуля коммитятся в репозиторий. Реальные `third_party/curl-android/include` и `third_party/curl-android/libs` обычно переносятся отдельным offline artifact, потому что это тяжёлые prebuilt-зависимости и они игнорируются `.gitignore`.
