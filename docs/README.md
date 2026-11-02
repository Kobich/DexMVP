# Документация DexMVP

Главный вход для человека: `docs/deployment-guide.md`.

Если нужно развернуть проект, не начинать с отдельных HTTP/3/TLS/script файлов. Сначала открыть `docs/deployment-guide.md`, пройти порядок сверху вниз, и только потом уходить по ссылкам в детали.

## Основной Маршрут

1. `docs/deployment-guide.md` — единая последовательность развёртывания.
2. `docs/closed-infra-runbook.md` — что подготовить и перенести на закрытую машину.
3. `docs/http3-setup-guide.md` — подробный HTTP/3 setup Windows + WSL + Android.
4. `docs/http3-critical-handoff.md` — восстановление уже настроенного HTTP/3 стенда.
5. `docs/scripts-reference.md` — справочник команд и скриптов.

## По Задачам

Развернуть обычный HTTP:

- `docs/deployment-guide.md`
- `docs/closed-infra-runbook.md`

Развернуть HTTP/3:

- `docs/deployment-guide.md`
- `docs/http3-setup-guide.md`
- `docs/nginx-http3-from-scratch.md`
- `docs/caddy-http3-tls-guide.md`
- `docs/http3-tls-guide.md`
- `docs/http3-critical-handoff.md`

Понять архитектуру:

- `docs/architecture.md`
- `docs/client-plugin-architecture.md` — краткая схема client/plugin-границ
- `docs/code-map.md`
- `docs/system-flow.md`

Понять remote feature API:

- `feature/remote-execution/README.md`
- `docs/beginner-guide.md`

Понять безопасность и ограничения:

- `docs/security-notes.md`
- `docs/production-tls-and-code-trust-guide.md`
- `docs/http3-future-requirement.md`
- `docs/possible-changes.md`

## Reference / История Решений

Эти файлы не являются стартовой инструкцией. Открывать только если нужна конкретная деталь:

- `docs/http3-curl-build-guide.md` — сборка curl HTTP/3 через vcpkg.
- `docs/http3-curl-source-strategy.md` — почему нужен controlled curl bundle.
- `docs/http3-libcurl-integration.md` — детали JNI/libcurl integration.
- `docs/http3-current-state.md` — снимок состояния HTTP/3 стенда.
- `docs/production-tls-and-code-trust-guide.md` — production-like TLS, CSR под IP и подпись remote manifest/artifact.
- `docs/demo-script.md` — сценарий демонстрации.

## Самое Важное Для Переноса

В репозиторий:

- исходники модулей `app`, `feature`, `native-http3`, `remote-module`, `server`;
- `gradle`, `gradlew`, `gradlew.bat`, root Gradle files;
- `docs`;
- `scripts`;
- реальные `third_party/curl-android/include`;
- реальные `third_party/curl-android/libs`;

Если правила рабочего git запрещают бинарники, `third_party/curl-android/include` и `third_party/curl-android/libs` можно переносить отдельным archive, но путь после распаковки должен остаться тем же.

Отдельно от проекта:

- доступ к Gradle dependencies через mirror/cache;
- Android SDK/NDK/CMake installer/cache.

Если есть VM с интернетом, использовать `offline-artifacts/` как локальную staging-папку для transfer bundle. Тяжёлые payload-файлы из неё не коммитятся. Назначение папки описано в `offline-artifacts/README.md`.

Не тащить как исходники:

- `build`;
- `.gradle`;
- `.cxx`;
- private key локального CA;
- NGINX server private key.
