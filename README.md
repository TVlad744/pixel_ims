# Pixel IMS

Enable carrier IMS features on Pixel phones **without root** — VoLTE, 5G calling (VoNR),
Wi-Fi calling (VoWiFi) and more, configured per SIM.

## What it does

- **Turns on IMS features** by overriding the carrier configuration: VoLTE, VoNR (5G calling),
  VoWiFi, Wi-Fi calling while roaming, and supplementary services over UT.
- **Per-SIM control** — each SIM slot has its own toggles, status and actions.
- **Live status** — shows the device's real IMS registration state and the *effective* carrier
  config for each feature, read straight from the system.
- **Apply / Restore** — write your settings to a SIM, or roll it back to the carrier defaults.
- **Apply on boot** — re-applies your settings automatically in the
  background once the phone is back online.

## How it works

There is no public API to change carrier config, so the app borrows shell privileges locally:

1. It connects to the phone's **own Wireless Debugging** endpoint over loopback ADB (via
   [kadb](https://github.com/FlyfishXu/kadb)) — the port is discovered automatically with mDNS.
2. It launches a small `Instrumentation` (`am instrument --no-restart`) that adopts the shell
   permission identity and calls `CarrierConfigManager.overrideConfig` for the selected SIM.
3. Status is read back with the same shell privileges via a lightweight `app_process` tool.

## Requirements

- A Tensor Pixel device on **Android 14+** (`minSdk 34`).

## Usage

1. Open the app and follow the setup card: enable Wireless Debugging and enter the pairing code
   from the system dialog (you can type it straight from the notification).
2. Pick a SIM, toggle the features you want, and tap **Apply**.
3. Optionally enable **Apply on boot** so the configuration survives restarts.
4. Use **Restore** to return a SIM to its carrier defaults.

## Disclaimer

This tool changes low-level telephony configuration on your own device. Whether a feature actually
works still depends on your carrier and network. Use at your own risk.
