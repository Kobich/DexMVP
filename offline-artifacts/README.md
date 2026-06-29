# Offline Artifacts

Эта папка нужна как локальная staging-зона для передачи файлов с интернет-VM на рабочую машину без интернета.

Сюда кладутся тяжёлые внешние артефакты, которые обычно не являются исходниками проекта.

## Рекомендуемая Структура

```text
offline-artifacts/
  README.md
  wsl/
    dexmvp-ubuntu-http3.tar
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

- доступ к Gradle dependencies через mirror/cache/VM;
- Android SDK/NDK/CMake installer или archive;
- WSL export с готовым NGINX HTTP/3 или packages для установки NGINX;
- emulator image или инструкции для физического устройства;
- notes с версиями.

## Git Policy

Тяжёлые файлы внутри этой папки не коммитятся.

Идея такая:

1. На VM с интернетом скачать/собрать нужные files/images/packages.
2. Положить их в `offline-artifacts/`.
3. Сделать zip/transfer bundle.
4. Передать bundle на рабочую машину без интернета.
5. На рабочей машине распаковать и читать `docs/deployment-guide.md`.

В git остаются только README/инструкции. Реальные payload-файлы локальные.

## Что Уже Не Надо Дублировать Здесь

`third_party/curl-android` уже лежит в проекте отдельно и видим для git. Не надо копировать его второй раз в `offline-artifacts`.

Gradle dependencies лучше решать отдельно: через корпоративный mirror/cache или approved offline setup. Не складывать весь `%USERPROFILE%\.gradle` в репозиторий.
