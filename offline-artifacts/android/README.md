# Android Offline Artifacts

Эта папка зарезервирована под Android Studio / SDK / NDK / CMake installers или archives.

## Что Нужно Для Этого Проекта

На текущей рабочей машине используются:

```text
Android SDK platform: android-36
Android SDK Build Tools: 36.0.0
Android NDK: 28.2.13676358
Android CMake: 3.22.1
```

Точные установленные версии зафиксированы в:

```text
offline-artifacts/notes/installed-versions.txt
```

## Почему Здесь Нет Автоматически Скопированного SDK

Android SDK/NDK — большой внешний toolchain. Его лучше переносить как approved installer/archive или ставить через корпоративный образ машины.

Не надо коммитить случайную локальную папку SDK целиком без решения команды: она может быть большой, содержать лишние platform/system-images и быть привязанной к локальной машине.

## Что Положить Сюда Если Нужно

```text
android-studio-installer.exe
android-sdk-platform-36.zip
android-build-tools-36.0.0.zip
android-ndk-28.2.13676358.zip
android-cmake-3.22.1.zip
emulator-system-image.zip
```

Если закрытая машина уже имеет Android Studio/SDK/NDK/CMake, эта папка может оставаться пустой кроме README.
