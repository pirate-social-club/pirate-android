# Pirate Android Agent Notes

## Build Path

Blacksmith-backed GitHub Actions is the single normal Android build and
verification path for agents in this repo. Do not run local Gradle compile,
test, assemble, bundle, or build commands for routine verification on this
workstation.

```bash
rtk gh workflow run android-ci.yml --ref <branch-or-commit-ref>
```

The quality workflow is `.github/workflows/android-ci.yml`, runs on
`blacksmith-4vcpu-ubuntu-2404`, and covers unit tests, lint, and the debug APK
build.

Blacksmith can only verify code that exists on the pushed ref. For local dirty
work, finish static review, commit the intended files on a branch, push that
branch, and run `android-ci.yml` against the branch ref. Do this instead
of trying a local `androidw`, `gradlew`, `testDebugUnitTest`,
`compileDebugKotlin`, `assemble`, or `bundle` command.

For installing a Blacksmith build onto an attached Android phone, do not repeat
the manual GitHub artifact flow. Use the install helper:

```bash
rtk ./scripts/install-blacksmith-apk.sh --ref <pushed-branch> --launch
```

If a successful `android-ci.yml` run already exists:

```bash
rtk ./scripts/install-blacksmith-apk.sh --run-id <github-run-id> --launch
```

The helper triggers or uses `.github/workflows/android-ci.yml`, waits for the
Blacksmith run, downloads the `debug-apk` artifact, falls back to direct artifact
download if `gh run download` hangs, unzips the APK, handles debug-signature
mismatches by uninstalling only `sc.pirate.mobile.blacksmith`, installs with adb,
and verifies the installed package. It must not uninstall `sc.pirate.mobile`.

Important package/API split:

- `android-ci.yml` builds the debug "Pirate Blacksmith" app:
  - package: `sc.pirate.mobile.blacksmith`
  - API: `https://api-staging.pirate.sc`
  - install helper: `rtk ./scripts/install-blacksmith-apk.sh --ref <pushed-ref> --launch`
- `android-release-apk.yml` builds the production "Pirate" app:
  - package: `sc.pirate.mobile`
  - API: `https://api.pirate.sc`
  - direct phone install path:

```bash
rtk gh workflow run android-release-apk.yml --ref <pushed-ref>
rtk gh run watch <run-id> --exit-status
rtk gh run download <run-id> -n release-apk -D /tmp/pirate-android-prod-release-<run-id>
rtk /home/t42/Android/Sdk/platform-tools/adb install -r /tmp/pirate-android-prod-release-<run-id>/app-release.apk
rtk /home/t42/Android/Sdk/platform-tools/adb shell monkey -p sc.pirate.mobile -c android.intent.category.LAUNCHER 1
```

## Slow Machine Policy

- Static review locally; build and compile verification on Blacksmith.
- Do not run local Gradle offline or online just because dependencies are
  missing locally. Push a branch and use Blacksmith.
- Do not run local Android unit tests, Kotlin compile, APK assemble, or release
  bundle tasks as routine verification. These are still Gradle builds.
- Local Gradle is an emergency fallback only when the user explicitly asks for a
  local fallback after being told Blacksmith is the normal path.
- Never call `./gradlew` directly in agent workflows.

## Local Setup

- `local.properties` is gitignored and should contain `sdk.dir=/home/t42/Android/Sdk` on this machine.
- Keep Privy values local-only:
  - `PRIVY_APP_ID`
  - `PRIVY_APP_CLIENT_ID`
