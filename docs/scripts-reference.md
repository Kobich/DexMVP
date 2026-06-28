# Scripts Reference

Стартовая инструкция по развёртыванию: `docs/deployment-guide.md`.

Краткий справочник по scripts проекта.

## Main project

### `scripts/verify-project.ps1`

Собирает основной проект:

```text
:app:assembleDebug
:remote-module:assembleDebug
:server:build
```

Команда:

```powershell
.\scripts\verify-project.ps1
```

### `scripts/run-server.ps1`

Собирает `remote-module` и запускает Ktor server.

Обычный HTTP fallback:

```powershell
.\scripts\run-server.ps1
```

HTTP/3 через NGINX WSL:

```powershell
.\scripts\run-server.ps1 -BaseUrl "https://CURRENT_WSL_IP:8443"
```

### `scripts/check-server.ps1`

Проверяет Ktor endpoints:

```powershell
.\scripts\check-server.ps1
```

## HTTP/3 Android curl

### `scripts/check-curl-android-layout.ps1`

Проверяет, что `third_party/curl-android` содержит headers и libs.

Все ABI:

```powershell
.\scripts\check-curl-android-layout.ps1
```

Только emulator:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis x86_64
```

Только физическое устройство:

```powershell
.\scripts\check-curl-android-layout.ps1 -RequiredAbis arm64-v8a
```

### `scripts/import-curl-from-vcpkg.ps1`

Копирует `curl[http3]` из vcpkg в `third_party/curl-android`.

```powershell
.\scripts\import-curl-from-vcpkg.ps1 -VcpkgRoot C:\Users\RED.DOT\Downloads\vcpkg-master\vcpkg-master
```

Только один triplet:

```powershell
.\scripts\import-curl-from-vcpkg.ps1 -VcpkgRoot C:\Users\RED.DOT\Downloads\vcpkg-master\vcpkg-master -Triplets x64-android
```

### `scripts/verify-native-http3.ps1`

Проверяет сборку `native-http3` с CMake, но без curl.

```powershell
.\scripts\verify-native-http3.ps1
```

### `scripts/verify-native-http3-curl.ps1`

Проверяет сборку `native-http3` с curl.

Все ABI:

```powershell
.\scripts\verify-native-http3-curl.ps1
```

Только emulator:

```powershell
.\scripts\verify-native-http3-curl.ps1 -Abis x86_64
```

Только физическое устройство:

```powershell
.\scripts\verify-native-http3-curl.ps1 -Abis arm64-v8a
```

## HTTP/3 app run

### `scripts/show-http3-wsl-state.ps1`

Показывает текущий WSL IP и команду запуска Ktor с правильным `BaseUrl`.

```powershell
.\scripts\show-http3-wsl-state.ps1
```

### `scripts/wsl-generate-http3-certs.sh`

WSL script. Генерирует локальный CA, выпускает NGINX server cert под текущий WSL IP и копирует CA в Android debug resources.

Выполнять из WSL:

```bash
cd /mnt/c/Users/RED.DOT/AndroidStudioProjects/DexMVP
bash ./scripts/wsl-generate-http3-certs.sh
```

### `scripts/build-http3-apk.ps1`

Собирает `app-debug.apk` с HTTP/3 flags.

Emulator:

```powershell
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

Physical device:

```powershell
.\scripts\build-http3-apk.ps1 -Abi arm64-v8a
```

### `scripts/install-http3-apk.ps1`

Устанавливает уже собранный APK через `adb` и запускает приложение.

```powershell
.\scripts\install-http3-apk.ps1
```

Собрать, установить и запустить:

```powershell
.\scripts\install-http3-apk.ps1 -Build -Abi x86_64
```
