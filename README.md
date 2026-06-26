# G20 Robot Demo

This application is a demonstration for integrating the **Skydroid G20 Controller** with a physical robot. It provides a real-time control interface, telemetry monitoring, and live video feeds from the robot's cameras.

## Reference Documentation

- **Control Protocol**: The communication protocol for UDP and TCP commands was developed in reference to the [Robot Control Specification](https://alidocs.dingtalk.com/i/p/OlnXRl7ed542DGLp/docs/EpGBa2Lm8azb3ZNbCZEA3LMGWgN7R35y).
- **Hardware**: Designed for the [Skydroid G20 Remote Controller](https://skydroidglobal.com/products/skydroid-g20-remote-controller?srsltid=AfmBOorI6PPD2_8Rpu1GkHP6UzyXCLN_G52X-nf36v850ewpJHpFniwl).

## Key Features

- **Skydroid SDK Integration**: Direct communication with the G20 remote controller via the `rcsdk` library.
- **UDP Robot Communication**: Custom UDP client for sending heartbeats and receiving telemetry/status reports.
- **Battery Monitoring**: Real-time display of front and rear battery levels, voltages, temperatures, and charging status.
- **Sleep/Wake Control**: Dedicated toggle to enter and exit the robot's sleep mode, with visual state transitions (Awake, Entering Sleep, Sleeping).
- **RTSP Video Streaming**: Low-latency dual video feeds (Front and Rear) using Media3 ExoPlayer with autonomous reconnection logic.
- **Immersive UI**: Modern Jetpack Compose interface designed for landscape tablets/handhelds, featuring dynamic joystick visualizations and button state indicators.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Media Player**: Media3 ExoPlayer (RTSP Support)
- **Networking**: UDP (Custom binary protocol with JSON payload)
- **Supported Hardware**: Skydroid G20 Remote Controller

## Protocol Overview

The app communicates with the robot over UDP (Port 30000 by default).

### Header (16 bytes)
| Field | Size | Description |
|---|---|---|
| Sync | 4 bytes | `0xEB 0x91 0xEB 0x90` |
| Length | 2 bytes | Little-endian ASDU length |
| Message ID | 2 bytes | Little-endian identifier |
| Format | 1 byte | `0x01` (JSON) |
| Reserved | 7 bytes | Zero-padded |

### Heartbeat (Command 100)
Sent every 2 seconds to maintain connection.

### Status Report (Command 5/6)
Received from the robot to update battery and sleep states.

## Getting Started

1. Connect your Android device to the Skydroid G20 controller via USB.
2. Ensure the robot and Android device are on the same network (e.g., `10.21.33.x`).
3. Launch the application.
4. Use the G20 joysticks to monitor channel values and the on-screen controls to manage robot states.
