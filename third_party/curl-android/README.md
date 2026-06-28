# curl-android offline bundle

Эта папка содержит готовый offline-bundle `libcurl` для Android с HTTP/3.

## Ожидаемая структура

```text
third_party/curl-android/
  include/
    curl/
      curl.h
  libs/
    arm64-v8a/
      libcurl.so OR libcurl.a
    x86_64/
      libcurl.so OR libcurl.a
```

Опционально, если нужны дополнительные ABI:

```text
third_party/curl-android/libs/armeabi-v7a/libcurl.so
third_party/curl-android/libs/x86/libcurl.so
```

## Проверка

```powershell
.\scripts\check-curl-android-layout.ps1
```

## Сборка с libcurl

```powershell
.\scripts\verify-native-http3-curl.ps1
```

Скрипт по умолчанию ищет bundle именно здесь:

```text
third_party/curl-android
```

## Git

Текущая политика проекта: реальные файлы `include/` и `libs/` видимы для git.

Причина: без этих headers/libs закрытая машина не соберёт HTTP/3 APK.

Если в рабочем контуре нельзя хранить бинарники в git, перенести эту папку отдельным zip/archive и распаковать в тот же путь:

```text
third_party/curl-android
```
