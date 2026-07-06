# Клиентская сторона DexMVP

Этот документ описывает Android-клиент DexMVP: как он получает manifest, как скачивает remote module, как проверяет файл и как загружает код в host application.

Статус: `MVP / controlled стенд / remote code loading без production-подписи artifact`.

Документ самодостаточный: в нём описаны Android-клиент, transport layer, TLS trust через CAINFO, manifest flow, download, SHA-256 verification, хранение artifact и загрузка через `DexClassLoader`.

## Коротко

Клиент делает четыре обязательных шага:

| Шаг | Что происходит | Ключевая проверка |
| --- | --- | --- |
| `Check` | Загружается manifest | `minHostApi <= HOST_API_VERSION` |
| `Download` | Скачивается APK remote module | SHA-256 совпадает с manifest |
| `Store` | Artifact сохраняется в private storage | Финальный APK становится read-only |
| `Open` | Загружается class из APK | `id` и `version` совпадают с manifest |

Рабочий поток:

```text
Android app
  -> HTTP3_ONLY
  -> libcurl + CURLOPT_CAINFO
  -> Caddy
  -> manifest
  -> APK artifact
  -> SHA-256 verification
  -> DexClassLoader
  -> RemoteFeature / RemoteComposeFeature
```

## Назначение

Клиентская часть отвечает за безопасную загрузку и запуск remote module внутри основного Android-приложения.

Текущий сценарий:

```text
User нажимает Check
  -> app запрашивает manifest

User нажимает Download
  -> app скачивает APK remote module
  -> проверяет SHA-256
  -> сохраняет verified artifact

User нажимает Open / запускает feature
  -> app загружает класс через DexClassLoader
  -> проверяет id/version feature
  -> выполняет output или compose feature
```

## Главные модули

### Host app

Основное Android-приложение находится в:

```text
app
```

Оно содержит UI и подключает feature-модуль remote execution.

### Remote execution API

Публичный контракт между host app и remote module:

```text
feature/remote-execution/api/src/main/kotlin/com/engboost/remoteapi/RemoteFeature.kt
```

Основные интерфейсы:

```text
RemoteFeature
RemoteComposeFeature
RemoteHost
```

Remote module компилируется против этого API как `compileOnly`. Это важно: API принадлежит host app, а remote module должен быть совместим с ним во время загрузки.

### Remote execution implementation

Реальная логика загрузки находится в:

```text
feature/remote-execution/impl
```

Ключевые классы:

```text
RemoteModuleRepository
ManifestApiClient
ArtifactDownloader
Sha256Verifier
ModuleStorage
DexModuleLoader
RemoteFeatureRunner
```

### Native HTTP/3 transport

HTTP/3 реализован через JNI + libcurl:

```text
native-http3
```

Kotlin-обёртка:

```text
NativeHttp3Client
NativeHttp3Config
```

Native-часть:

```text
native-http3/src/main/cpp/native_http3_client.cpp
```

Именно native-код выставляет libcurl:

```text
CURLOPT_HTTP_VERSION = CURL_HTTP_VERSION_3ONLY
CURLOPT_SSL_VERIFYPEER = 1
CURLOPT_SSL_VERIFYHOST = 2
CURLOPT_CAINFO = путь к dexmvp-root-ca.crt в cache приложения
```

## Транспорт

Клиент поддерживает три режима:

| Режим | Transport | Когда использовать |
| --- | --- | --- |
| `HTTP_FALLBACK` | OkHttp | Только для обычной HTTP/HTTPS проверки без HTTP/3 |
| `HTTP3_ONLY` | native libcurl HTTP/3 | Основной режим для проверки текущего Caddy/HTTP3 стенда |
| `HTTP3_PREFERRED` | libcurl HTTP/3, затем OkHttp | Удобен для UX, но может скрыть поломку HTTP/3 |

Файл:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/transport/TransportMode.kt
```

Режимы создаются через:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/transport/RemoteTransportFactory.kt
```

Для текущего Caddy/HTTP3 стенда важен режим:

```text
HTTP3_ONLY
```

Он заставляет app идти через native libcurl HTTP/3. Если HTTP/3 или TLS не работают, ошибка должна быть видна сразу, без тихого fallback на OkHttp.

## Доверие к Caddy certificate

Для IP-only стенда Android-приложение содержит public Root CA certificate:

```text
app/src/main/res/raw/dexmvp_root_ca.crt
```

Это не private key. Этим файлом нельзя подписывать сертификаты. Он нужен только для проверки server certificate, который отдаёт Caddy.

Цепочка выглядит так:

```text
dexmvp-root-ca.key
  -> подписал dexmvp-ip.crt

Caddy
  -> отдаёт dexmvp-ip.crt

Android app
  -> содержит dexmvp_root_ca.crt
  -> копирует его в cache
  -> передаёт путь в libcurl CURLOPT_CAINFO
  -> libcurl проверяет Caddy
```

Класс, который готовит CA file:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/transport/CaddyCaProvider.kt
```

Он делает две вещи:

1. Находит raw resource `dexmvp_root_ca`.
2. Копирует его в cache приложения как обычный файл.

Это нужно потому, что libcurl/OpenSSL ожидает путь к CA file на filesystem, а не Android resource id.

Рабочий признак в ошибках/диагностике:

```text
caFilePath не пустой
```

Если `caFilePath` пустой, значит native HTTP/3 transport не получил CA file и TLS verification будет падать.

Текущая роль сертификатов на клиенте:

| Файл | Попадает в APK | Назначение | Секрет |
| --- | --- | --- | --- |
| `dexmvp_root_ca.crt` | Да | Проверить server certificate Caddy | Нет |
| `dexmvp-root-ca.key` | Нет | Подписывать новые server certificates | Да |
| `dexmvp-ip.key` | Нет | Private key Caddy server certificate | Да |

## Получение manifest

Manifest запрашивается здесь:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/ManifestApiClient.kt
```

Запрос:

```text
GET {serverBaseUrl}/api/v1/modules/active
```

Для текущего стенда:

```text
GET https://10.72.217.180:8443/api/v1/modules/active
```

Manifest описывает:

| Поле | Назначение |
| --- | --- |
| `moduleId` | Идентификатор remote module |
| `version` | Версия remote module |
| `hostApiVersion` | Версия API, под которую собран module |
| `minHostApi` | Минимальная версия host API, допустимая для запуска |
| `artifactUrl` | URL APK-артефакта |
| `sha256` | Ожидаемый hash APK-артефакта |
| `signature` | Поле под будущую подпись; сейчас не используется |
| `features` | Список feature entry points внутри artifact |

Kotlin-модель:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/RemoteModuleManifest.kt
```

## Проверка совместимости

Перед загрузкой module клиент проверяет:

```text
manifest.minHostApi <= RemoteFeatureRunner.HOST_API_VERSION
```

Текущая host API version:

```text
1
```

Если remote module требует более новую host API, клиент должен остановиться до скачивания/запуска.

## Скачивание artifact

Скачивание выполняет:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/ArtifactDownloader.kt
```

Фактический network transport зависит от выбранного режима:

```text
HTTP3_ONLY      -> Http3RemoteTransport -> NativeHttp3Client -> libcurl
HTTP_FALLBACK   -> OkHttpRemoteTransport
HTTP3_PREFERRED -> сначала HTTP/3, потом OkHttp
```

В production-like проверках для HTTP/3 нужно использовать `HTTP3_ONLY`, иначе можно не заметить, что HTTP/3 не работает.

## Проверка SHA-256

После скачивания клиент проверяет файл:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/Sha256Verifier.kt
```

Проверка сравнивает:

```text
actual SHA-256 скачанного файла
expected SHA-256 из manifest
```

Если hash не совпал, artifact не должен быть принят.

Важно: SHA-256 защищает от случайной порчи файла и от несовпадения artifact с manifest. Но если атакующий контролирует и manifest, и artifact, одного SHA-256 недостаточно. Для production-доверия нужна подпись manifest или artifact отдельным release key.

## Хранение artifact

Хранение выполняет:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/ModuleStorage.kt
```

Путь внутри app private storage:

```text
filesDir/remote-modules/{moduleId}-{version}.apk
```

Схема:

```text
скачать во временный .download файл
проверить SHA-256
скопировать в финальный .apk файл
удалить временный файл
сделать финальный файл read-only
```

Перед загрузкой `DexModuleLoader` требует, чтобы artifact уже был read-only:

```text
require(!artifact.canWrite())
```

Это простая защита от изменения файла между verification и class loading.

## Загрузка кода

Загрузка remote code происходит здесь:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/DexModuleLoader.kt
```

Используется:

```text
dalvik.system.DexClassLoader
```

Клиент создаёт optimized directory:

```text
codeCacheDir/remote-dex/{moduleId}-{version}
```

Затем загружает класс из manifest:

```text
feature.entryPoint
```

Например:

```text
com.engboost.remote.HelloRemoteFeature
```

После создания instance клиент проверяет, что класс реализует нужный интерфейс:

```text
RemoteFeature
```

или:

```text
RemoteComposeFeature
```

## Запуск feature

Запуском управляет:

```text
feature/remote-execution/impl/src/main/java/com/engboost/dexmvp/loader/RemoteFeatureRunner.kt
```

Перед выполнением проверяется:

```text
feature.id == manifest feature id
feature.version == manifest feature version
```

Это защищает от ситуации, когда manifest обещает одну feature, а внутри artifact лежит другой класс или другая версия.

Для output feature вызывается:

```text
feature.execute(input)
```

Для compose feature возвращается `RemoteComposeFeature`, и host app уже вызывает composable `Content`.

## Remote module

Remote module находится в:

```text
remote-module
```

Он собирается как Android APK, но для host app является dex-контейнером.

Примеры классов:

```text
remote-module/src/main/java/com/engboost/remote/HelloRemoteFeature.kt
remote-module/src/main/java/com/engboost/remote/CounterComposeFeature.kt
remote-module/src/main/java/com/engboost/remote/ProfileCardComposeFeature.kt
remote-module/src/main/java/com/engboost/remote/ChecklistComposeFeature.kt
```

Зависимости remote module на host API объявлены как `compileOnly`. Это означает:

- remote module компилируется против API;
- API не упаковывается как полноценная независимая runtime-библиотека;
- во время выполнения классы API должны приходить от host app.

## Сборка клиента с HTTP/3

Для HTTP/3 сборки нужны prebuilt curl libraries:

```text
third_party/curl-android
```

Debug APK для emulator `x86_64`:

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

Для реального Android-устройства обычно нужен ABI:

```text
arm64-v8a
```

Команда:

```powershell
.\gradlew.bat :app:assembleDebug `
  "-PnativeHttp3.enableCmake=true" `
  "-PnativeHttp3.enableCurl=true" `
  "-PnativeHttp3.curlRootDir=$CurlRoot" `
  "-PnativeHttp3.abis=arm64-v8a"
```

## Проверка APK

После сборки нужно проверить, что APK содержит:

```text
res/raw/dexmvp_root_ca.crt
lib/{abi}/libnative-http3.so
```

Команда:

```powershell
$Apk = ".\app\build\outputs\apk\debug\app-debug.apk"
& "$env:JAVA_HOME\bin\jar.exe" tf $Apk | Select-String -Pattern "dexmvp_root_ca|dexmvp_local_ca|lib/.*/libnative-http3.so"
```

Должно быть:

```text
res/raw/dexmvp_root_ca.crt
lib/x86_64/libnative-http3.so
```

или:

```text
lib/arm64-v8a/libnative-http3.so
```

Не должно быть:

```text
dexmvp_local_ca
```

## Проверка на устройстве

Установить APK:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Запустить:

```powershell
adb shell monkey -p com.engboost.dexmvp 1
```

В приложении:

```text
Server URL = https://10.72.217.180:8443
Transport = HTTP3_ONLY
```

Порядок проверки:

```text
Check
Download
Open
```

Если падает TLS, смотреть поля:

```text
curlCode
sslVerifyResult
message
caFilePath
```

Для рабочей CAINFO-схемы важно:

```text
caFilePath не пустой
sslVerifyResult = 0
```

## Инварианты клиента

Клиентская часть считается корректной, если:

- APK содержит `dexmvp_root_ca.crt`;
- APK содержит `libnative-http3.so` для нужного ABI;
- `HTTP3_ONLY` делает запросы через libcurl, а не OkHttp;
- `caFilePath` передаётся в native layer;
- manifest успешно загружается с `https://10.72.217.180:8443`;
- artifact скачивается по `artifactUrl` из manifest;
- SHA-256 скачанного artifact совпадает с manifest;
- verified artifact становится read-only;
- `DexClassLoader` загружает entry point из manifest;
- loaded feature проходит проверку `id` и `version`.

## Границы текущей безопасности

Сейчас уже есть:

- TLS verification через Root CA в APK;
- HTTP/3-only режим для строгой проверки транспорта;
- SHA-256 verification artifact;
- read-only artifact перед загрузкой;
- проверка host API compatibility;
- проверка feature id/version после загрузки.

Пока нет:

- подписи manifest или artifact release key-ом;
- pinning-а конкретного server/public key;
- защиты от rollback/replay старого валидного manifest;
- политики обновлений по versionCode приложения;
- sandbox-а для remote feature сверх обычных ограничений Android process;
- runtime permission isolation для remote code.

Ключевой вывод: текущая схема подходит для MVP и controlled стенда. Для production-доставки кода следующим обязательным шагом должна быть криптографическая подпись manifest или artifact отдельным ключом, не связанным с TLS.
