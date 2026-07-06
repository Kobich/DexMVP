# DexMVP Wiki Overview

Статус: `MVP / controlled local IP-only стенд / HTTP/3 через Caddy / dynamic code loading`.

Эта страница — верхний вход в документацию DexMVP. Она объясняет, что делает проект, как устроен текущий стенд, какие документы читать дальше и по каким признакам понимать, что система работает корректно.

## 1. Что это за проект

DexMVP — Android-прототип динамической доставки и запуска remote module.

Host-приложение не содержит весь feature-код заранее. Оно получает manifest с сервера, скачивает APK-артефакт remote module, проверяет его целостность и загружает entry point через `DexClassLoader`.

Ключевая цепочка:

```text
Android host app
  -> получает manifest
  -> скачивает remote APK artifact
  -> проверяет SHA-256
  -> сохраняет artifact во внутреннее хранилище
  -> грузит entry point через DexClassLoader
  -> запускает RemoteFeature или RemoteComposeFeature
```

Текущий transport-стенд:

```text
Android app
  -> libcurl HTTP/3
  -> Caddy on Windows :8443
  -> Ktor server on Windows :8080
  -> remote-module-debug.apk
```

## 2. Текущая целевая схема

Текущая рабочая схема рассчитана на IP `10.72.217.180` без домена.

```text
Client URL:
  https://10.72.217.180:8443

Caddy:
  10.72.217.180:8443
  HTTPS + HTTP/3
  TLS certificate for IP

Ktor:
  127.0.0.1:8080 или 0.0.0.0:8080
  /health
  /api/v1/modules/active
  /api/v1/modules/{moduleId}/{version}/artifact

Android:
  HTTP3_ONLY
  bundled public Root CA
  CURLOPT_CAINFO
```

Важно: Ktor в коде сейчас слушает `0.0.0.0:8080`. Для чистой схемы внешний вход должен идти через Caddy, поэтому прямой доступ к `8080` нужно закрывать firewall-ом или переводить bind на `127.0.0.1`.

## 3. Карта предметной области

| Область | Что важно понимать |
| --- | --- |
| Server side | Caddy принимает HTTPS/HTTP3, Ktor отдаёт manifest и APK artifact |
| Client side | Android app скачивает manifest/artifact, проверяет SHA-256 и грузит код |
| TLS | IP-only стенд использует локальный Root CA public certificate внутри APK |
| Native HTTP/3 | `native-http3` связывает Kotlin/JNI с prebuilt libcurl HTTP/3 |
| Remote module | `remote-module` собирается как APK-контейнер с dex-кодом |
| Production trust | Для production нужна подпись manifest или artifact отдельным release key |

Эта страница содержит полный верхнеуровневый обзор без необходимости переходить в другие документы.


## 4. Компоненты проекта

| Компонент | Роль |
| --- | --- |
| `app` | Android host application и основной UI |
| `feature:remote-execution:api` | Стабильный API-контракт между host app и remote module |
| `feature:remote-execution:impl` | Загрузка manifest, download artifact, verification, storage, DexClassLoader |
| `native-http3` | JNI/CMake bridge к prebuilt libcurl с HTTP/3 |
| `remote-module` | Demo APK с remote features |
| `server` | Ktor backend, который отдаёт manifest и artifact |
| `third_party/curl-android` | Prebuilt curl/OpenSSL/ngtcp2/nghttp3 bundle для Android |
| `E:\caddy` | Локальная папка Caddy, Caddyfile и TLS certificates |

## 5. Server-side flow

Server-side отвечает за две вещи:

1. Вернуть manifest активного remote module.
2. Отдать APK-артефакт, hash которого указан в manifest.

Endpoints:

| Method | Path | Назначение |
| --- | --- | --- |
| `GET` | `/health` | Проверка, что Ktor жив |
| `GET` | `/api/v1/modules/active` | Manifest активного module |
| `GET` | `/api/v1/modules/{moduleId}/{version}/artifact` | APK-артефакт remote module |

Manifest содержит:

```text
moduleId
version
hostApiVersion
minHostApi
artifactUrl
sha256
signature
features[]
```

`signature` сейчас есть в контракте, но не используется как production-подпись.

## 6. Client-side flow

Client-side выполняет последовательность:

| Шаг | Что делает клиент | Что проверяется |
| --- | --- | --- |
| `Check` | Загружает manifest | `minHostApi <= HOST_API_VERSION` |
| `Download` | Скачивает APK artifact | TLS + HTTP status |
| `Verify` | Считает SHA-256 | Hash совпадает с manifest |
| `Store` | Сохраняет artifact | Финальный APK становится read-only |
| `Open` | Загружает entry point | Класс реализует API, `id/version` совпадают |

Текущий строгий режим проверки HTTP/3:

```text
Transport = HTTP3_ONLY
```

Он нужен, чтобы не скрывать проблемы HTTP/3 fallback-ом на OkHttp.

## 7. TLS и сертификаты

Так как текущий стенд работает по IP без домена, используется локальная Root CA цепочка.

```text
dexmvp-root-ca.key
  -> подписывает dexmvp-ip.crt

Caddy
  -> отдаёт dexmvp-ip.crt

Android app
  -> содержит dexmvp_root_ca.crt
  -> копирует его в cache
  -> передаёт путь в libcurl CURLOPT_CAINFO
  -> libcurl проверяет Caddy certificate
```

Роли файлов:

| Файл | Где используется | Секрет |
| --- | --- | --- |
| `dexmvp-ip.crt` | Caddy отдаёт клиенту | Нет |
| `dexmvp-ip.key` | Caddy завершает TLS | Да |
| `dexmvp-root-ca.crt` | Android/libcurl доверяет Caddy cert | Нет |
| `dexmvp-root-ca.key` | Выпуск новых server cert | Да |
| `app/src/main/res/raw/dexmvp_root_ca.crt` | Public Root CA внутри APK | Нет |

В APK допустим только public certificate:

```text
dexmvp_root_ca.crt
```

В APK нельзя класть:

```text
dexmvp-root-ca.key
dexmvp-ip.key
```

## 8. Быстрый health check

Проверить Ktor:

```powershell
curl.exe http://127.0.0.1:8080/health
```

Ожидаемо:

```text
OK
```

Проверить TLS Caddy по IP:

```powershell
& "C:\Program Files\Git\usr\bin\openssl.exe" s_client `
  -connect 10.72.217.180:8443 `
  -verify_ip 10.72.217.180 `
  -CAfile E:\caddy\certs\dexmvp-root-ca.crt `
  -brief
```

Ожидаемо:

```text
Verification: OK
```

Проверить, что manifest идёт через Caddy:

```powershell
curl.exe -k https://10.72.217.180:8443/api/v1/modules/active
```

`-k` здесь допустим только для ручной проверки Windows curl. Android-приложение должно проходить TLS verification без отключения проверки.

## 9. Сборка и проверка Android APK

HTTP/3 debug APK для emulator:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$CurlRoot = Resolve-Path ".\third_party\curl-android"

.\gradlew.bat :app:assembleDebug `
  "-PnativeHttp3.enableCmake=true" `
  "-PnativeHttp3.enableCurl=true" `
  "-PnativeHttp3.curlRootDir=$CurlRoot" `
  "-PnativeHttp3.abis=x86_64"
```

Для физического Android-устройства обычно нужен:

```text
arm64-v8a
```

Проверить APK:

```powershell
$Apk = ".\app\build\outputs\apk\debug\app-debug.apk"
& "$env:JAVA_HOME\bin\jar.exe" tf $Apk | Select-String -Pattern "dexmvp_root_ca|dexmvp_local_ca|lib/.*/libnative-http3.so"
```

Должно быть:

```text
res/raw/dexmvp_root_ca.crt
lib/{abi}/libnative-http3.so
```

Не должно быть:

```text
dexmvp_local_ca
```

## 10. Проверка в приложении

В UI:

```text
Server URL = https://10.72.217.180:8443
Transport = HTTP3_ONLY
```

Порядок:

```text
Check
Download
Open
```

Рабочий результат:

- manifest загружается;
- artifact скачивается;
- SHA-256 совпадает;
- remote feature открывается;
- TLS error отсутствует;
- при диагностике `caFilePath` не пустой.

Если падает TLS, смотреть:

```text
curlCode
sslVerifyResult
message
caFilePath
```

## 11. Definition of Done для текущего стенда

Стенд считается рабочим, если выполняется весь список:

- Ktor отвечает `OK` на `/health`;
- Caddy слушает TCP и UDP `8443`;
- `openssl s_client -verify_ip` возвращает `Verification: OK`;
- manifest содержит `artifactUrl` через `https://10.72.217.180:8443`;
- APK содержит `dexmvp_root_ca.crt`;
- APK содержит `libnative-http3.so` для нужного ABI;
- Android app работает в `HTTP3_ONLY`;
- `Check -> Download -> Open` проходит успешно;
- downloaded artifact проходит SHA-256 verification;
- remote feature загружается через `DexClassLoader`;
- private keys не лежат в APK и не коммитятся.

## 12. Production gaps

Текущий стенд не равен production-системе доставки кода.

Уже есть:

- TLS verification через public Root CA в APK;
- HTTP/3-only transport check;
- SHA-256 artifact verification;
- read-only artifact перед загрузкой;
- проверка `minHostApi`;
- проверка `feature.id` и `feature.version`.

Пока нет:

- подписи manifest или artifact отдельным release key;
- защиты от replay/rollback старого валидного manifest;
- rollout-политики по версиям app/module;
- auth клиента;
- audit trail скачиваний;
- server-side хранилища нескольких module versions;
- изоляции remote code за пределами обычного Android process.

Следующий обязательный production-шаг:

```text
подписывать manifest или artifact отдельным release private key
проверять подпись в Android app перед DexClassLoader
```

TLS защищает канал до Caddy. Подпись artifact/manifest должна защищать доверие к remote code независимо от транспорта.

## 13. Что не путать

Важно разделять:

| Область | За что отвечает | Не отвечает за |
| --- | --- | --- |
| TLS/Caddy | Защита канала и подлинность endpoint-а | Доверие к remote code как к release artifact |
| SHA-256 | Целостность скачанного APK относительно manifest | Защита, если manifest тоже подменён |
| Root CA в APK | Проверка Caddy server certificate | Подписание новых certificates |
| DexClassLoader | Загрузка проверенного APK | Sandbox remote code |
| HTTP3_ONLY | Гарантия, что тест идёт через HTTP/3 | Production trust model |

## 14. Минимальная процедура переноса на другую машину

1. Установить Git for Windows, чтобы был доступен OpenSSL:

```powershell
& "C:\Program Files\Git\usr\bin\openssl.exe" version
```

2. Положить Caddy в локальную директорию:

```text
E:\caddy
```

3. Сгенерировать локальный Root CA и server certificate для текущего IP.

4. Положить Caddy server certificate и key:

```text
E:\caddy\certs\dexmvp-ip.crt
E:\caddy\certs\dexmvp-ip.key
```

5. Скопировать public Root CA в Android app:

```text
app/src/main/res/raw/dexmvp_root_ca.crt
```

6. Создать `E:\caddy\Caddyfile`:

```caddyfile
{
    servers {
        protocols h1 h2 h3
    }
}

:8443 {
    tls E:/caddy/certs/dexmvp-ip.crt E:/caddy/certs/dexmvp-ip.key

    reverse_proxy 127.0.0.1:8080
}
```

7. Запустить Ktor с public base URL:

```powershell
$env:DEX_SERVER_BASE_URL = "https://10.72.217.180:8443"
.\gradlew.bat :server:run
```

8. Запустить Caddy:

```powershell
& E:\caddy\caddy_windows_amd64.exe run --config E:\caddy\Caddyfile --adapter caddyfile
```

9. Собрать Android APK с HTTP/3 flags.

10. Проверить `Check -> Download -> Open` в режиме `HTTP3_ONLY`.
