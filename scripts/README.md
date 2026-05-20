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

## `androidw.sh`

Canonical Android Gradle entry point for this repo.

Use from `android/`:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

What it does:

- resolves Java 17 from `JAVA_HOME`, `javac`, or `java`
- sets `GRADLE_USER_HOME` to `/tmp/gradle-$USER` when unset
- resolves `ANDROID_SDK_ROOT` / `ANDROID_HOME`
- creates `local.properties` with `sdk.dir` when possible
- supports low-impact mode with `PIRATE_ANDROID_SLOW=1`

Use `PIRATE_ANDROID_MAX_WORKERS=1`, `--offline`, and `--no-daemon` on this machine unless explicitly doing a heavier build. The first online build after dependency changes may still be expensive; do it sparingly.
