# Release & Play Store Publishing Guide

## 1. Generate a Signing Keystore (one-time)

```bash
keytool -genkey -v -keystore reunion-release.keystore \
  -alias reunion -keyalg RSA -keysize 2048 -validity 10000
```

Store this keystore securely — never commit it to git.

Add to `.gitignore`:
```
*.keystore
*.jks
keystore.properties
```

---

## 2. Configure Signing in `app/build.gradle`

```groovy
android {
    signingConfigs {
        release {
            storeFile file("../reunion-release.keystore")
            storePassword System.getenv("KEYSTORE_PASSWORD") ?: "your_password"
            keyAlias "reunion"
            keyPassword System.getenv("KEY_PASSWORD") ?: "your_password"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            buildConfigField "String", "BASE_URL", '"https://alumni.sammalani.edu.bd"'
        }
    }
}
```

Then use `BuildConfig.BASE_URL` in `MainActivity.java` instead of a hardcoded URL.

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
- [ ] Release URL set to `https://alumni.sammalani.edu.bd`
- [ ] Keystore file backed up securely
- [ ] Build passes without warnings
- [ ] Tested on a real device in release mode
