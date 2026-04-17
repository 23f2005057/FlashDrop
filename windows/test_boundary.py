"""
FlashDrop — Boundary & Mutation Tests
Tests edge cases and deliberately wrong inputs.
Run with: pytest test_boundary.py -v
"""
import sys, os, socket, threading, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'windows'))

from core import config
from core.history import add, get_all, clear
from utils.helpers import human_size, human_time


# ── BT-01: Boundary — file size extremes ─────────────────────────
def test_human_size_1_byte():
    """Boundary: smallest possible file size."""
    result = human_size(1)
    assert result == "1.0 B"


def test_human_size_max_tb():
    """Boundary: very large file size must not crash."""
    result = human_size(1099511627776)  # 1 TB
    assert "TB" in result


# ── BT-02: Boundary — port number limits ─────────────────────────
def test_port_is_valid_range():
    """Boundary: default ports must be in valid TCP/UDP range."""
    settings = config.load()
    assert 1024 <= settings["tcp_port"] <= 65535
    assert 1024 <= settings["udp_port"] <= 65535


# ── BT-03: Boundary — empty history ──────────────────────────────
def test_empty_history_returns_list():
    """Boundary: empty history must return empty list not None."""
    clear()
    result = get_all()
    assert result is not None
    assert isinstance(result, list)
    assert len(result) == 0


# ── BT-04: Boundary — long filename ──────────────────────────────
def test_history_long_filename():
    """Boundary: very long filename must be stored correctly."""
    clear()
    long_name = "a" * 255 + ".txt"
    add(long_name, 1024, "SENT", "Device", "Success")
    records = get_all()
    assert records[0]["filename"] == long_name
    clear()


# ── BT-05: Mutation — wrong port connection refused ───────────────
def test_connection_refused_on_wrong_port():
    """Mutation: connecting to wrong port must raise error not crash."""
    result = False
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1)
        s.connect(("127.0.0.1", 19999))  # nothing running here
        s.close()
    except (ConnectionRefusedError, OSError, TimeoutError):
        result = True  # expected — connection should be refused
    assert result, "Should have raised connection error on wrong port"


# ── BT-06: Mutation — invalid history status ─────────────────────
def test_history_accepts_failed_status():
    """Mutation: Failed status must be stored exactly as given."""
    clear()
    add("broken.txt", 0, "SENT", "Device", "Failed")
    records = get_all()
    assert records[0]["status"] == "Failed"
    clear()


def test_history_accepts_cancelled_status():
    """Mutation: Cancelled status must be stored exactly as given."""
    clear()
    add("cancelled.txt", 0, "SENT", "Device", "Cancelled")
    records = get_all()
    assert records[0]["status"] == "Cancelled"
    clear()


# ── BT-07: Boundary — zero time remaining ────────────────────────
def test_human_time_large_value():
    """Boundary: large time value must show hours format."""
    result = human_time(7200)  # 2 hours
    assert "h" in result


# ── BT-08: Mutation — settings save and reload ───────────────────
def test_settings_save_and_reload():
    """Mutation: saved settings must survive reload."""
    settings = config.load()
    original_name = settings["device_name"]

    settings["device_name"] = "MutationTestDevice"
    config.save(settings)

    reloaded = config.load()
    assert reloaded["device_name"] == "MutationTestDevice"

    # Restore original
    settings["device_name"] = original_name
    config.save(settings)
