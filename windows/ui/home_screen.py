import customtkinter as ctk
from tkinter import simpledialog
from utils.helpers import human_size


class HomeScreen(ctk.CTkFrame):
    def __init__(self, master, settings: dict, on_send_to_device):
        super().__init__(master, fg_color="transparent")
        self.settings         = settings
        self.on_send_to_device = on_send_to_device
        self._devices: dict   = {}   # device_id -> info
        self._status_var      = ctk.StringVar(value="Waiting for Connection")
        self._build()

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        # ── Top status bar ─────────────────────────────────────
        top = ctk.CTkFrame(self, height=44, corner_radius=0,
                           fg_color=("gray92", "gray18"))
        top.grid(row=0, column=0, sticky="ew", padx=0, pady=0)
        top.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(top, text="FlashDrop",
                     font=ctk.CTkFont(size=16, weight="bold")).grid(
            row=0, column=0, padx=16, pady=10)

        self._status_badge = ctk.CTkLabel(
            top, textvariable=self._status_var,
            fg_color=("gray80", "gray30"),
            corner_radius=8,
            font=ctk.CTkFont(size=11)
        )
        self._status_badge.grid(row=0, column=2, padx=16, pady=8)

        # ── Main content ────────────────────────────────────────
        content = ctk.CTkFrame(self, fg_color="transparent")
        content.grid(row=1, column=0, sticky="nsew", padx=24, pady=20)
        content.grid_columnconfigure(0, weight=1)
        content.grid_rowconfigure(1, weight=1)

        # Info card
        info_card = ctk.CTkFrame(content, corner_radius=12)
        info_card.grid(row=0, column=0, sticky="ew", pady=(0, 16))
        info_card.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(info_card,
                     text="Ready to Receive Files",
                     font=ctk.CTkFont(size=18, weight="bold")).grid(
            row=0, column=0, columnspan=2, padx=20, pady=(18, 4))
        ctk.CTkLabel(info_card,
                     text=f"Device: {self.settings['device_name']}",
                     text_color="gray").grid(
            row=1, column=0, columnspan=2, padx=20, pady=(0, 14))

        ctk.CTkFrame(info_card, height=1,
                     fg_color=("gray80", "gray30")).grid(
            row=2, column=0, columnspan=2, sticky="ew", padx=16)

        # Auto accept toggle
        auto_row = ctk.CTkFrame(info_card, fg_color="transparent")
        auto_row.grid(row=3, column=0, columnspan=2,
                      sticky="ew", padx=20, pady=10)
        auto_row.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(auto_row, text="Auto Accept Incoming Files").grid(
            row=0, column=0, sticky="w")
        self._auto_var = ctk.BooleanVar(
            value=self.settings.get("auto_accept", False))
        ctk.CTkSwitch(auto_row, text="", variable=self._auto_var,
                      command=self._toggle_auto).grid(row=0, column=1)

        ctk.CTkFrame(info_card, height=1,
                     fg_color=("gray80", "gray30")).grid(
            row=4, column=0, columnspan=2, sticky="ew", padx=16)

        # IP and port
        for i, (label, val) in enumerate([
            ("Local IP Address:", self._get_local_ip()),
            ("Port Number:",      str(self.settings["tcp_port"])),
        ]):
            row_f = ctk.CTkFrame(info_card, fg_color=("gray95", "gray20"),
                                 corner_radius=6)
            row_f.grid(row=5 + i, column=0, columnspan=2,
                       sticky="ew", padx=16,
                       pady=(8 if i == 0 else 4, 4 if i == 0 else 16))
            row_f.grid_columnconfigure(1, weight=1)
            ctk.CTkLabel(row_f, text=label,
                         font=ctk.CTkFont(size=12)).grid(
                row=0, column=0, padx=12, pady=8)
            val_label = ctk.CTkLabel(
                row_f, text=val,
                font=ctk.CTkFont(size=14 if i == 0 else 12, weight="bold"))
            val_label.grid(row=0, column=1, padx=12, pady=8, sticky="e")

            # Copy button for IP
            if i == 0:
                def copy_ip(v=val):
                    self.clipboard_clear()
                    self.clipboard_append(v)
                ctk.CTkButton(
                    row_f, text="Copy", width=60, height=28,
                    command=copy_ip,
                    font=ctk.CTkFont(size=11)).grid(
                    row=0, column=2, padx=(0, 8))

        ctk.CTkLabel(content,
                     text="Waiting for incoming file transfers from devices on the same network",
                     text_color="gray",
                     font=ctk.CTkFont(size=11)).grid(
            row=1, column=0, pady=(0, 8))

        # ── Device list ─────────────────────────────────────────
        dev_header = ctk.CTkFrame(content, fg_color="transparent")
        dev_header.grid(row=2, column=0, sticky="ew", pady=(16, 6))
        dev_header.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(dev_header, text="AVAILABLE DEVICES",
                     font=ctk.CTkFont(size=11),
                     text_color="gray").grid(row=0, column=0, sticky="w")

        self._device_list = ctk.CTkScrollableFrame(
            content, corner_radius=10, height=200)
        self._device_list.grid(row=3, column=0, sticky="ew")
        self._device_list.grid_columnconfigure(0, weight=1)
        self._no_devices_label = ctk.CTkLabel(
            self._device_list,
            text="Scanning for devices…",
            text_color="gray",
            font=ctk.CTkFont(size=12))
        self._no_devices_label.grid(row=0, column=0, pady=20)

        # ── Bottom buttons ──────────────────────────────────────
        btn_row = ctk.CTkFrame(content, fg_color="transparent")
        btn_row.grid(row=4, column=0, sticky="ew", pady=(16, 0))
        btn_row.grid_columnconfigure(0, weight=1)
        btn_row.grid_columnconfigure(1, weight=1)

        ctk.CTkButton(btn_row, text="Refresh",
                      command=self._refresh,
                      height=40).grid(
            row=0, column=0, sticky="ew", padx=(0, 8))
        ctk.CTkButton(btn_row, text="Enter IP Manually",
                      command=self._manual_ip,
                      height=40,
                      fg_color="transparent",
                      border_width=1,
                      text_color=("gray20", "gray80")).grid(
            row=0, column=1, sticky="ew")

    # ── Device management ────────────────────────────────────────
    def add_device(self, info: dict):
        self.after(0, lambda: self._add_device_ui(info))

    def remove_device(self, device_id: str):
        self.after(0, lambda: self._remove_device_ui(device_id))

    def set_device_status(self, device_id: str, status: str):
        self.after(0, lambda: self._update_status_ui(device_id, status))

    def _add_device_ui(self, info: dict):
        self._no_devices_label.grid_remove()
        device_id = info["id"]
        if device_id in self._devices:
            return
        row = len(self._devices)
        card = ctk.CTkFrame(self._device_list, corner_radius=8)
        card.grid(row=row, column=0, sticky="ew",
                  padx=4, pady=4)
        card.grid_columnconfigure(0, weight=1)

        name_label = ctk.CTkLabel(
            card,
            text=f"  {info['device_name']}",
            font=ctk.CTkFont(size=13, weight="bold"),
            anchor="w")
        name_label.grid(row=0, column=0, sticky="ew", padx=12, pady=(10, 2))

        meta_label = ctk.CTkLabel(
            card,
            text=f"  {info['device_type']} | {info.get('status', 'Available')}",
            text_color="gray",
            font=ctk.CTkFont(size=11),
            anchor="w")
        meta_label.grid(row=1, column=0, sticky="ew", padx=12, pady=(0, 10))

        send_btn = ctk.CTkButton(
            card, text="Send Files", width=100, height=32,
            command=lambda i=info: self.on_send_to_device(i))
        send_btn.grid(row=0, column=1, rowspan=2, padx=12, pady=8)

        self._devices[device_id] = {
            "info":       info,
            "card":       card,
            "meta_label": meta_label,
            "send_btn":   send_btn,
        }
        self._status_var.set("Connected to Android Device")

    def _remove_device_ui(self, device_id: str):
        if device_id in self._devices:
            self._devices[device_id]["card"].destroy()
            del self._devices[device_id]
        if not self._devices:
            self._no_devices_label.grid()
            self._status_var.set("Waiting for Connection")

    def _update_status_ui(self, device_id: str, status: str):
        if device_id in self._devices:
            d = self._devices[device_id]
            d["info"]["status"] = status
            d["meta_label"].configure(
                text=f"  {d['info']['device_type']} | {status}")

    def _toggle_auto(self):
        self.settings["auto_accept"] = self._auto_var.get()
        from core import config
        config.save(self.settings)

    def _refresh(self):
        pass  # Discovery auto-refreshes — button is cosmetic feedback

    def _manual_ip(self):
        ip = simpledialog.askstring("Enter IP", "Enter device IP address:",
                                    parent=self)
        if not ip:
            return
        port = self.settings["tcp_port"]
        # Inject a manual device entry
        info = {
            "id":          f"{ip}:{port}",
            "device_name": ip,
            "device_type": "Manual",
            "ip":          ip,
            "tcp_port":    port,
            "status":      "Available",
        }
        self._add_device_ui(info)

    def _get_local_ip(self) -> str:
        import socket
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return "127.0.0.1"

    def set_busy(self, busy: bool):
        status = "Busy — transfer in progress" if busy else "Waiting for Connection"
        self._status_var.set(status)
        for d in self._devices.values():
            d["send_btn"].configure(state="disabled" if busy else "normal")
