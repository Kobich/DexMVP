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

## 2.1. Offline Package Для Машины Без Интернета

Если закрытая машина не имеет доступа к интернету, заранее скачать и перенести:

- полный архив проекта `DexMVP`;
- Gradle cache: `%USERPROFILE%\.gradle\caches` и `%USERPROFILE%\.gradle\wrapper`;
- Android Studio installer или approved portable install;
- Android SDK platform `36`;
- Android SDK Build Tools;
- Android NDK;
- CMake из Android SDK;
- emulator system image или драйвер/настройки для физического устройства;
- `third_party/curl-android/include` и `third_party/curl-android/libs`;
- WSL Ubuntu image/export или готовую Linux-машину;
- NGINX binary/package/build с HTTP/3 module или готовый WSL/Linux image, где `nginx -V` показывает `--with-http_v3_module`;
- OpenSSL внутри WSL/Linux для генерации local CA/server cert;
- если планируется пересобирать curl: vcpkg archive/cache и все исходники/бинарный cache vcpkg.

Без этих файлов “с нуля без интернета” не получится: Gradle, Android SDK/NDK/CMake, NGINX HTTP/3 и curl bundle должны быть уже принесены внутрь контура.

Если можно принести только один zip репозитория, положить внешние offline artifacts внутрь проекта перед упаковкой:

```text
offline-artifacts/
  wsl/dexmvp-ubuntu-http3.tar
  gradle/gradle-user-home.zip
  android/android-sdk.zip
  android/android-ndk.zip
  android/android-cmake.zip
  nginx/packages/
```

Потом упаковать всю папку `DexMVP` целиком. На закрытой машине распаковать zip и идти по этому guide. `offline-artifacts/README.md` описывает назначение этой папки.

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

## 8.1. Как Принести Готовый NGINX HTTP/3

Лучший offline-вариант для Windows + WSL: настроить NGINX HTTP/3 на машине с интернетом, затем перенести WSL distribution целиком.

На машине-источнике:

```powershell
wsl --list --verbose
wsl --shutdown
wsl --export Ubuntu C:\transfer\dexmvp-ubuntu-http3.tar
```

Файл `dexmvp-ubuntu-http3.tar` перенести на закрытую машину.

На закрытой машине:

```powershell
wsl --import DexMvpUbuntu C:\wsl\DexMvpUbuntu C:\transfer\dexmvp-ubuntu-http3.tar --version 2
wsl -d DexMvpUbuntu
```

Проверить внутри WSL:

```bash
nginx -V 2>&1 | grep -o -- '--with-http_v3_module'
sudo nginx -t
```

Ожидаемо:

```text
--with-http_v3_module
nginx: configuration file ... test is successful
```

Альтернативы:

- перенести готовую Linux VM image;
- перенести approved `.deb`/package NGINX mainline и все зависимости;
- использовать отдельный Linux server, где NGINX и Ktor крутятся на одной машине.

Не лучший вариант: собирать NGINX HTTP/3 с исходников прямо в закрытой инфраструктуре. Для этого придётся заранее принести весь dependency set.

Если всё-таки нужно ставить/собирать NGINX с нуля, смотреть: `docs/nginx-http3-from-scratch.md`.

## 8.2. Как Проверить, Что Это Реально HTTP/3

Проверка `curl -k https://...` из WSL обычно показывает только HTTPS/HTTP2. Это не доказывает HTTP/3, потому что системный curl часто собран без HTTP/3.

Надёжные проверки:

1. В Android app выбрать:

```text
Transport mode = HTTP/3 only
```

2. В `Transport Diagnostics` должно быть:

```text
transport = libcurl HTTP/3 via JNI
engine = libcurl/... ngtcp2/... nghttp3/...
tls = enabled with local debug CA
```

3. `Check -> Download -> Open` должны пройти именно в `HTTP/3 only`. В этом режиме OkHttp fallback не используется.

4. На NGINX можно добавить лог/заголовок с `$http3`. По официальному NGINX `ngx_http_v3_module`, переменная `$http3` равна `h3` для HTTP/3 connections и пустая для не-HTTP/3.

Пример заголовка в server block:

```nginx
add_header X-DexMvp-Http3 $http3 always;
```

После reload:

```bash
sudo nginx -t
sudo nginx -s reload
```

Если Android HTTP/3 запрос прошёл, в response headers должен появиться:

```text
X-DexMvp-Http3: h3
```

Если значение пустое или заголовка нет, запрос пришёл не как HTTP/3.

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
