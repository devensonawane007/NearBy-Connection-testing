# Samekan Private Trek Room - Offline P2P Cluster Trekking System

This project is a production-grade, offline-first peer-to-peer group communication application designed for the **Samekan** trekking ecosystem. Operating 100% serverless, it utilizes Google Play Services Nearby Connections with the **`P2P_CLUSTER`** strategy to establish an authenticated, zero-internet communication mesh between multiple physical Android devices.

The project is fully refactored to comply with Android 14+ (API 34-36) Foreground Service (FGS), runtime permission, and Google Play console compliance requirements.

---

## Key Features & Production Enhancements

### 1. End-To-End Security (AES-256-GCM)
- **CryptoHelper**: Encapsulates standard `AES/GCM/NoPadding` encryption using random 12-byte IVs and 128-bit authentication tags.
- **Key Derivation**: Keys are derived from custom room passwords via SHA-256. If a room does not have a password, the key is derived from the `roomId` as a fallback, ensuring traffic isolation.
- **Plaintext Headers**: Encryption is performed transparently inside `PacketSerializer` on the `payload` string only. Headers (e.g. sender, messageId, TTL, hop count) remain in plaintext so intermediate mesh nodes can route packets without needing the decryption keys.

### 2. Reliable Delivery & Mesh Routing
- **Retransmission Queue**: A background scheduler in `NearbyConnectionManager` tracks outgoing reliable packets (chat messages, file headers, chunks) and re-sends them if an ACK isn't received within 3000ms. It retries up to 3 times before updating the message status to `FAILED`.
- **Loop Prevention**: Active packets are tracked in a thread-safe `duplicatePacketCache` on each node. Re-captured packets are dropped immediately to prevent loop amplification.
- **TTL Decrement Mesh**: Packets are broadcasted with a Time-To-Live (TTL) field. Intermediate nodes automatically decrement the TTL, increment the `hopCount`, and relay the packet to peers (excluding the packet source).

### 3. Walkie-Talkie (PTT) Jitter Buffer
- **Jitter Priority Queue**: Voice packets are sequence-numbered at source. The playback loop in `PTTManager` buffers incoming chunks in a sorted `PriorityQueue`, playing them back sequentially.
- **Audio Loss Concealment**: If a packet in the sequence is lost or delayed, the player automatically inserts a brief silent frame to conceal the loss and prevent stuttering.

### 4. Chunked & Resumable File Sharing
- **Chunk Size**: Files are split into 32KB binary blocks encoded in Base64.
- **State Control**: Users can **Pause** sending. The sending loop breaks instantly, saving the progress and chunk offset. Clicking **Resume** launches a coroutine that continues writing from `chunkIndex + 1`.
- **Integrity Validation**: Once the final chunk is received, the app computes the SHA-256 hash of the reconstructed file and compares it with the checksum in the file header before moving it to destination storage.

### 5. Adaptive GPS & Power Optimization
- **Power Saver Awareness**: The `GpsManager` monitors device power states. If Android Battery Saver mode is active, the GPS polling interval is automatically throttled to a minimum of 30 seconds to conserve battery life.

### 6. Tactical Canvas Map & Sweeping Radar
- **Radar Compass**: Animates a rotating sweep gradient sector. Displays distance, battery, and ping latency in real-time under each member's callsign dot. Direction ticks (N, S, E, W) circle the outer ring.
- **Private Trail Map**: Draws concentric distance scale rings (50m, 100m, 200m) around the user. Trail breadcrumbs are color-coded based on altitude elevations. Draws translucent GPS accuracy circles and a rotating needle compass overlay.
- **Emergency SOS Warnings**: When a member triggers an SOS, a pulsing warning indicator shows up on both the Map and Radar canvas overlays.
- **Diagnostics Screen**: Displays real-time estimated bandwidth, link protocols, RTT latency, a live packet routing audit log, and peer performance statistics.

---

## Technical Specifications & Setup

* **Minimum SDK**: Android 10+ (API 29)
* **Target SDK**: Android 14+ (API 34/36)
* **Gradle Toolchain**: JDK 17 (Eclipse Adoptium 17)
* **Database**: Room Database version 3 with destructive fallback.
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
1. **Phone A**: Tap **CREATE OFFLINE ROOM**. Enter room name `"Rajgad Sunday Trek"`. Optionally enter password `"TrekSecure123"`.
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

### Test 6: Offline Trail Map & Resumable File sharing
1. Walk 50 meters with **Phone B** in any direction.
2. **Verification on Phone A**:
   * Go to the **Map** tab.
   * Verify that Phone B's movements are tracked as breadcrumbs on the zoomable Canvas vector map.
   * Toggle **Heading Sync**: verify the canvas rotates dynamically based on the compass direction.
3. Go to the **Chat** tab on **Phone A**, click the attachment icon, pick a large GPX trail, and start sharing.
   * Press the **Pause** icon on Phone A: verify progress halts immediately.
   * Press the **Resume/Play** icon: verify chunk streams continue, finalize transfer, and verify checksum match.
