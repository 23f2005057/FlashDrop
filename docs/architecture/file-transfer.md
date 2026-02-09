# File Transfer Module

## Overview
Handles reliable file transfer between Android and Windows peers.

## Technical Approach
- TCP connection (Port 8080)
- Chunk-based transfer (64KB blocks)
- ACK per chunk
- SHA-256 checksum verification

## Flow
1. Establish TCP connection
2. Send metadata
3. Transfer file in chunks
4. Verify integrity
5. Send completion acknowledgment
