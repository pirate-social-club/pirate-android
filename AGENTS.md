# Pirate Android Agent Notes

## Build Path

Use the repo wrapper for Gradle tasks:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

Avoid calling `./gradlew` directly in agent workflows.

## Slow Machine Policy

- Prefer static review and narrow Kotlin compile checks.
- Use the offline, no-daemon, low-priority, one-worker command above by default.
- Do not run full builds repeatedly.
- Batch code edits before compiling; do not compile after every small edit.
- If the offline compile fails because dependencies are missing, ask before running an online Gradle command.
- Only raise worker count or run larger tasks when the user explicitly accepts the load.

## Local Setup

- `local.properties` is gitignored and should contain `sdk.dir=/home/t42/Android/Sdk` on this machine.
- Keep Privy values local-only:
  - `PRIVY_APP_ID`
  - `PRIVY_APP_CLIENT_ID`
