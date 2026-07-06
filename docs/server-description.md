# Серверная сторона DexMVP

Этот документ описывает серверную часть DexMVP: зачем она нужна, из каких частей состоит, какие данные отдаёт клиенту и как участвует в цепочке загрузки remote module.

Статус: `MVP / local IP-only стенд / не production-ready update backend`.

Документ самодостаточный: в нём описаны роль сервера, Caddy, Ktor, manifest, artifact, TLS-цепочка, запуск и критерии готовности серверной стороны.

## Коротко

Серверная сторона состоит из двух уровней:

| Уровень | Компонент | Роль |
| --- | --- | --- |
| Edge | Caddy | Принимает HTTPS/HTTP3, отдаёт TLS certificate, проксирует в Ktor |
| Backend | Ktor server | Отдаёт healthcheck, manifest и APK-артефакт remote module |

Основной поток:

```text
Android app
  -> https://10.72.217.180:8443
  -> Caddy
  -> http://127.0.0.1:8080
  -> Ktor
  -> manifest + remote-module APK
```

## Назначение

Серверная сторона в текущей версии решает одну основную задачу: доставить Android-приложению описание активного remote module и сам артефакт модуля.

Клиент не получает с сервера произвольный код “вслепую”. Сначала он запрашивает manifest, затем по URL из manifest скачивает APK-артефакт, проверяет его SHA-256 и только после этого загружает классы через `DexClassLoader`.

Рабочая схема:

```text
Android app
  -> HTTPS/HTTP3
  -> Caddy :8443
  -> Ktor server :8080
  -> remote-module-debug.apk
```

## Компоненты

### Caddy

Caddy стоит на внешней границе стенда. Именно к нему подключается Android-приложение.

Его ответственность:

- принимать HTTPS/HTTP3 соединения на `10.72.217.180:8443`;
- отдавать server certificate для IP `10.72.217.180`;
- проксировать HTTP-запросы в локальный Ktor server на `127.0.0.1:8080`;
- держать открытыми TCP и UDP `8443`, потому что HTTP/3 работает поверх QUIC/UDP.

Текущий `Caddyfile`:

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

Caddy не знает про manifest, SHA-256 и remote features. Он только завершает TLS/HTTP3 и передаёт запросы дальше.

### Ktor server

Ktor server — это origin backend. В текущем коде он слушает `0.0.0.0:8080`, поэтому технически может быть доступен не только локально. В рабочей схеме внешний вход должен идти через Caddy, а прямой доступ к `8080` нужно закрывать firewall-ом или переводить server bind на `127.0.0.1`.

Точка входа:

```text
server/src/main/kotlin/com/engboost/server/Application.kt
```

Основные endpoints:

| Method | Path | Назначение |
| --- | --- | --- |
| `GET` | `/health` | Быстрая проверка, что backend жив |
| `GET` | `/api/v1/modules/active` | Возвращает manifest активного remote module |
| `GET` | `/api/v1/modules/{moduleId}/{version}/artifact` | Отдаёт APK-артефакт remote module |

### ModuleRegistry

`ModuleRegistry` описывает текущий активный модуль.

Файл:

```text
server/src/main/kotlin/com/engboost/server/modules/ModuleRegistry.kt
```

Сейчас registry жёстко описывает один модуль:

```text
moduleId = hello
version = 1
hostApiVersion = 1
minHostApi = 1
```

Также он перечисляет features, которые клиент сможет загрузить из remote module:

```text
hello-output
counter-compose
profile-compose
checklist-compose
```

Для каждой feature manifest содержит:

- `id` — стабильный идентификатор feature;
- `title` — человекочитаемое название;
- `kind` — тип feature, например `output` или `compose`;
- `version` — версия feature;
- `entryPoint` — полное имя класса, который клиент должен загрузить через `DexClassLoader`.

### Remote module artifact

Артефакт remote module — это Android APK:

```text
remote-module/build/outputs/apk/debug/remote-module-debug.apk
```

Этот APK не устанавливается как отдельное приложение. В текущей архитектуре он используется как контейнер с dex-кодом, который host app скачивает, проверяет и загружает динамически.

Если нужен другой путь к артефакту, сервер поддерживает env var:

```text
DEX_REMOTE_ARTIFACT
```

Если переменная не задана, сервер ищет артефакт в стандартных местах внутри проекта.

## Manifest

Manifest — центральный контракт между сервером и клиентом.

Пример логической структуры:

```json
{
  "moduleId": "hello",
  "version": 1,
  "hostApiVersion": 1,
  "minHostApi": 1,
  "artifactUrl": "https://10.72.217.180:8443/api/v1/modules/hello/1/artifact",
  "sha256": "...",
  "signature": "",
  "features": [
    {
      "id": "hello-output",
      "title": "Hello Output",
      "kind": "output",
      "version": 1,
      "entryPoint": "com.engboost.remote.HelloRemoteFeature"
    }
  ]
}
```

Важные поля:

- `artifactUrl` должен указывать на Caddy endpoint, а не напрямую на `127.0.0.1:8080`;
- `sha256` вычисляется сервером по фактическому APK-файлу;
- `minHostApi` защищает клиент от запуска модуля, который требует более новую host API;
- `signature` сейчас есть в контракте, но фактически не используется.

## SHA-256

Сервер вычисляет SHA-256 артефакта перед выдачей manifest.

Файл:

```text
server/src/main/kotlin/com/engboost/server/security/Sha256.kt
```

Клиент использует этот hash после скачивания APK. Если скачанный файл не совпал с `sha256` из manifest, модуль не должен быть принят и загружен.

Это контроль целостности, а не полноценная криптографическая подпись publisher-а. Если нужен production-grade trust для remote code, следующим шагом должна быть отдельная подпись manifest или artifact приватным ключом релиза.

## TLS и доверие

Для IP-only стенда используется локальная цепочка:

```text
dexmvp-root-ca.key
  -> подписывает dexmvp-ip.crt

Caddy
  -> отдаёт dexmvp-ip.crt

Android/libcurl
  -> проверяет dexmvp-ip.crt через dexmvp_root_ca.crt из APK
```

Файлы на стороне Caddy:

```text
E:\caddy\certs\dexmvp-ip.crt
E:\caddy\certs\dexmvp-ip.key
```

Файл Root CA public certificate:

```text
E:\caddy\certs\dexmvp-root-ca.crt
```

Этот public certificate копируется в Android project:

```text
app/src/main/res/raw/dexmvp_root_ca.crt
```

Private keys не должны попадать в APK и git:

```text
dexmvp-root-ca.key
dexmvp-ip.key
```

Текущая роль файлов:

| Файл | Где лежит | Кому нужен | Секрет |
| --- | --- | --- | --- |
| `dexmvp-ip.crt` | `E:\caddy\certs` | Caddy отдаёт клиенту | Нет |
| `dexmvp-ip.key` | `E:\caddy\certs` | Caddy завершает TLS | Да |
| `dexmvp-root-ca.crt` | `E:\caddy\certs` и `app/src/main/res/raw` | libcurl проверяет Caddy cert | Нет |
| `dexmvp-root-ca.key` | private storage/backup | Выпуск новых server cert | Да |

## Runtime configuration

| Переменная | Значение по умолчанию | Назначение |
| --- | --- | --- |
| `DEX_SERVER_PORT` | `8080` | Порт Ktor server |
| `DEX_SERVER_BASE_URL` | `http://10.0.2.2:8080` | Public base URL, который попадёт в `artifactUrl` |
| `DEX_REMOTE_ARTIFACT` | debug APK из `remote-module` | Явный путь к APK-артефакту |

Для текущей Caddy-схемы `DEX_SERVER_BASE_URL` должен быть:

```text
https://10.72.217.180:8443
```

## Нормальный порядок запуска

1. Собрать remote module APK:

```powershell
.\gradlew.bat :remote-module:assembleDebug
```

2. Запустить Ktor server с правильным public base URL:

```powershell
$env:DEX_SERVER_BASE_URL = "https://10.72.217.180:8443"
.\gradlew.bat :server:run
```

3. Запустить или перезагрузить Caddy:

```powershell
& E:\caddy\caddy_windows_amd64.exe run --config E:\caddy\Caddyfile --adapter caddyfile
```

или:

```powershell
& E:\caddy\caddy_windows_amd64.exe reload --config E:\caddy\Caddyfile --adapter caddyfile
```

4. Проверить backend:

```powershell
curl.exe http://127.0.0.1:8080/health
```

5. Проверить TLS через Caddy:

```powershell
& "C:\Program Files\Git\usr\bin\openssl.exe" s_client `
  -connect 10.72.217.180:8443 `
  -verify_ip 10.72.217.180 `
  -CAfile E:\caddy\certs\dexmvp-root-ca.crt `
  -brief
```

Ожидаемый результат:

```text
Verification: OK
```

## Что сервер не делает

Текущий сервер не является полноценным production update backend.

Он пока не делает:

- авторизацию клиента;
- rollout по версиям приложения;
- хранение нескольких версий модулей;
- подпись manifest приватным ключом релиза;
- аудит скачиваний;
- защиту от replay старого manifest;
- управление environment-ами dev/stage/prod.

Это нормально для текущего MVP, но важно не путать текущую схему с полноценной production-системой доставки кода.

## Инварианты

Серверная часть считается корректно настроенной, если выполняются условия:

- `/health` отвечает `OK` на Ktor;
- Caddy слушает TCP и UDP `8443`;
- TLS verification через `openssl s_client -verify_ip` даёт `Verification: OK`;
- `/api/v1/modules/active` возвращает manifest с `artifactUrl` через `https://10.72.217.180:8443`;
- APK-артефакт существует на диске;
- SHA-256 в manifest считается по тому же APK, который отдаётся endpoint-ом artifact.
