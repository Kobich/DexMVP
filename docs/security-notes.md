# Security Notes

## Что защищает MVP

MVP закрывает базовые риски uncontrolled dynamic loading:

- artifact не запускается до проверки SHA-256;
- hash приходит из manifest;
- artifact хранится во внутреннем хранилище приложения;
- artifact помечается read-only до загрузки;
- host проверяет совместимость `minHostApi`;
- host загружает только manifest-defined `features[].entryPoint`;
- загруженный объект обязан реализовать `RemoteFeature` или `RemoteComposeFeature`;
- remote-модуль не получает Android `Context` напрямую.

## Что не защищает MVP

MVP не является production-ready security model:

- нет цифровой подписи manifest или artifact;
- trusted server задается конфигурацией и не закреплен certificate pinning;
- remote code выполняется внутри процесса host app;
- нет настоящей sandbox-изоляции между host и remote-кодом;
- нет rollback-кеша последней валидной версии;
- нет защиты от downgrade-атак;
- нет allowlist версий или ключей подписи.

## Dynamic Code Loading риск

Dynamic Code Loading опасен, потому что приложение начинает исполнять код, которого не было в исходном APK host-приложения. Для production это может конфликтовать с требованиями безопасности, внутренними политиками и правилами публикации.

В этом проекте риск ограничен тем, что host:

- получает код только с контролируемого сервера;
- требует manifest;
- проверяет SHA-256;
- использует узкий контракт `RemoteFeature` / `RemoteComposeFeature`;
- не передает remote-модулю полный доступ к host API.

## Android 14+

Для приложений с современным target SDK динамически загружаемые файлы должны быть read-only до загрузки. В MVP это делает `ModuleStorage.commitVerifiedArtifact()`.

Важное свойство сценария:

```text
download -> sha256 verify -> copy to internal storage -> set read-only -> DexClassLoader
```

## Что добавить для production

Минимальные следующие шаги:

- подпись manifest или artifact;
- hardcoded public key в host app;
- certificate pinning для server API;
- rollback на последнюю валидную версию;
- запрет downgrade по `version`;
- allowlist `moduleId` и `features[].entryPoint`;
- аудит API, доступного remote-модулю;
- отдельная threat model;
- проверка Google Play policy или выбор альтернативы: Play Feature Delivery, server-driven config, DSL, WebView/JS sandbox.

## Защитная позиция проекта

Этот проект не позиционируется как универсальный загрузчик неизвестного кода. Это демонстрация controlled remote module system:

- источник контролируемый;
- формат manifest фиксированный;
- integrity check обязателен;
- entry point заранее известен;
- API-контракт узкий;
- ограничения явно описаны.
