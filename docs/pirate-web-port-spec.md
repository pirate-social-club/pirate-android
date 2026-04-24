# Pirate Android Native App Spec

## Status

No existing spec for the Android port was found in `pirate-android/`, `pirate-web/`, or the parent docs/specs tree during this review.

This document is the working spec for building `pirate-android` as a full native Kotlin app, using `pirate-web` as the reference for product structure, route intent, backend behavior, and user flows.

Immediate execution backlog: [phase-0-backlog.md](./phase-0-backlog.md)
Native start work spec: [native-start-work-spec.md](./native-start-work-spec.md)
Phase 1 contract gaps: [phase-1-contract-gaps.md](./phase-1-contract-gaps.md)
Package ownership: [package-ownership.md](./package-ownership.md)
Build setup: [android-setup.md](./android-setup.md)

## Audit Scope

This spec now also serves as the review handoff for work already landed in `pirate-android`.

It has three jobs:

- define the intended native Android product shape
- record what has actually been implemented so far
- make remaining gaps and likely audit targets explicit for another reviewer or model

## Review Summary

The current Android app is not yet a real native implementation of the Pirate product defined by `pirate-web`. It is an early shell with auth wiring, a small API client, and a few basic screens. Most of the product surface that exists on web is missing, and some Android flows are placeholders or misleading stand-ins.

### Current Android State

- Auth exists through Privy plus `/auth/session/exchange`.
- Onboarding exists in a reduced Reddit-import flow.
- Home is a static welcome screen.
- Community and post screens fetch basic data.
- Post creation supports only a basic text post flow.
- Inbox and Your Communities now have first-pass Android implementations, but still need web-grade interaction depth.
- Create Community now has its own Android entry screen, but not a real creation flow.
- Profile state ownership is split correctly, but profile surfaces are still minimal relative to web.

### Immediate Findings

1. Route parity is far behind web.
   `pirate-web/src/app/router.ts` and `pirate-web/src/app/authenticated-route-renderer.tsx` cover home, community, post, inbox, me, settings, onboarding, create community, create post, global submit, your communities, public profile, and community moderation. Android only covers a subset.

2. Several Android destinations are placeholders rather than product implementations.
   `HomeScreen` is a static welcome message, and several routes now have owned stub screens instead of parity-complete feature implementations.

3. Android navigation shape does not match the web information architecture.
   Web owns settings sections, moderation sections, public profile routes, and a global submit route. Android now has route ownership for those surfaces, but several targets are still first-pass implementations.

4. Android data models are too small for web parity.
   Web routes depend on richer API objects for feed entries, comments, join eligibility, verification launch state, moderation sections, listings/purchases, locale-aware rendering, and vote state. Android models currently cover only a narrow subset.

5. The Android app has verification and state-management gaps.
   Web community join handles gated membership, eligibility refresh, verification retry, and commerce state. Android now has verification groundwork, but community flow still does not cover those product rules.

6. Verification is intentionally narrow on this machine.
   The local SDK is configured through `local.properties`; use the wrapper and single-worker mode for Kotlin compile checks.

## Goal

Make `pirate-android` a full native Kotlin implementation of the same product as `pirate-web`, not a separate thin client with partial overlap.

That means:

- same core route coverage
- same backend contracts and state transitions
- same onboarding and identity model
- same community and post behavior
- same moderation and settings ownership
- native Kotlin UI and state management, not a web runtime wrapper
- `pirate-web` is the product reference, not the shipped Android presentation layer

## Implementation Audit

### What Has Been Implemented

The following groundwork is already in the Android repo and should be reviewed as shipped code, not future planning.

#### Navigation And Shell

- `PirateRoute` now declares routes for global submit, settings sections, moderation index and sections, public profile, and verification providers.
- `PirateNavHost` now routes unfinished surfaces to owned feature screens instead of unrelated inline placeholders.
- `CreateCommunity` now has a first-pass standard centralized creation flow instead of routing directly to verification.
- `MainActivity` no longer owns the bottom-nav shell directly; shell ownership is centered in `PirateScaffold`.
- `MainActivity` now forwards `pirate://` callback intents into app verification state.

Files to review first:

- [PirateRoute.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/navigation/PirateRoute.kt:1)
- [PirateNavHost.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/navigation/PirateNavHost.kt:1)
- [MainActivity.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/MainActivity.kt:1)
- [PirateScaffold.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/ui/PirateScaffold.kt:1)

#### Feature Ownership And Stubs

Owned screen files now exist for unfinished surfaces that previously looked like generic placeholders:

- inbox
- your communities
- create community
- settings
- global submit
- moderation
- public profile

These are structure-setting screens, not parity-complete implementations.

Files to review:

- [InboxScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/inbox/InboxScreen.kt:1)
- [YourCommunitiesScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/communities/YourCommunitiesScreen.kt:1)
- [CreateCommunityScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/createcommunity/CreateCommunityScreen.kt:1)
- [SettingsScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/settings/SettingsScreen.kt:1)
- [GlobalSubmitScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/submit/GlobalSubmitScreen.kt:1)
- [CommunityModerationScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/moderation/CommunityModerationScreen.kt:1)
- [PublicProfileScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/profile/PublicProfileScreen.kt:1)

#### Profile State Fix

The broken `me` vs viewed-user coupling has been split. Android now has separate state holders for current-user profile and viewed-user profile, rather than fetching another user and then rendering `getMe()` state anyway.

Primary file:

- [ProfileScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/profile/ProfileScreen.kt:1)

#### Repository Boundary

The app now exposes an app-level repository container. View models and screens that previously called `ApiClient.*` directly were moved onto repository interfaces first, while still using the current API client under the hood.

This pattern now covers:

- auth
- onboarding
- community
- post
- profile
- verification

Primary files:

- [PirateApp.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/PirateApp.kt:1)
- [AppRepositories.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/shared/api/AppRepositories.kt:1)

Secondary files to audit for adoption quality:

- [AuthViewModel.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/auth/AuthViewModel.kt:1)
- [OnboardingScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/onboarding/OnboardingScreen.kt:1)
- [CommunityScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/community/CommunityScreen.kt:1)
- [PostScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/post/PostScreen.kt:1)
- [PostComposerScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/post/PostComposerScreen.kt:1)
- [ProfileScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/profile/ProfileScreen.kt:1)

#### Privy Foundation

Privy is now treated as the first real auth dependency rather than optional sample config.

What changed:

- checked-in fallback Privy credentials were removed from Gradle config
- local setup docs now require explicit `PRIVY_APP_ID` and `PRIVY_APP_CLIENT_ID`
- the manifest now declares a `pirate://` callback intent filter
- auth UI can now distinguish between missing app configuration and ordinary login failure
- `MainActivity` now retains callback intents through `onNewIntent`

Files to review:

- [app/build.gradle.kts](/home/t42/Documents/pirate-workspace/android/app/build.gradle.kts:1)
- [AndroidManifest.xml](/home/t42/Documents/pirate-workspace/android/app/src/main/AndroidManifest.xml:1)
- [AuthViewModel.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/auth/AuthViewModel.kt:1)
- [AuthScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/auth/AuthScreen.kt:1)
- [android-setup.md](./android-setup.md)

#### Verification Infrastructure

Verification work is now split into contract models, repository access, callback coordination, and provider screens.

What exists now:

- expanded verification request and response models
- repository-backed verification session calls
- app-global pending-session storage and callback parsing
- a first-pass Self deep-link flow with validated intent routing
- a repository-backed Very external-launch/pending screen

Primary files:

- [ApiRequests.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/api/ApiRequests.kt:1)
- [ApiModels.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/api/model/ApiModels.kt:1)
- [VerificationCoordinator.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/verification/VerificationCoordinator.kt:1)
- [SelfVerificationLaunch.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/verification/SelfVerificationLaunch.kt:1)
- [SelfVerificationScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/verification/SelfVerificationScreen.kt:1)
- [VeryVerificationScreen.kt](/home/t42/Documents/pirate-workspace/android/app/src/main/java/sc/pirate/app/verification/VeryVerificationScreen.kt:1)

### What Is Still Incomplete

The app is still far from parity with `pirate-web`. The following gaps are still active and should be treated as open work, not polish.

#### Core Product Gaps

- home has a first-pass real feed with pagination
- your communities has a first-pass created communities list from the user's public profile handle
- inbox has first-pass notification tasks, recent activity, dismiss, and feed pagination
- create community has a first-pass standard centralized creation flow; gated, 18+, media, namespace, and policy-heavy creation remain
- settings has first-pass profile, handle, and locale editing; agents remain an explicit web-only placeholder
- moderation is still mostly a stub, but namespace verification has a first-pass happy-path implementation
- global submit has a first-pass community picker backed by created communities plus home top communities
- public profile has a first-pass handle-backed profile page with created communities
- post thread has first-pass top-level comments, replies, creation, voting, and pagination; it still needs web-grade depth/interaction polish
- community screen covers first-pass join eligibility, gating, Self launch, preview metadata, and paginated posts; namespace verification is reachable after creation and from inbox tasks

#### Verification Gaps

- Self currently uses a first-pass deep-link callback flow only
- Self verification intent now comes from a validated route argument; requested capability is still `unique_human`
- callback parsing is restricted to `pirate://verification/callback`, with query/hash parsing still intentionally broad for provider compatibility
- Very is not integrated with the native SDK yet
- Very screen no longer completes with a synthetic local proof; it launches/polls backend session state
- manifest callback handling is restricted to the verification callback host/path
- app `minSdk` is still `28`, while Very requires Android API `29+`
- camera permission has not been added because the native Very SDK is not wired yet

#### Architecture Gaps

- repositories still wrap the static `ApiClient`; they are boundaries, not full transport refactors
- feature packages are established, but most features do not yet own real domain state
- contract sync is still manual; there is no generated DTO workflow
- there is still no parity checklist per route against `pirate-web`

#### Verification Of The Work Itself

- no Android build has been rerun after these changes
- no Compose tests were added
- no repository tests were added
- no callback deep-link test coverage exists
- all review so far is static

Default Android verification should use Blacksmith-backed GitHub Actions:

- compile-only: [android-compile.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-compile.yml), `:app:compileDebugKotlin`
- artifact validation: [android-ci.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-ci.yml), `assembleDebug` and `assembleRelease`

Local compile should be a fallback only. If local Gradle is unavoidable, use [scripts/androidw.sh](/home/t42/Documents/pirate-workspace/android/scripts/androidw.sh) in offline, low-priority, no-daemon, single-worker mode after checking memory and swap health. Batch edits before compiling.

### Highest-Risk Areas For Another Reviewer

Another model or engineer auditing this work should focus here first:

1. Navigation correctness and route argument handling
2. Profile state split correctness after the refactor
3. Whether repository adoption changed behavior or just moved code around
4. Whether `VerificationCoordinator` can lose or misroute callback state
5. Whether `SelfVerificationScreen` can duplicate completion or resume the wrong session
6. Whether the current Very placeholder flow creates misleading product assumptions
7. Whether the spec and backlog still match the actual repo after the above changes

### Spec Drift To Reconcile

This spec originally described Phase 0 work as planned future work. Some of that work is now implemented and should no longer be read as purely pending.

Status by Phase 0 area:

- route cleanup: partially complete
- route model expansion: partially complete
- profile state split: complete as a structural fix, not yet parity-complete as a feature
- shell ownership cleanup: complete as a structural fix
- repository boundary: partially complete
- build setup docs: complete
- verification groundwork: partially complete
- real feature parity: still largely pending

Another reviewer should use the repo state first and this spec second if they find conflicts.

## Non-Goals

- Pixel-perfect reproduction of web layout
- Shipping every desktop-only affordance on day one
- Carrying over web-only implementation details that should be native on Android
- Embedding the web app in a webview as the primary app architecture

## Product Surface To Port

### Phase 1: Must Match Web Core

- auth
- onboarding
- home feed
- your communities
- community page
- post thread with comments
- create post
- me profile
- settings

### Phase 2: Must Match Web Product Depth

- create community
- community join eligibility and gated membership
- verification-required joins
- public profile
- inbox
- moderation index
- moderation sections:
  `profile`, `rules`, `links`, `donations`, `pricing`, `namespace`, `gates`, `safety`, `agents`

### Phase 3: Depends On Backend/Product Readiness

- song commerce flows
- wallet-sensitive flows
- advanced agent ownership surfaces
- specialized public-host routing behavior that has no Android equivalent

## Route Parity Spec

Android should own an explicit route model that mirrors web route intent, not just current placeholders.

### Required Android Routes

- `auth`
- `onboarding`
- `home`
- `your-communities`
- `community/:communityId`
- `community/:communityId/submit`
- `submit`
- `post/:postId`
- `inbox`
- `me`
- `settings/:section`
- `communities/new`
- `community/:communityId/mod`
- `community/:communityId/mod/:section`
- `user/:userId`
- `public-profile/:handleLabel`

### Mapping Notes

- Web host-based public profiles should map to an explicit Android screen route.
- Web `/submit` can become a native community picker plus composer flow; Android has a first pass using created communities plus home top communities.
- Web settings and moderation tabs should become top-level Android destinations with section arguments, not one oversized screen.

## UX Rules For Android

Android should preserve Pirate’s existing product rules from `AGENTS.md` and `pirate-web/docs/ui-best-practices.md`, while implementing them natively in Compose.

- Keep copy direct.
- Do not add helper prose when labels already explain the action.
- Do not use badge-heavy status UI.
- Use Pirate’s dark shell and restrained orange accent.
- Treat mobile as first-class: bottom nav for core destinations, top app bars for drill-in screens, thumb-friendly actions.
- Keep onboarding structure stable and tight.

## Architecture Requirements

### 1. Web As Product Reference, Not Runtime

`pirate-web` is the canonical reference for:

- route coverage
- flow ordering
- backend contract usage
- state transitions
- content hierarchy

It is not the Android UI runtime.

Android should re-express those flows natively in Kotlin and Compose.

### 2. Shared Contract Discipline

Android should be generated from or aligned directly with the same API contract source that drives web. Hand-maintained drift in request and response models will break parity quickly.

Required outcome:

- one contract source of truth
- generated or strongly audited Kotlin DTOs
- route-by-route request/response coverage

### 3. Feature Ownership By Domain

Android code should be reorganized around feature domains, not just screens.

Recommended package structure:

- `auth/`
- `onboarding/`
- `feed/`
- `community/`
- `post/`
- `profile/`
- `settings/`
- `moderation/`
- `verification/`
- `shared/api/`
- `shared/navigation/`
- `shared/ui/`

Each feature should own:

- screen composables
- view models or state holders
- feature-specific mappers
- feature-specific repository calls
- tests

### 4. Repositories Over Static Endpoint Objects

The current `ApiClient` exposes global endpoint objects with static mutable state. That is workable for a prototype but not for a real parity client.

Target:

- injected repositories per domain
- authenticated client wrapper
- explicit error types
- pagination helpers
- retry rules where product allows them

### 5. Route-State Parity

Android state machines must follow web behavior for:

- onboarding phase resolution
- join eligibility
- gated verification
- optimistic vote updates where supported
- post thread loading and comment creation
- settings section switching
- moderation section switching

## Data And Capability Gaps To Close

Android needs support for these web capabilities before parity can be claimed:

- home feed items and sorting
- top communities rail data
- vote state and optimistic updates
- thread comments and reply creation; top-level comments, top-level creation, top-level voting, reply loading, and reply creation have a first Android pass
- community preview plus sidebar-equivalent metadata; first pass renders gates, rules, and reference links
- join eligibility and missing capabilities
- verification session launch/completion state
- owned communities
- settings section data
- moderation section data and save flows
- locale-aware content rendering
- richer profile and public-profile data

## Recommended Review Order

For another AI or engineer auditing this project, use this order:

1. Read this spec and [phase-0-backlog.md](./phase-0-backlog.md).
2. Review navigation and shell ownership changes first.
3. Review repository boundary changes second.
4. Review verification flow changes third.
5. Compare each changed Android feature against its `pirate-web` reference route before accepting parity claims.
6. Treat all unbuilt code paths as provisional until a configured Android environment verifies them.

## Delivery Plan

### Phase 0: Stabilize Android Foundation

- configure local Android build requirements in repo docs
- keep local Android builds sparse because this machine is resource-constrained
- prefer remote GitHub Actions or Blacksmith validation when an actual Android build is needed
- add compile and test instructions that actually run
- replace placeholder routes that misrepresent product behavior
- introduce a real route table matching the spec above
- decide contract generation or contract sync workflow

Acceptance:

- app compiles locally in a configured environment
- no route points to the wrong screen
- no placeholder screen is presented as a finished feature

### Phase 1: Core Logged-In Experience

- implement home feed from the real backend
- implement your communities
- upgrade community screen to match web core behavior
- upgrade post screen into a real thread view; top-level comments, pagination, creation, voting, reply loading, and reply creation have a first pass
- support native create-post flows from both community and global entry points; first pass supports text/link posts and opens the created post after submit, deeper policy/flair/media parity remains
- upgrade profile/me surface

Acceptance:

- a signed-in Android user can browse feed, open community, open post, read thread, and create a post with the same backend behavior as web

### Phase 2: Onboarding And Identity Parity

- bring onboarding to parity with web flow decisions
- support verification-required transitions used during onboarding and joins
- add settings sections
- add public profile route

Acceptance:

- auth and onboarding outcomes match web account state
- settings and public profile are navigable and backed by real data

### Phase 3: Community Ownership And Moderation

- implement create community
- implement moderation index
- implement moderation sections one by one
- add namespace and gate flows where supported by backend

Acceptance:

- a community owner can complete the core community-management flows on Android without falling back to web

### Phase 4: Advanced Commerce And Edge Features

- evaluate song commerce parity
- add wallet-aware actions
- add any remaining agent or advanced ownership surfaces that matter on mobile

Acceptance:

- Android supports the mobile-relevant subset of advanced Pirate product behavior without drifting from backend contracts

## Testing And Verification

Minimum required verification for parity work:

- route-level Compose smoke tests for each major destination
- repository tests for API mapping and error handling
- view-model tests for onboarding, join eligibility, and thread state
- screenshot or golden coverage for core screens if the team adopts it
- one parity checklist per shipped feature comparing Android against the corresponding web route

## Working Rule

Do not port screen-by-screen by copying visible UI only. Port feature-by-feature by matching:

1. route intent
2. backend contract
3. state transitions
4. user-visible outcome
5. mobile-native presentation

If a web feature has no Android owner, the port is incomplete even if a screen with similar visuals exists.
