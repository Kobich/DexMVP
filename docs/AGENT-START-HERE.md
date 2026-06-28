# Agent Start Here

Этот файл — первая точка входа для будущего агента/локальной модели.

## Что Это За Проект

`DexMVP` — Android prototype для динамической загрузки внешних модулей.

Главная цепочка:

```text
Android host app
  -> получает manifest
  -> скачивает remote APK artifact
  -> проверяет SHA-256
  -> сохраняет artifact во внутреннее хранилище
  -> грузит entry point через DexClassLoader
  -> запускает output или Compose feature
```

## Основные Модули

- `app` — тонкий Android host.
- `feature:remote-execution:api` — общий контракт host/remote.
- `feature:remote-execution:impl` — UI, transport, loader, storage, verifier.
- `remote-module` — demo remote APK с output/Compose features.
- `server` — Ktor server manifest/artifact.
- `native-http3` — JNI/CMake/libcurl HTTP/3 transport backend.

## Что Читать Сначала

1. `docs/deployment-guide.md` — главный маршрут развёртывания проекта.
2. `docs/README.md` — индекс документации и что читать по задачам.
3. `docs/architecture.md` — архитектура проекта.
4. `docs/code-map.md` — карта кода.
5. `docs/scripts-reference.md` — все helper scripts.
6. `docs/http3-setup-guide.md` — последовательный HTTP/3 setup.
7. `docs/http3-critical-handoff.md` — критичный текущий handoff HTTP/3.

Если нужно восстановить текущий HTTP/3 стенд, начинать с:

```text
docs/http3-critical-handoff.md
docs/http3-setup-guide.md
```

## Текущий Статус HTTP/3

HTTP/3 end-to-end локально уже заработал.

Рабочая схема:

```text
Android app
  -> NativeHttp3Client / libcurl HTTP/3
  -> NGINX HTTP/3 in WSL :8443
  -> Ktor server on Windows :8080
  -> manifest/artifact
```

Ktor не является HTTP/3 server. HTTP/3 endpoint — NGINX в WSL.

## TLS Статус

Локальный HTTP/3 transport переведён на CA-based TLS verification.

Подтверждено: `HTTP3_ONLY -> Check -> Download -> Open` работает с `verifyTls=true`.

Схема:

```text
local debug CA
  -> app/src/debug/res/raw/dexmvp_local_ca.crt
  -> NativeHttp3Client
  -> CURLOPT_CAINFO
  -> NGINX server cert
```

Если CA ещё не сгенерирован, HTTP/3 режим покажет ошибку про `dexmvp_local_ca.crt`.

Guide:

```text
docs/http3-tls-guide.md
```

## Базовые Проверки

Обычная сборка проекта:

```powershell
.\scripts\verify-project.ps1
```

Проверка native HTTP/3 curl:

```powershell
.\scripts\verify-native-http3-curl.ps1
```

Показать текущий WSL IP и команду server start:

```powershell
.\scripts\show-http3-wsl-state.ps1
```

Собрать HTTP/3 APK для emulator:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

Установить APK:

```powershell
.\scripts\install-http3-apk.ps1
```

## Важные Скрипты

- `scripts/verify-project.ps1` — полный обычный build.
- `scripts/run-server.ps1` — запуск Ktor server.
- `scripts/show-http3-wsl-state.ps1` — текущий WSL IP и правильный `BaseUrl`.
- `scripts/build-http3-apk.ps1` — сборка app с HTTP/3 flags.
- `scripts/install-http3-apk.ps1` — установка app через `adb`.
- `scripts/import-curl-from-vcpkg.ps1` — импорт `curl[http3]` из vcpkg.
- `scripts/check-curl-android-layout.ps1` — проверка `third_party/curl-android`.

## UI Diagnostics

Главный экран показывает `Transport Diagnostics`:

- выбранный transport mode;
- transport backend;
- TLS verification state;
- native layer status;
- `NativeHttp3Client.engineInfo()`.

## Частые Ошибки

### `Check` работает, `Download` падает

Почти всегда причина — неправильный `artifactUrl` в manifest.

Ktor надо запускать с актуальным WSL IP:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

Проверить manifest:

```bash
curl -k https://127.0.0.1:8443/api/v1/modules/active
```

`artifactUrl` должен содержать тот же WSL IP.

### `curlCode=60`

TLS certificate не доверен или server cert выпущен не на текущий WSL IP.

Решение: выполнить `docs/http3-tls-guide.md` и пересобрать APK.

Если в ошибке есть:

```text
sslVerifyResult=9
```

Это почти точно неверное время Android/emulator. Проверить `adb shell date`, включить automatic date/time или сделать Cold Boot.

### Android Studio пересобрала без HTTP/3 flags

Для HTTP/3 теста не полагаться на обычный Run из Studio.

Использовать:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
.\scripts\install-http3-apk.ps1
```

## Что Не Трогать Без Причины

- `feature:remote-execution:api` package/API names.
- `compileOnly` зависимости в `remote-module`.
- SHA-256 verification flow.
- `ModuleStorage` read-only commit перед `DexClassLoader`.
- OkHttp fallback transport.

## Ближайшие TODO

1. Добавить logs для fallback reason.
2. Зафиксировать NGINX runbook для закрытой инфраструктуры.
3. Проверить HTTP/3 на физическом устройстве.
