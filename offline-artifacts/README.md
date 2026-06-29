# Offline Artifacts

Эта папка нужна, если в закрытую инфраструктуру можно принести только один zip проекта.

Сюда кладутся тяжёлые внешние артефакты, которые обычно не являются исходниками проекта.

## Рекомендуемая Структура

```text
offline-artifacts/
  README.md
  wsl/
    dexmvp-ubuntu-http3.tar
  gradle/
    gradle-user-home.zip
  android/
    android-studio-installer.exe
    android-sdk.zip
    android-ndk.zip
    android-cmake.zip
    emulator-system-image.zip
  nginx/
    packages/
  notes/
    installed-versions.txt
```

## Что Класть Обязательно

Если закрытая машина уже имеет Android Studio, SDK, NDK, CMake, WSL и NGINX HTTP/3, эта папка может быть почти пустой.

Если закрытая машина голая, одного исходного кода недостаточно. Тогда в zip проекта нужно добавить:

- Gradle cache;
- Android SDK/NDK/CMake installer или archive;
- WSL export с готовым NGINX HTTP/3 или packages для установки NGINX;
- emulator image или инструкции для физического устройства;
- notes с версиями.

## Git Policy

По умолчанию тяжёлые файлы внутри этой папки не должны коммититься без явного решения команды.

Идея такая:

1. В рабочем git хранить только `offline-artifacts/README.md`.
2. Перед передачей в закрытую инфраструктуру положить сюда реальные artifacts.
3. Сделать zip всей папки проекта.
4. На закрытой машине распаковать zip и читать `docs/deployment-guide.md`.
