# Pirate Android Native Start Work Spec

Status: draft work spec after reviewing `pirate-web` and the current `android` app.

This document is the execution-oriented companion to [pirate-web-port-spec.md](./pirate-web-port-spec.md). Use that file for the broad parity target. Use this file for the first native Android work slices.

## Current Baseline

The Android app is not starting from zero. It already has:

- a Kotlin + Compose app shell
- native Privy SDK dependency and runtime config
- `/auth/session/exchange` integration through the Pirate API
- a route table with owned destinations for most major web surfaces
- repository interfaces in front of the current `ApiClient`
- first-pass Self and Very verification screens
- deep-link callback capture through `pirate://`

The current app is still not product-parity with web. Most major destinations are either thin implementations or explicit stubs.

## Web Reference Surface

`pirate-web` currently defines these logged-in and public route intents:

- `/`
- `/your-communities`
- `/wallet`
- `/settings`
- `/settings/:section`
- `/communities/new`
- `/submit`
- `/c/:communityId`
- `/c/:communityId/submit`
- `/c/:communityId/mod`
- `/c/:communityId/mod/:section`
- `/p/:postId`
- `/u/:handleLabel`
- `/a/:handleLabel`
- `/inbox`
- `/me`
- `/onboarding`
- host-based public profile routes

Android already has a matching route model for the core non-agent surfaces, but many route targets are stubs.

## Priority Decisions

### 1. Native App, Not WebView

Android should keep using native Kotlin + Compose. The web app is the product reference for route intent, backend contract usage, and state transitions.

### 2. Privy Is The Auth Path

Use native Privy SDK login and exchange the Privy access token with Pirate API.

Immediate requirements:

- keep `PRIVY_APP_ID` and `PRIVY_APP_CLIENT_ID` local-only
- keep auth unavailable when either value is missing
- keep Privy initialized as an application-context singleton
- add explicit tests around missing config and session exchange once local/CI Android testing is available

The current implementation follows Privy's documented Android shape: Android API 28+, Kotlin 2.1+, Maven Central dependency, `Privy.init(...)` with app id and app client id, and a single app-wide instance.

### 3. Self And Very Are External Launches First

Until Pirate has native Very API-key support, verification should be modeled as provider launch plus callback or completion polling, not as an in-app final proof generator.

Self target:

- start a Pirate verification session with provider `self`
- build launch URL from the backend-provided `launch.self_app`
- launch external Self flow
- accept only callback data that matches the pending session
- complete the Pirate verification session with the returned proof
- clear pending callback state after success, error, or expiry

Very target before native API key:

- start a Pirate verification session with provider `very`
- prefer a backend-provided `launch.very_widget.verify_url` or provider app link when available
- try `ACTION_VIEW`
- if no activity can handle the intent, show provider download/open instructions
- do not fabricate a local `proofHash`
- mark the session as pending until backend/provider completion can be confirmed

Very target after native API key:

- add the native SDK only when credentials and product flow are ready
- move `minSdk` only if the selected SDK requires it
- add camera permission only with the native scan flow
- replace external launch fallback with SDK-first, deeplink-fallback behavior

## Findings To Fix Before Phase 1

### F1. Very Placeholder Can Create False Success

Status: fixed in the current working tree.

`VeryVerificationScreen` previously completed with `proofHash = "very-local-${System.currentTimeMillis()}"`.

This should not ship, even in an alpha flow, because it trains the app around a fake verification contract. Replace it with pending/external-launch behavior until native Very support is real.

### F2. Self Launch Builder Does Not Match Web Launch Shape

Status: fixed in the current working tree.

Web builds a `https://redirect.self.xyz` URL with a `selfApp` JSON payload derived from backend launch data. Android now mirrors that shape and overrides `deeplinkCallback` with the native verification callback URI.

Remaining validation: run this against a real backend Self session once Android SDK config is available.

### F3. Deep-Link Callback Filter Is Too Broad

Status: partially fixed in the current working tree.

The manifest still accepts any `pirate://` URL because native Privy auth also uses the `pirate` scheme and its callback host/path has not been pinned here yet. The verification parser is stricter:

- callback host/path is now `pirate://verification/callback`
- callbacks whose provider does not match pending session are ignored
- callbacks whose session id does not match pending session are ignored
- Self callback params are accepted from query or hash fragments, matching web behavior
- duplicate Self completion is guarded in the view model; explicit unit coverage is still needed once Android test scaffolding exists

### F4. Navigation Has No Auth Gate Beyond Start Destination

`PirateNavHost` starts at `auth`, then navigates to onboarding after authentication. Future deep links into `community`, `post`, verification callbacks, and public-profile routes need explicit unauthenticated behavior.

### F5. API Contract Parity Is Still Manual

Android DTOs are much smaller than the web API usage. Phase 1 should not grow this ad hoc. Pick one:

- generated Kotlin DTOs from the shared contract source
- or a route-by-route contract checklist that must be updated with each feature

Generated DTOs are preferable once the contract package can support Kotlin output.

## Phase 0.5 Work Slice

Goal: make the current foundation honest and ready for real feature work.

Tasks:

- replace fake Very completion with external-launch pending behavior - done
- tighten Self callback validation against pending session - done
- decide and document the native Self launch URL format - done by matching web's Self redirect payload
- add Android App Links/deep-link callback constants in one place - done for verification callbacks
- add route constants for valid settings and moderation sections - done
- document API contract gaps per Phase 1 route
- avoid local full builds on this machine; use the wrapper single-worker Kotlin compile when validation is needed

Acceptance:

- no verification screen can report success without provider/backend proof
- callbacks cannot complete the wrong pending session
- route arguments are constrained where web constrains them
- Phase 1 feature work can start without changing app-level structure again

## Phase 1 Work Slice

Goal: make the logged-in read/create loop real.

Tasks:

- implement real home feed
- implement your communities from backend data
- upgrade community screen with join eligibility, gating, and verification launch states
- upgrade post screen into a thread view; top-level comments, pagination, comment creation, comment voting, reply loading, and reply creation have a first pass
- support community-scoped create post
- make global submit pick a community before composing - first pass uses created communities plus feed top communities
- keep profile/me backed by real profile edit state

Acceptance:

- signed-in user can browse feed, open a community, open a post, read comments, create a text post, and return through native navigation
- backend state transitions match web for the same account
- every shipped screen has loading, empty, error, and retry states

## Phase 2 Work Slice

Goal: bring identity, onboarding, and settings into parity.

Tasks:

- align onboarding phases with web
- support Self verification from onboarding and community-gated flows
- support Very pending flow with download/open fallback
- implement settings sections: `profile`, `preferences`, `agents`
- implement public profile by handle
- add wallet route policy even if wallet-heavy actions remain deferred

Acceptance:

- auth/onboarding outcome matches web account state
- verification-required transitions are native and recover after process death
- public profiles and settings sections are real routes, not stubs

## Phase 3 Work Slice

Goal: community ownership.

Tasks:

- implement create community
- implement moderation index
- implement moderation sections: `profile`, `rules`, `links`, `labels`, `donations`, `pricing`, `namespace`, `gates`, `safety`, `agents`
- integrate namespace verification where backend flow is ready

Acceptance:

- a community owner can perform the mobile-relevant management flows without falling back to web

## Build And Test Policy

This machine is slow. Keep checks narrow.

Preferred local check when SDK is configured:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

Do not use repeated full Gradle builds during spec or static review work. Batch edits before compiling, and prefer CI/Blacksmith for heavier validation.

Minimum future test targets:

- unit tests for `VerificationCoordinator`
- view-model tests for auth missing-config and session exchange
- view-model tests for Self callback success/error/expiry
- repository mapping tests for Phase 1 API calls
- Compose smoke tests for route destinations once UI stabilizes

## Open Questions

- What exact Android deep-link scheme/host/path should Pirate reserve for auth and verification callbacks?
- Should Android support host-based public profiles through App Links, or only explicit in-app profile routes?
- Where should Kotlin DTO generation live if the API contract package becomes the source of truth?
- What Very native SDK and credential model will be used once API-key support is ready?
- Should verification sessions be globally scoped, or should each feature own intent-specific pending state?
