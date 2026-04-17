import socket, threading, json, time
from core import config

class Discovery:
    """
    Broadcasts this device's presence via UDP every 2 seconds.
    Listens for other devices doing the same.
    Calls on_device_found(info_dict) when a new device is seen.
    Calls on_device_lost(device_id) when a device hasn't been heard for 6s.
    """

    def __init__(self, settings: dict, on_device_found, on_device_lost):
        self.settings       = settings
        self.on_device_found = on_device_found
        self.on_device_lost  = on_device_lost
        self._running        = False
        self._devices: dict  = {}   # device_id -> {info, last_seen}
        self._lock           = threading.Lock()

    def start(self):
        self._running = True
        threading.Thread(target=self._broadcaster, daemon=True).start()
        threading.Thread(target=self._listener,    daemon=True).start()
        threading.Thread(target=self._reaper,      daemon=True).start()

    def stop(self):
        self._running = False

    def _my_ip(self) -> str:
        """Returns the IP on the same network as devices trying to connect."""
        try:
            # Try connecting to a local address to find the right interface
            # This works even without internet
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            # Use hotspot gateway range — works for most hotspots
            s.connect(("192.168.43.1", 80))
            ip = s.getsockname()[0]
            s.close()
            if not ip.startswith("127."):
                return ip
        except Exception:
            pass
        try:
            # Fallback — try general connectivity
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return "127.0.0.1"

    def _get_all_broadcast_addresses(self) -> list:
        """Get broadcast addresses for ALL active network interfaces."""
        broadcasts = []
        try:
            import socket
            for iface in socket.getaddrinfo(socket.gethostname(), None):
                ip = iface[4][0]
                if ip.startswith('127.') or ':' in ip:
                    continue
                # Calculate broadcast from IP (assume /24 for simplicity)
                parts = ip.split('.')
                if len(parts) == 4:
                    broadcasts.append(f"{parts[0]}.{parts[1]}.{parts[2]}.255")
        except Exception:
            pass
        # Always include general broadcast as fallback
        broadcasts.append('<broadcast>')
        return list(set(broadcasts))

    def _broadcaster(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        while self._running:
            try:
                payload = json.dumps({
                    "app":         "flashdrop",
                    "device_name": self.settings["device_name"],
                    "device_type": "Windows",
                    "ip":          self._my_ip(),
                    "tcp_port":    self.settings["tcp_port"],
                }).encode()
                # Broadcast on ALL interfaces
                for bcast in self._get_all_broadcast_addresses():
                    try:
                        sock.sendto(payload, (bcast, self.settings["udp_port"]))
                    except Exception:
                        pass
            except Exception:
                pass
            time.sleep(config.BROADCAST_INT)
        sock.close()

    def _listener(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.settimeout(1.0)
        try:
            sock.bind(("", self.settings["udp_port"]))
        except Exception:
            return

        my_ip = self._my_ip()
        while self._running:
            try:
                data, addr = sock.recvfrom(1024)
                info = json.loads(data.decode())
                if info.get("app") != "flashdrop":
                    continue
                if info.get("ip") == my_ip:
                    continue   # ignore own broadcast

                device_id = f"{info['ip']}:{info['tcp_port']}"
                info["id"] = device_id
                info["status"] = "Available"

                with self._lock:
                    is_new = device_id not in self._devices
                    self._devices[device_id] = {
                        "info": info,
                        "last_seen": time.time()
                    }
                if is_new:
                    self.on_device_found(info)
            except socket.timeout:
                continue
            except Exception:
                continue
        sock.close()

    def _reaper(self):
        """Remove devices not heard from in 6 seconds."""
        while self._running:
            time.sleep(2)
            now = time.time()
            with self._lock:
                lost = [
                    did for did, d in self._devices.items()
                    if now - d["last_seen"] > 6
                ]
                for did in lost:
                    del self._devices[did]
                    self.on_device_lost(did)

    def get_devices(self) -> list:
        with self._lock:
            return [d["info"] for d in self._devices.values()]
