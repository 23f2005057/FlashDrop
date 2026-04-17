import json, os
from datetime import datetime

HISTORY_FILE = os.path.join(os.path.expanduser("~"), ".flashdrop_history.json")

def _load() -> list:
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE) as f:
                return json.load(f)
        except Exception:
            pass
    return []

def _save(entries: list):
    with open(HISTORY_FILE, "w") as f:
        json.dump(entries, f, indent=2)

def add(filename: str, filesize: int, direction: str, peer_name: str, status: str):
    entries = _load()
    entries.insert(0, {
        "filename":  filename,
        "filesize":  filesize,
        "direction": direction,     # "SENT" or "RECEIVED"
        "peer_name": peer_name,
        "status":    status,        # "Success" / "Failed" / "Cancelled"
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M"),
    })
    _save(entries)

def get_all() -> list:
    return _load()

def clear():
    _save([])
