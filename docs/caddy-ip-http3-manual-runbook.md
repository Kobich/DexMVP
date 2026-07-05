# DexMVP: Caddy HTTP/3 по IP на Windows

## Назначение

Локальный тестовый контур:

~~~text
Android (libcurl HTTP/3 only)
  -> https://10.72.217.180:8443
  -> Caddy
  -> Ktor на 127.0.0.1:8080
~~~

TLS использует собственный Root CA. Его public certificate включается в APK, а private key хранится отдельно от Caddy. Для production нужен корпоративный или публично доверенный CA.

## Когда выполнять разделы

| Операция | Когда |
|---|---|
| Создание Root CA | Один раз |
| Добавление Root CA в APK | Один раз; повторить только при смене CA |
| Выпуск server certificate | Первый запуск, смена IP или истечение сертификата |
| Проверка TLS и HTTP/3 | После каждого изменения |

Команды выполнять в PowerShell. Не запускайте создание Root CA поверх существующих файлов.

## 1. Подготовить закрытую машину

Заранее перенести разрешёнными средствами:

1. Офлайн-установщик Git for Windows — он содержит OpenSSL.
2. Caddy для Windows amd64.
3. Репозиторий и prebuilt-библиотеки libcurl.
4. Android SDK, JDK и ADB, если APK собирается здесь.

Исходники OpenSSL собирать не требуется. Бинарь Caddy привести к имени E:\caddy\caddy.exe.

~~~powershell
$Ip = "10.72.217.180"
$Repo = "C:\Users\RED.DOT\AndroidStudioProjects\DexMVP"
$CaddyDir = "E:\caddy"
$CertDir = "$CaddyDir\certs"
$CaDir = "E:\dexmvp-ca"
$OpenSsl = "C:\Program Files\Git\usr\bin\openssl.exe"
$CaddyExe = "$CaddyDir\caddy.exe"

New-Item -ItemType Directory -Force -Path $CaddyDir, $CertDir | Out-Null

if (-not (Test-Path -LiteralPath $OpenSsl)) { throw "OpenSSL not found: $OpenSsl" }
if (-not (Test-Path -LiteralPath $CaddyExe)) { throw "Caddy not found: $CaddyExe" }
if (-not (Test-Path -LiteralPath $Repo)) { throw "Repository not found: $Repo" }

& $OpenSsl version
& $CaddyExe version
~~~

Переменные нужно задавать заново в новом окне PowerShell.

## 2. Создать Root CA — только один раз

Root CA key защищается паролем и хранится отдельно от runtime-файлов Caddy.

~~~powershell
New-Item -ItemType Directory -Force -Path $CaDir | Out-Null

$RootKey = "$CaDir\dexmvp-root-ca.key"
$RootCert = "$CaDir\dexmvp-root-ca.crt"
$RootConfig = "$CaDir\dexmvp-root-ca-openssl.cnf"
$RootSerial = "$CaDir\dexmvp-root-ca.srl"

if (Test-Path -LiteralPath $RootKey) { throw "Root CA already exists: $RootKey" }
if (Test-Path -LiteralPath $RootCert) { throw "Root CA already exists: $RootCert" }

$CurrentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $CaDir /inheritance:r
& icacls.exe $CaDir /grant:r ($CurrentUser + ":(OI)(CI)F") "*S-1-5-18:(OI)(CI)F"
~~~

Создать конфигурацию:

~~~powershell
@"
[req]
prompt = no
distinguished_name = dn
x509_extensions = v3_ca

[dn]
CN = DexMVP Local Root CA

[v3_ca]
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
"@ | Set-Content -LiteralPath $RootConfig -Encoding ascii
~~~

Создать зашифрованный ключ и Root CA certificate:

~~~powershell
& $OpenSsl genpkey -algorithm RSA -aes-256-cbc -pkeyopt rsa_keygen_bits:4096 -out $RootKey
if ($LASTEXITCODE -ne 0) { throw "Root CA key generation failed" }

& $OpenSsl req -x509 -new -key $RootKey -sha256 -days 3650 -out $RootCert -config $RootConfig -extensions v3_ca
if ($LASTEXITCODE -ne 0) { throw "Root CA certificate generation failed" }

& $OpenSsl x509 -in $RootCert -noout -subject -dates -ext basicConstraints,keyUsage
~~~

Сделать защищённую резервную копию каталога Root CA. Пароль хранить отдельно. Потеря ключа исключит выпуск новых сертификатов; компрометация потребует замены CA во всех APK.

## 3. Выпустить server certificate

Пути и защита от перезаписи:

~~~powershell
$ServerKey = "$CertDir\dexmvp-ip.key"
$ServerCsr = "$CertDir\dexmvp-ip.csr"
$ServerCert = "$CertDir\dexmvp-ip.crt"
$ServerConfig = "$CertDir\dexmvp-ip-openssl.cnf"

if (Test-Path -LiteralPath $ServerKey) { throw "Archive existing server files first" }
if (Test-Path -LiteralPath $ServerCsr) { throw "Archive existing server files first" }
if (Test-Path -LiteralPath $ServerCert) { throw "Archive existing server files first" }
~~~

Создать конфигурацию:

~~~powershell
@"
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req

[dn]
CN = $Ip

[v3_req]
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
IP.1 = $Ip
"@ | Set-Content -LiteralPath $ServerConfig -Encoding ascii
~~~

Создать ключ и CSR, затем подписать. OpenSSL запросит пароль Root CA:

~~~powershell
& $OpenSsl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $ServerKey
if ($LASTEXITCODE -ne 0) { throw "Server key generation failed" }

& $OpenSsl pkey -in $ServerKey -check -noout
& $OpenSsl req -new -key $ServerKey -out $ServerCsr -config $ServerConfig
& $OpenSsl req -in $ServerCsr -verify -noout
if ($LASTEXITCODE -ne 0) { throw "CSR generation or validation failed" }

& $OpenSsl x509 -req -in $ServerCsr -CA $RootCert -CAkey $RootKey -CAserial $RootSerial -CAcreateserial -out $ServerCert -days 365 -sha256 -extensions v3_req -extfile $ServerConfig
if ($LASTEXITCODE -ne 0) { throw "Certificate signing failed" }
~~~

Serial-файл не удалять: это состояние CA.

Проверить сертификат:

~~~powershell
& $OpenSsl x509 -in $ServerCert -noout -issuer -subject -dates -ext subjectAltName -ext extendedKeyUsage
& $OpenSsl verify -CAfile $RootCert $ServerCert
~~~

Ожидаются IP в SAN и результат OK. Ограничить чтение server key:

~~~powershell
& icacls.exe $ServerKey /inheritance:r
& icacls.exe $ServerKey /grant:r ($CurrentUser + ":F") "*S-1-5-18:F"
~~~

Если Caddy работает от служебной учётной записи, ей отдельно выдать чтение server key. Доступ к Root CA key ей не нужен.

## 4. Добавить Root CA в Android

~~~powershell
$AndroidRawDir = "$Repo\app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $AndroidRawDir | Out-Null
Copy-Item -Force -LiteralPath $RootCert -Destination "$AndroidRawDir\dexmvp_root_ca.crt"
~~~

В APK разрешён только dexmvp-root-ca.crt. Файлы dexmvp-root-ca.key и dexmvp-ip.key туда попадать не должны.

## 5. Настроить Caddy

Caddy по умолчанию включает HTTP/1.1, HTTP/2 и HTTP/3. Дополнительный блок protocols не нужен.

Сделать резервную копию Caddyfile и создать новый:

~~~powershell
$Caddyfile = "$CaddyDir\Caddyfile"
if (Test-Path -LiteralPath $Caddyfile) {
    Copy-Item -LiteralPath $Caddyfile -Destination "$Caddyfile.$(Get-Date -Format yyyyMMdd-HHmmss).bak"
}

$CertPath = $ServerCert -replace "\\", "/"
$KeyPath = $ServerKey -replace "\\", "/"

@"
https://$($Ip):8443 {
    tls $CertPath $KeyPath
    reverse_proxy 127.0.0.1:8080
    log
}
"@ | Set-Content -LiteralPath $Caddyfile -Encoding ascii

& $CaddyExe validate --config $Caddyfile --adapter caddyfile
if ($LASTEXITCODE -ne 0) { throw "Invalid Caddy configuration" }
~~~

## 6. Открыть firewall

HTTP/3 использует UDP, а HTTP/1.1 и HTTP/2 — TCP. Этот раздел выполнять в PowerShell от администратора:

~~~powershell
if (-not (Get-NetFirewallRule -DisplayName "DexMVP Caddy 8443 TCP" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName "DexMVP Caddy 8443 TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443
}

if (-not (Get-NetFirewallRule -DisplayName "DexMVP Caddy 8443 UDP" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName "DexMVP Caddy 8443 UDP" -Direction Inbound -Action Allow -Protocol UDP -LocalPort 8443
}
~~~

## 7. Запустить и проверить сервер

~~~powershell
curl.exe http://127.0.0.1:8080/health
& $CaddyExe run --config "$CaddyDir\Caddyfile" --adapter caddyfile
~~~

Caddy запускать в отдельном PowerShell после задания переменных из раздела 1.

Проверить порты и TLS:

~~~powershell
Get-NetTCPConnection -LocalPort 8443 -State Listen
Get-NetUDPEndpoint -LocalPort 8443
& $OpenSsl s_client -connect "$($Ip):8443" -verify_ip $Ip -CAfile $RootCert -brief
~~~

Ожидаемый результат TLS: Verification: OK.

## 8. Собрать и проверить APK

~~~powershell
Set-Location $Repo
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$CurlRoot = (Resolve-Path "$Repo\third_party\curl-android").Path
$Abi = "arm64-v8a"

if (-not ((Test-Path "$CurlRoot\libs\$Abi\libcurl.so") -or (Test-Path "$CurlRoot\libs\$Abi\libcurl.a"))) {
    throw "libcurl not found for ABI $Abi"
}

.\gradlew.bat :app:assembleDebug "-PnativeHttp3.enableCmake=true" "-PnativeHttp3.enableCurl=true" "-PnativeHttp3.curlRootDir=$CurlRoot" "-PnativeHttp3.abis=$Abi"
if ($LASTEXITCODE -ne 0) { throw "APK build failed" }
~~~

Для эмулятора использовать ABI x86_64.

~~~powershell
$Apk = "$Repo\app\build\outputs\apk\debug\app-debug.apk"
& "$env:JAVA_HOME\bin\jar.exe" tf $Apk | Select-String -Pattern "dexmvp_root_ca|lib/$Abi/.*\.so"
adb devices
adb install -r $Apk
adb shell monkey -p com.engboost.dexmvp 1
~~~

В приложении задать:

~~~text
Server URL = https://10.72.217.180:8443
Transport = HTTP3_ONLY
~~~

Выполнить Check, Download и Open. Успех в HTTP3_ONLY подтверждает HTTP/3: native-клиент использует CURL_HTTP_VERSION_3ONLY. Рабочие признаки TLS: sslVerifyResult равен 0, caFilePath не пустой. В access log Caddy должен быть протокол HTTP/3.

## 9. Смена IP или обновление server certificate

Root CA не пересоздавать. APK не пересобирать, если Root CA и код не изменились.

1. Остановить Caddy.
2. Архивировать текущие server-файлы:

~~~powershell
$ArchiveDir = "$CertDir\archive\$(Get-Date -Format yyyyMMdd-HHmmss)"
New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null
Get-Item -LiteralPath $ServerKey, $ServerCsr, $ServerCert -ErrorAction SilentlyContinue | Move-Item -Destination $ArchiveDir
~~~

3. Задать новый Ip в переменной.
4. Повторить разделы 3 и 5.
5. Запустить Caddy и повторить раздел 7.
6. Изменить Server URL в приложении.

Если меняется Root CA, повторить разделы 2–8 и переустановить APK.

## 10. Диагностика

| Симптом | Что проверить |
|---|---|
| OpenSSL not found | Git for Windows установлен; путь к openssl.exe правильный |
| Caddy not found | Бинарь лежит в E:\caddy\caddy.exe |
| Ключ не соответствует сертификату | Server key и certificate выпущены одной операцией |
| certificate verify failed | IP есть в SAN; выбран правильный Root CA; системное время корректно |
| TCP работает, HTTP/3 нет | UDP 8443 разрешён в Windows Firewall и сети |
| Caddy возвращает 502 | Ktor отвечает на http://127.0.0.1:8080/health |
| В APK нет CA | Проверить app/src/main/res/raw/dexmvp_root_ca.crt |
| HTTP3_ONLY не работает | Проверить ABI, native-библиотеку, UDP и curlCode |

## 11. Артефакты и секреты

| Файл | Назначение | Распространять |
|---|---|---|
| dexmvp-root-ca.key | Root CA private key | Нет |
| dexmvp-root-ca.crt | Root CA public certificate | Да |
| dexmvp-root-ca.srl | Состояние CA | Нет; хранить с CA |
| dexmvp-ip.key | Server private key | Нет |
| dexmvp-ip.csr | Запрос на сертификат | Да |
| dexmvp-ip.crt | Server certificate | Да |

## Справка

- Caddy TLS: https://caddyserver.com/docs/caddyfile/directives/tls
- Caddy HTTP protocols: https://caddyserver.com/docs/json/apps/http/servers/protocols
- Caddy command line: https://caddyserver.com/docs/command-line
