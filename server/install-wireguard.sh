#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash server/install-wireguard.sh" >&2
  exit 1
fi

WG_DIR="/etc/wireguard"
WG_PORT="${WG_PORT:-51820}"
WG_NET="10.8.0.0/24"
SERVER_ADDR="10.8.0.1/24"
CLIENT_ADDR="10.8.0.2/32"
DNS_SERVER="${DNS_SERVER:-1.1.1.1}"
PUBLIC_IFACE="${PUBLIC_IFACE:-$(ip route show default | awk '/default/ {print $5; exit}')}"

if [[ -z "${PUBLIC_IFACE}" ]]; then
  echo "Could not detect the public network interface. Set PUBLIC_IFACE and run again." >&2
  exit 1
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard iptables curl

install -d -m 700 "${WG_DIR}"
umask 077

if [[ ! -f "${WG_DIR}/server-private.key" ]]; then
  wg genkey | tee "${WG_DIR}/server-private.key" | wg pubkey > "${WG_DIR}/server-public.key"
fi

if [[ ! -f "${WG_DIR}/client-private.key" ]]; then
  wg genkey | tee "${WG_DIR}/client-private.key" | wg pubkey > "${WG_DIR}/client-public.key"
fi

SERVER_PRIVATE_KEY="$(cat "${WG_DIR}/server-private.key")"
SERVER_PUBLIC_KEY="$(cat "${WG_DIR}/server-public.key")"
CLIENT_PRIVATE_KEY="$(cat "${WG_DIR}/client-private.key")"
CLIENT_PUBLIC_KEY="$(cat "${WG_DIR}/client-public.key")"

echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-bangla-vpn.conf
sysctl --system >/dev/null

cat > "${WG_DIR}/wg0.conf" <<EOF
[Interface]
Address = ${SERVER_ADDR}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIVATE_KEY}
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -A FORWARD -o wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${PUBLIC_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -D FORWARD -o wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${PUBLIC_IFACE} -j MASQUERADE

[Peer]
PublicKey = ${CLIENT_PUBLIC_KEY}
AllowedIPs = ${CLIENT_ADDR}
EOF

chmod 600 "${WG_DIR}/wg0.conf" "${WG_DIR}"/*.key
systemctl enable --now wg-quick@wg0

PUBLIC_IP="$(curl -4 -fsS --max-time 8 https://api.ipify.org || true)"
if [[ -z "${PUBLIC_IP}" ]]; then
  PUBLIC_IP="<YOUR_BANGLADESH_VPS_PUBLIC_IP>"
fi

cat > "/root/bangla-vpn-client.conf" <<EOF
[Interface]
PrivateKey = ${CLIENT_PRIVATE_KEY}
Address = ${CLIENT_ADDR}
DNS = ${DNS_SERVER}

[Peer]
PublicKey = ${SERVER_PUBLIC_KEY}
Endpoint = ${PUBLIC_IP}:${WG_PORT}
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
EOF

chmod 600 /root/bangla-vpn-client.conf

echo
echo "WireGuard server is running on UDP ${WG_PORT}."
echo "Public interface: ${PUBLIC_IFACE}"
echo "Server public key: ${SERVER_PUBLIC_KEY}"
echo "Client config: /root/bangla-vpn-client.conf"
echo
echo "IMPORTANT:"
echo "- The VPS public IP must genuinely be hosted/geolocated in Bangladesh for Bangladesh egress."
echo "- Open UDP ${WG_PORT} in the VPS provider firewall/security group."
echo "- Copy the client config securely, then remove the client private key from the server if you do not need it there."
echo "- This script creates a standard authorized-use VPN and does not override third-party service access, KYC, or anti-fraud policies."
