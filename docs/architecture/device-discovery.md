# Device Discovery Module

## Overview
The Device Discovery module is responsible for detecting nearby peers over the same WiFi network.

## Technical Approach
- Uses UDP broadcast (Port 8081)
- Sends discovery packet
- Listens for peer response
- Updates UI with available devices

## Flow
1. Broadcast discovery packet
2. Peer responds with device metadata
3. Device list updated in Presentation Layer

