# HTTP/3 Critical Handoff

Стартовая инструкция по развёртыванию: `docs/deployment-guide.md`.

Дата фиксации: 2026-06-24.

Этот файл важен после перезагрузки контекста.

## Главное

HTTP/3 end-to-end **заработал** в локальном стенде.

HTTP/3 с TLS verification через local debug CA тоже **заработал**.

Рабочая цепочка:

```text
Android app
  -> NativeHttp3Client / libcurl HTTP/3
  -> NGINX HTTP/3 in WSL
  -> Ktor server on Windows
  -> manifest/artifact
```

Ktor **не является HTTP/3 сервером**. HTTP/3 endpoint — это NGINX в WSL.

## Критично: TLS сейчас debug-only

TLS verification для локального HTTP/3 transport должен идти через debug CA.

Guide:

```text
docs/http3-tls-guide.md
```

Схема:

```text
app/src/debug/res/raw/dexmvp_local_ca.crt
  -> NativeHttp3Client caFilePath
  -> libcurl CURLOPT_CAINFO
  -> NGINX server cert
```

Если CA не сгенерирован/не скопирован, HTTP/3 режим должен упасть с ошибкой про `dexmvp_local_ca.crt`.

Текущий подтверждённый статус:

```text
verifyTls = true
caFilePath = /data/user/0/com.engboost.dexmvp/cache/dexmvp-local-ca.crt
NGINX cert trusted by Android libcurl
```

Это всё ещё **не production PKI**.

Для production нужно заменить debug CA на доверенный сертификат/CA.

## Рабочие адреса текущего стенда

WSL IP на момент проверки:

```text
172.31.123.239
```

Android app server URL:

```text
https://172.31.123.239:8443
```

Ktor server должен запускаться с тем же base URL:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://172.31.123.239:8443"
```

Если `BaseUrl` неправильный, `Check` может работать, а `Download` будет падать, потому что `artifactUrl` в manifest будет указывать на старый IP.

Пример неправильного значения, которое уже ломало download:

```text
https://10.255.255.254:8443/api/v1/modules/hello/1/artifact
```

## Как восстановить после перезапуска

Быстрая подсказка с текущим WSL IP:

```powershell
.\scripts\show-http3-wsl-state.ps1
```

### 1. Узнать новый WSL IP

В WSL:

```bash
hostname -I
```

Пример:

```text
172.31.123.239
```

### 2. Проверить Ktor upstream для NGINX

В WSL:

```bash
curl http://172.31.112.1:8080/health
```

Ожидаемо:

```text
OK
```

Если gateway поменялся:

```bash
ip route | grep default
cat /etc/resolv.conf | grep nameserver
```

В NGINX config должно быть что-то вроде:

```nginx
proxy_pass http://172.31.112.1:8080;
```

Файл:

```text
/etc/nginx/conf.d/dexmvp-http3.conf
```

### 3. Перезапустить Ktor на Windows

В PowerShell проекта:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

### 4. Перезагрузить NGINX

В WSL:

```bash
sudo nginx -t
sudo nginx -s reload
```

### 5. Проверить NGINX proxy

В WSL:

```bash
curl -k https://127.0.0.1:8443/health
curl -k https://127.0.0.1:8443/api/v1/modules/active
```

`artifactUrl` в manifest должен содержать:

```text
https://CURRENT_WSL_IP:8443
```

### 6. Собрать APK с HTTP/3 curl

Если WSL IP изменился или CA ещё не создан, сначала выполнить в WSL:

```bash
cd /mnt/c/Users/RED.DOT/AndroidStudioProjects/DexMVP
bash ./scripts/wsl-generate-http3-certs.sh
sudo nginx -t
sudo nginx -s reload
```

Для emulator:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

Для физического устройства:

```powershell
.\scripts\build-http3-apk.ps1 -Abi arm64-v8a
```

Установить и запустить:

```powershell
.\scripts\install-http3-apk.ps1
```

Сразу собрать, установить и запустить:

```powershell
.\scripts\install-http3-apk.ps1 -Build -Abi x86_64
```

### 7. В приложении

Server URL:

```text
https://CURRENT_WSL_IP:8443
```

Transport mode:

```text
HTTP3_ONLY
```

Порядок:

```text
Check -> Download -> Open
```

## Что уже было проверено

- `Check` заработал через HTTP/3.
- `Download` заработал после исправления `BaseUrl`.
- Причина старого падения download: manifest отдавал `artifactUrl` с неправильным IP.
- `curlCode=60` был из-за TLS/cert. Сейчас решается локальным CA из `docs/http3-tls-guide.md`.
- После подключения local debug CA `HTTP3_ONLY -> Check -> Download -> Open` заработал с `verifyTls=true`.
- Отдельная найденная проблема: `sslVerifyResult=9` означает, что certificate ещё не валиден для времени Android/emulator. Решение — поправить время emulator/устройства или сделать Cold Boot.

## Что ещё не production-ready

- TLS использует debug/local CA, не production PKI.
- Нет автоматического скрипта обновления WSL IP.
- Нет отдельной Studio run configuration для HTTP/3 flags.
- Нет production NGINX runbook.
- Не проверено на физическом устройстве через внешнюю сеть/firewall.
