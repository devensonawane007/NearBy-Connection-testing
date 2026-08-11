# Samekan Private Trek Room - Offline P2P Cluster Prototype

This project is a standalone offline peer-to-peer group communication prototype for **Samekan**, a trekking ecosystem application. It uses Google Play Services Nearby Connections with the **`P2P_CLUSTER`** strategy to establish an authenticated, zero-internet group room between multiple physical Android phones.

## Features Built
1. **P2P Cluster Networking**: Direct M-to-N peer-to-peer connection topology using Nearby Connections API.
2. **Offline Room Concept**: Local room creation (QR generation) and joining (QR scan via CameraX & ZXing) without a centralized backend or Firebase.
3. **Manual Authentication**: PIN code display and verification (`ACCEPT`/`REJECT` dialogs) to authorize room join requests.
4. **GPS Sharing**: High-accuracy local coordinates (Fused Location Provider) broadcasted to direct peers every 10 seconds.
5. **Private Trek Radar**: A custom Compass/Radar Compose Canvas plotting members relative to the user's location with concentric range rings (up to 250m).
6. **Chat Log**: Local room messaging showing delivery state (`SENDING`, `SENT`, `FAILED`).
7. **Local Persistence**: Jetpack Room Database storing rooms, active members, messages, and coordinates.
8. **Connection Recovery**: Auto-reconnection with exponential backoff.

---

## Technical Architecture & Setup

### Requirements
* **Minimum SDK**: Android 10+ (API 29)
* **Target SDK**: Android 14+ (API 34/36)
* **Gradle Toolchain**: JDK 17 or JDK 21 (for build configuration compatibility)
* **Hardware**: Physical devices must support Bluetooth LE, Wi-Fi Direct, and GPS.

### Build Instructions
Run the following build command using your Gradle wrapper. Ensure `JAVA_HOME` is pointed to JDK 17+:
```powershell
# On Windows PowerShell
$env:JAVA_HOME="C:\Path\To\JDK17_or_21"
.\gradlew.bat assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 4-Phone Testing Instructions

Follow this structured test script on **4 physical Android devices** (e.g., Phone A, B, C, and D) to verify P2P cluster behavior:

### Preparation
1. Install the `app-debug.apk` on all 4 phones.
2. On every phone, toggle **Mobile Data OFF** and **Wi-Fi Internet OFF** (keep the Wi-Fi and Bluetooth antennas turned **ON** in settings; do not connect to any access point).
3. Open the app and grant the required permissions (Bluetooth, Location, Camera).
4. Tap the Edit button on the Profile Card and set distinct names (e.g., `A-Venn`, `B-Kshitij`, `C-Ved`, `D-Amogh`).

---

### Test 1: Two-Phone Connection
1. **Phone A**: Tap **CREATE NEW ROOM**. Enter room name `"Rajgad Sunday Trek"`.
2. A QR Code containing the generated Room ID (e.g., `T-ROOM-RAJ8`) will be displayed.
3. **Phone B**: Tap **SCAN CODE** and scan Phone A's screen.
4. An authentication modal showing matching digits (e.g., `123-456`) will pop up on both screens.
5. Tap **[ ACCEPT ]** on both phones.
6. **Verification**: 
   * The status bar transitions to `OFFLINE` (Internet) and show active advertising/discovery flags.
   * Both phones show each other under the **Members** tab as `🟢 CONNECTED`.

### Test 2: Multi-Peer Mesh (4 Phones)
1. **Phone A**: Tap the QR icon in the top bar to display the Room QR.
2. **Phone C** & **Phone D**: Tap **SCAN CODE** and scan the QR from Phone A.
3. Verify and tap **[ ACCEPT ]** on the corresponding authentication modals.
4. **Verification**:
   * All 4 devices should dynamically connect to one another in the P2P cluster.
   * Go to the **Debug** tab on any phone: under `RAW CONNECTED ENDPOINTS`, verify that multiple simultaneous connection entries are active (A displays B, C, D; B displays A, C, D, etc.).

### Test 3: Broadcast Chat
1. Go to the **Chat** tab on **Phone A**.
2. Type `"Reached checkpoint 2. Waiting near water point."` and tap **Send**.
3. **Verification**:
   * All active phones (B, C, and D) immediately receive the message with `A-Venn` as the sender.
   * On Phone A, the message indicator transitions from `PENDING` (yellow icon) to `SENT` (green checkmark).

### Test 4: Real-time GPS sharing & Trek Radar
1. Verify that all 4 phones have a GPS lock.
2. Go to the **Radar** tab on **Phone A**.
3. **Verification**:
   * Phone A is represented by the bright green dot at the center.
   * The relative locations of B, C, and D are plotted as dots with their names, updating dynamically.
   * Go to the **Members** tab on **Phone A** and check the listed distances: verify they match geographic calculations (e.g. `B-Kshitij: 84 m away (Close)`).

### Test 5: Local Boot Validation (No Internet Cold Boot)
1. Close the app and toggle Airplane mode ON then OFF on all phones (ensuring Wi-Fi/data remain fully disconnected).
2. Start the app. It will restore the last room configuration, start advertising/discovery, and update coordinates.

### Test 6 & 7: Connection Loss & Auto-Recovery
1. Move **Phone D** out of wireless range (or temporarily turn Bluetooth OFF on Phone D).
2. **Verification on A, B, C**:
   * Phone D's status changes to `🔴 DISCONNECTED` under the Members tab.
   * Phone D's last known location and details remain visible but its radar dot reflects the disconnected color.
3. Bring **Phone D** back into range (or turn Bluetooth ON again).
4. **Verification**:
   * Nearby Connections discovery triggers automatically.
   * The connection is restored, and the status returns to `🟢 CONNECTED` without manually re-creating the room.

---

## Known Android & Device Limitations

Keep these hardware and operating system constraints in mind during testing:
1. **Radio Interferences**: Since Nearby Connections uses Wi-Fi and Bluetooth concurrently, devices sharing high amounts of data may experience packet latency if both antennas share a single hardware chip (common in older budget phones).
2. **Location Services Requirement**: Android requires Location services (GPS) to be actively turned on in system settings for Bluetooth LE and Wi-Fi scanning to function.
3. **Wi-Fi Direct Coexistence**: On some Android 10/11 devices, starting advertising and discovery simultaneously under `P2P_CLUSTER` can cause the device to toggle its Wi-Fi hotspot, which may disconnect it from existing local Wi-Fi networks.
4. **Background Restrictions**: If the app is minimized, Android's battery manager may restrict Bluetooth scanning. Keep the app in the foreground during multi-peer testing for optimal performance.
