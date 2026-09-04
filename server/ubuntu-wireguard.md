# Ubuntu WireGuard server (authorized use)

This guide provisions a standard WireGuard server on a VPS you control. If you need a Bangladesh egress IP, choose a VPS whose public IP is actually geolocated in Bangladesh and confirm that using the service complies with the VPS provider and any third-party service terms.

## 1. Install WireGuard

```bash
sudo apt update
sudo apt install -y wireguard
```

## 2. Create server keys

Do this on the server. Never commit the private key to GitHub.

```bash
sudo -i
umask 077
wg genkey | tee /etc/wireguard/server-private.key | wg pubkey > /etc/wireguard/server-public.key
cat /etc/wireguard/server-public.key
```

Generate a client key pair on the client or another trusted machine:

```bash
umask 077
wg genkey | tee client-private.key | wg pubkey > client-public.key
```

## 3. Enable IPv4 forwarding

```bash
echo 'net.ipv4.ip_forward=1' | sudo tee /etc/sysctl.d/99-wireguard-forward.conf
sudo sysctl --system
```

## 4. Create `/etc/wireguard/wg0.conf`

Replace `eth0` with the VPS public network interface if it has another name.

```ini
[Interface]
Address = 10.8.0.1/24
ListenPort = 51820
PrivateKey = <SERVER_PRIVATE_KEY>
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -A FORWARD -o wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -D FORWARD -o wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE

[Peer]
PublicKey = <CLIENT_PUBLIC_KEY>
AllowedIPs = 10.8.0.2/32
```

Then lock down permissions and start it:

```bash
sudo chmod 600 /etc/wireguard/wg0.conf /etc/wireguard/server-private.key
sudo systemctl enable --now wg-quick@wg0
sudo wg show
```

Open UDP port `51820` in the VPS firewall/security group.

## 5. Values to enter in the Android app

- Client private key: contents of `client-private.key`
- Client address: `10.8.0.2/32`
- DNS: a resolver you trust, for example `1.1.1.1`
- Server public key: contents of `/etc/wireguard/server-public.key`
- Server endpoint: `<VPS_PUBLIC_IP>:51820`
- Allowed IPs: `0.0.0.0/0, ::/0` for a full tunnel

For a production deployment, add per-user peers, key rotation, revocation, rate limiting, monitoring, IPv6 handling, and a secure enrollment API instead of manually sharing keys.
