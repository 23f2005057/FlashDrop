# FlashDrop

FlashDrop is a peer-to-peer file transfer system that enables high-speed file sharing between Android and Windows devices over a local WiFi network.

It is designed to be lightweight, secure, ad-free, and independent of internet connectivity.

---

## 📌 Problem Statement

Existing file transfer solutions often suffer from:

- Advertisements and bloatware
- Privacy concerns (cloud routing)
- Internet dependency
- Slow Bluetooth-based transfers
- Complex USB setup

FlashDrop aims to provide a clean, fast, and reliable LAN-based file transfer system without relying on internet services or third-party cloud platforms.

---

## 👥 Target Users (Personas)

### 1. College Student
Needs to quickly share assignments, PDFs, and media between phone and laptop without cables or internet.

### 2. Office Professional
Requires secure local file transfer between work devices without exposing data to cloud storage.

### 3. Home User
Wants a simple, ad-free solution to move files between Android phone and Windows PC.

---

## 🎯 Vision Statement

To build a secure, reliable, and cross-platform peer-to-peer file transfer system that operates entirely over local WiFi using direct socket communication.

FlashDrop focuses on performance, integrity verification, and simplicity.

---

## 🏗 System Architecture

FlashDrop follows a layered peer-to-peer architecture.

Each device (Android and Windows) acts as a peer with the following layers:

- Presentation Layer (UI Screens)
- Application Controller (Session & Transfer Management)
- P2P Communication Module (Discovery + Protocol Handling)
- Transport Layer (UDP for discovery, TCP for file transfer)
- Streaming & Storage Layer (Chunk-based transfer + SQLite persistence)

### Communication Flow:

1. Device Discovery (UDP Broadcast)
2. TCP Handshake
3. Metadata Exchange
4. Chunked File Streaming (64KB blocks)
5. Checksum Verification (SHA-256)
6. Completion Acknowledgment

No cloud server is involved. Communication happens directly over local WiFi.

---

## 🚀 Key Features

- Automatic device discovery
- Manual IP connection option
- Multi-file selection
- Real-time transfer progress
- Transfer speed display
- Pause / Resume support
- SHA-256 file integrity verification
- Transfer history tracking
- Configurable save location
- Cross-platform support (Android + Windows)

---

## 📊 MoSCoW Prioritization

### Must Have
- Device discovery (UDP)
- Reliable TCP file transfer
- Real-time progress tracking
- File integrity verification
- Transfer history logging

### Should Have
- Pause / Resume transfer
- Manual IP entry
- Configurable save location
- Network port configuration

### Could Have
- Dark mode UI
- Multiple simultaneous sessions
- Device nickname customization

### Won’t Have (Current Version)
- Cloud storage integration
- Internet-based file transfer
- iOS support

---

## 📈 Success Metrics

- 100MB file transfer under 30 seconds (LAN conditions)
- 90% successful transfer rate without manual retry
- 100% file integrity verification using checksum
- Less than 3 user interactions to initiate transfer

---

## ⚠ Assumptions

- Devices are connected to the same WiFi network
- Firewall allows TCP communication
- Users grant required file permissions
- Stable LAN environment

---

## ⛔ Constraints

- Android file system access limitations
- Windows firewall restrictions
- Network bandwidth dependency
- Platform-specific socket handling differences

---

## 🐳 Development Setup (Docker)

FlashDrop uses Docker for local development environment setup.

### Build Docker Image

```bash
docker build -t flashdrop-dev .
