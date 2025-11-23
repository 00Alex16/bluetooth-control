# Bluetooth RC Controller

A Kotlin/Jetpack Compose Android app that connects to two Bluetooth devices:
- HC-05 (Serial Port Profile) to send driving commands
- Bluetooth Gamepad / Controller to read inputs

This app allows you to drive an Arduino-based RC car using a standard Bluetooth controller.

# Features
- Connect to any paired HC-05 module
- Real‑time command sending (characters only)
- Supports:
  - Joystick movement mapping (F, B, L, R, diagonals)
  - Button mapping for speed control
  - Emergency stop
  - Spin‑in‑place commands
- Scrollable log panel with timestamped entries
