# Pirate Android

Kotlin + Jetpack Compose Android client for Pirate.

## Build

Use the repo wrapper from this directory:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

## Play Store Bundle

Google Play uploads should use the signed Android App Bundle, not an APK:

```bash
rtk timeout 600 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain :app:bundleRelease
```

Bundle output:

```text
app/build/outputs/bundle/release/app-release.aab
```

Release signing reads local `signing.properties`:

```properties
storeFile=/absolute/path/to/upload-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`signing.properties` is local-only and must not be committed.

CI builds the same Play artifact on Blacksmith runners through:

```text
.github/workflows/android-release-bundle.yml
```

Required GitHub secrets:

```text
SIGNING_STORE_FILE      # base64-encoded upload keystore
SIGNING_STORE_PASSWORD
SIGNING_KEY_ALIAS
SIGNING_KEY_PASSWORD
PRIVY_APP_ID
PRIVY_APP_CLIENT_ID
```

The Android signing secrets are also stored in Infisical as the recoverable source of truth:

```text
prod:/services/android-release
```

The workflow uploads a `release-aab` artifact containing:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Store Listing Assets

Play listing metadata and screenshots are staged under:

```text
fastlane/metadata/android/en-US/
```

The legacy screenshot capture helper has been ported:

```bash
./scripts/capture-screenshots.sh --build --type phone
./scripts/capture-screenshots.sh --build --type tablet
```

The script writes to `fastlane/metadata/android/en-US/images`.
