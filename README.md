# Samekan Private Trek Room - Offline P2P Cluster Trekking System

This project is a production-grade, offline-first peer-to-peer group communication application designed for the **Samekan** trekking ecosystem. Operating 100% serverless, it utilizes Google Play Services Nearby Connections with the **`P2P_CLUSTER`** strategy to establish an authenticated, zero-internet communication mesh between multiple physical Android devices.

The project is fully refactored to comply with Android 14+ (API 34-36) Foreground Service (FGS), runtime permission, and Google Play console compliance requirements.

---

## Technical Architecture & Compliance (Android 14+)

### 1. Foreground Service Decoupling
- **Decoupled FGS Types**: The persistent background service (`TrekForegroundService`) is configured in the manifest to run using only the `location` and `connectedDevice` types. It coordinates GPS polling and Nearby Connection network advertising/discovery in the background.
- **On-Demand Microphone**: Access to the microphone (`RECORD_AUDIO`) is requested only during active Push-To-Talk (PTT) use in the foreground. The foreground service does NOT run with `microphone` type continuously, eliminating immediate startup crashes on Android 14+ devices.
- **Crash Prevention**: All calls transitioning to Foreground Service are wrapped in try-catch blocks to catch `SecurityException` gracefully.

### 2. Consolidated Permission Manager
- **Dynamic API Mapping**: Centralized in `PermissionManager` to check and request the exact permissions needed depending on the device's Android OS version (handling Bluetooth Scan/Connect/Advertise on API 31+, and Nearby WiFi / Post Notifications on API 33+).
- **Graceful Revocation**: Monitors permission states dynamically. If permissions are revoked during runtime, the background service is immediately stopped to prevent crashes.
- **Rationale Dialogs**: Built a beautiful Compose Permission Screen explaining exactly why each permission is required (e.g. Bluetooth for offline mesh, Location for radar coordinates, Camera for room QR scanning, Microphone for PTT walkie-talkie).

### 3. Hardware Lifecycle Optimizations
- **On-Demand PTT Recorder**: In `PTTManager`, the `AudioRecord` is instantiated only when the user presses and holds the microphone button, and is immediately released and destroyed when the user releases it. No microphone resources are held in the background.
- **Adaptive GPS Tracker**: In `GpsManager`, coordinate requests are dynamically adjusted based on permissions, airplane mode, battery saver settings, and system provider states, reporting status flags to the UI.
- **Multi-Hop SOS Mesh**: High-priority SOS packets propagate through peers using mesh routing (automatically decrementing TTL limits and dropping duplicate packet IDs) to extend coverage across rugged trails.

### 4. Consolidated Logger Utility
- **Diagnostic Terminal**: The central `Logger` coordinates log messages (`debug`, `info`, `warn`, `error`) and forwards them directly to the UI diagnostic monitor while mirroring to Android Logcat.

---

## Technical Specifications & Setup

* **Minimum SDK**: Android 10+ (API 29)
* **Target SDK**: Android 14+ (API 34/36)
* **Gradle Toolchain**: JDK 17 (Eclipse Adoptium 17)
* **Hardware**: Physical devices with functional Bluetooth, Wi-Fi, and GPS antennas.

### Build Instructions
Specify the JDK 17 path in `gradle.properties` (`org.gradle.java.home=...`) or run compiling via:
```powershell
# On Windows PowerShell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 4-Phone Testing Script

Follow this script using **4 physical Android devices** (e.g., Phone A, B, C, and D) to verify ad-hoc cluster capabilities:

### Preparation
1. Install `app-debug.apk` on all 4 phones.
2. Toggle **Mobile Data OFF** and **Wi-Fi Internet OFF** (keep Bluetooth and Wi-Fi antennas turned **ON** in settings; do not connect to any access point).
3. Open the app and grant the requested permissions. Set distinct trekker names (e.g., `Leader-Venn`, `Walker-Kshitij`, `Walker-Ved`, `Sweep-Amogh`).

---

### Test 1: Two-Phone Connection & FGS Boot
1. **Phone A**: Tap **CREATE OFFLINE ROOM**. Enter room name `"Rajgad Sunday Trek"`.
2. A QR Code containing the Room ID (e.g. `T-ROOM-RAJ8`) is displayed.
3. **Phone B**: Tap **SCAN QR** and scan Phone A's screen.
4. Verify matching authentication digits (e.g., `123-456`) and tap **ACCEPT** on both.
5. **Verification**:
   * The top status bar displays active indicators: `BT` (active), `GPS` (active), `FGS` (active), `PERM` (active).
   * Both phones show each other under the **Group** tab as `🟢 CONNECTED`.

### Test 2: Multi-Peer Mesh (4 Phones)
1. **Phone A**: Tap the QR icon in the top bar to display the Room QR.
2. **Phone C** & **Phone D**: Scan the QR from Phone A.
3. Verify the authentication PIN and tap **ACCEPT**.
4. **Verification**:
   * Go to the **Group** tab on any phone: verify that all 4 devices are connected to the cluster.
   * Go to the **Diag** tab: verify the active peers count shows `PEERS: 3` and that packet counters update dynamically.

### Test 3: Broadcast Chat & Replies
1. Go to the **Chat** tab on **Phone A**.
2. Type `"Reached checkpoint 2. Waiting near water point."` and tap **Send**.
3. **Verification**:
   * All phones (B, C, D) receive the message immediately.
   * Long-press the received message on **Phone B**, select **Reply**, type `"Roger that, we are 5 mins away."`, and send.
   * Verify thread reply hierarchy renders correctly on all devices.

### Test 4: Push-to-Talk (PTT) Voice Walkie-Talkie
1. Go to the **Chat** tab on **Phone B**.
2. Press and hold the green **Microphone** icon. Speak into the microphone.
3. **Verification**:
   * Phone B's microphone status icon glows red.
   * An active wave progress indicator reflects live audio levels.
   * Release the button: verify the microphone icon in the device status bar disappears immediately.
   * Phones A, C, and D play the voice message sequentially using Mu-Law decompression.

### Test 5: Multi-Hop SOS Emergency Relay
1. Move **Phone D** far away, such that it can ONLY reach **Phone C**, but has lost direct connection to **Phone A** and **Phone B**.
2. **Phone D**: Go to the **Group** tab, tap **TRIGGER EMERGENCY SOS**, select **Wildlife Danger**, and confirm.
3. **Verification**:
   * Phone C receives the SOS packet, starts flashing the screen red, playing a siren sound, and showing Phone D's battery/coordinates.
   * Phone C decrements the SOS packet's TTL and automatically relays it to A and B.
   * Phone A and B (despite being out of range of D) receive the SOS, play the alarm, and render direction guides showing distance/bearing to Phone D.
   * Tap **SEND ACKNOWLEDGEMENT** on Phone A: verify that D receives the ack packet via the mesh relay and silences its local emergency alert.

### Test 6: Offline Trail Map
1. Walk 50 meters with **Phone B** in any direction.
2. **Verification on Phone A**:
   * Go to the **Map** tab.
   * Verify that Phone B's movements are tracked as breadcrumbs on the zoomable Canvas vector map.
   * Toggle **Heading Sync**: verify the canvas rotates dynamically based on the compass direction.

### Test 7: Local Boot Validation & Connection Recovery
1. Force close the app on **Phone B** (simulating a crash or battery pull).
2. Start the app. Verify it restores the last room config automatically and re-registers listeners.
3. Bring B back near the other phones: verify Nearby Connections auto-reconnects with exponential backoff, restoring connections without requiring a manual QR scan.
