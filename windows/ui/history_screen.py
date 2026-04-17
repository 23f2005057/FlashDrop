import customtkinter as ctk
from core import history
from utils.helpers import human_size


class HistoryScreen(ctk.CTkFrame):
    def __init__(self, master):
        super().__init__(master, fg_color="transparent")
        self._tab = "Sent"
        self._build()

    def _build(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(2, weight=1)

        ctk.CTkLabel(self, text="Transfer History",
                     font=ctk.CTkFont(size=18, weight="bold")).grid(
            row=0, column=0, sticky="w", padx=4, pady=(0, 12))

        # Tabs
        tab_row = ctk.CTkFrame(self, fg_color="transparent")
        tab_row.grid(row=1, column=0, sticky="ew", pady=(0, 10))

        self._sent_btn = ctk.CTkButton(
            tab_row, text="Sent", width=100, height=32,
            command=lambda: self._show_tab("Sent"))
        self._sent_btn.pack(side="left", padx=(0, 8))

        self._recv_btn = ctk.CTkButton(
            tab_row, text="Received", width=100, height=32,
            fg_color="transparent", border_width=1,
            text_color=("gray20", "gray80"),
            command=lambda: self._show_tab("Received"))
        self._recv_btn.pack(side="left")

        self._list = ctk.CTkScrollableFrame(self, corner_radius=10)
        self._list.grid(row=2, column=0, sticky="nsew")
        self._list.grid_columnconfigure(0, weight=1)

        ctk.CTkButton(self, text="Clear History", height=40,
                      fg_color="transparent", border_width=1,
                      text_color=("gray20", "gray80"),
                      command=self._clear).grid(
            row=3, column=0, sticky="ew", pady=(12, 0))

        self._show_tab("Sent")

    def _show_tab(self, tab: str):
        self._tab = tab
        for btn, t in [(self._sent_btn, "Sent"),
                       (self._recv_btn, "Received")]:
            btn.configure(
                fg_color=("gray20", "gray80") if t == tab else "transparent",
                text_color=("white", "white") if t == tab else ("gray20", "gray80"),
                border_width=0 if t == tab else 1)
        self._refresh_list()

    def _refresh_list(self):
        for w in self._list.winfo_children():
            w.destroy()

        entries = [e for e in history.get_all()
                   if e["direction"] == self._tab.upper()]

        if not entries:
            ctk.CTkLabel(self._list,
                         text=f"No {self._tab.lower()} files yet",
                         text_color="gray").grid(pady=30)
            return

        for i, e in enumerate(entries):
            row = ctk.CTkFrame(self._list, fg_color="transparent")
            row.grid(row=i, column=0, sticky="ew", pady=2)
            row.grid_columnconfigure(0, weight=1)

            ctk.CTkFrame(self._list, height=1,
                         fg_color=("gray85", "gray25")).grid(
                row=i, column=0, sticky="ew")

            inner = ctk.CTkFrame(self._list, fg_color="transparent")
            inner.grid(row=i, column=0, sticky="ew", pady=6)
            inner.grid_columnconfigure(0, weight=1)

            ctk.CTkLabel(inner,
                         text=e["filename"],
                         font=ctk.CTkFont(size=13, weight="bold"),
                         anchor="w").grid(row=0, column=0, sticky="ew")
            ctk.CTkLabel(inner,
                         text=f"{human_size(e['filesize'])}  ·  {e['timestamp']}  ·  {e['peer_name']}",
                         text_color="gray",
                         font=ctk.CTkFont(size=11),
                         anchor="w").grid(row=1, column=0, sticky="ew")

            color = {"Success": "green",
                     "Failed":  "#e05555",
                     "Cancelled": "gray"}.get(e["status"], "gray")
            ctk.CTkLabel(inner,
                         text=e["status"],
                         text_color=color,
                         font=ctk.CTkFont(size=11)).grid(
                row=0, column=1, padx=8)

    def refresh(self):
        self._refresh_list()

    def _clear(self):
        history.clear()
        self._refresh_list()
