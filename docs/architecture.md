# Архитектура DexMVP

## Цель

DexMVP демонстрирует контролируемую динамическую загрузку Android-кода. Host-приложение загружает заранее согласованный remote-модуль, проверяет его целостность и запускает только entry point из manifest. Entry point может реализовать `RemoteFeature` для обычного output-сценария или `RemoteComposeFeature` для remote Compose-экрана.

## Компоненты

### `app`

Android host-приложение на Jetpack Compose.

Ответственность:

- показывает UI демо-сценария;
- хранит текущий manifest и путь к скачанному artifact;
- вызывает `RemoteModuleRepository`;
- отображает результат выполнения remote-модуля и ошибки.

Основной экран: `app/src/main/java/com/engboost/dexmvp/MainActivity.kt`.

### `feature:remote-execution:api`

Общий API-контракт между host и remote-модулем.

Контракт:

```kotlin
interface RemoteFeature {
    val id: String
    val version: Int
    fun execute(input: RemoteInput): RemoteOutput
}

interface RemoteComposeFeature {
    val id: String
    val version: Int

    @Composable
    fun Content(input: RemoteInput, host: RemoteHost)
}
```

Host использует этот интерфейс после загрузки класса через `DexClassLoader`. Remote-модуль компилируется против этого же API.

### `feature:remote-execution:impl`

Технический слой загрузки и запуска remote-кода.

Ключевые классы загрузчика:

- `ManifestApiClient` - получает active manifest с сервера.
- `ArtifactDownloader` - скачивает APK-артефакт.
- `Sha256Verifier` - проверяет SHA-256 скачанного файла.
- `ModuleStorage` - сохраняет artifact во внутреннее хранилище приложения.
- `DexModuleLoader` - загружает entry point через `DexClassLoader`.
- `RemoteFeatureRunner` - проверяет совместимость API и вызывает `execute()` или загружает Compose-фичу.
- `RemoteModuleRepository` - фасад для UI.
- `RemoteTransport` / `OkHttpRemoteTransport` / `Http3RemoteTransport` - сетевой слой manifest/artifact.

UI фичи:

- `RemoteExecutionDemoScreen` - demo screen, который вызывает repository и показывает manifest/result/log.

### `native-http3`

Изолированный модуль native/JNI HTTP/3 transport.

Сейчас это Java API + opt-in NDK/CMake + JNI bridge к prebuilt `libcurl` с HTTP/3. Обычная Gradle-синхронизация не требует native-сборки: CMake включается только флагом `-PnativeHttp3.enableCmake=true`, а реальный curl backend — флагом `-PnativeHttp3.enableCurl=true`. Для локального стенда подтверждён сценарий `HTTP3_ONLY -> Check -> Download -> Open` через NGINX HTTP/3 в WSL.

```text
feature:remote-execution:impl
  -> Http3RemoteTransport
    -> native-http3
      -> JNI
        -> libcurl + ngtcp2/nghttp3 + OpenSSL
```

Loader-слой не должен знать, каким протоколом скачан manifest или artifact.

### `remote-module`

Отдельный Android APK, который содержит demo-реализации remote-фич:

```text
com.engboost.remote.HelloRemoteFeature
com.engboost.remote.CounterComposeFeature
com.engboost.remote.ProfileCardComposeFeature
com.engboost.remote.ChecklistComposeFeature
```

Классы имеют public no-arg constructor и реализуют `RemoteFeature` или `RemoteComposeFeature`.

### `server`

Ktor server, который отдает manifest и APK.

Ответственность:

- возвращает `/health`;
- возвращает active manifest;
- на старте запроса считает SHA-256 от собранного `remote-module-debug.apk`;
- отдает APK-артефакт по endpoint.

## Поток выполнения

```text
MainActivity
  -> RemoteExecutionDemoScreen
  -> RemoteModuleRepository.fetchManifest()
  -> ManifestApiClient
  -> RemoteTransport
  -> Ktor /api/v1/modules/active
  -> RemoteModuleRepository.downloadAndVerify()
  -> ArtifactDownloader
  -> RemoteTransport
  -> Sha256Verifier
  -> ModuleStorage
  -> RemoteModuleRepository.run()
  -> RemoteFeatureRunner
  -> DexModuleLoader
  -> RemoteFeature.execute() OR RemoteComposeFeature.Content()
```

## Manifest

Пример manifest:

```json
{
  "moduleId": "hello",
  "version": 1,
  "hostApiVersion": 1,
  "minHostApi": 1,
  "artifactUrl": "http://10.0.2.2:8080/api/v1/modules/hello/1/artifact",
  "sha256": "calculated-by-server",
  "signature": "",
  "features": [
    {
      "id": "hello-output",
      "title": "Hello Output",
      "kind": "output",
      "version": 1,
      "entryPoint": "com.engboost.remote.HelloRemoteFeature"
    },
    {
      "id": "counter-compose",
      "title": "Counter Compose",
      "kind": "compose",
      "version": 1,
      "entryPoint": "com.engboost.remote.CounterComposeFeature"
    }
  ]
}
```

Host проверяет:

- `minHostApi <= HOST_API_VERSION`;
- SHA-256 скачанного artifact совпадает с manifest;
- загруженный класс реализует контракт, соответствующий `features[].kind`;
- `feature.id` и `feature.version` совпадают с записью feature в manifest.

## Хранение artifact

APK сохраняется во внутреннюю директорию приложения:

```text
filesDir/remote-modules/{moduleId}-{version}.apk
```

Перед загрузкой файл помечается read-only. Это важно для Android 14+ и снижает риск подмены между проверкой hash и загрузкой.

## Feature API/Impl split

`api` содержит только стабильный контракт, который должен быть одинаковым у host и remote APK. Пакет `com.engboost.remoteapi` специально сохранен стабильным: по нему `DexClassLoader` и host видят один и тот же интерфейс.

`impl` содержит Android-зависимую реализацию: сеть, storage, проверку hash, `DexClassLoader` и demo UI. При встраивании в рабочий проект обычно переносится вся фича `feature:remote-execution`, а `app` зависит от `impl`.

## Почему APK, а не сырой `.dex`

`DexClassLoader` штатно работает с `.jar` и `.apk`, внутри которых есть `classes.dex`. Для MVP используется APK, потому что его удобно собирать обычным Android Gradle Plugin и отдавать с локального сервера.
