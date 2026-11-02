# Сборка libcurl с HTTP/3 для Android (Ubuntu)

Итог:

```
NDK:  30.0.16248370 (r30)  — такой же должен стоять в Android Studio
API:  36 (minSdk = targetSdk = 36)
ABI:  arm64-v8a (устройства), x86_64 (эмулятор)
libcurl + ngtcp2 + nghttp3 + nghttp2 + OpenSSL + zlib, всё статически, только release
```

Все команды выполняются в Ubuntu, в домашней папке (`~`). Не в `/mnt/c/...`: там сборка медленная и бывают ошибки прав.

---

## 0. Проверка

```bash
cd ~
lsb_release -a   # желательно 22.04 или 24.04
df -h ~          # нужно ~15 ГБ свободного места
```

## 1. Пакеты

```bash
sudo apt update
sudo apt install -y build-essential pkg-config cmake ninja-build git curl zip unzip tar \
                    autoconf automake libtool python3 perl linux-libc-dev
```

## 2. NDK 30.0.16248370

```bash
cd ~
NDK_VER=r30
curl -fLO https://dl.google.com/android/repository/android-ndk-${NDK_VER}-linux.zip
unzip -q android-ndk-${NDK_VER}-linux.zip
export ANDROID_NDK_HOME=~/android-ndk-${NDK_VER}
echo "export ANDROID_NDK_HOME=$ANDROID_NDK_HOME" >> ~/.bashrc
```

> Если `curl` вернул 404, проверь точное имя архива на https://developer.android.com/ndk/downloads
> (например, у beta-версий имя вида `android-ndk-r30-beta1-linux.zip`) и поменяй `NDK_VER`.

Проверка:

```bash
grep Pkg.Revision $ANDROID_NDK_HOME/source.properties
# должно быть: Pkg.Revision = 30.0.16248370

ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/ | grep 36
# должно вывести: 36
```

## 3. vcpkg

```bash
git clone https://github.com/microsoft/vcpkg ~/vcpkg
cd ~/vcpkg
./bootstrap-vcpkg.sh -disableMetrics
```

## 4. Триплеты под API 36

```bash
mkdir -p ~/triplets

cat > ~/triplets/arm64-android-36.cmake <<'EOF'
set(VCPKG_TARGET_ARCHITECTURE arm64)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_BUILD_TYPE release)
set(VCPKG_CMAKE_SYSTEM_NAME Android)
set(VCPKG_CMAKE_SYSTEM_VERSION 36)
set(VCPKG_MAKE_BUILD_TRIPLET "--host=aarch64-linux-android")
set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-36)
EOF

cat > ~/triplets/x64-android-36.cmake <<'EOF'
set(VCPKG_TARGET_ARCHITECTURE x64)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)
set(VCPKG_BUILD_TYPE release)
set(VCPKG_CMAKE_SYSTEM_NAME Android)
set(VCPKG_CMAKE_SYSTEM_VERSION 36)
set(VCPKG_MAKE_BUILD_TRIPLET "--host=x86_64-linux-android")
set(VCPKG_CMAKE_CONFIGURE_OPTIONS -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-36)
EOF
```

## 5. Сборка (15–40 минут)

```bash
cd ~/vcpkg
./vcpkg install "curl[http2,http3]" --triplet arm64-android-36 --overlay-triplets=$HOME/triplets
./vcpkg install "curl[http2,http3]" --triplet x64-android-36  --overlay-triplets=$HOME/triplets
```

> Нужен асинхронный DNS — замени `curl[http2,http3]` на `curl[http2,http3,c-ares]` в обеих командах.

## 6. Проверка результата

```bash
# HTTP/3, HTTP/2 и OpenSSL включены — должны быть все 4 строки
grep -hE "define (USE_NGTCP2|USE_NGHTTP3|USE_NGHTTP2|USE_OPENSSL)" \
  ~/vcpkg/buildtrees/curl/arm64-android-36-rel/lib/curl_config.h

# собрано под API 36 — должны быть совпадения
grep -rl "android36" ~/vcpkg/buildtrees/curl/*.log | head -3

# версии всех компонентов
~/vcpkg/vcpkg list --triplet arm64-android-36
```

## 7. Собрать всё в одну папку

```bash
OUT=~/curl-android
rm -rf $OUT
mkdir -p $OUT/include $OUT/libs/arm64-v8a $OUT/libs/x86_64

cp -r ~/vcpkg/installed/arm64-android-36/include/curl $OUT/include/
cp ~/vcpkg/installed/arm64-android-36/lib/*.a $OUT/libs/arm64-v8a/
cp ~/vcpkg/installed/x64-android-36/lib/*.a   $OUT/libs/x86_64/

# корневые сертификаты (нужны для доменов с публичными сертификатами)
curl -fo $OUT/cacert.pem https://curl.se/ca/cacert.pem

# паспорт сборки
{
  echo "NDK: $(grep Pkg.Revision $ANDROID_NDK_HOME/source.properties)"
  echo "API: 36"
  echo "vcpkg commit: $(git -C ~/vcpkg rev-parse HEAD)"
  echo
  ~/vcpkg/vcpkg list --triplet arm64-android-36
  echo
  (cd $OUT && sha256sum libs/*/*.a)
} > $OUT/BUILD-INFO.txt

ls -R $OUT
cat $OUT/BUILD-INFO.txt
```

Ожидаемая структура:

```
curl-android/
  include/curl/curl.h ...
  libs/arm64-v8a/  libcurl.a libngtcp2.a libngtcp2_crypto_ossl.a libnghttp3.a libnghttp2.a libssl.a libcrypto.a libz.a
  libs/x86_64/     то же самое
  cacert.pem
  BUILD-INFO.txt
```

## 8. Сохранить исходники

Все исходники, которые скачал vcpkg (curl, OpenSSL, ngtcp2, nghttp3, nghttp2, zlib):

```bash
tar czf ~/curl-android-sources.tar.gz -C ~/vcpkg downloads
```

## 9. Перенести на Windows

```bash
cp -r ~/curl-android ~/curl-android-sources.tar.gz /mnt/c/Users/<твой_пользователь>/Desktop/
```

Или открыть в проводнике Windows: `\\wsl$\Ubuntu\home\<user>\curl-android`

## 10. В Android-проекте

- В Android Studio: SDK Manager → SDK Tools → NDK (Side by side) → **30.0.16248370**
- В `build.gradle.kts` модуля с нативным кодом:

```kotlin
android {
    ndkVersion = "30.0.16248370"
    defaultConfig {
        minSdk = 36
    }
}
```

---

## Если что-то упало

Пришли последние ~30 строк вывода. vcpkg в конце пишет путь к логу, обычно вида:

```
~/vcpkg/buildtrees/<пакет>/<что-то>-arm64-android-36-rel-out.log
```

Его тоже: `tail -50 <путь к логу>`.
