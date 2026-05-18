# Pirate Android Phase 0 Backlog

This backlog turns the native app spec into immediate groundwork for `pirate-android`.

Scope: stabilize the Android codebase so native Kotlin feature work can proceed without carrying placeholder routes, incorrect flow ownership, or API-model drift.

Related docs:

- [pirate-web-port-spec.md](./pirate-web-port-spec.md)
- [native-start-work-spec.md](./native-start-work-spec.md)
- [phase-1-contract-gaps.md](./phase-1-contract-gaps.md)
- [package-ownership.md](./package-ownership.md)
- [android-setup.md](./android-setup.md)

## Build Policy

- Do not use frequent local Android builds on this machine.
- Prefer static review and narrow code changes first.
- Use Blacksmith-backed GitHub Actions as the default Android compile/build path.
- Keep any local verification to the smallest possible check.

## Exit Criteria

Phase 0 is done when:

- the Android app has a route table that matches the intended native product surface
- no route points to the wrong product flow
- obvious placeholders are either removed, renamed as stubs, or replaced with real feature entry points
- feature ownership is clear enough to start Phase 1 implementation without re-arguing structure
- API contract drift is documented and assigned
- Android build setup is documented for environments that can actually compile the app

## Priority 0

### 1. Replace misleading route ownership

Problem:

- `CreateCommunity` currently opens `VeryVerificationScreen`, which is the wrong product flow.
- `Inbox` and `YourCommunities` are shipped as empty placeholders.

Current code:

- [PirateNavHost.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/navigation/PirateNavHost.kt:126)
- [PirateNavHost.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/navigation/PirateNavHost.kt:145)

Tasks:

- replace the `CreateCommunity` destination with a dedicated native stub screen owned by a `create-community` feature package
- replace inline placeholder composables for `Inbox` and `YourCommunities` with dedicated feature screens
- make stub states explicit in copy, so unfinished features are not confused with final product behavior

Acceptance:

- no navigation destination points to an unrelated screen
- all unfinished routes have their own owning screen files

### 2. Expand the route model to match native product intent

Problem:

- current Android route coverage is much smaller than the reference app
- missing routes include settings sections, moderation routes, global submit, and public-profile entry

Current code:

- [PirateRoute.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/navigation/PirateRoute.kt:5)
- reference: [router.ts](/home/t42/Documents/pirate-workspace/web/src/app/router.ts:10)
- reference sections: [route-definitions.ts](/home/t42/Documents/pirate-workspace/web/src/app/route-definitions.ts:1)

Tasks:

- add native route definitions for:
  - `settings/:section`
  - `submit`
  - `community/:communityId/mod`
  - `community/:communityId/mod/:section`
  - `public-profile/:handleLabel`
- keep route naming consistent inside Android even if exact path strings differ from web
- define typed route builders/parsers for sectioned destinations

Acceptance:

- every required Phase 1 and Phase 2 surface has a declared route
- settings and moderation sections are represented as explicit route arguments rather than one catch-all screen

### 3. Fix the public profile bug

Problem:

- `ProfileScreen(userId)` fetches another user’s profile and discards it, then renders the view model that loads `getMe()`

Current code:

- [ProfileScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/profile/ProfileScreen.kt:35)
- [ProfileScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/profile/ProfileScreen.kt:106)

Tasks:

- split current-user profile state from viewed-user profile state
- create a separate state holder for public/user profiles
- remove the dead fetch inside `LaunchedEffect`

Acceptance:

- `me` and `user/:userId` no longer share incorrect state behavior
- there is a clear path to adding `public-profile/:handleLabel`

## Priority 1

### 4. Move shell ownership out of `MainActivity`

Problem:

- bottom navigation and shell logic live directly in `MainActivity`
- there is already a `PirateScaffold`, but shell ownership is duplicated

Current code:

- [MainActivity.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/MainActivity.kt:44)
- [PirateScaffold.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/ui/PirateScaffold.kt:13)

Tasks:

- choose one shell owner for app chrome
- move bottom-nav configuration and visibility logic out of `MainActivity`
- keep `MainActivity` limited to app startup, theme, and root composition

Acceptance:

- one source of truth owns shell chrome
- route-aware bottom-nav behavior is not duplicated

### 5. Define feature package ownership before new work lands

Problem:

- current package structure is mostly screen-based
- planned native work spans feed, settings, moderation, public profile, and verification flows

Tasks:

- document package ownership for:
  - `auth`
  - `onboarding`
  - `feed`
  - `community`
  - `post`
  - `profile`
  - `settings`
  - `moderation`
  - `verification`
  - `shared/api`
  - `shared/navigation`
  - `shared/ui`
- move only the minimum files needed to establish the pattern
- do not do a massive rename-only refactor without functional value

Acceptance:

- the next feature branchless change can land into an agreed package boundary
- new screens do not have to invent structure ad hoc

### 6. Replace prototype API access patterns

Problem:

- `ApiClient` uses global nested endpoint objects with mutable initialization
- this is fragile for native feature growth, testing, and previewing

Current code:

- [ApiClient.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/api/ApiClient.kt:18)

Tasks:

- define repository interfaces for onboarding, communities, posts, profiles, and verification
- keep the existing `ApiClient` behind those repositories initially
- stop calling global endpoint objects directly from every view model

Acceptance:

- at least one feature is routed through a repository boundary as the pattern-setter
- direct static endpoint usage is no longer the only access path

## Priority 2

### 7. Document contract gaps against the web reference

Problem:

- Android DTOs cover only a fraction of the data used by the web app

Current code:

- [ApiModels.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/api/model/ApiModels.kt:1)
- reference usage: [community-route.tsx](/home/t42/Documents/pirate-workspace/web/src/app/authenticated-routes/community-route.tsx:42)
- reference usage: [authenticated-route-renderer.tsx](/home/t42/Documents/pirate-workspace/web/src/app/authenticated-route-renderer.tsx:84)

Tasks:

- produce a route-by-route contract gap table for:
  - home feed
  - community
  - post thread
  - settings
  - moderation
  - public profile
- identify which Android DTOs can be retained, which need expansion, and which should be replaced
- decide whether Kotlin models will be generated or manually synced from the shared contract source

Acceptance:

- there is a written contract-gap list that can drive Phase 1 implementation
- the team is not guessing API shape screen by screen

### 8. Tighten onboarding scope and state ownership

Problem:

- Android onboarding has some real backend behavior, but it is still narrow and includes dead imports/state smells

Current code:

- [OnboardingScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/onboarding/OnboardingScreen.kt:29)

Tasks:

- remove unused imports and dead state references
- define the intended native onboarding phases relative to the web reference flow
- document which onboarding states are already supported and which remain Phase 2

Acceptance:

- onboarding state ownership is clear
- no one mistakes the current onboarding surface for complete parity

### 9. Harden composer scope before feature expansion

Problem:

- Android composer is community-only and text-only
- web reference includes global submit behavior and richer submit-state logic

Current code:

- [PostComposerScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/post/PostComposerScreen.kt:39)

Tasks:

- separate composer screen state from route state
- define how native global submit should choose a community
- document composer capabilities that are intentionally deferred beyond Phase 1

Acceptance:

- the app has a clear path to both community-scoped and global post creation

## Verification Tasks

### 10. Add Android build setup documentation

Problem:

- compile verification currently fails in this environment because Android SDK configuration is missing

Previously observed failure:

- Direct `./gradlew` compile failed because `sdk.dir` or `ANDROID_HOME` was not configured.

Default compile verification should use Blacksmith-backed CI:

```bash
rtk gh workflow run android-compile.yml --ref main
```

Local fallback only when remote CI is not practical:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

Tasks:

- add a short Android setup doc covering SDK requirements and `local.properties`
- list the Blacksmith compile workflow as the default verification command
- document the local fallback for cases where remote CI is not practical

Acceptance:

- a contributor with a real Android environment can compile the app without rediscovering setup

### 11. Add lightweight test targets for future feature work

Tasks:

- define the first route-level Compose smoke tests to add once Phase 1 work begins
- define the first view-model test targets:
  - onboarding
  - community eligibility
  - post thread state

Acceptance:

- the repo has a documented minimum testing shape for native feature parity work

## Suggested Execution Order

1. Fix route ownership and misleading placeholders.
2. Expand the route model.
3. Fix the profile/public-profile state split.
4. Collapse shell ownership into one place.
5. Establish package boundaries.
6. Put repositories in front of `ApiClient`.
7. Document contract gaps.
8. Add Android setup and verification docs.

## Out Of Scope For Phase 0

- full home feed implementation
- full thread comments implementation
- moderation UI implementation
- song commerce
- wallet-heavy flows
- complete settings UI

Those belong to later phases once the foundation above is in place.
