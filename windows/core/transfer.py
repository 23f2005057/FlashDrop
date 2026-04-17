import socket, os, threading, time, json
from core import config as cfg

class TransferEngine:
    """
    Handles both sending and receiving files over TCP.
    Runs a persistent TCP server to accept incoming connections.
    """

    def __init__(self, settings: dict,
                 on_incoming_request,   # (info_dict) -> bool  (True=accept)
                 on_receive_progress,   # (filename, pct, speed_mbps, eta_s)
                 on_receive_done,       # (filename, save_path, peer_name)
                 on_receive_failed,     # (filename, reason)
                 on_send_progress,      # (filename, pct, speed_mbps, eta_s)
                 on_send_done,          # (filename, peer_name)
                 on_send_failed):       # (filename, reason)
        self.settings             = settings
        self.on_incoming_request  = on_incoming_request
        self.on_receive_progress  = on_receive_progress
        self.on_receive_done      = on_receive_done
        self.on_receive_failed    = on_receive_failed
        self.on_send_progress     = on_send_progress
        self.on_send_done         = on_send_done
        self.on_send_failed       = on_send_failed
        self._running             = False
        self._cancel_send         = False
        self._pause_send          = False

    def start_server(self):
        self._running = True
        threading.Thread(target=self._server_loop, daemon=True).start()

    def stop(self):
        self._running = False

    def cancel_send(self):
        self._cancel_send = True

    def pause_send(self):
        self._pause_send = True

    def resume_send(self):
        self._pause_send = False

    # ── TCP server ────────────────────────────────────────────────
    def _server_loop(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("", self.settings["tcp_port"]))
        server.listen(5)
        server.settimeout(1.0)
        while self._running:
            try:
                conn, addr = server.accept()
                threading.Thread(
                    target=self._handle_incoming,
                    args=(conn, addr),
                    daemon=True
                ).start()
            except socket.timeout:
                continue
        server.close()

    def _handle_incoming(self, conn: socket.socket, addr):
        try:
            # Read metadata header
            raw = self._recv_line(conn)
            meta = json.loads(raw)
            if meta.get("type") != "FILE_REQUEST":
                conn.close()
                return

            filename    = meta["filename"]
            filesize    = meta["filesize"]
            peer_name   = meta.get("peer_name", addr[0])
            total_chunks = meta["total_chunks"]

            # Ask UI whether to accept
            accept = self.on_incoming_request({
                "filename":  filename,
                "filesize":  filesize,
                "peer_name": peer_name,
                "ip":        addr[0],
            })

            if not accept:
                conn.sendall(b"REJECTED\n")
                conn.close()
                return

            conn.sendall(b"ACCEPTED\n")

            # Ensure save folder exists
            save_folder = self.settings["save_folder"]
            os.makedirs(save_folder, exist_ok=True)
            save_path = self._unique_path(save_folder, filename)

            # Receive chunks
            received   = 0
            start_time = time.time()
            with open(save_path, "wb") as f:
                for chunk_idx in range(total_chunks):
                    # Read chunk size header
                    size_line = self._recv_line(conn)
                    chunk_size = int(size_line.strip())
                    chunk_data = self._recv_exact(conn, chunk_size)
                    f.write(chunk_data)
                    received += chunk_size

                    elapsed = time.time() - start_time
                    speed   = (received / elapsed / 1024 / 1024) if elapsed > 0 else 0
                    pct     = received / filesize * 100
                    eta     = (filesize - received) / (received / elapsed) if received > 0 else 0
                    self.on_receive_progress(filename, pct, speed, eta)

            conn.sendall(b"TRANSFER_COMPLETE\n")
            self.on_receive_done(filename, save_path, peer_name)

        except Exception as e:
            try:
                self.on_receive_failed(meta.get("filename", "unknown"), str(e))
            except Exception:
                pass
        finally:
            conn.close()

    # ── Send file ─────────────────────────────────────────────────
    def send_file(self, filepath: str, dest_ip: str, dest_port: int, peer_name: str):
        threading.Thread(
            target=self._do_send,
            args=(filepath, dest_ip, dest_port, peer_name),
            daemon=True
        ).start()

    def _do_send(self, filepath: str, dest_ip: str, dest_port: int, peer_name: str):
        filename = os.path.basename(filepath)
        filesize = os.path.getsize(filepath)
        total_chunks = max(1, -(-filesize // cfg.CHUNK_SIZE))  # ceiling division
        self._cancel_send = False
        self._pause_send  = False

        try:
            conn = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            conn.settimeout(10)
            conn.connect((dest_ip, dest_port))
            conn.settimeout(None)

            # Send metadata
            meta = json.dumps({
                "type":         "FILE_REQUEST",
                "filename":     filename,
                "filesize":     filesize,
                "total_chunks": total_chunks,
                "peer_name":    self.settings["device_name"],
            })
            conn.sendall((meta + "\n").encode())

            # Wait for accept/reject
            response = self._recv_line(conn).strip()
            if response != "ACCEPTED":
                self.on_send_failed(filename, "Rejected by receiver")
                conn.close()
                return

            # Stream chunks
            sent       = 0
            start_time = time.time()
            with open(filepath, "rb") as f:
                for chunk_idx in range(total_chunks):
                    while self._pause_send:
                        time.sleep(0.1)
                    if self._cancel_send:
                        self.on_send_failed(filename, "Cancelled")
                        conn.close()
                        return

                    chunk = f.read(cfg.CHUNK_SIZE)
                    if not chunk:
                        break

                    # Send chunk size then data
                    conn.sendall(f"{len(chunk)}\n".encode())
                    conn.sendall(chunk)
                    sent += len(chunk)

                    elapsed = time.time() - start_time
                    speed   = (sent / elapsed / 1024 / 1024) if elapsed > 0 else 0
                    pct     = sent / filesize * 100
                    eta     = (filesize - sent) / (sent / elapsed) if sent > 0 else 0
                    self.on_send_progress(filename, pct, speed, eta)

            # Wait for completion ACK
            ack = self._recv_line(conn).strip()
            if ack == "TRANSFER_COMPLETE":
                self.on_send_done(filename, peer_name)
            else:
                self.on_send_failed(filename, "No completion ACK")

        except Exception as e:
            self.on_send_failed(filename, str(e))
        finally:
            try:
                conn.close()
            except Exception:
                pass

    # ── Helpers ───────────────────────────────────────────────────
    def _recv_line(self, conn: socket.socket) -> str:
        buf = b""
        while not buf.endswith(b"\n"):
            chunk = conn.recv(1)
            if not chunk:
                raise ConnectionError("Connection closed")
            buf += chunk
        return buf.decode()

    def _recv_exact(self, conn: socket.socket, size: int) -> bytes:
        buf = b""
        while len(buf) < size:
            chunk = conn.recv(min(65536, size - len(buf)))
            if not chunk:
                raise ConnectionError("Connection closed")
            buf += chunk
        return buf

    def _unique_path(self, folder: str, filename: str) -> str:
        path = os.path.join(folder, filename)
        if not os.path.exists(path):
            return path
        name, ext = os.path.splitext(filename)
        i = 1
        while True:
            path = os.path.join(folder, f"{name}_{i}{ext}")
            if not os.path.exists(path):
                return path
            i += 1
