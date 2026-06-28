# HTTP/3 Requirement Status

Этот файл фиксирует требование HTTP/3 и текущее состояние реализации. Старое название файла оставлено, чтобы не ломать ссылки из README и старых заметок.

## Требование

Component B должен уметь отдавать Component A:

- active manifest;
- module artifact;
- будущие metadata endpoints.

Клиент должен работать по IP-адресу без обязательного доменного имени.

## Что Уже Есть

Клиент:

- `RemoteTransport` abstraction для manifest/artifact запросов;
- `OkHttpRemoteTransport` как обычный HTTP fallback;
- `Http3RemoteTransport` через `native-http3`;
- `TransportMode`: `HTTP_FALLBACK`, `HTTP3_PREFERRED`, `HTTP3_ONLY`;
- UI diagnostics для режима, TLS, native layer, CA file и engine info;
- SHA-256 verification после download остаётся общей для всех transport.

Native:

- `native-http3` с opt-in NDK/CMake;
- JNI bridge к libcurl;
- prebuilt curl/OpenSSL/ngtcp2/nghttp3 через `third_party/curl-android`;
- TLS verification через `CURLOPT_CAINFO` и local debug CA.

Server/proxy:

- Ktor остаётся HTTP/1.1 backend на Windows `:8080`;
- NGINX в WSL является HTTP/3 endpoint на `:8443`;
- NGINX proxy_pass ведёт на Ktor;
- server cert выпускается на текущий WSL IP через local debug CA.

Подтверждённый локальный сценарий:

```text
HTTP3_ONLY -> Check -> Download -> Open
```

## Рабочая Схема

```text
Android app
  -> Http3RemoteTransport
  -> NativeHttp3Client JNI
  -> libcurl HTTP/3
  -> NGINX HTTP/3 in WSL :8443
  -> Ktor server on Windows :8080
  -> manifest/artifact
```

HTTP/3 меняет только transport. Pipeline загрузки остаётся тем же:

```text
download artifact
  -> verify SHA-256
  -> store in internal storage
  -> mark read-only
  -> load with DexClassLoader
```

## Почему Не Cronet

Cronet не выбран как основной вариант из-за IP-only требования. Для этого проекта нужен прямой доступ по IP, а не обязательная доменная схема. Текущий путь — libcurl HTTP/3 через JNI.

## Что Осталось До Полноценного Production

- Production PKI вместо local debug CA.
- Понятный процесс перевыпуска server cert под реальные IP/контуры.
- Проверка UDP/QUIC в целевой закрытой сети.
- Проверка на физическом Android 16 устройстве.
- Решение, как официально доставлять offline `curl-android` bundle в закрытую инфраструктуру.
- Логи причины fallback в режиме `HTTP3_PREFERRED`.
- Threat model и политика доверия к remote modules.
- Настоящая цифровая подпись manifest/artifact, если SHA-256 из trusted manifest недостаточно.

## Основные Команды

Обычная проверка проекта:

```powershell
.\scripts\verify-project.ps1
```

Проверка curl bundle:

```powershell
.\scripts\check-curl-android-layout.ps1 -Abis x86_64
```

Сборка HTTP/3 APK:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

Установка HTTP/3 APK:

```powershell
.\scripts\install-http3-apk.ps1
```

Запуск Ktor с artifact URL через NGINX HTTP/3:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

Генерация local CA/server cert в WSL:

```bash
bash scripts/wsl-generate-http3-certs.sh 172.xx.xx.xx
```

## Где Читать Дальше

- `docs/http3-critical-handoff.md` — текущее критичное состояние и known pitfalls.
- `docs/http3-setup-guide.md` — пошаговый setup Windows + WSL + Android.
- `docs/http3-tls-guide.md` — local CA, server cert, `CURLOPT_CAINFO`.
- `docs/scripts-reference.md` — справочник helper scripts.
- `docs/closed-infra-runbook.md` — перенос в закрытую инфраструктуру.