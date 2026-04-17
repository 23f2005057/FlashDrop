import customtkinter as ctk
from tkinter import filedialog
from utils.helpers import human_size
import threading


class IncomingRequestDialog(ctk.CTkToplevel):
    """
    Modal popup shown when a device wants to send a file.
    Blocks until user clicks Accept or Reject.
    """

    def __init__(self, master, info: dict, default_save_folder: str):
        super().__init__(master)
        self.title("FlashDrop — Incoming File Request")
        self.geometry("480x320")
        self.resizable(False, False)
        self.grab_set()   # modal
        self.lift()
        self.focus_force()

        self._result     = False
        self._save_path  = default_save_folder
        self._event      = threading.Event()

        self._build(info, default_save_folder)
        self.protocol("WM_DELETE_WINDOW", self._reject)

    def _build(self, info: dict, default_folder: str):
        self.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(self, text="Incoming File Request",
                     font=ctk.CTkFont(size=16, weight="bold")).grid(
            row=0, column=0, columnspan=2, pady=(20, 4))
        ctk.CTkLabel(self, text="A device wants to send you a file",
                     text_color="gray").grid(
            row=1, column=0, columnspan=2, pady=(0, 16))

        # File info card
        card = ctk.CTkFrame(self, corner_radius=10)
        card.grid(row=2, column=0, columnspan=2,
                  sticky="ew", padx=24, pady=(0, 16))
        card.grid_columnconfigure(0, weight=1)

        ctk.CTkLabel(card,
                     text=info["filename"],
                     font=ctk.CTkFont(size=14, weight="bold"),
                     anchor="w").grid(
            row=0, column=0, sticky="ew", padx=16, pady=(14, 2))
        ctk.CTkLabel(card,
                     text=f"File Size: {human_size(info['filesize'])}",
                     text_color="gray",
                     anchor="w").grid(
            row=1, column=0, sticky="ew", padx=16, pady=2)
        ctk.CTkLabel(card,
                     text=f"From: {info['peer_name']}",
                     text_color="gray",
                     anchor="w").grid(
            row=2, column=0, sticky="ew", padx=16, pady=2)

        ctk.CTkFrame(card, height=1,
                     fg_color=("gray80", "gray30")).grid(
            row=3, column=0, sticky="ew", padx=12, pady=8)

        # Save location
        ctk.CTkLabel(card, text="Save Location:",
                     font=ctk.CTkFont(size=11),
                     text_color="gray",
                     anchor="w").grid(
            row=4, column=0, sticky="ew", padx=16, pady=(0, 4))

        path_row = ctk.CTkFrame(card, fg_color="transparent")
        path_row.grid(row=5, column=0, sticky="ew", padx=16, pady=(0, 14))
        path_row.grid_columnconfigure(0, weight=1)

        self._path_var = ctk.StringVar(value=default_folder)
        ctk.CTkEntry(path_row, textvariable=self._path_var,
                     state="readonly").grid(
            row=0, column=0, sticky="ew", padx=(0, 8))
        ctk.CTkButton(path_row, text="Browse", width=80,
                      command=self._browse).grid(row=0, column=1)

        # Buttons
        btn_row = ctk.CTkFrame(self, fg_color="transparent")
        btn_row.grid(row=3, column=0, columnspan=2,
                     sticky="ew", padx=24, pady=(0, 20))
        btn_row.grid_columnconfigure(0, weight=1)
        btn_row.grid_columnconfigure(1, weight=1)

        ctk.CTkButton(btn_row, text="Accept",
                      height=44,
                      command=self._accept).grid(
            row=0, column=0, sticky="ew", padx=(0, 8))
        ctk.CTkButton(btn_row, text="Reject",
                      height=44,
                      fg_color="transparent",
                      border_width=1,
                      text_color=("gray20", "gray80"),
                      command=self._reject).grid(
            row=0, column=1, sticky="ew")

    def _browse(self):
        folder = filedialog.askdirectory(title="Choose save folder")
        if folder:
            self._path_var.set(folder)
            self._save_path = folder

    def _accept(self):
        self._result    = True
        self._save_path = self._path_var.get()
        self._event.set()
        self.destroy()

    def _reject(self):
        self._result = False
        self._event.set()
        self.destroy()

    def wait_for_response(self) -> tuple:
        """Returns (accepted: bool, save_path: str)"""
        self._event.wait()
        return self._result, self._save_path
