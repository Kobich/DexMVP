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
