import customtkinter as ctk
from tkinter import filedialog, messagebox
import threading, os

from core import config, history
from core.discovery import Discovery
from core.transfer  import TransferEngine
from ui.home_screen     import HomeScreen
from ui.transfer_screen import TransferScreen
from ui.history_screen  import HistoryScreen
from ui.settings_screen import SettingsScreen
from ui.incoming_dialog import IncomingRequestDialog

ctk.set_appearance_mode("system")
ctk.set_default_color_theme("blue")


class FlashDropApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("FlashDrop")
        self.geometry("860x600")
        self.minsize(760, 520)

        self.settings    = config.load()
        self._busy       = False
        self._cur_screen = None
        self._pending_incoming = None  # (info, event, result_box)

        os.makedirs(self.settings["save_folder"], exist_ok=True)

        self._build_layout()
        self._start_services()
        self._show("home")

    # ── Layout ────────────────────────────────────────────────────
    def _build_layout(self):
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # Sidebar
        sidebar = ctk.CTkFrame(self, width=180, corner_radius=0)
        sidebar.grid(row=0, column=0, sticky="nsew")
        sidebar.grid_propagate(False)
        sidebar.grid_rowconfigure(6, weight=1)

        ctk.CTkLabel(sidebar, text="FlashDrop",
                     font=ctk.CTkFont(size=15, weight="bold")).grid(
            row=0, column=0, padx=16, pady=(20, 16))

        self._nav_btns = {}
        for i, (label, key) in enumerate([
            ("Home",     "home"),
            ("History",  "history"),
            ("Settings", "settings"),
        ]):
            btn = ctk.CTkButton(
                sidebar, text=label, anchor="w", height=36,
                fg_color="transparent",
                text_color=("gray10", "gray90"),
                hover_color=("gray85", "gray25"),
                corner_radius=8,
                command=lambda k=key: self._show(k))
            btn.grid(row=i + 1, column=0,
                     padx=10, pady=2, sticky="ew")
            self._nav_btns[key] = btn

        # Content area
        self._content = ctk.CTkFrame(self, fg_color="transparent")
        self._content.grid(row=0, column=1, sticky="nsew",
                           padx=28, pady=24)
        self._content.grid_columnconfigure(0, weight=1)
        self._content.grid_rowconfigure(0, weight=1)

    # ── Navigation ────────────────────────────────────────────────
    def _show(self, key: str):
        for k, btn in self._nav_btns.items():
            btn.configure(
                fg_color=("gray20", "gray75") if k == key else "transparent",
                text_color=("white", "white") if k == key else ("gray10", "gray90"))

        if self._cur_screen:
            self._cur_screen.grid_forget()

        if key == "home":
            if not hasattr(self, "_home"):
                self._home = HomeScreen(
                    self._content, self.settings,
                    on_send_to_device=self._pick_and_send)
            self._cur_screen = self._home
        elif key == "history":
            if not hasattr(self, "_history"):
                self._history = HistoryScreen(self._content)
            else:
                self._history.refresh()
            self._cur_screen = self._history
        elif key == "settings":
            if not hasattr(self, "_settings"):
                self._settings = SettingsScreen(
                    self._content, self.settings,
                    on_save=self._on_settings_saved)
            self._cur_screen = self._settings
        elif key == "transfer":
            self._cur_screen = self._transfer_screen

        self._cur_screen.grid(row=0, column=0, sticky="nsew")

    # ── Services ──────────────────────────────────────────────────
    def _start_services(self):
        self._discovery = Discovery(
            self.settings,
            on_device_found=self._on_device_found,
            on_device_lost=self._on_device_lost)
        self._discovery.start()

        self._engine = TransferEngine(
            self.settings,
            on_incoming_request  = self._on_incoming_request,
            on_receive_progress  = self._on_receive_progress,
            on_receive_done      = self._on_receive_done,
            on_receive_failed    = self._on_receive_failed,
            on_send_progress     = self._on_send_progress,
            on_send_done         = self._on_send_done,
            on_send_failed       = self._on_send_failed)
        self._engine.start_server()

    # ── Discovery callbacks ───────────────────────────────────────
    def _on_device_found(self, info: dict):
        if hasattr(self, "_home"):
            self._home.add_device(info)

    def _on_device_lost(self, device_id: str):
        if hasattr(self, "_home"):
            self._home.remove_device(device_id)

    # ── Send flow ─────────────────────────────────────────────────
    def _pick_and_send(self, device_info: dict):
        if self._busy:
            messagebox.showwarning("Busy", "A transfer is already in progress.")
            return
        paths = filedialog.askopenfilenames(title="Select files to send")
        if not paths:
            return
        # Send files one by one
        threading.Thread(
            target=self._send_sequence,
            args=(list(paths), device_info),
            daemon=True).start()

    def _send_sequence(self, paths: list, device_info: dict):
        for path in paths:
            filename = os.path.basename(path)
            filesize = os.path.getsize(path)
            self._busy = True
            self.after(0, lambda fn=filename, fs=filesize:
                       self._show_transfer_screen(
                           "Sending", fn, fs,
                           device_info["device_name"]))
            # Block until this file is done
            self._send_done_event = threading.Event()
            self._engine.send_file(
                path,
                device_info["ip"],
                device_info["tcp_port"],
                device_info["device_name"])
            self._send_done_event.wait()
        self._busy = False
        self.after(0, lambda: self._show("home"))

    # ── Receive flow (incoming request runs in server thread) ─────
    def _on_incoming_request(self, info: dict) -> bool:
        if self.settings.get("auto_accept"):
            return True

        result_box = [False]
        event      = threading.Event()
        self.after(0, lambda: self._show_incoming_dialog(
            info, event, result_box))
        event.wait()
        if result_box[0]:
            # Update save folder for this transfer
            self.settings["save_folder"] = result_box[1]
        return result_box[0]

    def _show_incoming_dialog(self, info: dict,
                               event: threading.Event,
                               result_box: list):
        dlg = IncomingRequestDialog(
            self, info, self.settings["save_folder"])
        accepted, save_path = dlg.wait_for_response()
        result_box[0] = accepted
        result_box.append(save_path)
        if accepted:
            self._busy = True
            self._show_transfer_screen(
                "Receiving",
                info["filename"],
                info["filesize"],
                info["peer_name"])
        event.set()

    # ── Transfer screen ───────────────────────────────────────────
    def _show_transfer_screen(self, mode, filename, filesize, peer_name):
        self._transfer_screen = TransferScreen(
            self._content, mode, filename, filesize, peer_name,
            on_cancel       = self._cancel_transfer,
            on_pause_resume = self._pause_resume)
        self._show("transfer")
        if hasattr(self, "_home"):
            self._home.set_busy(True)

    def _cancel_transfer(self):
        self._engine.cancel_send()
        self._busy = False
        self._show("home")
        if hasattr(self, "_home"):
            self._home.set_busy(False)

    def _pause_resume(self, paused: bool):
        if paused:
            self._engine.pause_send()
        else:
            self._engine.resume_send()

    # ── Transfer engine callbacks ─────────────────────────────────
    def _on_send_progress(self, filename, pct, speed, eta):
        if hasattr(self, "_transfer_screen"):
            self._transfer_screen.update_progress(pct, speed, eta)

    def _on_send_done(self, filename, peer_name):
        history.add(filename,
                    os.path.getsize(
                        filedialog.askopenfilename()) if False else 0,
                    "SENT", peer_name, "Success")
        if hasattr(self, "_transfer_screen"):
            self._transfer_screen.set_done()
        self._busy = False
        if hasattr(self, "_send_done_event"):
            self._send_done_event.set()
        self.after(1500, lambda: self._show("home"))
        if hasattr(self, "_home"):
            self.after(1500, lambda: self._home.set_busy(False))

    def _on_send_failed(self, filename, reason):
        self._busy = False
        if hasattr(self, "_send_done_event"):
            self._send_done_event.set()
        history.add(filename, 0, "SENT", "unknown",
                    "Cancelled" if "Cancel" in reason else "Failed")
        self.after(0, lambda: messagebox.showerror(
            "Transfer failed", reason))
        self.after(0, lambda: self._show("home"))
        if hasattr(self, "_home"):
            self.after(0, lambda: self._home.set_busy(False))

    def _on_receive_progress(self, filename, pct, speed, eta):
        if hasattr(self, "_transfer_screen"):
            self._transfer_screen.update_progress(pct, speed, eta)

    def _on_receive_done(self, filename, save_path, peer_name):
        history.add(filename,
                    os.path.getsize(save_path),
                    "RECEIVED", peer_name, "Success")
        if hasattr(self, "_transfer_screen"):
            self._transfer_screen.set_done()
        self._busy = False
        self.after(1500, lambda: messagebox.showinfo(
            "Received!", f"Saved to:\n{save_path}"))
        self.after(1500, lambda: self._show("home"))
        if hasattr(self, "_home"):
            self.after(1500, lambda: self._home.set_busy(False))

    def _on_receive_failed(self, filename, reason):
        self._busy = False
        history.add(filename, 0, "RECEIVED", "unknown", "Failed")
        self.after(0, lambda: messagebox.showerror(
            "Receive failed", reason))
        self.after(0, lambda: self._show("home"))
        if hasattr(self, "_home"):
            self.after(0, lambda: self._home.set_busy(False))

    def _on_settings_saved(self, new_settings: dict):
        self.settings = new_settings
        messagebox.showinfo("Saved", "Settings saved successfully.")


if __name__ == "__main__":
    FlashDropApp().mainloop()
