# Deployment Guide

Главный документ для развёртывания проекта. Читать сверху вниз. Остальные документы — справочники и детали.

## 0. Что Разворачиваем

Проект состоит из:

- Android host app — запускает remote modules.
- Remote APK — внешний модуль с output/Compose features.
- Ktor server — отдаёт manifest и APK artifact.
- Опционально NGINX HTTP/3 в WSL — reverse proxy перед Ktor.

Два рабочих режима:

```text
Обычный HTTP:
Android app -> Ktor :8080

HTTP/3:
Android app -> libcurl HTTP/3 -> NGINX WSL :8443 -> Ktor :8080
```

Ktor сам не является HTTP/3 server. HTTP/3 endpoint — только NGINX.

## 1. Что Открывать Первым

1. `docs/deployment-guide.md` — этот файл, основной порядок развёртывания.
2. `docs/closed-infra-runbook.md` — если машина без нормального интернета.
3. `docs/http3-setup-guide.md` — если нужен HTTP/3 стенд.
4. `docs/http3-critical-handoff.md` — если HTTP/3 уже был настроен и надо восстановить состояние.
5. `docs/scripts-reference.md` — если забыл, какой скрипт что делает.

## 2. Что Должно Быть На Машине

Минимум для обычного HTTP:

- Android Studio.
- Android SDK `compileSdk 36`.
- JDK/JBR из Android Studio.
- PowerShell.
- Android emulator или физическое устройство.

Дополнительно для HTTP/3:

- Android NDK.
- CMake из Android SDK.
- WSL2 Ubuntu.
- NGINX mainline с HTTP/3 module.
- `third_party/curl-android` offline bundle.
- Debug CA/server cert для локального TLS.

Детали по закрытой инфраструктуре: `docs/closed-infra-runbook.md`.

## 3. Что Должно Быть В Репозитории

Обязательно:

```text
app
feature
native-http3
remote-module
server
docs
scripts
third_party/curl-android/README.md
gradle
gradlew
gradlew.bat
settings.gradle.kts
build.gradle.kts
```

Для HTTP/3 в репозитории или в явно переносимом archive должны быть:

```text
third_party/curl-android/include
third_party/curl-android/libs
```

Текущая политика проекта: `third_party/curl-android/include` и `third_party/curl-android/libs` не игнорируются git, потому что без них закрытая машина не соберёт HTTP/3 APK. Если в рабочем контуре нельзя хранить бинарники в git, переносить эту папку отдельным archive и не забывать распаковать в тот же путь.

Не коммитить и не тащить как часть исходников:

- `build`
- `.gradle`
- `.cxx`
- private key локального CA
- NGINX server private key
- machine-specific cert/private key файлы

## 4. Первый Smoke Test

Запустить в корне проекта:

```powershell
.\scripts\verify-project.ps1
```

Ожидаемо: скрипт собирает `app`, `remote-module`, `server`.

Если это упало — сначала чинить Gradle/SDK/cache. Сервер и HTTP/3 пока не трогать.

## 5. Обычный HTTP: Поднять Server

Запуск:

```powershell
.\scripts\run-server.ps1
```

Server крутится на Windows:

```text
http://localhost:8080
```

Для Android emulator использовать:

```text
http://10.0.2.2:8080
```

Проверка в другом терминале:

```powershell
.\scripts\check-server.ps1
```

Ожидаемо:

```text
health: OK
manifest: HTTP 200
artifact: HTTP 200
```

## 6. Обычный HTTP: Запустить App

В Android Studio:

1. Открыть проект.
2. Дождаться Gradle sync.
3. Выбрать конфигурацию `app`.
4. Запустить emulator.
5. Нажать Run.

В приложении:

```text
Server URL = http://10.0.2.2:8080
Transport mode = HTTP fallback
Check -> Download -> Open
```

Для физического устройства `10.0.2.2` не подходит. Нужно указать IP Windows-компьютера:

```powershell
ipconfig
.\scripts\run-server.ps1 -BaseUrl "http://YOUR_HOST_IP:8080"
```

В приложении:

```text
Server URL = http://YOUR_HOST_IP:8080
```

## 7. HTTP/3: Общая Последовательность

HTTP/3 поднимать только после успешного обычного HTTP.

Порядок:

1. Проверить/import `third_party/curl-android`.
2. Проверить NGINX HTTP/3 в WSL.
3. Узнать текущий WSL IP.
4. Сгенерировать local debug CA/server cert под текущий WSL IP.
5. Запустить Ktor с `BaseUrl = https://CURRENT_WSL_IP:8443`.
6. Проверить NGINX proxy.
7. Собрать app с HTTP/3 flags.
8. Установить app через `adb`.
9. В app выбрать `HTTP3_ONLY` и выполнить `Check -> Download -> Open`.

Подробная инструкция: `docs/http3-setup-guide.md`.

## 8. HTTP/3: Минимальные Команды

Проверить curl bundle:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64
```

Показать текущий WSL IP и подсказку запуска server:

```powershell
.\scripts\show-http3-wsl-state.ps1
```

Сгенерировать cert из WSL:

```bash
bash scripts/wsl-generate-http3-certs.sh CURRENT_WSL_IP
```

Запустить Ktor за NGINX:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

Проверить proxy в WSL:

```bash
curl -k https://127.0.0.1:8443/health
curl -k https://127.0.0.1:8443/api/v1/modules/active
```

Собрать и установить HTTP/3 APK для emulator:

```powershell
.\scripts\install-http3-apk.ps1 -Build -Abi x86_64
```

В приложении:

```text
Server URL = https://CURRENT_WSL_IP:8443
Transport mode = HTTP/3 only
Check -> Download -> Open
```

## 9. Что Проверять После Развёртывания

Обычный HTTP:

- `.\scripts\verify-project.ps1` проходит.
- `.\scripts\check-server.ps1` возвращает OK.
- App выполняет `Check -> Download -> Open`.

HTTP/3:

- `.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64` проходит.
- `curl -k https://127.0.0.1:8443/health` из WSL отвечает.
- App diagnostics показывает `libcurl HTTP/3 via JNI`.
- TLS diagnostics показывает `enabled with local debug CA`.
- В режиме `HTTP3_ONLY` проходят `Check`, `Download`, `Open`.

## 10. Если Что-то Сломалось

Сначала определить слой:

```text
Gradle/SDK не собирается -> docs/closed-infra-runbook.md
Ktor не отвечает -> scripts/run-server.ps1, scripts/check-server.ps1
Check работает, Download нет -> проверить artifactUrl в manifest
HTTP/3 не отвечает -> docs/http3-critical-handoff.md
TLS curlCode=60 -> docs/http3-tls-guide.md
Remote class не грузится -> docs/architecture.md и feature/remote-execution/README.md
```

Самая частая HTTP/3 ошибка: WSL IP изменился, а server cert или `BaseUrl` остались старыми.

## 11. Роль Остальных Документов

- `docs/README.md` — короткий индекс документации.
- `docs/closed-infra-runbook.md` — перенос на закрытую машину.
- `docs/http3-setup-guide.md` — детальный HTTP/3 setup.
- `docs/http3-critical-handoff.md` — текущее рабочее состояние HTTP/3 и восстановление.
- `docs/http3-tls-guide.md` — TLS/local CA.
- `docs/scripts-reference.md` — справочник скриптов.
- `docs/architecture.md` — архитектура модулей.
- `docs/code-map.md` — карта классов и файлов.
