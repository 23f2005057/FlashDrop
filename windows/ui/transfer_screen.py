import customtkinter as ctk
from utils.helpers import human_size, human_time


class TransferScreen(ctk.CTkFrame):
    def __init__(self, master, mode: str,  # "Sending" or "Receiving"
                 filename: str, filesize: int, peer_name: str,
                 on_cancel, on_pause_resume):
        super().__init__(master, fg_color="transparent")
        self.mode     = mode
        self.filename = filename
        self.filesize = filesize
        self._paused  = False
        self.on_cancel       = on_cancel
        self.on_pause_resume = on_pause_resume
        self._build(filename, filesize, peer_name)

    def _build(self, filename, filesize, peer_name):
        self.grid_columnconfigure(0, weight=1)

        # Header
        top = ctk.CTkFrame(self, height=44, corner_radius=0,
                           fg_color=("gray92", "gray18"))
        top.grid(row=0, column=0, sticky="ew")
        top.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(top, text="FlashDrop",
                     font=ctk.CTkFont(size=16, weight="bold")).grid(
            row=0, column=0, padx=16, pady=10, sticky="w")
        self._mode_label = ctk.CTkLabel(
            top, text=f"{self.mode} File",
            fg_color=("gray80", "gray30"),
            corner_radius=8,
            font=ctk.CTkFont(size=11))
        self._mode_label.grid(row=0, column=1, padx=16, pady=8)

        inner = ctk.CTkFrame(self, fg_color="transparent")
        inner.grid(row=1, column=0, sticky="nsew", padx=24, pady=20)
        inner.grid_columnconfigure(0, weight=1)

        # File info card
        card = ctk.CTkFrame(inner, corner_radius=10)
        card.grid(row=0, column=0, sticky="ew", pady=(0, 20))
        card.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(card, text=filename,
                     font=ctk.CTkFont(size=14, weight="bold"),
                     anchor="w").grid(
            row=0, column=0, sticky="ew", padx=16, pady=(14, 2))
        ctk.CTkLabel(card,
                     text=f"{human_size(filesize)}  ·  {self.mode} {'to' if self.mode == 'Sending' else 'from'} {peer_name}",
                     text_color="gray",
                     anchor="w").grid(
            row=1, column=0, sticky="ew", padx=16, pady=(0, 14))

        # Progress bar + %
        self._progress_bar = ctk.CTkProgressBar(inner, height=12)
        self._progress_bar.grid(row=1, column=0, sticky="ew", pady=(0, 8))
        self._progress_bar.set(0)

        self._pct_label = ctk.CTkLabel(
            inner, text="0%",
            font=ctk.CTkFont(size=28, weight="bold"))
        self._pct_label.grid(row=2, column=0, pady=(0, 12))

        # Speed + ETA
        info_row = ctk.CTkFrame(inner, corner_radius=8,
                                fg_color=("gray95", "gray20"))
        info_row.grid(row=3, column=0, sticky="ew", pady=(0, 16))
        info_row.grid_columnconfigure(0, weight=1)
        info_row.grid_columnconfigure(1, weight=1)

        self._speed_label = ctk.CTkLabel(
            info_row,
            text="Transfer Speed: — MB/s",
            font=ctk.CTkFont(size=12),
            anchor="w")
        self._speed_label.grid(row=0, column=0, padx=14, pady=10, sticky="w")

        self._eta_label = ctk.CTkLabel(
            info_row,
            text="Time Remaining: —",
            font=ctk.CTkFont(size=12),
            anchor="e")
        self._eta_label.grid(row=0, column=1, padx=14, pady=10, sticky="e")

        # Status label
        self._status_label = ctk.CTkLabel(
            inner,
            text=f"Status: {self.mode}",
            fg_color=("gray95", "gray20"),
            corner_radius=6,
            anchor="w")
        self._status_label.grid(row=4, column=0, sticky="ew",
                                pady=(0, 20), ipady=8, ipadx=12)

        # Buttons
        btn_row = ctk.CTkFrame(inner, fg_color="transparent")
        btn_row.grid(row=5, column=0, sticky="ew")
        btn_row.grid_columnconfigure(0, weight=1)
        btn_row.grid_columnconfigure(1, weight=1)

        self._pause_btn = ctk.CTkButton(
            btn_row, text="Pause", height=44,
            fg_color="transparent", border_width=1,
            text_color=("gray20", "gray80"),
            command=self._toggle_pause)
        self._pause_btn.grid(row=0, column=0, sticky="ew", padx=(0, 8))

        ctk.CTkButton(
            btn_row, text="Cancel", height=44,
            command=self.on_cancel).grid(
            row=0, column=1, sticky="ew")

        # Only show pause for sending
        if self.mode == "Receiving":
            self._pause_btn.grid_remove()

    def update_progress(self, pct: float, speed_mbps: float, eta_s: float):
        self.after(0, lambda: self._update_ui(pct, speed_mbps, eta_s))

    def _update_ui(self, pct: float, speed_mbps: float, eta_s: float):
        self._progress_bar.set(pct / 100)
        self._pct_label.configure(text=f"{pct:.0f}%")
        self._speed_label.configure(
            text=f"Transfer Speed: {speed_mbps:.1f} MB/s")
        self._eta_label.configure(
            text=f"Time Remaining: {human_time(eta_s)}")
        self._status_label.configure(text=f"Status: {self.mode}")

    def set_done(self):
        self.after(0, self._mark_done)

    def _mark_done(self):
        self._progress_bar.set(1)
        self._pct_label.configure(text="100%")
        self._status_label.configure(text="Status: Complete")
        self._pause_btn.configure(state="disabled")

    def _toggle_pause(self):
        self._paused = not self._paused
        self._pause_btn.configure(
            text="Resume" if self._paused else "Pause")
        self._status_label.configure(
            text="Status: Paused" if self._paused else f"Status: {self.mode}")
        self.on_pause_resume(self._paused)
