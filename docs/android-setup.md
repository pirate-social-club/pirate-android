# Pirate Android Setup

Use remote Android builds. This laptop has repeatedly frozen during local
Gradle work, so Blacksmith is the single normal build and compile verification
path.

## Policy

- Prefer static review and narrow edits first.
- Use Blacksmith-backed GitHub Actions for Android compile/build verification.
- Do not run local Gradle compile, test, assemble, bundle, or build tasks as
  routine verification.
- For local dirty work, finish static review, commit and push a branch, then run
  Blacksmith against that ref.
- Local Gradle is an emergency fallback only when the user explicitly asks for a
  local fallback after being told Blacksmith is the normal path.

## Remote Build Policy

Primary verification workflow:

- [android-compile.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-compile.yml)
- runner: `blacksmith-4vcpu-ubuntu-2404`
- task: `:app:compileDebugKotlin`

This workflow runs on Android-relevant pushes and pull requests, and can also be started manually:

```bash
rtk gh workflow run android-compile.yml --ref <pushed-branch-or-commit>
```

The heavier APK workflow is:

- [android-ci.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-ci.yml)
- runner: `blacksmith-4vcpu-ubuntu-2404`
- task: `assembleDebug`

Use the heavier workflow when a debug APK artifact is needed. Use compile-only
for day-to-day Kotlin/Compose verification.

That debug APK is the staging "Pirate Blacksmith" app:

- package: `sc.pirate.mobile.blacksmith`
- API: `https://api-staging.pirate.sc`
- install helper: `rtk ./scripts/install-blacksmith-apk.sh --ref <pushed-ref> --launch`

Production release artifacts use:

- [android-release-bundle.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-release-bundle.yml)
- [android-release-apk.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-release-apk.yml)
- runner: `blacksmith-4vcpu-ubuntu-2404`
- tasks: `bundleRelease`, `assembleRelease`

Use this release workflow when a Play AAB or production APK artifact is needed.
Do not create those artifacts with local Gradle during the normal
install/screenshot/release loop.

Play Console AAB download path:

```bash
rtk gh workflow run android-release-bundle.yml --ref <pushed-ref>
rtk gh run watch <run-id> --exit-status
rtk gh run download <run-id> -n release-aab -D play-upload/<run-id>
```

The release APK is the production "Pirate" app:

- package: `sc.pirate.mobile`
- API: `https://api.pirate.sc`

Production phone install path:

```bash
rtk gh workflow run android-release-apk.yml --ref <pushed-ref>
rtk gh run watch <run-id> --exit-status
rtk gh run download <run-id> -n release-apk -D play-upload/<run-id>
rtk /home/t42/Android/Sdk/platform-tools/adb install -r play-upload/<run-id>/app-release.apk
rtk /home/t42/Android/Sdk/platform-tools/adb shell monkey -p sc.pirate.mobile -c android.intent.category.LAUNCHER 1
```

## Local Requirements

- Android SDK installed
- `ANDROID_HOME` set, or `sdk.dir` configured in `local.properties`
- Java 17 available

## Local Properties

Create `pirate-android/local.properties` with at least:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Optional runtime values can also be set there:

```properties
API_BASE_URL=http://127.0.0.1:8787
PRIVY_APP_ID=...
PRIVY_APP_CLIENT_ID=...
REOWN_PROJECT_ID=...
REOWN_REDIRECT_URI=pirate://wallet-connect
VERY_SDK_KEY=...
```

When `API_BASE_URL` is omitted, Android defaults to `https://api.pirate.sc` so CI-built APKs can load production public data on a phone. Use `local.properties` only when you intentionally want a local or staging API target.
When `VERY_SDK_KEY` is omitted, Android native Very verification is unavailable and the app shows a hard configuration error.

The repo already includes [local.properties.example](/home/t42/Documents/pirate-workspace/android/local.properties.example).

This machine is configured with:

```properties
sdk.dir=/home/t42/Android/Sdk
```

## Privy Setup

`pirate-android` uses the native Privy Android SDK for sign-in.

Required local properties:

```properties
PRIVY_APP_ID=your_privy_app_id
PRIVY_APP_CLIENT_ID=your_privy_app_client_id
```

Wallet connect uses Reown/AppKit. To enable external wallets locally, also set:

```properties
REOWN_PROJECT_ID=08db15bf8bdb09d1cbc714f4c39d11a8
REOWN_REDIRECT_URI=pirate://wallet-connect
```

If `REOWN_PROJECT_ID` is omitted, Android defaults to the shared Pirate Reown project ID. Set it explicitly only when targeting a different Reown project.

When these values are omitted, Android defaults to the production Pirate Privy app used by the legacy Android project and the shared Pirate Reown project. Blacksmith/GitHub Actions builds set Reown explicitly and can override Privy from repository secrets named `PRIVY_APP_ID` and `PRIVY_APP_CLIENT_ID`. Local `local.properties` values take precedence over environment variables.

Current auth flow:

1. native Privy login starts from [AuthViewModel.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/auth/AuthViewModel.kt:41)
2. Privy OAuth uses the custom app scheme `pirate`
3. Android receives that callback through the `VIEW` intent filter in [AndroidManifest.xml](/home/t42/Documents/pirate-workspace/android/app/src/main/AndroidManifest.xml:14)
4. the returned Privy access token is exchanged with Pirate backend `/auth/session/exchange`

If `PRIVY_APP_ID` or `PRIVY_APP_CLIENT_ID` is explicitly set blank, auth is intentionally disabled for the build. Public feed data should still load because the default API target is production.

## Compile Verification

Use the Blacksmith compile workflow:

```bash
rtk gh workflow run android-compile.yml --ref <pushed-branch-or-commit>
```

Blacksmith can only build pushed code. If changes are local, commit the intended
files on a branch, push the branch, and run the workflow against that branch.
Do not run a local Gradle check because dependencies are missing locally.

## Previous Failure In This Workspace

Earlier local compile checks repeatedly failed or risked freezing the
workstation because Gradle dependencies, SDK state, and daemon behavior varied
between sessions. Do not use those local checks as precedent. Use Blacksmith for
Android compile/build verification.
