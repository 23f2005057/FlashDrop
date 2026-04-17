import json, os, socket

SETTINGS_FILE = os.path.join(os.path.expanduser("~"), ".flashdrop_settings.json")

UDP_PORT      = 5005
TCP_PORT      = 5006
BROADCAST_INT = 2        # seconds between UDP broadcasts
CHUNK_SIZE    = 5 * 1024 * 1024   # 5 MB

DEFAULTS = {
    "device_name":   socket.gethostname(),
    "save_folder":   os.path.join(os.path.expanduser("~"), "Downloads", "FlashDrop"),
    "auto_accept":   False,
    "udp_port":      UDP_PORT,
    "tcp_port":      TCP_PORT,
}

def load():
    if os.path.exists(SETTINGS_FILE):
        try:
            with open(SETTINGS_FILE) as f:
                data = json.load(f)
                return {**DEFAULTS, **data}
        except Exception:
            pass
    return dict(DEFAULTS)

def save(settings: dict):
    with open(SETTINGS_FILE, "w") as f:
        json.dump(settings, f, indent=2)
