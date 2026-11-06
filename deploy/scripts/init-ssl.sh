#!/usr/bin/env bash
# Issue the first Let's Encrypt certificate (run once, BEFORE starting nginx).
# DOMAIN in .env can be a domain name OR a public IPv4 (Elastic IP).
#   - Domain: requires DNS A record -> Elastic IP
#   - IP:     Let's Encrypt short-lived (~6 days) IP certificate, auto-renewed by the certbot service
# Port 80 must be open in the Security Group.
# Usage (from ~/roms): bash scripts/init-ssl.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DOMAIN="$(grep -E '^DOMAIN=' .env | cut -d= -f2-)"
CERTBOT_EMAIL="$(grep -E '^CERTBOT_EMAIL=' .env | cut -d= -f2-)"
: "${DOMAIN:?DOMAIN missing in .env}"
: "${CERTBOT_EMAIL:?CERTBOT_EMAIL missing in .env}"

mkdir -p certbot/conf certbot/www

if [ -d "certbot/conf/live/$DOMAIN" ]; then
  echo "Certificate for $DOMAIN already exists. Skipping."
  exit 0
fi

if [[ "$DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  IDENTIFIER_ARGS=(--ip-address "$DOMAIN" --preferred-profile shortlived)
else
  IDENTIFIER_ARGS=(-d "$DOMAIN")
fi

docker compose stop nginx 2>/dev/null || true

docker pull certbot/certbot:latest
docker run --rm -p 80:80 \
  -v "$PWD/certbot/conf:/etc/letsencrypt" \
  -v "$PWD/certbot/www:/var/www/certbot" \
  certbot/certbot:latest certonly --standalone \
  "${IDENTIFIER_ARGS[@]}" --email "$CERTBOT_EMAIL" \
  --agree-tos --no-eff-email --non-interactive

echo "Certificate issued for $DOMAIN. Now run: docker compose up -d"
