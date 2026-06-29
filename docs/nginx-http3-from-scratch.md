# NGINX HTTP/3 From Scratch

Стартовая инструкция по развёртыванию: `docs/deployment-guide.md`.

Этот файл нужен, если нельзя принести готовый WSL/Linux image с NGINX HTTP/3 и нужно собрать/поставить NGINX на новой машине.

## Короткий Ответ

С нуля можно, но есть условие:

- если есть интернет на машине — ставим NGINX mainline/package с HTTP/3 и настраиваем config;
- если интернета нет — заранее переносим все `.deb`/packages и зависимости или локальный package mirror;
- если нельзя ни интернет, ни packages, ни готовый image — с нуля не получится.

Самый надёжный offline-вариант остаётся: подготовить WSL/Linux image заранее и перенести его целиком.

## Что Нужно Для Сборки/Установки С Нуля

На Linux/WSL должны быть:

- Linux distribution, например Ubuntu;
- NGINX версии `1.25.0+` с `ngx_http_v3_module`;
- OpenSSL;
- права на установку packages;
- UDP/TCP порт `8443` открыт для входящих подключений;
- config `dexmvp-http3.conf`;
- server certificate с IP в SAN.

Проверка, что NGINX умеет HTTP/3:

```bash
nginx -V 2>&1 | grep -o -- '--with-http_v3_module'
```

Ожидаемо:

```text
--with-http_v3_module
```

## Вариант A: Машина С Интернетом

На машине с интернетом проще поставить NGINX mainline package, где есть HTTP/3 support.

После установки проверить:

```bash
nginx -v
nginx -V 2>&1 | grep -o -- '--with-http_v3_module'
```

Если `--with-http_v3_module` не выводится, этот NGINX не подходит.

Дальше настроить config:

```bash
sudo mkdir -p /etc/nginx/dexmvp/certs
sudo cp dexmvp-http3.conf /etc/nginx/conf.d/dexmvp-http3.conf
sudo nginx -t
sudo nginx -s reload
```

Certificate лучше генерировать уже на целевой машине под её IP:

```bash
bash scripts/wsl-generate-http3-certs.sh CURRENT_SERVER_IP
```

## Вариант B: Закрытая Машина Без Интернета

Нужно заранее скачать на открытой машине:

```text
NGINX package с HTTP/3 module
все package dependencies
OpenSSL package
Ubuntu/WSL base image или Linux installer
```

Потом перенести в закрытый контур и установить локально.

Проблема этого варианта: у `.deb` packages могут быть зависимости. Если не принести все зависимости, установка остановится.

Практически лучше:

```text
не собирать NGINX в закрытом контуре
а принести готовый WSL/Linux image
```

## Вариант C: Сборка NGINX Из Исходников

Это самый тяжёлый путь для закрытой инфраструктуры.

Понадобится заранее принести:

- исходники NGINX;
- compiler toolchain;
- make/cmake/build tools;
- TLS library с QUIC support;
- zlib;
- pcre/pcre2;
- все transitive dependencies;
- patches/configure flags, если используются.

После сборки всё равно проверить:

```bash
nginx -V 2>&1 | grep -o -- '--with-http_v3_module'
```

Если проверки нет — не продолжать Android/HTTP3 тесты.

## Минимальный Config Для Нашего Проекта

Пример для Linux-only машины, где Ktor слушает `127.0.0.1:8080`:

```nginx
server {
    listen 8443 ssl;
    listen 8443 quic reuseport;

    http2 on;
    http3 on;

    ssl_certificate /etc/nginx/dexmvp/certs/dexmvp-nginx.crt;
    ssl_certificate_key /etc/nginx/dexmvp/certs/dexmvp-nginx.key;
    ssl_protocols TLSv1.3;

    add_header Alt-Svc 'h3=":8443"; ma=86400' always;
    add_header X-DexMvp-Http3 $http3 always;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

Для Windows + WSL заменить upstream:

```nginx
proxy_pass http://WINDOWS_GATEWAY_IP:8080;
```

`WINDOWS_GATEWAY_IP` смотреть внутри WSL:

```bash
ip route | grep default
```

## Проверка

Проверить config:

```bash
sudo nginx -t
sudo nginx -s reload
```

Проверить обычный HTTPS/proxy:

```bash
curl -k https://127.0.0.1:8443/health
curl -k https://127.0.0.1:8443/api/v1/modules/active
```

Проверить настоящий HTTP/3:

```text
Android app
Transport mode = HTTP/3 only
Check -> Download -> Open
```

В response/diagnostics должен быть marker:

```text
X-DexMvp-Http3: h3
```

Если marker пустой, запрос пришёл не через HTTP/3.

## Вывод

Для рабочей закрытой инфраструктуры лучший порядок такой:

1. Снаружи подготовить NGINX HTTP/3 на Linux/WSL.
2. Проверить `--with-http_v3_module`.
3. Проверить Android `HTTP3_ONLY`.
4. Перенести готовый image/package set.

Сборка NGINX HTTP/3 прямо внутри закрытой сети — запасной вариант, не основной.
