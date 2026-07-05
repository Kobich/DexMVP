# Production TLS and Remote Code Trust Guide

Цель: описать, как делать production-like TLS для HTTP/3 API и как отдельно защищать загрузку remote APK/DEX.

Важно: HTTP/3 остаётся обязательным требованием. Тезис "TLS не должен быть единственной защитой" означает не отказ от HTTP/3/TLS, а то, что при загрузке исполняемого кода нужен второй контур доверия: подпись manifest/artifact.

## 1. Что именно защищаем

Текущая логика приложения:

```text
Android host app
  -> GET /api/v1/modules/active
  -> получает manifest
  -> берёт artifactUrl и sha256
  -> скачивает remote APK
  -> проверяет SHA-256
  -> сохраняет APK во internal storage
  -> DexClassLoader загружает entryPoint
```

Кодовые точки:

```text
ManifestApiClient
  -> получает manifest JSON

RemoteModuleRepository
  -> проверяет minHostApi
  -> вызывает downloadAndVerify

ArtifactDownloader
  -> скачивает artifactUrl

Sha256Verifier
  -> проверяет artifact SHA-256

DexModuleLoader
  -> загружает APK через DexClassLoader
```

Server сейчас отдаёт manifest здесь:

```text
server/src/main/kotlin/com/engboost/server/modules/ModuleRegistry.kt
```

Manifest уже содержит поле:

```text
signature
```

Но сейчас оно пустое и клиент его не проверяет.

## 2. Два разных trust слоя

Нужно не смешивать TLS certificate и подпись remote code.

### Слой A: TLS для API transport

Назначение:

```text
Android/libcurl проверяет, что подключился к правильному Caddy/API server.
```

Что подписывается:

```text
Caddy server certificate
```

Кто подписывает:

```text
Corporate/internal CA или public CA
```

Где private key:

```text
На стороне Caddy/server. В APK не кладётся.
```

Где public trust:

```text
В системном/default trust store libcurl/OpenSSL или approved trust bundle.
```

В текущей ветке app не хранит project-local CA:

```text
caFilePath = ""
CURLOPT_CAINFO не задаётся
```

### Слой B: подпись remote manifest/artifact

Назначение:

```text
Android app проверяет, что manifest/artifact выпущены trusted release pipeline, а не просто пришли с сервера.
```

Что подписывается:

```text
Canonical manifest payload:
  moduleId
  version
  hostApiVersion
  minHostApi
  artifactUrl
  sha256
  features[]
```

Кто подписывает:

```text
Offline/release private signing key.
```

Где private key:

```text
Не на Caddy.
Не в Android app.
Не в обычном runtime server.
Только в release/signing pipeline или approved secret storage.
```

Где public key:

```text
В Android app.
```

Это нормально: public key нужен только для проверки подписи. Им нельзя подписать злой manifest.

## 3. Почему SHA-256 недостаточно

Сейчас server кладёт в manifest:

```text
sha256 = SHA-256(remote-module-debug.apk)
```

Клиент проверяет, что скачанный файл совпадает с этим hash.

Это защищает от:

- повреждения файла;
- несоответствия artifact тому manifest, который получил клиент;
- случайной подмены artifact по пути.

Но если атакующий контролирует server/API, он может отдать:

```text
новый artifact
новый sha256
signature = ""
```

И текущий клиент примет это, если TLS/session уже проходит или server сам скомпрометирован.

Поэтому для remote code нужен signed manifest.

## 4. Как должен выглядеть production flow

Release pipeline:

```text
1. Собрать remote APK.
2. Посчитать SHA-256 artifact.
3. Сформировать manifest без signature.
4. Canonicalize manifest.
5. Подписать canonical manifest private key.
6. Положить signature в manifest.
7. Server отдаёт signed manifest и artifact.
```

Android app:

```text
1. Через HTTP/3/TLS получает manifest.
2. Проверяет manifest signature public key.
3. Проверяет minHostApi/hostApiVersion.
4. Скачивает artifactUrl.
5. Проверяет SHA-256 artifact из подписанного manifest.
6. Проверяет allowlist entryPoint/moduleId/version.
7. Только после этого грузит artifact через DexClassLoader.
```

## 5. TLS certificate под IP

Текущий Windows IP:

```text
10.72.217.180
```

Если Android будет ходить по:

```text
https://10.72.217.180:8443
```

то server certificate должен иметь Subject Alternative Name:

```text
IP Address = 10.72.217.180
```

Common Name недостаточно. Нужен именно SAN.

## 6. Генерация key + CSR под IP

На Windows можно использовать OpenSSL из Git for Windows, если он есть:

```powershell
where.exe openssl
```

Если OpenSSL не найден, установить OpenSSL для Windows или использовать другой Windows-инструмент генерации CSR. В Caddy-only сценарии WSL не нужен.

Создать папку:

```powershell
New-Item -ItemType Directory -Force E:\caddy\certs
```

Создать OpenSSL config:

```text
E:\caddy\certs\dexmvp-ip-openssl.cnf
```

Содержимое:

```ini
[ req ]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req

[ dn ]
CN = 10.72.217.180

[ v3_req ]
subjectAltName = @alt_names

[ alt_names ]
IP.1 = 10.72.217.180
```

Сгенерировать private key и CSR:

```powershell
openssl genrsa -out E:\caddy\certs\dexmvp-ip.key 2048
openssl req -new `
  -key E:\caddy\certs\dexmvp-ip.key `
  -out E:\caddy\certs\dexmvp-ip.csr `
  -config E:\caddy\certs\dexmvp-ip-openssl.cnf
```

Результат:

```text
E:\caddy\certs\dexmvp-ip.key  # private key для Caddy
E:\caddy\certs\dexmvp-ip.csr  # запрос на подпись для CA/security
```

`dexmvp-ip.key` нельзя отдавать наружу без необходимости. В CA/security отдаётся только CSR.

## 7. Подписание CSR

CSR надо подписать в corporate/internal CA.

На выходе нужен certificate chain:

```text
dexmvp-ip.crt
```

В нём должен быть leaf certificate для `10.72.217.180` и, если требуется, intermediate chain.

Положить результат:

```text
E:\caddy\certs\dexmvp-ip.crt
E:\caddy\certs\dexmvp-ip.key
```

Caddyfile уже может использовать:

```caddyfile
:8443 {
    tls E:/caddy/certs/dexmvp-ip.crt E:/caddy/certs/dexmvp-ip.key

    reverse_proxy 127.0.0.1:8080
}
```

## 8. Проверка TLS до Android

Проверить Caddy config:

```powershell
E:\caddy\caddy_windows_amd64.exe validate --config E:\caddy\Caddyfile --adapter caddyfile
```

Запустить Caddy:

```powershell
E:\caddy\caddy_windows_amd64.exe run --config E:\caddy\Caddyfile --adapter caddyfile
```

Запустить Ktor:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://10.72.217.180:8443"
```

Проверить с Windows:

```powershell
curl.exe https://10.72.217.180:8443/health
curl.exe https://10.72.217.180:8443/api/v1/modules/active
```

Если нужен `-k`, значит trust ещё не production-like.

## 9. Проверка Android HTTP/3

Собрать installable debug APK. В этой ветке debug тоже не содержит local CA:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64 -BuildType Debug
.\scripts\install-http3-apk.ps1
```

В app:

```text
Server URL = https://10.72.217.180:8443
Transport mode = HTTP3_ONLY
Check -> Download -> Open
```

Ожидаемая диагностика:

```text
tls = enabled with default libcurl CA trust
ca = not set
```

Если Android падает с `curlCode=60`, это значит:

- cert не доверен libcurl/OpenSSL;
- cert SAN не содержит `10.72.217.180`;
- цепочка CA не попала в trust store;
- время Android/emulator вне срока действия certificate.

## 10. Что хранится в приложении

Сейчас после удаления debug CA:

```text
TLS CA certificate в app не хранится.
Server certificate в app не хранится.
Server private key в app не хранится.
```

Приложение только включает TLS verification:

```text
verifyTls = true
caFilePath = ""
```

Для будущей подписи remote manifest в приложении должен храниться только:

```text
public key для проверки подписи manifest
```

Это не TLS certificate. Это отдельный application-level signing key.

## 11. Рекомендуемое production решение для Dex loading

Минимально нужные проверки:

- HTTP/3/TLS с trusted Caddy certificate.
- Signed manifest.
- SHA-256 artifact из signed manifest.
- Allowlist `moduleId`, `features[].entryPoint`, `minHostApi`.
- Версионирование и rollback policy.
- Запрет unsigned/unknown manifest.

Не полагаться только на:

```text
TLS + server-generated sha256
```

Это защищает transport, но не защищает от скомпрометированного API server.

## 12. Источники

- Caddy Automatic HTTPS: https://caddyserver.com/docs/automatic-https
- Caddy TLS directive: https://caddyserver.com/docs/caddyfile/directives/tls
- Android Network Security Config: https://developer.android.com/privacy-and-security/security-config
