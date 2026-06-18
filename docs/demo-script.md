# Demo Script

Этот сценарий подходит для демонстрации проекта ревьюеру.

## 1. Собрать remote module

```bash
./gradlew.bat :remote-module:assembleDebug
```

Ожидаемый результат:

```text
BUILD SUCCESSFUL
```

После сборки появляется APK:

```text
remote-module/build/outputs/apk/debug/remote-module-debug.apk
```

## 2. Запустить server

```bash
./gradlew.bat :server:run
```

Сервер слушает:

```text
http://localhost:8080
```

Для Android emulator host доступен как:

```text
http://10.0.2.2:8080
```

## 3. Проверить server вручную

Health check:

```bash
curl http://localhost:8080/health
```

Ожидаемый ответ:

```text
OK
```

Manifest:

```bash
curl http://localhost:8080/api/v1/modules/active
```

Ожидаемый ответ содержит:

```json
{
  "moduleId": "hello",
  "version": 1,
  "features": [
    {
      "id": "hello-output",
      "kind": "output",
      "entryPoint": "com.engboost.remote.HelloRemoteFeature"
    }
  ],
  "sha256": "..."
}
```

## 4. Запустить Android host

Запустить `app` из Android Studio или собрать:

```bash
./gradlew.bat :app:assembleDebug
```

В поле `Server URL` оставить:

```text
http://10.0.2.2:8080
```

## 5. Выполнить сценарий в UI

Нажать `Check`.

Ожидаемо:

- приложение получает manifest;
- на экране появляется `moduleId`, `version`, `features`, `sha256`;
- в event log появляется `Check complete`.

Нажать `Download`.

Ожидаемо:

- приложение скачивает APK;
- проверяет SHA-256;
- сохраняет artifact во внутреннее хранилище;
- на экране появляется путь к artifact;
- в event log появляется `Download complete`.

Выбрать `Hello Output` и нажать `Open`.

Ожидаемо:

- приложение загружает `com.engboost.remote.HelloRemoteFeature`;
- приводит объект к `RemoteFeature`;
- вызывает `execute()`;
- показывает результат `Hello from remote module`.

Выбрать `Counter Compose`, `Profile Card` или `Checklist` и нажать `Open`.

Ожидаемо:

- приложение загружает выбранный Compose entry point;
- приводит объект к `RemoteComposeFeature`;
- переходит на отдельный экран выбранной фичи;
- вызывает `Content()`;
- на экране появляется remote Compose UI.
- кнопка `Back` возвращает на список фич.

## 6. Негативные проверки

### Server down

Остановить server и нажать `Check`.

Ожидаемо:

- UI показывает ошибку сетевого запроса;
- приложение не падает.

### Wrong hash

Изменить server так, чтобы он вернул неверный `sha256`, или временно испортить APK после получения manifest.

Ожидаемо:

- `Download` завершается ошибкой `SHA-256 mismatch`;
- artifact не используется для запуска.

### Wrong entry point

Вернуть несуществующий `features[].entryPoint`.

Ожидаемо:

- `Run` завершается ошибкой загрузки класса;
- приложение не падает.

## 7. Формулировка для защиты

Короткое объяснение:

> Я сделал controlled dynamic loading prototype. Host app сначала получает manifest со списком фич, entry point и SHA-256, скачивает APK с trusted local Ktor server, проверяет целостность, сохраняет файл во внутреннее хранилище, помечает read-only и только после этого загружает выбранный класс через DexClassLoader. Запускается не произвольный код, а заранее согласованный контракт RemoteFeature или RemoteComposeFeature.
