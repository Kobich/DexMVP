# Closed Infra Runbook

Стартовая инструкция по развёртыванию: `docs/deployment-guide.md`.

Этот файл нужен для работы на компьютере без нормального интернета и с ограниченными AI-инструментами.

## 1. Что взять с собой

Лучше перенести весь проект целиком:

```text
DexMVP
```

Обязательно должны быть:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradle/libs.versions.toml
app
feature
native-http3
remote-module
server
docs
scripts
third_party
```

Желательно взять уже прогретый Gradle cache с машины, где проект собирался:

```text
%USERPROFILE%\.gradle\caches
%USERPROFILE%\.gradle\wrapper
```

Иначе закрытая инфраструктура может не скачать Gradle distribution, Android Gradle Plugin, Kotlin, Ktor, OkHttp и Compose зависимости.

## 2. Что должно быть установлено

Минимум:

- Android Studio;
- Android SDK с `compileSdk 36`;
- JDK/JBR, который видит Gradle;
- Android emulator или физическое устройство;
- PowerShell.

Для HTTP/3 дополнительно:

- Android NDK;
- CMake;
- WSL с NGINX mainline, собранным/установленным с HTTP/3 module;
- offline `third_party/curl-android` bundle.

Проверить Java:

```powershell
java -version
```

Если `JAVA_HOME` сломан, временно задать JBR Android Studio:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 3. Первый smoke test

```powershell
.\scripts\verify-project.ps1
```

Этот скрипт собирает:

```text
:app:assembleDebug
:remote-module:assembleDebug
:server:build
```

Если это прошло, Gradle и зависимости в порядке.

## 4. Запуск server

```powershell
.\scripts\run-server.ps1
```

Скрипт сначала собирает remote APK, потом запускает Ktor server.

Server крутится на компьютере:

```text
http://localhost:8080
```

Для Android emulator этот же компьютер доступен как:

```text
http://10.0.2.2:8080
```

## 5. Проверка server

В другом терминале:

```powershell
.\scripts\check-server.ps1
```

Ожидаемо:

```text
health: OK
manifest: HTTP 200
artifact: HTTP 200
```

## 6. Запуск app на emulator

1. Открыть проект в Android Studio.
2. Дождаться Gradle sync.
3. Выбрать конфигурацию `app`.
4. Запустить emulator.
5. Нажать Run.
6. В приложении оставить:

```text
http://10.0.2.2:8080
```

7. Нажать:

```text
Check -> Download -> Open
```

## 7. Запуск на физическом устройстве

Узнать IP компьютера:

```powershell
ipconfig
```

Запустить server с base URL компьютера:

```powershell
.\scripts\run-server.ps1 -BaseUrl "http://YOUR_HOST_IP:8080"
```

В приложении указать:

```text
http://YOUR_HOST_IP:8080
```

Если не работает:

- телефон и компьютер должны быть в одной сети;
- firewall должен разрешать входящие подключения на порт `8080`;
- endpoint `http://YOUR_HOST_IP:8080/health` должен открываться с телефона.

## 8. Самые частые проблемы

### Server говорит, что APK не найден

Причина:

```text
remote-module-debug.apk еще не собран
```

Решение:

```powershell
.\gradlew.bat :remote-module:assembleDebug
```

### App не видит server на emulator

Проверить, что в app указан именно:

```text
http://10.0.2.2:8080
```

`localhost` внутри emulator означает сам emulator, а не компьютер.

### App не видит server на физическом телефоне

Использовать IP компьютера, не `10.0.2.2`.

### SHA-256 mismatch

Manifest был получен от одной версии APK, а скачался другой APK.

Решение:

```powershell
остановить server
пересобрать remote-module
снова запустить server
в app нажать Check заново
потом Download
```

### Class not found

`features[].entryPoint` в server manifest не совпадает с классом внутри remote APK.

Проверить:

```text
server/src/main/kotlin/com/engboost/server/modules/ModuleRegistry.kt
remote-module/src/main/java/com/engboost/remote/HelloRemoteFeature.kt
remote-module/src/main/java/com/engboost/remote/CounterComposeFeature.kt
remote-module/src/main/java/com/engboost/remote/ProfileCardComposeFeature.kt
remote-module/src/main/java/com/engboost/remote/ChecklistComposeFeature.kt
```

### ClassCastException / does not implement RemoteFeature или RemoteComposeFeature

Обычно проблема в контракте:

- remote APK упаковал свою копию `RemoteFeature`, `RemoteComposeFeature` или Compose runtime;
- package `com.engboost.remoteapi` поменяли только с одной стороны;
- remote-module использует не `compileOnly`, а `implementation`.

Проверить:

```text
remote-module/build.gradle.kts
```

Должно быть:

```kotlin
compileOnly(project(":feature:remote-execution:api"))
```

## 9. Что можно заранее подготовить для закрытого контура

- Сохранить Gradle cache после успешной сборки.
- Сохранить Android SDK platform `36`.
- Сохранить Android NDK и CMake installer/cache.
- Сохранить этот проект с `docs` и `scripts`.
- Сделать zip проекта без `build` папок.
- Перенести `third_party/curl-android` отдельным archive, если реальные libs/headers не коммитятся.
- Сохранить успешные команды запуска в README.
- Держать рядом `remote-module-debug.apk`, если сборка remote module временно недоступна.

Не переносить в git/archive проекта:

- `build`, `.gradle`, `.cxx`;
- private key локального CA;
- NGINX server private key;
- machine-specific cert/private key файлы.

## 10. HTTP/3 offline bundle

Для HTTP/3 не нужно собирать `libcurl` на закрытом компьютере. Проще перенести готовый bundle:

```text
third_party/curl-android
```

Минимальная структура:

```text
third_party/curl-android/include/curl/curl.h
third_party/curl-android/libs/arm64-v8a/libcurl.a или libcurl.so
third_party/curl-android/libs/x86_64/libcurl.a или libcurl.so
```

Проверка структуры:

```powershell
.\scripts\check-curl-android-layout.ps1
```

Проверка сборки native HTTP/3 с curl:

```powershell
.\scripts\verify-native-http3-curl.ps1
```

Если bundle ещё не готов, это нормально. Основной проект проверяется без него:

```powershell
.\scripts\verify-project.ps1
.\scripts\verify-native-http3.ps1
```

Как выбрать источник `libcurl`: `docs/http3-curl-source-strategy.md`.
