# Pirate Android Agent Notes

## Build Path

Blacksmith-backed GitHub Actions is the default Android verification path. Do
not start with a local Gradle compile for routine verification on this
workstation.

```bash
rtk gh workflow run android-compile.yml --ref <branch-or-commit-ref>
```

The compile-only workflow is `.github/workflows/android-compile.yml`, runs on
`blacksmith-4vcpu-ubuntu-2404`, and checks `:app:compileDebugKotlin`.

Blacksmith can only verify code that exists on the pushed ref. For local dirty
work, finish the static review, commit the intended files on a branch, push that
branch, and run `android-compile.yml` against the branch ref.

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
mismatches by uninstalling only `sc.pirate.app.blacksmith`, installs with adb,
and verifies the installed package. It must not uninstall `sc.pirate.app`.

Use local Gradle only as a fallback when remote CI is not practical. If local
Gradle is unavoidable, use the repo wrapper:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

Avoid calling `./gradlew` directly in agent workflows.

## Slow Machine Policy

- Prefer static review first, then Blacksmith compile verification.
- Do not use the local offline Gradle command by default.
- Do not run local online Gradle just because dependencies are missing; push a branch and use Blacksmith unless the user explicitly accepts a local fallback.
- Use local Gradle only as a narrow fallback when remote CI is not practical and swap pressure is low.
- Do not run repeated full local builds.
- Batch code edits before compiling; do not compile after every small edit.
- If the offline compile fails because dependencies are missing, ask before running an online Gradle command.
- Only raise worker count or run larger tasks when the user explicitly accepts the load.

## Local Setup

- `local.properties` is gitignored and should contain `sdk.dir=/home/t42/Android/Sdk` on this machine.
- Keep Privy values local-only:
  - `PRIVY_APP_ID`
  - `PRIVY_APP_CLIENT_ID`
