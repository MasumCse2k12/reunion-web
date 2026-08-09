# Alumni Android App

A WebView wrapper that loads the alumni web app. The native shell handles camera access, file picking, back navigation, and offline detection.

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 or newer (bundled with Android Studio) |
| Android SDK | API 35 (installed via SDK Manager) |
| ADB | Comes with Android Studio platform-tools |

---

## 1. Open the project

1. Clone the repo (if you haven't already):
   ```
   git clone <repo-url>
   cd reunion-web/android
   ```
2. Open **Android Studio** → **Open** → select the `android/` folder.
3. Let Gradle sync finish. If it asks to upgrade AGP, choose **Don't remind me again** — the project pins AGP 8.6.1.

---

## 2. Configure the dev URL (real-device testing)

The debug build needs to know your machine's LAN IP so the WebView can reach the Docker containers.

```bash
# Find your LAN IP
ifconfig | grep "inet 192"
# Example output: inet 192.168.1.42 netmask 0xff00...
```

Create `android/local.properties` (already gitignored):

```properties
dev.web_app_url=http://192.168.1.42:8080
sdk.dir=/Users/yourname/Library/Android/sdk
```

> **Emulator only?** Skip this step. The default `http://10.0.2.2:8080` already routes to your machine.

---

## 3. Start the backend for real-device testing

The Docker containers must bind to your LAN interface, not just `127.0.0.1`.

In `.env` (repo root):

```env
WEB_BIND=0.0.0.0
API_BIND=0.0.0.0
```

Then start everything:

```bash
cd reunion-web
docker compose up --build
```

The web app will be reachable at `http://192.168.1.42:8080` from any device on the same Wi-Fi.

> **Security note:** Only do this on a trusted home/office network, never on public Wi-Fi.

---

## 4. Build the APK

### From Android Studio

**Build** menu → **Build Bundle(s) / APK(s)** → **Build APK(s)**

Output: `app/build/outputs/apk/debug/app-debug.apk`

### From the command line

```bash
cd android
./gradlew assembleDebug
```

---

## 5. Install on a device

### Option A — USB cable

Enable **Developer Options** and **USB Debugging** on the phone:

1. Go to **Settings** → **About phone** → tap **Build number** 7 times.
2. Go to **Settings** → **Developer options** → enable **USB debugging**.
3. Plug in the phone and accept the RSA key prompt.

Install:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Re-install (update) without uninstalling:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option B — Copy the APK file

1. Build the APK (step 4).
2. Copy `app-debug.apk` to the phone (USB transfer, Google Drive, etc.).
3. On the phone, open the file and tap **Install**.
   - If prompted, enable **Install from unknown sources** for your file manager.

---

## 6. Run directly from Android Studio

1. Connect the device (USB or wireless — see section 7).
2. Select your device in the target dropdown (top toolbar).
3. Click the green **Run** button (Shift+F10).

Android Studio builds, installs, and launches the app in one step.

---

## 7. Wireless pairing (Android 11+ / API 30+)

You can develop without a USB cable after the first pairing.

### Pair via QR code (easiest)

1. On the phone: **Settings** → **Developer options** → **Wireless debugging** → turn it **on** → tap **Pair device with QR code**.
2. In Android Studio: **View** → **Tool Windows** → **Device Manager** → click the **Wi-Fi** tab → click **+** → **Pair using Wi-Fi** → select **QR code**.
3. Scan the QR code shown on screen. The device appears in the list immediately.

### Pair via pairing code

1. On the phone: **Settings** → **Developer options** → **Wireless debugging** → **Pair device with pairing code**. Note the IP:port and 6-digit code shown.
2. In Android Studio: **Device Manager** → **Wi-Fi** tab → **+** → **Pair using Wi-Fi** → **Pairing code**.
3. Enter the IP, port, and code. Click **Pair**.

### Connect after pairing

Once paired once, the device remembers Android Studio. Future connections:

1. Enable **Wireless debugging** on the phone.
2. Run `adb connect <phone-ip>:<port>` using the port shown under Wireless debugging (it changes each session), **or** just select the device in the Device Manager — Android Studio reconnects automatically when on the same Wi-Fi.

### ADB over TCP/IP (Android 10 and below)

For older phones, a USB connection is needed to bootstrap wireless ADB:

```bash
# With USB cable plugged in:
adb tcpip 5555
adb connect 192.168.1.55:5555   # phone's LAN IP
# Unplug the cable — the device stays in the ADB device list
```

---

## 8. Release build

> Only needed when distributing outside Android Studio.

1. **Build** → **Generate Signed Bundle / APK** → choose **APK**.
2. Create or select a keystore.
3. Choose **release** build variant.
4. The signed APK ends up in `app/build/outputs/apk/release/`.

The release build uses `https://alumni.sammalani.edu.bd` as the web app URL and enforces HTTPS-only (no cleartext, no user CAs).

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| "ERR_CONNECTION_REFUSED" in WebView | Docker containers not running, or `WEB_BIND` not set to `0.0.0.0` |
| Blank page on real device | Check `dev.web_app_url` in `local.properties` — must be your machine's LAN IP |
| Camera button does nothing | Grant Camera permission in phone Settings → Apps → Alumni |
| Device not appearing in Android Studio | Enable USB Debugging; try `adb kill-server && adb start-server` |
| Gradle sync fails on AGP version | Use the version already pinned in the project (8.6.1); bumping it also needs a matching Gradle wrapper bump |
| App crashes on launch | Run `adb logcat -s AndroidRuntime` to see the stack trace |
