"""
FlashDrop — Integration Tests
Tests that components work together correctly.
Run with: pytest test_integration.py -v
"""
import sys, os, socket, threading, time, json
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'windows'))

from core import config
from core.history import add, get_all, clear
from core.transfer import TransferEngine


# ── IT-01: Settings integrate with discovery ──────────────────────
def test_settings_have_required_keys():
    """Settings must contain all keys needed by discovery and transfer."""
    settings = config.load()
    required = ["device_name", "tcp_port", "udp_port", "save_folder", "auto_accept"]
    for key in required:
        assert key in settings, f"Missing key: {key}"


# ── IT-02: History integrates with transfer result ────────────────
def test_history_records_transfer():
    """Transfer result must be correctly stored and retrieved from history."""
    clear()
    add("test_file.txt", 1024, "SENT", "Android-Device", "Success")
    records = get_all()
    assert len(records) == 1
    assert records[0]["filename"] == "test_file.txt"
    assert records[0]["direction"] == "SENT"
    assert records[0]["status"] == "Success"
    assert records[0]["peer_name"] == "Android-Device"
    clear()


# ── IT-03: Multiple history entries maintain order ────────────────
def test_history_order_newest_first():
    """Most recent transfer must appear first in history."""
    clear()
    add("first.txt",  512,  "SENT",     "Device1", "Success")
    add("second.txt", 1024, "RECEIVED", "Device2", "Success")
    records = get_all()
    assert records[0]["filename"] == "second.txt"  # newest first
    assert records[1]["filename"] == "first.txt"
    clear()


# ── IT-04: TCP server starts and accepts connections ──────────────
def test_tcp_server_accepts_connection():
    """Transfer engine TCP server must bind and accept a connection."""
    settings = config.load()
    settings["tcp_port"] = 15006  # use test port

    connected = threading.Event()

    def fake_incoming(info):
        return False  # reject — we just test connection accepted

    engine = TransferEngine(
        settings,
        on_incoming_request  = fake_incoming,
        on_receive_progress  = lambda *a: None,
        on_receive_done      = lambda *a: None,
        on_receive_failed    = lambda *a: None,
        on_send_progress     = lambda *a: None,
        on_send_done         = lambda *a: None,
        on_send_failed       = lambda *a: None,
    )
    engine.start_server()
    time.sleep(0.3)

    # Try connecting to the server
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2)
        s.connect(("127.0.0.1", 15006))
        connected.set()
        s.close()
    except Exception:
        pass

    engine.stop()
    assert connected.is_set(), "TCP server did not accept connection"
