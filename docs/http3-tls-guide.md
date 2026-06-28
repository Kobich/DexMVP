# HTTP/3 TLS Guide

Цель: убрать локальный `verifyTls=false` и проверять self-signed NGINX certificate через локальный debug CA.

## Что Меняется

Было:

```text
verifyTls = false
```

Стало:

```text
verifyTls = true
CURLOPT_CAINFO = app debug CA file
```

CA certificate кладётся в Android debug resources:

```text
app/src/debug/res/raw/dexmvp_local_ca.crt
```

Server certificate используется NGINX в WSL:

```text
/etc/nginx/dexmvp/certs/dexmvp-nginx.crt
/etc/nginx/dexmvp/certs/dexmvp-nginx.key
```

## 1. Сгенерировать CA и Server Cert

В WSL из корня проекта:

```bash
cd /mnt/c/Users/RED.DOT/AndroidStudioProjects/DexMVP
bash ./scripts/wsl-generate-http3-certs.sh
```

Скрипт:

- создаёт локальный CA, если его ещё нет;
- выпускает server cert под текущий WSL IP;
- копирует CA в `app/src/debug/res/raw/dexmvp_local_ca.crt`;
- печатает пути для NGINX.

Если WSL IP поменялся, запустить этот скрипт заново. CA останется прежним, server cert перевыпустится под новый IP.

## 2. Обновить NGINX Config

В WSL:

```bash
sudo nano /etc/nginx/conf.d/dexmvp-http3.conf
```

Должно быть:

```nginx
ssl_certificate /etc/nginx/dexmvp/certs/dexmvp-nginx.crt;
ssl_certificate_key /etc/nginx/dexmvp/certs/dexmvp-nginx.key;
```

Проверить и reload:

```bash
sudo nginx -t
sudo nginx -s reload
```

## 3. Запустить Ktor С Правильным BaseUrl

На Windows:

```powershell
.\scripts\show-http3-wsl-state.ps1
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

## 4. Пересобрать APK

CA лежит в debug resources, поэтому после генерации cert APK надо пересобрать.

Для emulator:

```powershell
.\scripts\install-http3-apk.ps1 -Build -Abi x86_64
```

Для физического устройства:

```powershell
.\scripts\install-http3-apk.ps1 -Build -Abi arm64-v8a
```

## 5. Проверка В App

В Android app:

```text
Server URL = https://CURRENT_WSL_IP:8443
Transport mode = HTTP3_ONLY
Check -> Download -> Open
```

В `Transport Diagnostics` должно быть:

```text
tls = enabled with local debug CA
ca = /data/user/0/com.engboost.dexmvp/cache/dexmvp-local-ca.crt
```

Подтверждённый рабочий сценарий:

```text
HTTP3_ONLY -> Check -> Download -> Open
```

## Troubleshooting

### `sslVerifyResult=9`

Значение `9` у OpenSSL означает, что certificate ещё не валиден относительно текущего времени устройства.

Признаки:

```text
curlCode=60
sslVerifyResult=9
message=SSL peer certificate or SSH remote key was not OK
```

Проверить даты cert:

```bash
openssl x509 -in /etc/nginx/dexmvp/certs/dexmvp-nginx.crt -noout -dates
openssl x509 -in /etc/nginx/dexmvp/certs/dexmvp-local-ca.crt -noout -dates
```

Проверить время Android:

```powershell
adb shell date
```

Решения:

- включить automatic date/time в emulator/device;
- сделать Cold Boot emulator;
- выставить дату вручную так, чтобы она была позже `notBefore` certificate.

## Важные Ограничения

- Это debug/local CA, не production PKI.
- CA private key лежит в WSL: `/etc/nginx/dexmvp/certs/dexmvp-local-ca.key`.
- Не переносить CA private key в приложение.
- Если WSL IP изменился, перевыпустить server cert и перезапустить NGINX.
- Для production нужен корпоративный/доверенный CA или нормальная PKI-процедура.
