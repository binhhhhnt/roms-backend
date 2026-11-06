#!/usr/bin/env bash
# One-time EC2 (Ubuntu 22.04/24.04) bootstrap: Docker Engine + compose plugin + 2GB swap.
# Usage: sudo bash setup-vps.sh
set -euo pipefail

# --- Docker ---
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
TARGET_USER="${SUDO_USER:-ubuntu}"
usermod -aG docker "$TARGET_USER" || true
systemctl enable --now docker

# --- Swap 2GB (t3.small only has 2GB RAM) ---
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
sysctl -w vm.swappiness=10
grep -q 'vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf

# --- Timezone ---
timedatectl set-timezone Asia/Ho_Chi_Minh || true

# --- App directory ---
mkdir -p "/home/$TARGET_USER/roms/certbot/conf" "/home/$TARGET_USER/roms/certbot/www"
chown -R "$TARGET_USER:$TARGET_USER" "/home/$TARGET_USER/roms"

echo "Done. Log out and back in so '$TARGET_USER' can run docker without sudo."

