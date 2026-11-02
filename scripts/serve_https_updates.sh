#!/usr/bin/env bash
set -euo pipefail

# Serve a static HTTPS update feed locally for testing.
#
# Usage:
#   ./scripts/serve_https_updates.sh [port]
#
# Example:
#   ./scripts/serve_https_updates.sh 8443
# Then use:
#   https://localhost:8443/updates.json

PORT="${1:-8443}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERT_DIR="$ROOT_DIR/build/https"
mkdir -p "$CERT_DIR" "$ROOT_DIR/build/updates"

CERT_KEY="$CERT_DIR/server.key"
CERT_CRT="$CERT_DIR/server.crt"

if [ ! -f "$CERT_KEY" ] || [ ! -f "$CERT_CRT" ]; then
  echo "Generating self-signed certificate for localhost..."
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$CERT_KEY" \
    -out "$CERT_CRT" \
    -days 365 \
    -subj "/CN=localhost" >/dev/null 2>&1
fi

python3 - "$CERT_CRT" "$CERT_KEY" "$ROOT_DIR/build/updates" "$PORT" <<'PY'
import http.server
import ssl
import socketserver
import sys
from pathlib import Path

cert = sys.argv[1]
key = sys.argv[2]
root = Path(sys.argv[3]).resolve()
port = int(sys.argv[4])

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(root), **kwargs)
    def log_message(self, format, *args):
        print(f"[HTTPS] {self.address_string()} - {format % args}")

context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(certfile=cert, keyfile=key)

socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("0.0.0.0", port), Handler) as httpd:
    httpd.socket = context.wrap_socket(httpd.socket, server_side=True)
    print(f"Serving HTTPS updates from {root} on https://localhost:{port}")
    httpd.serve_forever()
PY
