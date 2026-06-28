# HTTP/3 Current State

Дата фиксации: 2026-06-24.

Критичный handoff после успешного локального HTTP/3 теста: `docs/http3-critical-handoff.md`.

## Что сейчас есть

- HTTP/3 end-to-end локально заработал.
- Android transport abstraction уже есть.
- `OkHttpRemoteTransport` остаётся рабочим fallback.
- `Http3RemoteTransport` вызывает `NativeHttp3Client`.
- `native-http3` собирается через NDK/CMake.
- `libcurl` с HTTP/3 собран через vcpkg и импортирован в:

```text
third_party/curl-android
```

- Есть ABI:

```text
arm64-v8a
x86_64
```

- NGINX mainline в WSL установлен и имеет `--with-http_v3_module`.
- NGINX слушает:

```text
https://<WSL_IP>:8443
```

- Ktor остаётся обычным HTTP server на Windows:

```text
http://0.0.0.0:8080
```

- NGINX проксирует в Ktor через Windows gateway:

```text
proxy_pass http://172.31.112.1:8080;
```

## Важная схема

Ktor не стал HTTP/3 сервером.

HTTP/3 endpoint сейчас — это NGINX:

```text
Android app
  -> HTTPS/HTTP3/QUIC
  -> NGINX in WSL :8443
  -> HTTP/1.1 proxy_pass
  -> Ktor on Windows :8080
```

## Какой адрес использовать

Для Android emulator сначала пробовать:

```text
https://172.31.123.239:8443
```

Это текущий WSL IP из `hostname -I`.

Если WSL IP изменился:

```bash
hostname -I
```

Для обычного HTTP fallback по-прежнему можно использовать:

```text
http://10.0.2.2:8080
```

## TLS сейчас

Локальный HTTP/3 transport переведён на проверку TLS через debug CA.

Схема:

```text
app/src/debug/res/raw/dexmvp_local_ca.crt
  -> NativeHttp3Client caFilePath
  -> libcurl CURLOPT_CAINFO
  -> NGINX server certificate
```

Генерация CA/server cert:

```text
docs/http3-tls-guide.md
```

Важно: это debug/local CA, не production PKI.

## Что проверено

- Ktor доступен из WSL:

```bash
curl http://172.31.112.1:8080/health
```

- NGINX config валиден:

```bash
sudo nginx -t
```

- NGINX HTTPS endpoint отвечает и отдаёт `Alt-Svc: h3=":8443"`.
- Android native curl bundle собран и линкуется:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-native-http3-curl.ps1
```

- Главный экран app показывает `Transport Diagnostics`: mode, backend, TLS state, native layer, engine info.
- HTTP/3 с `verifyTls=true` и local debug CA подтверждён как рабочий.
- Известный troubleshooting: `sslVerifyResult=9` означает неверное время Android/emulator относительно `notBefore` certificate.

## Как собрать APK для HTTP/3 теста

Для emulator:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

Установить без пересборки Android Studio:

```powershell
.\scripts\install-http3-apk.ps1
```

Для физического устройства:

```powershell
.\scripts\build-http3-apk.ps1 -Abi arm64-v8a
```

## Чего не хватает до полноценного решения

- Надёжный запуск NGINX из проекта скриптами.
- Генерация local debug CA/server cert есть, но production PKI ещё нет.
- Проверка UDP 8443 из Android до WSL.
- Production TLS trust.
- Отдельная Studio run configuration для HTTP/3.
- Логи transport-режима и fallback-причины в UI.
- NGINX runbook под закрытую инфраструктуру.

## Следующие шаги

1. Проверить HTTP/3 из Android с `HTTP3_ONLY`.
2. Если нет подключения к `https://172.31.123.239:8443`, проверить UDP/Firewall/WSL networking.
3. Для production заменить debug CA на доверенный cert/CA.
