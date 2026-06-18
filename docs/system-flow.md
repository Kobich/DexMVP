# System Flow

Ниже описана система связей между модулями без лишних деталей реализации.

## Gradle зависимости

```text
app
  -> feature:remote-execution:impl
       -> feature:remote-execution:api

remote-module
  -> compileOnly feature:remote-execution:api
  -> compileOnly Compose dependencies

server
  -> не зависит от Android модулей
  -> читает APK из remote-module/build/outputs/apk/debug
```

## Runtime связи

```text
Android emulator / app
  -> HTTP http://10.0.2.2:8080
  -> Ktor server on host machine
  -> remote-module-debug.apk on disk
```

## Почему server не зависит от `remote-module` напрямую

Server не компилирует remote APK. Он только отдает файл, который уже собрал Gradle task:

```text
:remote-module:assembleDebug
```

Это проще для MVP:

- remote APK можно пересобрать отдельно;
- server просто считает SHA-256 файла;
- manifest всегда содержит hash текущего APK на диске.

## Почему remote-module зависит от api через `compileOnly`

Host app уже содержит `RemoteFeature`, `RemoteComposeFeature` и Compose runtime.

Remote APK должен знать эти интерфейсы и Compose API на этапе компиляции, но не должен упаковывать свою копию внутрь APK.

Иначе может получиться:

```text
host RemoteFeature != remote APK RemoteFeature
host RemoteComposeFeature != remote APK RemoteComposeFeature
host Compose runtime != remote APK Compose runtime
```

Даже если package и class name одинаковые, разные classloader могут сделать это разными типами.

## Полный успешный сценарий

```text
1. Developer запускает :remote-module:assembleDebug
2. Gradle собирает remote-module-debug.apk
3. Developer запускает :server:run
4. Server слушает 0.0.0.0:8080
5. App на emulator открывается
6. User нажимает Check
7. App получает manifest
8. User нажимает Download
9. App скачивает APK
10. App проверяет SHA-256
11. App сохраняет APK во внутреннее хранилище
12. App делает APK read-only
13. User выбирает фичу и нажимает Open
14. Host переключает UI route на отдельный RemoteFeatureScreen
15. App создает DexClassLoader
16. App загружает feature entryPoint из manifest.features[]
17. Для output-фичи App вызывает execute()
18. Для compose-фичи App вызывает Content()
19. UI показывает RemoteOutput или remote Compose content
20. Back возвращает на список фич
```

## Где менять поведение

### Поменять адрес server по умолчанию

```text
RemoteExecutionDemoState.serverUrl
```

Файл:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/remoteexecution/ui/RemoteExecutionDemoScreen.kt
```

### Поменять формат manifest

```text
RemoteModuleManifest
ServerModuleManifest
ManifestApiClient
ModuleRegistry
```

### Поменять remote entry point

```text
server/src/main/kotlin/com/engboost/server/modules/ModuleRegistry.kt
remote-module/src/main/java/com/engboost/remote/...
```

### Добавить новую remote Compose-фичу

1. Создать класс в `remote-module`, который реализует `RemoteComposeFeature`.
2. Добавить запись в `features` внутри `ModuleRegistry`.
3. Пересобрать `:remote-module:assembleDebug`.
4. Перезапустить server.
5. В app нажать `Check -> Download -> Open`.

### Добавить подпись artifact

Точки входа:

```text
RemoteModuleManifest.signature
RemoteModuleRepository.downloadAndVerify()
```

### Добавить rollback

Точка входа:

```text
ModuleStorage
RemoteModuleRepository
```
