# Advance Stylo Haptics

An advanced tactile and handwriting haptic engine for **OnePlus Stylo 2** and **OPPO Pencil 2** on OnePlus Pad 2 and ColorOS / OxygenOS 14+ tablets.

---

## Features

- **Native ColorOS / OxygenOS Design:** Built with ColorOS squircle containers, rounded popups, authentic toggle switches, flush intensity slider, and dark/light mode switching that dynamically adapts to system themes and floating window sizes.
- **Dual Launch Points:**
  - Standard launcher home screen icon (`Advance Stylo Haptics`).
  - Seamless system integration inside **Settings &rarr; System & update &rarr; Advance Stylo Haptics**.
- **Hardware-Level Tactile Textures:**
  - **Pencil:** Realistic graphite paper friction.
  - **Eraser:** Firm high-resistance rubber friction feel.
  - **Ballpoint:** Crisp, delicate mechanical roller tick.
  - **Fountain Pen:** Ultra-smooth fluid glide.
- **Global & Per-App Tactile Profiles:**
  - **Global Mode:** Universal stylus tactile feedback enabled across every drawing surface, browser, launcher, and floating window.
  - **Per-App Customization:** Customize distinct tactile patterns and vibration strength levels for specific drawing, sketching, note-taking apps, and floating keyboards (e.g. GboardWu).
- **In-App Vibration Test:** Test tactile waveforms and motor buzz directly in hand without switching apps.
- **KernelSU & Magisk Privileged Integration:** Automatically deploys as a privileged system app with `com.oplus.permission.safe.IOT` to interface directly with ColorOS `IPeManager` (`ISdkAidlInterface`) and hardware touchpanel nodes.

---

## Technical Architecture

1. **Bluetooth Low Energy (BLE) Stylus Motor Sync:**
   - Interacts with ColorOS `com.oplus.ipemanager` via AIDL (`ISdkAidlInterface` on `com.oplus.ipemanager.ACTION.PENCIL_SDK`).
   - Syncs physical vibration level and waveforms to stylus NVRAM config via `content://com.oplus.ipemanager.provider`.
2. **Touchpanel RF Micro-Vibration Coupling:**
   - Interfaces directly with `/proc/touchpanel/pencil_control` to coordinate screen touch events with pen motor resonance.
3. **Accessibility Foreground Tracker:**
   - Non-intrusive window focus and input-method watcher (`StyloAccessibilityService`) detecting top active applications and IMEs (including floating handwriting keyboards) within 250ms latency.

---

## Project Structure

```
StyloHapticsProject/
├── AndroidManifest.xml        # Manifest with launcher & settings injection
├── build.sh                  # One-click portable build & deployment script
├── debug.keystore            # Development signing keystore
├── zipalign.py               # Pure Python 4-byte APK alignment utility
├── res/                      # ColorOS styled drawables, layouts, and colors
└── src/
    ├── com/oplus/ipemanager/sdk/
    │   ├── ISdkAidlInterface.java   # ColorOS Stylus AIDL interface
    │   ├── ISDKAidlCallback.java    # Stylus state change callback
    │   ├── Vibration.java           # Vibration waveform definitions
    │   └── IpeFeature.java          # Stylus hardware capability flags
    └── io/github/Subodhkr360/stylo2/haptics/
        ├── MainActivity.java            # ColorOS settings dashboard
        ├── OnePlusSeekBar.java          # ColorOS flush thumb seekbar
        ├── StylusHapticsManager.java    # Stylus BLE & touchpanel driver
        ├── StyloAccessibilityService.java # App & IME focus tracker
        ├── AppListAdapter.java          # Per-app profile configuration
        ├── BootReceiver.java            # Boot auto-initialization receiver
        └── StylusTapMonitor.java        # Stylus gesture monitor
```

---

## Building from Source

### Prerequisites

- A Linux or Termux / Android Userspace environment with:
  - `aapt2`
  - `javac` (OpenJDK 17+)
  - `d8` (Android Dex compiler)
  - `apksigner`
  - `python3`
  - `android.jar` (API 34)

### Build Command

```bash
# Build standalone APK only:
./build.sh --no-install

# Build and automatically deploy to active KernelSU/Magisk root environment:
./build.sh
```

The compiled APK will be generated at `./StyloHapticsStudio.apk`.

---

## Credits & Acknowledgements

- **Original Base Project:** [YeetingWaterbottle/stylo2-haptics](https://github.com/YeetingWaterbottle/stylo2-haptics) by **YeetingWaterbottle**. Sincere appreciation for the foundational research and initial discovery of OnePlus Stylo 2 haptic control interfaces.
- **Enhanced Edition:** Developed and refactored by **Subodhkr360** with Antigravity AI pair programming.

---

## License

This project is licensed under the Apache License 2.0 or GNU General Public License v3 (see original upstream repository for base licensing terms).
