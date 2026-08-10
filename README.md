# Pirate Android

Kotlin + Jetpack Compose Android client for Pirate.

## Build

Use Blacksmith-backed GitHub Actions for Android builds and compile
verification. This is the normal path for agents and local development on this
workstation. Do not run local Gradle compile/test/assemble/bundle/build tasks
unless the user explicitly asks for a local fallback after being told Blacksmith
is the normal path.

```bash
rtk gh workflow run android-ci.yml --ref <pushed-branch-or-commit>
```

The workflow runs `.github/workflows/android-ci.yml` on `blacksmith-4vcpu-ubuntu-2404`
and gates changes with debug and release unit tests, Android lint, and a debug APK build.

For unpushed local changes: finish static review, commit the intended files on a
branch, push the branch, then run the workflow against that ref. Blacksmith can
only build code that exists on GitHub.

## Install a Blacksmith Build on a Phone

Use this for the normal "build on Blacksmith and install the new app on my
attached phone" loop:

```bash
rtk ./scripts/install-blacksmith-apk.sh --ref <pushed-branch> --launch
```

From the current pushed branch:

```bash
rtk ./scripts/install-blacksmith-apk.sh --launch
```

From an existing successful Blacksmith run:

```bash
rtk ./scripts/install-blacksmith-apk.sh --run-id <github-run-id> --launch
```

This script runs `.github/workflows/android-ci.yml`, waits for Blacksmith,
downloads the `debug-apk` artifact, unzips it, installs it with adb, and verifies
the installed `sc.pirate.mobile.blacksmith` package. If the old Blacksmith install
was signed by a different debug key, the script uninstalls only
`sc.pirate.mobile.blacksmith` and reinstalls. The release package `sc.pirate.mobile`
is not touched.

This is a staging/debug app:

```text
label: Pirate Blacksmith
package: sc.pirate.mobile.blacksmith
API: https://api-staging.pirate.sc
```

It is expected to show staging feed data. Use the release APK workflow below
when the production Pirate app on a phone needs the latest code.

## Play Store Bundle

Google Play uploads should use the signed Android App Bundle produced by
Blacksmith CI, not a local Gradle build.

Release signing in CI reads GitHub secrets and writes a temporary
`signing.properties` file on the Blacksmith runner. A developer-owned local
`signing.properties` file is only for an explicitly requested emergency local
fallback and must not be treated as the normal release path:

```properties
storeFile=/absolute/path/to/upload-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`signing.properties` is local-only and must not be committed.

CI builds Play/release artifacts on Blacksmith runners through:

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
VERY_SDK_KEY
```

The bundle workflow only accepts a `v*` tag whose value matches the app's
`versionName`. Both signed release workflows depend on the reusable Android CI
gate and use the named `production` GitHub environment. Configure required
reviewers for that environment before merging this release change.

The Android signing secrets are also stored in Infisical as the recoverable source of truth:

```text
prod:/services/android-release
```

The workflow uploads a `release-aab` artifact containing:

```text
app/build/outputs/bundle/release/app-release.aab
```

When a production APK is needed for a directly attached phone, use the same
Blacksmith release workflow and download the `release-apk` artifact. Do not
assemble a release APK locally for the normal screenshot/install loop.

Production phone install path:

```bash
rtk gh workflow run android-release-apk.yml --ref <pushed-branch-or-commit>
rtk gh run watch <run-id> --exit-status
rtk gh run download <run-id> -n release-apk -D /tmp/pirate-android-prod-release-<run-id>
rtk /home/t42/Android/Sdk/platform-tools/adb install -r /tmp/pirate-android-prod-release-<run-id>/app-release.apk
rtk /home/t42/Android/Sdk/platform-tools/adb shell monkey -p sc.pirate.mobile -c android.intent.category.LAUNCHER 1
```

This updates the production app:

```text
label: Pirate
package: sc.pirate.mobile
API: https://api.pirate.sc
```

## Store Listing Assets

Play listing metadata and screenshots are staged under:

```text
fastlane/metadata/android/en-US/
```

The legacy screenshot capture helper has been ported:

```bash
./scripts/capture-screenshots.sh --type phone
./scripts/capture-screenshots.sh --type tablet
```

The script writes to `fastlane/metadata/android/en-US/images`. Avoid the
helper's local `--build` mode on this workstation; install a Blacksmith APK
first when screenshots need a fresh build.
