import customtkinter as ctk
from tkinter import filedialog
from core import config


class SettingsScreen(ctk.CTkFrame):
    def __init__(self, master, settings: dict, on_save):
        super().__init__(master, fg_color="transparent")
        self.settings = settings
        self.on_save  = on_save
        self._build()

    def _build(self):
        self.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(self, text="Settings",
                     font=ctk.CTkFont(size=18, weight="bold")).grid(
            row=0, column=0, sticky="w", padx=4, pady=(0, 16))

        card = ctk.CTkFrame(self, corner_radius=12)
        card.grid(row=1, column=0, sticky="ew")
        card.grid_columnconfigure(0, weight=1)

        row = 0

        def section(label):
            nonlocal row
            ctk.CTkLabel(card, text=label,
                         font=ctk.CTkFont(size=11),
                         text_color="gray").grid(
                row=row, column=0, columnspan=2,
                sticky="w", padx=16, pady=(14, 4))
            ctk.CTkFrame(card, height=1,
                         fg_color=("gray85", "gray25")).grid(
                row=row + 1, column=0, columnspan=2,
                sticky="ew", padx=12)
            row += 2

        def field(label, var, placeholder=""):
            nonlocal row
            ctk.CTkLabel(card, text=label,
                         anchor="w").grid(
                row=row, column=0, sticky="ew",
                padx=16, pady=(10, 2))
            entry = ctk.CTkEntry(card, textvariable=var,
                                 placeholder_text=placeholder)
            entry.grid(row=row + 1, column=0,
                       sticky="ew", padx=16, pady=(0, 10))
            row += 2

        def toggle_row(label, var):
            nonlocal row
            r = ctk.CTkFrame(card, fg_color="transparent")
            r.grid(row=row, column=0, sticky="ew", padx=16, pady=8)
            r.grid_columnconfigure(0, weight=1)
            ctk.CTkLabel(r, text=label).grid(row=0, column=0, sticky="w")
            ctk.CTkSwitch(r, text="", variable=var).grid(row=0, column=1)
            row += 1

        # Device settings
        section("DEVICE SETTINGS")
        self._name_var = ctk.StringVar(value=self.settings["device_name"])
        field("Device Name", self._name_var, "e.g. My-Windows-PC")

        self._auto_var = ctk.BooleanVar(value=self.settings["auto_accept"])
        toggle_row("Auto Accept Incoming Files", self._auto_var)

        self._manual_var = ctk.BooleanVar(
            value=not self.settings["auto_accept"])
        toggle_row("Manual Confirmation Required", self._manual_var)

        # Storage
        section("STORAGE")
        ctk.CTkLabel(card, text="Default Save Location",
                     anchor="w").grid(
            row=row, column=0, sticky="ew", padx=16, pady=(10, 2))
        row += 1

        path_row = ctk.CTkFrame(card, fg_color="transparent")
        path_row.grid(row=row, column=0, sticky="ew",
                      padx=16, pady=(0, 10))
        path_row.grid_columnconfigure(0, weight=1)
        self._path_var = ctk.StringVar(value=self.settings["save_folder"])
        ctk.CTkEntry(path_row, textvariable=self._path_var,
                     state="readonly").grid(
            row=0, column=0, sticky="ew", padx=(0, 8))
        ctk.CTkButton(path_row, text="Change Location",
                      command=self._browse).grid(row=0, column=1)
        row += 1

        # Network
        section("NETWORK")
        self._port_var = ctk.StringVar(
            value=str(self.settings["tcp_port"]))
        field("Port Number", self._port_var, "Default: 5006")
        ctk.CTkLabel(card, text="Default: 5006",
                     text_color="gray",
                     font=ctk.CTkFont(size=11)).grid(
            row=row, column=0, sticky="w", padx=16, pady=(0, 8))
        row += 1

        # About
        section("ABOUT")
        ctk.CTkLabel(card,
                     text="App Version: 1.0.0\nDeveloper: FlashDrop",
                     text_color="gray",
                     font=ctk.CTkFont(size=11),
                     justify="left",
                     anchor="w").grid(
            row=row, column=0, sticky="ew",
            padx=16, pady=(8, 16))
        row += 1

        # Save button
        ctk.CTkButton(self, text="Save Settings",
                      height=48,
                      command=self._save).grid(
            row=2, column=0, sticky="ew", pady=(16, 0))

    def _browse(self):
        folder = filedialog.askdirectory(title="Choose default save folder")
        if folder:
            self._path_var.set(folder)

    def _save(self):
        self.settings["device_name"]  = self._name_var.get().strip() or "Windows-PC"
        self.settings["auto_accept"]  = self._auto_var.get()
        self.settings["save_folder"]  = self._path_var.get()
        self.settings["tcp_port"]     = int(self._port_var.get() or 5006)
        config.save(self.settings)
        self.on_save(self.settings)
