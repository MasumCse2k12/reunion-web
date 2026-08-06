# Release & Play Store Publishing Guide

## 0. Build Prerequisites

- **JDK 17.** AGP 8.1.2 cannot build on JDK 21 — `JdkImageTransform` fails on
  `core-for-system-modules.jar`. Point `JAVA_HOME` at a 17 install.
- **Android SDK** with `platforms;android-34` and `build-tools;34.0.0`, and
  `sdk.dir` set in `local.properties` (gitignored).

---

## 1. Signing Keystore (one-time — already done)

`reunion-release.keystore` (RSA 4096, alias `reunion`, valid to 2053) is the
**upload key**. It and its credentials live outside git:

- `android/reunion-release.keystore`
- `android/keystore.properties`

Both are gitignored via `*.keystore` / `keystore.properties`. **Back both up**
— without them you cannot ship an update under the same upload key.

To regenerate from scratch:

```bash
keytool -genkeypair -v -keystore reunion-release.keystore \
  -alias reunion -keyalg RSA -keysize 4096 -validity 10000
```

Certificate SHA-256 fingerprint:
`28:4B:25:A5:EE:44:A4:34:DC:2A:C1:80:36:03:50:82:DE:FA:3F:28:80:B3:AD:0B:6C:57:A5:79:ED:8A:8B:AD`

---

## 2. How Signing Is Wired

`app/build.gradle` reads `keystore.properties` from the project root:

```properties
storeFile=reunion-release.keystore
storePassword=…
keyAlias=reunion
keyPassword=…
```

Environment variables override the file, so CI can inject secrets without
writing them to disk: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

If neither the file nor `KEYSTORE_FILE` resolves to an existing keystore, the
release build still runs but produces an **unsigned** bundle — Play will
reject it. Check for `Task :app:signReleaseBundle` in the build log.

---

## 3. Bump Version Before Each Release

In `app/build.gradle`:

```groovy
defaultConfig {
    versionCode 2        // increment by 1 for every release
    versionName "1.1.0"  // semantic version shown to users
}
```

---

## 4. Build the Release AAB

```bash
cd /path/to/reunion-web/android
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk

# AAB is required for Play Store
./gradlew bundleRelease

# Optional: build APK for direct/side-load distribution
./gradlew assembleRelease
```

Output locations:
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

---

## 5. Verify the Build is Signed

```bash
# For APK
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# For AAB
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

---

## 6. Publish to Google Play Store

### Prerequisites
- Google Play Developer account ($25 one-time fee) at https://play.google.com/console
- App icon: 512x512 PNG (for store listing)
- Screenshots: at least 2 phone screenshots
- Privacy policy URL (mandatory)

### Known risk: the backend is plain HTTP

The app currently points at `http://103.165.162.229:8092`. Two consequences:

1. **Data safety declaration.** Play asks whether user data is encrypted in
   transit. The honest answer today is *no*. The app handles signup, login,
   profile photos and payments, which is exactly the data Play's User Data
   policy expects to be transmitted securely. Declare it accurately — a false
   "yes" is what gets apps removed.
2. **The IP is baked into every install.** If that address ever changes, every
   shipped copy breaks with no way to redirect users.

Both are fixed by putting the server behind a domain with TLS (Caddy gets
Let's Encrypt certs automatically) and pointing the release build at it.

### Steps

1. Open Play Console and click **Create app**
2. Fill in **Store listing**: title, short description, full description, screenshots, feature graphic
3. Complete the **Content rating** questionnaire
4. Set **Pricing & distribution** (countries, free/paid)
5. Upload the AAB:
   - Go to **Release > Production > Create new release**
   - Upload `app-release.aab`
   - Add release notes (what's new)
6. Click **Review release**, then **Start rollout to Production**
7. First submission review typically takes 1-3 days

---

## Checklist Before Every Release

- [ ] `versionCode` incremented
- [ ] `versionName` updated
- [ ] Release URL in `app/build.gradle` matches the live server
- [ ] Every host in the release URL has a matching entry in
      `src/main/res/xml/network_security_config.xml` (cleartext hosts must be
      listed explicitly, or the WebView loads a blank page)
- [ ] Keystore file and `keystore.properties` backed up securely
- [ ] `jarsigner -verify` reports `jar verified.`
- [ ] Build passes without warnings
- [ ] Tested on a real device in release mode
