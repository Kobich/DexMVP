# curl-android offline bundle

Эта папка зарезервирована под готовый offline-bundle `libcurl` для Android с HTTP/3.

Сейчас ничего скачивать не нужно. Здесь лежит только шаблон структуры.

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

Реальные файлы `include/` и `libs/` игнорируются через `.gitignore`, чтобы случайно не закоммитить тяжёлые бинарники.

Для закрытого контура эту папку лучше переносить рядом с проектом в zip/archive.
