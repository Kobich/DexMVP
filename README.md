# DexMVP

DexMVP - прототип Android-приложения с контролируемой динамической загрузкой DEX-кода с локального Ktor-сервера.

Host-приложение получает manifest активного remote-модуля, скачивает APK-артефакт, проверяет SHA-256, сохраняет файл во внутреннее хранилище, помечает его read-only и только после этого загружает entry point через `DexClassLoader`.

## Что показывает проект

- Android host не запускает произвольный код из сети без проверки.
- Manifest фиксирует `moduleId`, `version`, `minHostApi`, `entryPoint`, `artifactUrl`, `sha256`.
- Remote APK компилируется отдельно и реализует общий контракт `RemoteFeature`.
- Ktor server отдает manifest и APK-артефакт.
- UI показывает этапы `Check`, `Download`, `Run`, ошибки, manifest и результат выполнения remote-кода.

## Модули

- `app` - Android Compose host-приложение.
- `feature:remote-execution:api` - общий контракт host и remote-модуля.
- `feature:remote-execution:impl` - UI демо, загрузка manifest, скачивание APK, SHA-256, internal storage, `DexClassLoader`.
- `remote-module` - demo APK с `com.engboost.remote.HelloRemoteFeature`.
- `server` - Ktor server с endpoints manifest/artifact.

## Быстрый запуск

Собрать remote APK:

```bash
./gradlew.bat :remote-module:assembleDebug
```

Запустить server:

```bash
./gradlew.bat :server:run
```

Собрать host-приложение:

```bash
./gradlew.bat :app:assembleDebug
```

В Android emulator адрес сервера:

```text
http://10.0.2.2:8080
```

В приложении нажать:

```text
Check -> Download -> Run
```

## Запуск на физическом устройстве

Если приложение запускается на реальном телефоне, `10.0.2.2` не подойдет. Нужно указать IP компьютера в локальной сети:

```powershell
$env:DEX_SERVER_BASE_URL="http://YOUR_HOST_IP:8080"
./gradlew.bat :server:run
```

В поле `Server URL` в приложении указать тот же адрес:

```text
http://YOUR_HOST_IP:8080
```

## Endpoints сервера

- `GET /health` - проверка доступности сервера.
- `GET /api/v1/modules/active` - manifest активного remote-модуля.
- `GET /api/v1/modules/hello/1/artifact` - APK-артефакт remote-модуля.

## Документация

- [Разбор с нуля](docs/beginner-guide.md)
- [Архитектура](docs/architecture.md)
- [Карта кода](docs/code-map.md)
- [System Flow](docs/system-flow.md)
- [Demo Script](docs/demo-script.md)
- [Security Notes](docs/security-notes.md)
- [Feature Integration Notes](feature/remote-execution/README.md)
- [Closed Infra Runbook](docs/closed-infra-runbook.md)

## Как проверить проект

Полная локальная проверка:

```bash
./gradlew.bat :app:assembleDebug :remote-module:assembleDebug :server:build
```

То же самое через helper script:

```powershell
.\scripts\verify-project.ps1
```

Проверка server runtime:

```bash
./gradlew.bat :remote-module:assembleDebug
./gradlew.bat :server:run
```

То же самое через helper script:

```powershell
.\scripts\run-server.ps1
```

В другом терминале:

```bash
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/modules/active
curl http://localhost:8080/api/v1/modules/hello/1/artifact --output remote-module-debug.apk
```

То же самое через helper script:

```powershell
.\scripts\check-server.ps1
```

Проверка на emulator:

1. Запустить `server`.
2. Запустить `app`.
3. В `Server URL` оставить `http://10.0.2.2:8080`.
4. Нажать `Check -> Download -> Run`.
5. Убедиться, что на экране появился результат `Hello from remote module`.

## Ограничения MVP

Проект демонстрирует controlled dynamic loading, а не production-ready систему обновления кода. В MVP используется SHA-256 из trusted local server manifest. Полноценная цифровая подпись artifact/manifest пока не реализована.

Remote code выполняется внутри процесса host-приложения, поэтому настоящей sandbox-изоляции между host и remote-модулем нет. Для production нужно отдельно проектировать threat model, policy compliance и модель доверия.
