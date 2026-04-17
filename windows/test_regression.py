"""
FlashDrop — Regression Tests
Tests that previously fixed bugs have not reappeared.
Run with: pytest test_regression.py -v
"""
import sys, os, json, tempfile
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'windows'))

from core import config
from core.history import add, get_all, clear
from utils.helpers import human_size, human_time


# ── RT-01: Settings load without crashing (was crashing before) ───
def test_settings_load_no_crash():
    """Regression: settings must load cleanly even on first run."""
    try:
        settings = config.load()
        assert isinstance(settings, dict)
    except Exception as e:
        assert False, f"Settings crashed on load: {e}"


# ── RT-02: Settings have correct default values ───────────────────
def test_settings_default_tcp_port():
    """Regression: default TCP port must be 5006."""
    settings = config.DEFAULTS
    assert settings["tcp_port"] == 5006


def test_settings_default_udp_port():
    """Regression: default UDP port must be 5005."""
    settings = config.DEFAULTS
    assert settings["udp_port"] == 5005


# ── RT-03: History clear works correctly ──────────────────────────
def test_history_clear():
    """Regression: history clear must remove all entries."""
    add("file1.txt", 100, "SENT", "Device", "Success")
    add("file2.txt", 200, "SENT", "Device", "Success")
    clear()
    assert get_all() == []


# ── RT-04: human_size never crashes on any input ─────────────────
def test_human_size_zero():
    """Regression: human_size must handle 0 bytes."""
    assert human_size(0) == "0.0 B"


def test_human_size_large():
    """Regression: human_size must handle GB values."""
    result = human_size(1073741824)  # 1 GB
    assert "GB" in result


def test_human_size_kb():
    """Regression: 1024 bytes must show as 1.0 KB."""
    assert human_size(1024) == "1.0 KB"


def test_human_size_mb():
    """Regression: 1MB must show correctly."""
    assert human_size(1048576) == "1.0 MB"


# ── RT-05: human_time handles edge cases ─────────────────────────
def test_human_time_seconds():
    """Regression: time under 60s must show in seconds."""
    assert human_time(45) == "45s"


def test_human_time_minutes():
    """Regression: time over 60s must show minutes."""
    assert "m" in human_time(90)


def test_human_time_zero():
    """Regression: zero seconds must not crash."""
    result = human_time(0)
    assert isinstance(result, str)


# ── RT-06: History direction values are correct ───────────────────
def test_history_direction_values():
    """Regression: direction must be SENT or RECEIVED only."""
    clear()
    add("f1.txt", 100, "SENT",     "Dev", "Success")
    add("f2.txt", 200, "RECEIVED", "Dev", "Failed")
    records = get_all()
    directions = [r["direction"] for r in records]
    assert all(d in ["SENT", "RECEIVED"] for d in directions)
    clear()
