# Scripts

Repo-level helper scripts.

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
