# remote-execution feature

Фича разделена на `api` и `impl`, чтобы ее было проще переносить в рабочий проект.

## `api`

Gradle module:

```text
:feature:remote-execution:api
```

Содержит только общий контракт:

```text
com.engboost.remoteapi.RemoteFeature
com.engboost.remoteapi.RemoteInput
com.engboost.remoteapi.RemoteOutput
```

На этот модуль должны смотреть и host app, и remote APK. Для remote APK зависимость оставлена `compileOnly`, чтобы контракт не упаковывался второй копией внутрь remote artifact.

## `impl`

Gradle module:

```text
:feature:remote-execution:impl
```

Содержит:

- demo UI `RemoteExecutionDemoScreen`;
- HTTP manifest client;
- artifact downloader;
- SHA-256 verifier;
- internal storage;
- `DexClassLoader`;
- runner, который вызывает `RemoteFeature.execute()`.

## Как встраивать в рабочий проект

Минимально:

```kotlin
implementation(project(":feature:remote-execution:impl"))
```

Remote APK должен компилироваться против:

```kotlin
compileOnly(project(":feature:remote-execution:api"))
```

Если demo UI не нужен, оставь loader-классы из `impl`, а `RemoteExecutionDemoScreen` замени на свою ViewModel/use case.

## Что менять обычно

- `RemoteModuleRepository` - если нужен другой API или авторизация.
- `ManifestApiClient` - если manifest приходит не с Ktor endpoint.
- `ModuleStorage` - если нужна политика кеша/rollback.
- `RemoteFeatureRunner` - если появится новый host API contract.

## Что менять осторожно

- Package `com.engboost.remoteapi` - должен совпадать между host и remote APK.
- `entryPoint` в manifest - должен указывать на реальный класс remote APK.
- `compileOnly` у `remote-module` - важно для единого `RemoteFeature` type identity.
