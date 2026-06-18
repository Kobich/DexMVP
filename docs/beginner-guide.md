# DexMVP: разбор с нуля

Этот документ написан как рабочая шпаргалка. Его цель - быстро вспомнить, что где лежит, зачем нужен каждый модуль и как весь механизм работает по шагам.

## 1. Общая идея проекта

Есть два Android-кода:

- host app - обычное приложение `app`, которое запускается на телефоне или emulator;
- remote module - отдельный APK `remote-module`, который host скачивает и запускает динамически.

Между ними есть общий контракт:

```text
feature:remote-execution:api
```

Сервер:

```text
server
```

отдает host-приложению информацию о remote module и сам APK-файл.

Главная мысль:

```text
host не запускает файл сразу
host сначала получает manifest
host скачивает APK
host проверяет SHA-256
host кладет APK во внутреннее хранилище
host делает APK read-only
host загружает выбранный features[].entryPoint через DexClassLoader
host вызывает RemoteFeature.execute() или RemoteComposeFeature.Content()
```

## 2. Из чего состоит проект

```text
DexMVP
  app
  feature
    remote-execution
      api
      impl
  remote-module
  server
  docs
  scripts
```

## 3. `app`

Путь:

```text
app
```

Это обычное Android-приложение. Оно нужно только чтобы запустить экран демо.

Главный файл:

```text
app/src/main/java/com/engboost/dexmvp/MainActivity.kt
```

Что делает `MainActivity`:

```kotlin
setContent {
    DexMVPTheme {
        RemoteExecutionDemoScreen()
    }
}
```

То есть `app` не знает деталей скачивания, проверки hash или `DexClassLoader`. Он просто открывает экран фичи.

Gradle-зависимость:

```kotlin
implementation(project(":feature:remote-execution:impl"))
```

## 4. `feature:remote-execution:api`

Путь:

```text
feature/remote-execution/api
```

Это самый важный контракт между host и remote APK.

Файл:

```text
feature/remote-execution/api/src/main/kotlin/com/engboost/remoteapi/RemoteFeature.kt
```

Там лежит:

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

Зачем это нужно:

- host после загрузки APK получает какой-то объект;
- host должен понять, что этот объект умеет выполнять нужную функцию;
- поэтому объект должен реализовать `RemoteFeature` или `RemoteComposeFeature`;
- host приводит объект к нужному контракту и вызывает `execute()` или `Content()`.

Важно:

```text
package com.engboost.remoteapi менять осторожно
```

Если переименовать package или классы, нужно синхронно поменять host и remote APK.

## 5. `feature:remote-execution:impl`

Путь:

```text
feature/remote-execution/impl
```

Это реализация фичи. Здесь весь Android-код, который:

- ходит в server;
- скачивает APK;
- проверяет hash;
- сохраняет файл;
- запускает remote code;
- показывает demo UI.

### UI

Файл:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/remoteexecution/ui/RemoteExecutionDemoScreen.kt
```

На экране есть:

- `Server URL`;
- `Check`;
- `Download`;
- `Run`;
- блок manifest;
- блок результата;
- event log.

### Loader classes

Папка:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader
```

Классы:

```text
RemoteModuleRepository
ManifestApiClient
ArtifactDownloader
Sha256Verifier
ModuleStorage
DexModuleLoader
RemoteFeatureRunner
RemoteModuleManifest
```

## 6. Что делает каждый loader-класс

### `RemoteModuleRepository`

Фасад для UI.

UI вызывает только три метода:

```kotlin
fetchManifest()
downloadAndVerify(manifest)
run(manifest, artifact, input)
```

Это главный класс, с которого лучше начинать чтение loader-кода.

### `ManifestApiClient`

Ходит на server:

```text
GET /api/v1/modules/active
```

Получает JSON manifest и превращает его в `RemoteModuleManifest`.

### `RemoteModuleManifest`

DTO manifest:

```text
moduleId
version
hostApiVersion
minHostApi
features[].entryPoint
artifactUrl
sha256
signature
```

Host использует эти поля, чтобы понять:

- какой модуль загрузить;
- где скачать APK;
- какой класс внутри APK создать;
- какой hash должен быть у файла;
- совместим ли remote module с текущим host API.

### `ArtifactDownloader`

Скачивает APK по `artifactUrl` из manifest.

На этом шаге файл еще нельзя запускать. Он только скачан.

### `Sha256Verifier`

Считает SHA-256 скачанного файла и сравнивает с `sha256` из manifest.

Если hash не совпал:

```text
загрузка останавливается
DexClassLoader не вызывается
remote code не запускается
```

### `ModuleStorage`

Кладет проверенный APK во внутреннее хранилище приложения:

```text
filesDir/remote-modules/hello-1.apk
```

После копирования делает файл read-only.

Это важно, потому что на Android 14+ динамически загружаемый файл должен быть read-only до загрузки.

### `DexModuleLoader`

Создает `DexClassLoader`.

Логика:

```text
взять путь к APK
создать optimized dir в codeCacheDir
создать DexClassLoader
загрузить class по выбранному features[].entryPoint
создать instance через no-arg constructor
привести instance к RemoteFeature или RemoteComposeFeature
```

### `RemoteFeatureRunner`

Последний слой перед вызовом remote-кода.

Проверяет:

- `minHostApi <= HOST_API_VERSION`;
- `feature.id == manifest.moduleId`;
- `feature.version == manifest.version`.

Потом вызывает:

```kotlin
feature.execute(input)
```

## 7. `remote-module`

Путь:

```text
remote-module
```

Это отдельный APK, который host скачивает с server.

Главный файл:

```text
remote-module/src/main/java/com/engboost/remote/HelloRemoteFeature.kt
remote-module/src/main/java/com/engboost/remote/CounterComposeFeature.kt
remote-module/src/main/java/com/engboost/remote/ProfileCardComposeFeature.kt
remote-module/src/main/java/com/engboost/remote/ChecklistComposeFeature.kt
```

Он реализует:

```kotlin
RemoteFeature или RemoteComposeFeature
```

Класс указан в manifest server:

```text
com.engboost.remote.HelloRemoteFeature
com.engboost.remote.CounterComposeFeature
com.engboost.remote.ProfileCardComposeFeature
com.engboost.remote.ChecklistComposeFeature
```

Важно:

- класс должен иметь no-arg constructor;
- `id` должен совпадать с `moduleId`;
- `version` должен совпадать с manifest version;
- remote module зависит от api через `compileOnly`.

Почему `compileOnly`:

```text
RemoteFeature, RemoteComposeFeature и Compose runtime должны жить в host app
remote APK компилируется против него
но не тащит свою вторую копию интерфейса
```

Если положить копию `RemoteFeature`, `RemoteComposeFeature` или Compose runtime внутрь remote APK, можно получить проблему type identity: класс вроде называется так же, но classloader видит другой тип.

## 8. `server`

Путь:

```text
server
```

Это Ktor server на локальном компьютере.

Файлы:

```text
server/src/main/kotlin/com/engboost/server/Application.kt
server/src/main/kotlin/com/engboost/server/modules/ModuleRegistry.kt
server/src/main/kotlin/com/engboost/server/security/Sha256.kt
```

Endpoints:

```text
GET /health
GET /api/v1/modules/active
GET /api/v1/modules/hello/1/artifact
```

Server берет APK отсюда:

```text
remote-module/build/outputs/apk/debug/remote-module-debug.apk
```

То есть перед запуском server нужно собрать:

```powershell
.\gradlew.bat :remote-module:assembleDebug
```

## 9. Как работает сценарий `Check`

Пользователь нажимает `Check`.

Дальше:

```text
RemoteExecutionDemoScreen
  -> RemoteModuleRepository.fetchManifest()
  -> ManifestApiClient.fetchActiveManifest()
  -> HTTP GET /api/v1/modules/active
  -> server возвращает JSON manifest
  -> app показывает manifest на экране
```

После `Check` remote APK еще не скачан.

## 10. Как работает сценарий `Download`

Пользователь нажимает `Download`.

Дальше:

```text
RemoteExecutionDemoScreen
  -> RemoteModuleRepository.downloadAndVerify(manifest)
  -> ArtifactDownloader.download(manifest.artifactUrl)
  -> HTTP GET /api/v1/modules/hello/1/artifact
  -> temp file
  -> Sha256Verifier.requireMatches(tempFile, manifest.sha256)
  -> ModuleStorage.commitVerifiedArtifact()
  -> filesDir/remote-modules/hello-1.apk
  -> set read-only
```

После `Download` APK уже лежит внутри host-приложения, но remote code еще не выполнялся.

## 11. Как работает сценарий `Run`

Пользователь нажимает `Run`.

Дальше:

```text
RemoteExecutionDemoScreen
  -> RemoteModuleRepository.run()
  -> RemoteFeatureRunner.run()
  -> DexModuleLoader.load()
  -> DexClassLoader
  -> loadClass(selectedFeature.entryPoint)
  -> newInstance()
  -> instance as RemoteFeature или RemoteComposeFeature
  -> feature.execute(...) или feature.Content(...)
  -> RemoteOutput или remote Compose UI
  -> UI показывает результат
```

## 12. Как запускать локально

Терминал 1:

```powershell
.\scripts\run-server.ps1
```

Терминал 2:

```powershell
.\scripts\check-server.ps1
```

Android Studio:

```text
Run app on emulator
```

В приложении:

```text
Server URL = http://10.0.2.2:8080
Check -> Download -> Open
```

## 13. Как понять, что все работает

Server:

```text
/health возвращает OK
/api/v1/modules/active возвращает JSON manifest
/api/v1/modules/hello/1/artifact возвращает APK
```

App:

```text
Check complete
Download complete
Open hello-output complete
Hello Output открывается на отдельном экране
Counter/Profile/Checklist открываются на отдельном экране
Back возвращает на список фич
```

## 14. Что переносить в рабочий проект

Минимально:

```text
feature/remote-execution/api
feature/remote-execution/impl
```

В host app добавить:

```kotlin
implementation(project(":feature:remote-execution:impl"))
```

В remote APK добавить:

```kotlin
compileOnly(project(":feature:remote-execution:api"))
```

Если demo UI не нужен, заменить `RemoteExecutionDemoScreen` на свой use case/ViewModel, но оставить loader-классы.

## 15. Самые важные места, которые нельзя случайно сломать

- `com.engboost.remoteapi` должен совпадать у host и remote APK.
- `features[].entryPoint` в server manifest должен совпадать с реальным class name в remote APK.
- `remote-module` должен использовать `compileOnly(project(":feature:remote-execution:api"))`.
- APK должен быть собран до запуска server.
- Для emulator server URL должен быть `http://10.0.2.2:8080`.
- Для физического телефона нужен IP компьютера в локальной сети.
