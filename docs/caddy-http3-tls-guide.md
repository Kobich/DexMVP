# Caddy HTTP/3 + TLS для IP

Цель: поднять HTTP/3 через Caddy на Windows по IP `10.72.217.180`, без домена.

Схема:

```text
Android app
  -> libcurl HTTP/3
  -> Caddy on Windows :8443
  -> Ktor server on Windows :8080
```

## 1. Что хранится где

В Caddy:

```text
E:\caddy\certs\dexmvp-ip.crt
E:\caddy\certs\dexmvp-ip.key
```

Это server certificate и private key сервера. Их использует Caddy.

В Android app:

```text
app/src/main/res/raw/dexmvp_root_ca.crt
```

Это только public Root CA certificate. Это не private key. Он нужен приложению, чтобы libcurl/OpenSSL мог проверить сертификат Caddy.

## 2. Почему нужен Root CA

Для IP без публичного домена обычный публичный TLS-сертификат получить нельзя.

Поэтому делается своя цепочка:

```text
dexmvp-root-ca.key
  -> подписывает dexmvp-ip.crt

Caddy
  -> отдаёт dexmvp-ip.crt

Android/libcurl
  -> проверяет dexmvp-ip.crt через dexmvp_root_ca.crt
```

Важно:

```text
dexmvp-root-ca.key      нельзя класть в APK
dexmvp-ip.key           нельзя класть в APK
dexmvp_root_ca.crt      можно класть в APK
```

## 3. Генерация сертификатов на новой машине

Нужен OpenSSL из Git for Windows:

```powershell
& "C:\Program Files\Git\usr\bin\openssl.exe" version
```

Ожидаемо:

```text
OpenSSL 3.2.1 ...
```

Генерация:

```powershell
.\scripts\generate-caddy-ip-certs.ps1 -IpAddress 10.72.217.180
```

Скрипт создаёт:

```text
E:\caddy\certs\dexmvp-root-ca.crt
E:\caddy\certs\dexmvp-root-ca.key
E:\caddy\certs\dexmvp-ip.crt
E:\caddy\certs\dexmvp-ip.key
app\src\main\res\raw\dexmvp_root_ca.crt
```

## 4. Проверка сертификата

Проверить, что server certificate выписан именно на IP:

```powershell
& "C:\Program Files\Git\usr\bin\openssl.exe" x509 -in E:\caddy\certs\dexmvp-ip.crt -noout -issuer -subject -dates -ext subjectAltName
```

Должно быть:

```text
issuer=CN=DexMVP Local Root CA
subject=CN=10.72.217.180
X509v3 Subject Alternative Name:
    IP Address:10.72.217.180
```

Проверить цепочку:

```powershell
& "C:\Program Files\Git\usr\bin\openssl.exe" verify -CAfile E:\caddy\certs\dexmvp-root-ca.crt E:\caddy\certs\dexmvp-ip.crt
```

Должно быть:

```text
E:\caddy\certs\dexmvp-ip.crt: OK
```

## 5. Caddyfile

Файл:

```text
E:\caddy\Caddyfile
```

Содержимое:

```caddyfile
{
    servers {
        protocols h1 h2 h3
    }
}

:8443 {
    tls E:/caddy/certs/dexmvp-ip.crt E:/caddy/certs/dexmvp-ip.key

    reverse_proxy 127.0.0.1:8080
}
```

Запуск:

```powershell
& E:\caddy\caddy_windows_amd64.exe run --config E:\caddy\Caddyfile --adapter caddyfile
```

Перезагрузка после изменения сертификатов:

```powershell
& E:\caddy\caddy_windows_amd64.exe reload --config E:\caddy\Caddyfile --adapter caddyfile
```

## 6. Проверка Caddy

Проверить, что Ktor жив:

```powershell
curl.exe http://127.0.0.1:8080/health
```

Проверить, что Caddy проксирует:

```powershell
curl.exe -k https://10.72.217.180:8443/health
```

`-k` здесь только для ручной проверки Caddy. В приложении отключать TLS verification нельзя.

## 7. Сборка APK

Сборка HTTP/3 debug APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\scripts\build-http3-apk.ps1 -Abi x86_64 -BuildType Debug
```

Проверить, что APK содержит CA:

```powershell
$apk='app\build\outputs\apk\debug\app-debug.apk'
& "$env:JAVA_HOME\bin\jar.exe" tf $apk | Select-String -Pattern 'dexmvp_root_ca|dexmvp_local_ca|lib/x86_64/.*\.so'
```

Должно быть:

```text
res/raw/dexmvp_root_ca.crt
lib/x86_64/libnative-http3.so
```

Не должно быть:

```text
dexmvp_local_ca
```

## 8. Проверка в приложении

В приложении:

```text
Server URL: https://10.72.217.180:8443
Transport: HTTP3_ONLY
```

Дальше:

```text
Check
Download
Open
```

Если снова ошибка TLS, важны поля:

```text
curlCode
sslVerifyResult
message
caFilePath
```

Рабочий признак: `caFilePath` не пустой и указывает на файл в cache приложения.

## 9. Если IP изменился

Если IP стал другим, старый сертификат больше не подходит.

Нужно заново выполнить:

```powershell
.\scripts\generate-caddy-ip-certs.ps1 -IpAddress НОВЫЙ_IP
& E:\caddy\caddy_windows_amd64.exe reload --config E:\caddy\Caddyfile --adapter caddyfile
.\scripts\build-http3-apk.ps1 -Abi x86_64 -BuildType Debug
```

Потом переустановить APK на устройство.
