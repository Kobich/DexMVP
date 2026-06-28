# HTTP/3 curl Source Strategy

Короткий ответ: для реального HTTP/3 нужен `libcurl` под Android, собранный с QUIC/HTTP3. Сам Android/NDK такой готовый `libcurl.so` не дают.

## Нужно ли качать

Не обязательно прямо сейчас.

Сейчас проект уже работает через HTTP fallback. Для продолжения обычной разработки ничего качать не нужно.

Но для реального режима `HTTP3_ONLY` в итоге нужно получить `libcurl` одним из способов:

1. получить approved/prebuilt bundle внутри рабочей инфраструктуры;
2. собрать bundle самостоятельно на машине с интернетом;
3. скачать готовый bundle из интернета.

Третий вариант нежелателен: высокий риск по безопасности, лицензиям, ABI, зависимостям и воспроизводимости.

## Рекомендованное решение

Для рабочего закрытого компьютера самый практичный путь:

```text
один раз подготовить bundle на нормальной машине
  -> проверить его скриптами
  -> положить в third_party/curl-android
  -> перенести проект вместе с этой папкой
```

На закрытом компьютере не надо будет собирать `curl`, `ngtcp2`, `nghttp3`, OpenSSL/BoringSSL/QuicTLS и прочие зависимости.

## Что именно должно появиться

Минимум для нашего проекта:

```text
third_party/curl-android/
  include/
    curl/
      curl.h
  libs/
    arm64-v8a/
      libcurl.so
    x86_64/
      libcurl.so
```

`arm64-v8a` нужен для физического устройства. `x86_64` нужен для большинства современных Android emulator на ПК.

## Почему не просто скачать первый libcurl.so

Нужен не обычный curl, а curl с HTTP/3.

По официальной документации curl HTTP/3 работает поверх QUIC. Для backend `ngtcp2` нужны три компонента:

- `ngtcp2`;
- `nghttp3`;
- TLS-библиотека с поддержкой QUIC.

Также curl HTTP/3 работает только для `https://` URL, потому что HTTP/3 поверх QUIC использует TLS.

## Что выбирать

### Вариант A — approved prebuilt

Лучший вариант для работы.

Что попросить у инфраструктурной/безопасностной команды:

- Android `libcurl.so` с HTTP/3;
- ABI минимум `arm64-v8a` и `x86_64`;
- header `include/curl/curl.h`;
- список зависимостей, если `libcurl.so` требует дополнительные `.so`;
- версии `curl`, `ngtcp2`, `nghttp3`, TLS backend;
- лицензии.

После получения:

```powershell
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
```

### Вариант B — собрать самим

Нормальный вариант для исследовательского прототипа.

Минусы:

- сборка Android cross-compile сложнее обычной desktop-сборки;
- нужно фиксировать версии;
- нужно понять, статически или динамически линковать зависимости;
- нужно отдельно проверять каждый ABI.

Плюсы:

- воспроизводимо;
- понятно, что внутри;
- проще пройти security review.

### Вариант C — скачать готовый из интернета

Не рекомендую для рабочего проекта.

Можно использовать только как временный локальный эксперимент, если:

- источник доверенный;
- есть версии и лицензии;
- понятно, какие зависимости внутри;
- bundle не попадёт в рабочий закрытый контур без проверки.

## Как будем делать в проекте

Текущая архитектура уже готова:

```text
Http3RemoteTransport
  -> NativeHttp3Client
    -> libnative-http3.so
      -> libcurl.so
```

Сейчас `libnative-http3.so` собирается без curl:

```powershell
.\scripts\verify-native-http3.ps1
```

Когда появится bundle:

```powershell
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
```

## Следующий инженерный шаг

До скачивания/сборки нужно выбрать источник `libcurl`:

```text
если есть корпоративный approved bundle -> берем его
иначе -> готовим собственный reproducible build bundle
```

Для этого проекта правильнее сначала попробовать получить approved/prebuilt bundle. Если его нет, тогда отдельно делать скрипты сборки curl под Android.

Практическая инструкция: `docs/http3-curl-build-guide.md`.

## Источники

- curl HTTP/3 documentation: https://curl.se/docs/http3.html
- everything curl HTTP/3: https://everything.curl.dev/http/versions/http3.html
