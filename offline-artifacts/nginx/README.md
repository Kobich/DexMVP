# NGINX Offline Artifacts

Эта папка содержит переносимые части текущего NGINX HTTP/3 стенда.

## Что Лежит Здесь

```text
config/nginx.conf
config/dexmvp-http3.conf
certs-public/dexmvp-local-ca.crt
certs-public/dexmvp-nginx.crt
```

Приватные ключи сюда не копируются:

```text
dexmvp-local-ca.key
dexmvp-nginx.key
```

## Как Использовать

Если используется готовый WSL export из `offline-artifacts/wsl`, эти config files обычно уже внутри WSL.

Если NGINX ставится отдельно, можно восстановить config вручную:

```bash
sudo cp config/nginx.conf /etc/nginx/nginx.conf
sudo cp config/dexmvp-http3.conf /etc/nginx/conf.d/dexmvp-http3.conf
sudo nginx -t
sudo nginx -s reload
```

Certificates на новой машине лучше перевыпустить под новый IP:

```bash
bash scripts/wsl-generate-http3-certs.sh CURRENT_SERVER_IP
```

`certs-public` нужен для диагностики и сверки текущего стенда, не для production trust.
