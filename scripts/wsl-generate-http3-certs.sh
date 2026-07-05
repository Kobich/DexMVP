#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERT_DIR="${CERT_DIR:-/etc/nginx/dexmvp/certs}"
WSL_IP="${WSL_IP:-$(hostname -I | awk '{print $1}')}"

ANDROID_CA_TARGET="$REPO_ROOT/app/src/debug/res/raw/dexmvp_local_ca.crt"
COPY_ANDROID_CA="${COPY_ANDROID_CA:-0}"

sudo mkdir -p "$CERT_DIR"

CA_KEY="$CERT_DIR/dexmvp-local-ca.key"
CA_CERT="$CERT_DIR/dexmvp-local-ca.crt"
SERVER_KEY="$CERT_DIR/dexmvp-nginx.key"
SERVER_CSR="$CERT_DIR/dexmvp-nginx.csr"
SERVER_CERT="$CERT_DIR/dexmvp-nginx.crt"
SERVER_CONFIG="$CERT_DIR/dexmvp-nginx-openssl.cnf"

if [ ! -f "$CA_KEY" ] || [ ! -f "$CA_CERT" ]; then
  echo "Generating local CA..."
  sudo openssl genrsa -out "$CA_KEY" 4096
  sudo openssl req -x509 -new -nodes \
    -key "$CA_KEY" \
    -sha256 \
    -days 3650 \
    -out "$CA_CERT" \
    -subj "/CN=DexMVP Local Debug CA"
else
  echo "Local CA already exists: $CA_CERT"
fi

echo "Generating server certificate for WSL IP: $WSL_IP"
sudo tee "$SERVER_CONFIG" >/dev/null <<EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req

[dn]
CN = DexMVP Local HTTP3

[v3_req]
subjectAltName = @alt_names

[alt_names]
IP.1 = $WSL_IP
IP.2 = 127.0.0.1
DNS.1 = localhost
EOF

sudo openssl genrsa -out "$SERVER_KEY" 2048
sudo openssl req -new \
  -key "$SERVER_KEY" \
  -out "$SERVER_CSR" \
  -config "$SERVER_CONFIG"

sudo openssl x509 -req \
  -in "$SERVER_CSR" \
  -CA "$CA_CERT" \
  -CAkey "$CA_KEY" \
  -CAcreateserial \
  -out "$SERVER_CERT" \
  -days 365 \
  -sha256 \
  -extensions v3_req \
  -extfile "$SERVER_CONFIG"

if [ "$COPY_ANDROID_CA" = "1" ]; then
  mkdir -p "$(dirname "$ANDROID_CA_TARGET")"
  sudo cp "$CA_CERT" "$ANDROID_CA_TARGET"
  sudo chown "$(id -u):$(id -g)" "$ANDROID_CA_TARGET"
fi

echo ""
echo "OK: certificates generated."
echo "WSL IP: $WSL_IP"
echo "NGINX ssl_certificate: $SERVER_CERT"
echo "NGINX ssl_certificate_key: $SERVER_KEY"
if [ "$COPY_ANDROID_CA" = "1" ]; then
  echo "Android debug CA: $ANDROID_CA_TARGET"
else
  echo "Android debug CA: not copied. Set COPY_ANDROID_CA=1 only for legacy debug-CA APKs."
fi
