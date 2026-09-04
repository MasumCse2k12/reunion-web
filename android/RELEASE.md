# Release & Play Store Publishing Guide

## 0. Build Prerequisites

- **JDK 17 or newer.** AGP 8.13.2 builds fine on JDK 21; the old JDK 17-only
  restriction applied to AGP 8.1.2 and no longer holds.
- **Android SDK** with `platforms;android-36` and `build-tools;36.0.0`, and
  `sdk.dir` set in `local.properties` (gitignored). Play requires
  `targetSdk 36`, which only AGP 8.11+ can compile against — hence AGP 8.13.2
  and Gradle 8.13 in the wrapper.

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

### The API must be HTTPS — this is what got us rejected

versionCode 5 and 12 were rejected under Play's **User Data** policy. The app
sends phone numbers, OTP codes, auth tokens, profile photos and payment reports;
all of it went over plain HTTP to a bare IP, and Play's static analysis flags
that without a human ever looking at the app.

The server now sits behind Caddy, which terminates TLS for a single hostname and
routes by path (see the repo-root `Caddyfile` and `PUBLIC_DOMAIN` in `.env`):

| Path      | Serves               |
|-----------|----------------------|
| `/`       | the React web app    |
| `/smbc/*` | this app's API       |
| `/photos/*` | profile photos     |

On the app side:

- There is **no** `networkSecurityConfig` in the release manifest, so the
  platform default applies and cleartext is blocked outright. The permissive
  config lives in `src/debug/` and merges into debug builds only.
- `app/build.gradle` defines `prodApiBaseUrl` once. Override it per build with
  `-PapiBaseUrl=https://…` or the `API_BASE_URL` environment variable.
- A `gradle.taskGraph.whenReady` guard **fails any release build** whose API URL
  is not `https://`. You cannot accidentally ship cleartext again.

Point the release build at a hostname, never an IP: an IP cannot hold a public
TLS certificate, and an IP baked into a shipped APK cannot be redirected if the
server moves.

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

### Account deletion

Play requires any app that lets a user create an account to offer an in-app
route to delete it. Profile → **Delete account** is that route: it reads
`GET /api/v1/me/deletion-preview`, shows what the member is about to lose, asks
twice, then calls `DELETE /api/v1/me`.

What the server does with it — what is scrubbed, what is kept, and why the
order of the writes is load-bearing — is documented on `AccountDeletionService`
and pinned by `AccountDeletionIT`.

**Still outstanding:** Play also wants a *web* URL where someone can request
deletion without installing the app, entered in the Data Safety form. That page
does not exist yet.

---

## Checklist Before Every Release

- [ ] `versionCode` incremented
- [ ] `versionName` updated
- [ ] Release URL in `app/build.gradle` matches the live server and is `https://`
      with a hostname, not an IP (the build fails otherwise)
- [ ] The certificate for that host is valid: `curl -sS https://<host>/smbc/api/v1/batches/totals`
- [ ] No `networkSecurityConfig` and no `usesCleartextTraffic="true"` in the
      merged release manifest:
      `./gradlew :app:processReleaseManifest && grep -i cleartext app/build/intermediates/merged_manifests/release/AndroidManifest.xml`
- [ ] Keystore file and `keystore.properties` backed up securely
- [ ] `jarsigner -verify` reports `jar verified.`
- [ ] Build passes without warnings
- [ ] Tested on a real device in release mode
