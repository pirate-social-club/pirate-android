# Pirate Android Setup

Use remote Android builds by default. This laptop has repeatedly frozen during local Gradle work.

## Policy

- Prefer static review and narrow edits first.
- Use Blacksmith-backed GitHub Actions as the main compile/build path.
- Avoid local Gradle builds unless the check is urgent, narrow, and the machine has low swap pressure.
- Use [scripts/androidw.sh](/home/t42/Documents/pirate-workspace/android/scripts/androidw.sh) for local Gradle tasks only when remote CI is not practical.
- Batch edits before compiling. Do not compile after every small change.

## Remote Build Policy

Primary verification workflow:

- [android-compile.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-compile.yml)
- runner: `blacksmith-4vcpu-ubuntu-2404`
- task: `:app:compileDebugKotlin`

This workflow runs on Android-relevant pushes and pull requests, and can also be started manually:

```bash
rtk gh workflow run android-compile.yml --ref main
```

The heavier APK workflow is:

- [android-ci.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-ci.yml)
- runner: `blacksmith-4vcpu-ubuntu-2404`
- tasks: `assembleDebug`, `assembleRelease`

Use the heavier workflow when an APK artifact or release build validation is needed. Use compile-only for day-to-day Kotlin/Compose verification.

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
```

When `API_BASE_URL` is omitted, Android defaults to `https://api.pirate.sc` so CI-built APKs can load production public data on a phone. Use `local.properties` only when you intentionally want a local or staging API target.

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
REOWN_PROJECT_ID=your_reown_project_id
REOWN_REDIRECT_URI=pirate://wallet-connect
```

If `REOWN_PROJECT_ID` is omitted, the wallet UI degrades gracefully and external wallet connect stays unavailable for that build.

When these values are omitted, Android defaults to the production Pirate Privy app used by the legacy Android project. Blacksmith/GitHub Actions builds can override them from repository secrets named `PRIVY_APP_ID` and `PRIVY_APP_CLIENT_ID`. Local `local.properties` values take precedence over environment variables.

Current auth flow:

1. native Privy login starts from [AuthViewModel.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/auth/AuthViewModel.kt:41)
2. Privy OAuth uses the custom app scheme `pirate`
3. Android receives that callback through the `VIEW` intent filter in [AndroidManifest.xml](/home/t42/Documents/pirate-workspace/android/app/src/main/AndroidManifest.xml:14)
4. the returned Privy access token is exchanged with Pirate backend `/auth/session/exchange`

If `PRIVY_APP_ID` or `PRIVY_APP_CLIENT_ID` is explicitly set blank, auth is intentionally disabled for the build. Public feed data should still load because the default API target is production.

## Smallest Local Check

Only run this if the machine is healthy enough. Check `free -h` first; do not run local Gradle when swap is heavily used. When local validation is unavoidable, prefer:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

Do not escalate immediately to larger Android build tasks unless the Kotlin compile check is insufficient. Prefer Blacksmith before running any larger local task. If `--offline` fails because dependencies are missing, run one online wrapper command only after confirming the machine can tolerate it.

## Previous Failure In This Workspace

Earlier local compile checks failed before Kotlin compilation because Android SDK configuration was missing.

This workspace now has `local.properties` with `sdk.dir=/home/t42/Android/Sdk`. Use the wrapper command above for narrow local checks.

Latest narrow check:

```bash
rtk timeout 180 env PIRATE_ANDROID_SLOW=1 PIRATE_ANDROID_MAX_WORKERS=1 ./scripts/androidw.sh --no-daemon --console=plain :app:compileDebugKotlin
```

Result: passed. Remaining output is limited to `Icons.Filled.ArrowBack` deprecation warnings.
