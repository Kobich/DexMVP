# Карта кода

Короткая навигация по проекту.

## Entry point

- `app/src/main/java/com/engboost/dexmvp/MainActivity.kt`

Тонкая Activity. Только включает edge-to-edge, применяет тему и открывает `RemoteExecutionDemoScreen`.

## Feature API

- `feature/remote-execution/api/src/main/kotlin/com/engboost/remoteapi/RemoteFeature.kt`

Общий контракт между host и remote APK:

- `RemoteFeature` - интерфейс, который реализует remote-класс.
- `RemoteComposeFeature` - интерфейс для remote Compose-экрана.
- `RemoteHost` - callback из remote UI обратно в host.
- `RemoteInput` - входные данные для remote-кода.
- `RemoteOutput` - результат remote-кода.

Этот пакет важно не переименовывать без одновременного изменения host и remote APK.

## Feature Impl UI

- `feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/remoteexecution/ui/RemoteExecutionDemoScreen.kt`

Demo UI:

- `Check` получает manifest;
- `Download` скачивает APK и проверяет SHA-256;
- `Open` загружает выбранный entry point через `DexClassLoader`.
- Для `kind = output` вызывается `RemoteFeature.execute()`.
- Для `kind = compose` host встраивает `RemoteComposeFeature.Content()`.
- Remote-контент открывается на отдельном host-owned экране с кнопкой `Back`.

В рабочем проекте этот экран можно заменить на ViewModel/use case, оставив loader-классы ниже.

## Feature Impl Loader

- `ManifestApiClient` - HTTP-запрос `/api/v1/modules/active`.
- `ArtifactDownloader` - скачивание APK по `artifactUrl`.
- `Sha256Verifier` - расчет и проверка SHA-256.
- `ModuleStorage` - сохранение APK во внутреннее хранилище и read-only перед загрузкой.
- `DexModuleLoader` - создание `DexClassLoader` и инстанса entry point.
- `RemoteFeatureRunner` - проверки `minHostApi`, `feature.id`, `feature.version`, затем `execute()` или загрузка Compose-фичи.
- `RemoteModuleRepository` - фасад, который объединяет все шаги для UI.

Путь:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader
```

## Feature Impl Transport

- `RemoteTransport` - общий интерфейс для manifest/artifact transport.
- `OkHttpRemoteTransport` - текущий рабочий HTTP fallback.
- `FallbackRemoteTransport` - transport wrapper для режима `HTTP3_PREFERRED`.
- `RemoteTransportFactory` - создает transport по выбранному режиму.
- `TransportMode` - `HTTP_FALLBACK`, `HTTP3_PREFERRED`, `HTTP3_ONLY`.
- `Http3RemoteTransport` - адаптер к `native-http3`; выполняет manifest/artifact запросы через libcurl HTTP/3 JNI backend.
- `TransportDiagnosticsProvider` - данные для UI diagnostics: mode/backend/TLS/native engine.

Путь:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/transport
```

## Native HTTP/3

- `docs/AGENT-START-HERE.md` - главный entrypoint для будущего агента/локальной модели.
- `native-http3/src/main/java/com/engboost/nativehttp3/NativeHttp3Client.java` - Java-вход в JNI/libcurl HTTP/3.
- `native-http3/src/main/java/com/engboost/nativehttp3/NativeHttp3Config.java` - базовые настройки timeout/TLS.
- `native-http3/src/main/java/com/engboost/nativehttp3/NativeHttp3UnavailableException.java` - диагностическая ошибка, если native-библиотека или curl backend недоступны.
- `native-http3/src/main/cpp/CMakeLists.txt` - opt-in CMake-конфиг; собирает JNI bridge и линкует prebuilt curl/OpenSSL/ngtcp2/nghttp3 static libs.
- `native-http3/src/main/cpp/native_http3_client.cpp` - JNI bridge `nativeEngineInfo()`, `nativeGetString()`, `nativeDownload()`.
- `scripts/verify-native-http3.ps1` - проверяет сборку `native-http3` с включённым CMake.
- `scripts/verify-native-http3-curl.ps1` - проверяет сборку `native-http3` с prebuilt libcurl.
- `scripts/check-curl-android-layout.ps1` - проверяет наличие `curl.h` и `libcurl.so` по ABI.
- `third_party/curl-android/README.md` - шаблон offline bundle для закрытого контура.
- `docs/http3-curl-source-strategy.md` - решение, откуда брать `libcurl` и почему не стоит тащить случайный `.so`.
- `docs/http3-curl-build-guide.md` - пошаговая инструкция: получить approved bundle или собрать через vcpkg.
- `docs/http3-current-state.md` - текущий статус HTTP/3 стенда, адреса, ограничения и следующие шаги.
- `docs/http3-critical-handoff.md` - исторический handoff: HTTP/3 заработал, старый TLS-вариант был через debug CA.
- `docs/http3-setup-guide.md` - последовательная установка HTTP/3 стенда Windows + WSL + Android.
- `docs/http3-tls-guide.md` - legacy local CA, NGINX server cert и Android/libcurl trust.
- `docs/caddy-http3-tls-guide.md` - Caddy HTTP/3 endpoint на Windows без project-local CA в APK.
- `docs/production-tls-and-code-trust-guide.md` - production-like TLS, CSR под IP и отдельная trust-модель для remote APK/DEX.
- `docs/scripts-reference.md` - справочник всех helper scripts.
- `scripts/import-curl-from-vcpkg.ps1` - копирует собранный vcpkg `curl[http3]` в `third_party/curl-android`.
- `scripts/build-http3-apk.ps1` - собирает `app-debug.apk` с HTTP/3 curl flags под выбранный ABI.
- `scripts/install-http3-apk.ps1` - устанавливает собранный APK через `adb` и запускает приложение.
- `scripts/show-http3-wsl-state.ps1` - показывает текущий WSL IP и команду запуска Ktor с правильным `BaseUrl`.
- `scripts/wsl-generate-http3-certs.sh` - WSL-скрипт генерации локального CA/server cert и копирования CA в Android debug resources.

CMake выключен по умолчанию, чтобы не ломать Gradle sync на машинах без NDK. Реальный HTTP/3 включается скриптами `build-http3-apk.ps1` / `install-http3-apk.ps1`, которые передают Gradle flags для CMake и curl backend.

## Remote APK

- `remote-module/src/main/java/com/engboost/remote/HelloRemoteFeature.kt`

Demo implementations. Сейчас APK содержит:

- `HelloRemoteFeature` - output-фича.
- `CounterComposeFeature` - интерактивный счетчик.
- `ProfileCardComposeFeature` - карточка профиля.
- `ChecklistComposeFeature` - чеклист.

Каждый класс должен:

- реализовать `RemoteFeature` или `RemoteComposeFeature`;
- иметь public no-arg constructor;
- совпадать с `features[].entryPoint` в server manifest.

## Server

- `server/src/main/kotlin/com/engboost/server/Application.kt` - Ktor routes.
- `server/src/main/kotlin/com/engboost/server/modules/ModuleRegistry.kt` - active module metadata и путь к APK.
- `server/src/main/kotlin/com/engboost/server/security/Sha256.kt` - расчет SHA-256 для manifest.

Server берет APK из:

```text
remote-module/build/outputs/apk/debug/remote-module-debug.apk
```

## Основной поток

```text
MainActivity
  -> RemoteExecutionDemoScreen
  -> RemoteModuleRepository
  -> ManifestApiClient
  -> ArtifactDownloader
  -> Sha256Verifier
  -> ModuleStorage
  -> DexModuleLoader
  -> RemoteFeature.execute() OR RemoteComposeFeature.Content()
```
