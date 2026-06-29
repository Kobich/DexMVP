# HTTP/3 Setup Guide

Стартовая инструкция по развёртыванию: `docs/deployment-guide.md`.

Пошаговый guide для текущего локального стенда Windows + WSL + Android.

## 0. Что сейчас строим

```text
Android app
  -> libcurl HTTP/3
  -> NGINX in WSL, port 8443
  -> Ktor server on Windows, port 8080
```

Ktor не становится HTTP/3 сервером. HTTP/3 endpoint — это NGINX.

## 1. Что установить

### Windows

Нужно:

- Android Studio;
- Android SDK;
- Android NDK;
- CMake из Android SDK;
- PowerShell;
- WSL2 Ubuntu;
- vcpkg, если нужно пересобрать `curl[http3]`;
- Android emulator или физическое устройство.

Проверка проекта:

```powershell
.\scripts\verify-project.ps1
```

### WSL Ubuntu

Нужно:

- NGINX mainline с HTTP/3 module;
- OpenSSL;
- curl для обычных HTTPS/proxy проверок.

В offline-инфраструктуре этот guide предполагает, что NGINX уже установлен или принесён готовым package/image. Если нужно ставить/собирать NGINX HTTP/3 с нуля, смотреть `docs/nginx-http3-from-scratch.md`.

Проверка NGINX:

```bash
nginx -v
nginx -V 2>&1 | grep -o -- '--with-http_v3_module'
```

Ожидаемо:

```text
--with-http_v3_module
```

## 2. Подготовить curl bundle для Android

Если bundle уже есть:

```powershell
.\scripts\check-curl-android-layout.ps1
```

Если надо импортировать из vcpkg:

```powershell
.\scripts\import-curl-from-vcpkg.ps1 -VcpkgRoot C:\Users\RED.DOT\Downloads\vcpkg-master\vcpkg-master
```

Для проверки только emulator ABI:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64
```

Для проверки только физического устройства:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis arm64-v8a
```

## 3. Настроить NGINX в WSL

Если закрытая машина без интернета, самый простой перенос — WSL export/import готового Ubuntu с установленным NGINX HTTP/3. Команды: `docs/deployment-guide.md`, раздел `8.1. Как Принести Готовый NGINX HTTP/3`.

Текущий config:

```text
/etc/nginx/conf.d/dexmvp-http3.conf
```

В нём должен быть upstream до Ktor на Windows:

```nginx
proxy_pass http://172.31.112.1:8080;
```

Если gateway изменился:

```bash
ip route | grep default
cat /etc/resolv.conf | grep nameserver
```

Проверка:

```bash
sudo nginx -t
sudo nginx -s reload
```

## 4. Узнать текущий WSL IP

На Windows:

```powershell
.\scripts\show-http3-wsl-state.ps1
```

Скрипт покажет:

- WSL IP;
- NGINX upstream;
- URL для Android app;
- команду запуска Ktor server.

Пример URL:

```text
https://172.31.123.239:8443
```

## 5. Запустить Ktor server на Windows

Важно: `BaseUrl` должен совпадать с WSL IP.

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

Если `BaseUrl` старый, `Check` может работать, а `Download` будет падать из-за неправильного `artifactUrl`.

## 6. Проверить proxy в WSL

В WSL:

```bash
curl http://172.31.112.1:8080/health
curl -k https://127.0.0.1:8443/health
curl -k https://127.0.0.1:8443/api/v1/modules/active
```

В manifest проверить:

```text
artifactUrl = https://CURRENT_WSL_IP:8443/...
```

## 7. Собрать APK с HTTP/3

Для emulator:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

Для физического устройства:

```powershell
.\scripts\build-http3-apk.ps1 -Abi arm64-v8a
```

## 8. Установить и запустить APK

Если APK уже собран:

```powershell
.\scripts\install-http3-apk.ps1
```

Сразу собрать и установить:

```powershell
.\scripts\install-http3-apk.ps1 -Build -Abi x86_64
```

## 9. Проверить в приложении

В Android app:

```text
Server URL = https://CURRENT_WSL_IP:8443
Transport mode = HTTP3_ONLY
```

Порядок:

```text
Check -> Download -> Open
```

Чтобы доказать, что это именно HTTP/3, а не обычный HTTPS/HTTP2, использовать режим `HTTP3_ONLY` и NGINX marker `$http3`. Команды: `docs/deployment-guide.md`, раздел `8.2. Как Проверить, Что Это Реально HTTP/3`.

## 10. Важные ограничения

- TLS verification для локального HTTP/3 идёт через debug CA.
- Сертификат self-signed.
- Это не production security.
- WSL IP может измениться после перезапуска.
- WSL curl обычно не умеет HTTP/3, поэтому HTTP/3 проверяется Android app.

Критичный handoff: `docs/http3-critical-handoff.md`.
