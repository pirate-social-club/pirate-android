# Scripts

Repo-level helper scripts.

## `install-blacksmith-apk.sh`

One-command path for installing the Blacksmith debug APK on an attached phone.

Build and install from the current pushed branch:

```bash
rtk ./scripts/install-blacksmith-apk.sh --launch
```

Build and install from a specific pushed branch:

```bash
rtk ./scripts/install-blacksmith-apk.sh --ref codex/my-branch --launch
```

Install from an existing successful `android-ci.yml` run:

```bash
rtk ./scripts/install-blacksmith-apk.sh --run-id 26142550816 --launch
```

What it does:

- triggers `.github/workflows/android-ci.yml` when no `--run-id` is provided
- waits for the Blacksmith run to pass
- downloads the `debug-apk` artifact
- falls back to a direct GitHub artifact download if `gh run download` hangs
- unzips the APK into `/tmp/pirate-android-blacksmith-<run-id>`
- selects the single attached adb device, or uses `--device <serial>`
- installs `sc.pirate.mobile.blacksmith`
- handles debug-key mismatch by uninstalling only `sc.pirate.mobile.blacksmith`
- verifies the installed version and optionally launches the app

The release package `sc.pirate.mobile` is never uninstalled by this helper.

This helper is only for the staging/debug app:

```text
label: Pirate Blacksmith
package: sc.pirate.mobile.blacksmith
API: https://api-staging.pirate.sc
```

For the production app, use the signed release APK workflow instead:

```bash
rtk gh workflow run android-release-apk.yml --ref <pushed-ref>
rtk gh run watch <run-id> --exit-status
rtk gh run download <run-id> -n release-apk -D /tmp/pirate-android-prod-release-<run-id>
rtk /home/t42/Android/Sdk/platform-tools/adb install -r /tmp/pirate-android-prod-release-<run-id>/app-release.apk
rtk /home/t42/Android/Sdk/platform-tools/adb shell monkey -p sc.pirate.mobile -c android.intent.category.LAUNCHER 1
```

## `androidw.sh`

Emergency local Gradle wrapper for this repo. It is not the normal build path
for agents or day-to-day verification on this workstation.

Use Blacksmith instead for builds, compile checks, APK artifacts, and release
bundles. Local Gradle should only be used when the user explicitly requests a
local fallback after being told Blacksmith is the normal path.

What it does:

- resolves Java 17 from `JAVA_HOME`, `javac`, or `java`
- sets `GRADLE_USER_HOME` to `/tmp/gradle-$USER` when unset
- resolves `ANDROID_SDK_ROOT` / `ANDROID_HOME`
- creates `local.properties` with `sdk.dir` when possible
- supports low-impact mode with `PIRATE_ANDROID_SLOW=1`
