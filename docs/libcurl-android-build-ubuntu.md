# libcurl HTTP/3 Build Guide (Ubuntu / WSL)

Цель: собрать в Ubuntu (WSL) из исходников Android-бандл `libcurl` с HTTP/3 и положить его в проект так, чтобы собрался `native-http3`.

Сборка идёт в Linux, результат используется на Windows: это кросс-компиляция через Android NDK, полученные `.a` не зависят от ОС, на которой собирались.

Итоговая папка:

```text
third_party/curl-android/
  include/curl/curl.h
  libs/arm64-v8a/*.a
  libs/x86_64/*.a
  cacert.pem
  BUILD-INFO.txt
```

`arm64-v8a` — физические устройства. `x86_64` — Android emulator на ПК.

## 0. Версии

| Что | Значение | Примечание |
|---|---|---|
| Android NDK | `30.0.16248370` (r30) | одинаковый в Ubuntu и в Android Studio |
| API level нативных библиотек | `23` | равен `minSdk`; `targetSdk 36` на сборку не влияет |
| ABI | `arm64-v8a`, `x86_64` | |
| Linkage | static, только release | |
| Компоненты | curl, ngtcp2, nghttp3, nghttp2, OpenSSL, zlib | последние версии из vcpkg |

Все команды выполняются в Ubuntu, в домашней папке (`~`). Не в `/mnt/c/...`: там сборка медленная и бывают ошибки прав.

## 1. Проверить Ubuntu

```bash
cd ~
lsb_release -a   # желательно 22.04 или 24.04
df -h ~          # нужно ~15 ГБ свободного места
```

## 2. Поставить пакеты

```bash
sudo apt update
sudo apt install -y build-essential pkg-config cmake ninja-build git curl zip unzip tar \
                    autoconf automake libtool python3 perl linux-libc-dev
```

## 3. Поставить NDK

```bash
cd ~
NDK_VER=r30
curl -fLO https://dl.google.com/android/repository/android-ndk-${NDK_VER}-linux.zip
unzip -q android-ndk-${NDK_VER}-linux.zip
export ANDROID_NDK_HOME=~/android-ndk-${NDK_VER}
echo "export ANDROID_NDK_HOME=$ANDROID_NDK_HOME" >> ~/.bashrc
```

Если `curl` вернул 404 — проверить точное имя архива на https://developer.android.com/ndk/downloads (у beta-версий имя вида `android-ndk-r30-beta1-linux.zip`) и поменять `NDK_VER`.

Проверить:

```bash
grep Pkg.Revision $ANDROID_NDK_HOME/source.properties
# Pkg.Revision = 30.0.16248370

cat $ANDROID_NDK_HOME/meta/platforms.json
# "min" должен быть <= 23
```

## 4. Подготовить vcpkg

```bash
git clone https://github.com/microsoft/vcpkg ~/vcpkg
cd ~/vcpkg
./bootstrap-vcpkg.sh -disableMetrics
```

## 5. Создать triplets под API 23

```bash
mkdir -p ~/triplets

cat > ~/triplets/arm64-android-23.cmake <<'EOF'
set(VCPKG_TARGET_ARCHITECTURE arm64)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_BUILD_TYPE release)
set(VCPKG_CMAKE_SYSTEM_NAME Android)
set(VCPKG_CMAKE_SYSTEM_VERSION 23)
set(VCPKG_MAKE_BUILD_TRIPLET "--host=aarch64-linux-android")
set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-23)
EOF

cat > ~/triplets/x64-android-23.cmake <<'EOF'
set(VCPKG_TARGET_ARCHITECTURE x64)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_BUILD_TYPE release)
set(VCPKG_CMAKE_SYSTEM_NAME Android)
set(VCPKG_CMAKE_SYSTEM_VERSION 23)
set(VCPKG_MAKE_BUILD_TRIPLET "--host=x86_64-linux-android")
set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-23)
EOF
```

Соответствие triplet и Android ABI:

```text
arm64-android-23 -> arm64-v8a
x64-android-23   -> x86_64
```

## 6. Собрать curl с HTTP/3

Занимает 15–40 минут.

```bash
cd ~/vcpkg
./vcpkg install "curl[http2,http3]" --triplet arm64-android-23 --overlay-triplets=$HOME/triplets
./vcpkg install "curl[http2,http3]" --triplet x64-android-23  --overlay-triplets=$HOME/triplets
```

- `http3` — HTTP/3 через `ngtcp2` + `nghttp3` + OpenSSL.
- `http2` — HTTP/2 внутри libcurl, чтобы был fallback без OkHttp.
- Если нужен асинхронный DNS — `curl[http2,http3,c-ares]` в обеих командах.

## 7. Проверить сборку

```bash
# должны быть все 4 строки
grep -hE "define (USE_NGTCP2|USE_NGHTTP3|USE_NGHTTP2|USE_OPENSSL)" \
  ~/vcpkg/buildtrees/curl/arm64-android-23-rel/lib/curl_config.h

# собрано под API 23 — должны быть совпадения
grep -rl "android23" ~/vcpkg/buildtrees/curl/*.log | head -3

# версии всех компонентов
~/vcpkg/vcpkg list --triplet arm64-android-23
```

## 8. Собрать бандл

```bash
OUT=~/curl-android
rm -rf $OUT
mkdir -p $OUT/include $OUT/libs/arm64-v8a $OUT/libs/x86_64

cp -r ~/vcpkg/installed/arm64-android-23/include/curl $OUT/include/
cp ~/vcpkg/installed/arm64-android-23/lib/*.a $OUT/libs/arm64-v8a/
cp ~/vcpkg/installed/x64-android-23/lib/*.a   $OUT/libs/x86_64/

# корневые сертификаты для доменов с публичными сертификатами
curl -fo $OUT/cacert.pem https://curl.se/ca/cacert.pem

# паспорт сборки
{
  echo "NDK: $(grep Pkg.Revision $ANDROID_NDK_HOME/source.properties)"
  echo "API: 23"
  echo "vcpkg commit: $(git -C ~/vcpkg rev-parse HEAD)"
  echo
  ~/vcpkg/vcpkg list --triplet arm64-android-23
  echo
  (cd $OUT && sha256sum libs/*/*.a)
} > $OUT/BUILD-INFO.txt

ls -R $OUT
cat $OUT/BUILD-INFO.txt
```

В `libs/<abi>/` должны быть:

```text
libcurl.a
libngtcp2.a
libngtcp2_crypto_ossl.a
libnghttp3.a
libnghttp2.a
libssl.a
libcrypto.a
libz.a
libcares.a   # только при c-ares
```

## 9. Сохранить исходники

Все архивы исходников, скачанные vcpkg (curl, OpenSSL, ngtcp2, nghttp3, nghttp2, zlib):

```bash
tar czf ~/curl-android-sources.tar.gz -C ~/vcpkg downloads
```

Нужно для security review и для повторной сборки без интернета.

## 10. Положить бандл в проект

Старый бандл заменить целиком:

```bash
PROJECT=/mnt/c/Users/<user>/AndroidStudioProjects/DexMVP
rm -rf $PROJECT/third_party/curl-android/include $PROJECT/third_party/curl-android/libs
cp -r ~/curl-android/* $PROJECT/third_party/curl-android/
```

Или скопировать через проводник Windows из `\\wsl$\Ubuntu\home\<user>\curl-android`.

## 11. Подключить в проекте

Android Studio: `SDK Manager -> SDK Tools -> NDK (Side by side) -> 30.0.16248370`.

`native-http3/build.gradle.kts`:

```kotlin
android {
    ndkVersion = "30.0.16248370"
    defaultConfig {
        minSdk = 23
    }
}
```

`native-http3/src/main/cpp/CMakeLists.txt` — в список `NATIVE_HTTP3_STATIC_DEPENDENCY_LIBS` добавить `libnghttp2.a` (и `libcares.a`, если собирали с c-ares). Иначе линковка упадёт с `undefined symbol: nghttp2_...`:

```cmake
set(NATIVE_HTTP3_STATIC_DEPENDENCY_LIBS
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libnghttp2.a"
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libngtcp2_crypto_ossl.a"
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libngtcp2.a"
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libnghttp3.a"
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libssl.a"
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libcrypto.a"
    "${NATIVE_HTTP3_CURL_ROOT}/libs/${ANDROID_ABI}/libz.a"
)
```

## 12. Проверить на Windows

```powershell
.\scripts\check-curl-android-layout.ps1
.\scripts\verify-native-http3-curl.ps1
.\scripts\build-http3-apk.ps1 -Abi x86_64
```

## Если Что-то Упало

Прислать последние ~30 строк вывода. vcpkg в конце пишет путь к логу, обычно вида:

```text
~/vcpkg/buildtrees/<пакет>/<что-то>-arm64-android-23-rel-out.log
```

```bash
tail -50 <путь к логу>
```

### `undefined symbol` при сборке `native-http3`

Не хватает библиотеки в `NATIVE_HTTP3_STATIC_DEPENDENCY_LIBS` (см. шаг 11) или бандл собран под другой ABI.

### `cannot locate symbol` при запуске приложения

Бандл собран под API выше `minSdk`. Проверить шаг 7 (`android23`) и triplets.

### Ошибки TLS для доменов

Передать в libcurl `cacert.pem` (склеенный со своим корневым сертификатом, если он есть) через `CURLOPT_CAINFO`.
