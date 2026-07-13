# Android ↔ Web Parity Audit & Implementation Plan — 2026-07-10 (rev 2)

Provenance: android audited at origin/main `501686a` (clean tree); web audited at
origin/main `fa63ed20` (detached read-only checkout). Rev 2 incorporates a second
adversarial review; every material correction below was re-verified against the code.

Build/verify policy: use Blacksmith CI (`android-compile.yml` / `android-ci.yml` +
`scripts/install-blacksmith-apk.sh`), never local Gradle — see README.md. The
push/PR `android-ci` workflow now runs unit tests, Android lint, debug assembly,
and uploads the APK plus quality reports. `android-compile.yml` may be disabled;
use the enabled Blacksmith workflow rather than falling back to local Gradle.

## Implementation ledger — 2026-07-13

The audit below remains the historical baseline. On branch
`codex/android-p0-release-integrity`, the following work is implemented and
Blacksmith-verified; do not rebuild it:

- **Release integrity:** post/comment reporting, account-deletion entry point,
  encrypted session migration, streaming/cancellable uploads with stable draft
  idempotency, privacy-safe Sentry crash/ANR reporting, verified App Links and
  native sharing, honest Play metadata/wallet presentation, a test+lint CI gate,
  and account-scoped encrypted user blocking with feed/comment suppression,
  settings-based unblock management, and XMTP consent enforcement. Cross-device
  blocking still needs the sibling API contract in `docs/user-blocking-api-contract.md`.
  Android also has a versioned, encrypted first-UGC Terms gate across native
  publishing/messaging/profile surfaces with legal-document links and settings
  status; server enforcement is specified in `docs/terms-acceptance-api-contract.md`.
- **Reddit-tier interaction pass:** automatic pagination and visible home sorting,
  skeleton/Snackbar states, motion and haptics, expandable comment threads,
  RTL/font-scaling/semantics fixes, and encrypted composer draft recovery.
- **Parity rocks:** native Song Study audio transcription; native karaoke capture,
  playback, timed lyrics, reconnect and protocol handling; wallet receive,
  connected-chain balance and guarded native send; owned-agent settings and
  encrypted signing; validated song royalty splits; native crosspost and safe
  replay publishing; public booking availability browsing; and the mobile
  moderation subset (active queue, membership requests, rules).

Latest verified moderation commits and runs:

- `98134b9` membership requests — `android-ci` run `29235618226`.
- `be2fb3c` community rules — `android-ci` run `29237278305`.
- `efe4f9f` active moderation queue — `android-ci` run `29238926469`.

Latest verified release-safety commits and runs:

- `2a57ddc` account-scoped user blocking — `android-ci` run `29240802645`.
- `ff285b0` versioned first-UGC Terms gate — `android-ci` run `29243366968`.

Still-open release blockers remain: server-backed cross-device block and Terms
enforcement, and legal finalization of the published Terms dispute section;
the Play Billing/alternative-billing/geo-gating decision for digital goods; the
API-event and XMTP push systems; and interactive device QA for study/karaoke.
Paid booking settlement and paid/included replay publishing remain deliberately
gated until their payment/production-readiness decisions are resolved.

---

## Where we are (verified)

Android (`sc.pirate.mobile`, `0.1.0-alpha.7`, versionCode 9, Play alpha) is a
Kotlin/Compose app with a genuinely complete core and a thin production/compliance
shell.

**Built and solid (do not rebuild):**
- Auth via Privy (Google, X, email code, wallet via Reown), session refresh,
  logged-out browsing, PoW/ALTCHA gate.
- Home + community feeds (keyed LazyColumn, optimistic voting, pull-to-refresh,
  in-memory TTL cache), post detail + comments + voting.
- Composer for text/image/video/link/song/live, incl. song original/remix +
  license presets + derivative source search; live solo/duet, free/gated/paid,
  scheduling, setlist. **Anonymous posting identity select already exists**
  (`post/PostComposerScreen.kt:1681`, `canUseAnonymousIdentity()`).
- **Community feed sorting already exists** (Best/New/Top,
  `community/CommunityScreen.kt:418`). Home has sort/time-range state and repo
  plumbing but **no visible control** — that's UI wiring, not a build.
- Livestream viewer (WebView Agora) + broadcaster (native Agora RTC), XMTP chat,
  profiles, in-app inbox, USDC checkout signing stack, Self/Very verification +
  age gates, custom-scheme deep links, bottom tabs (`MainActivity.kt:124-166`) + drawer.

**Existing but unmerged work (extend, don't rebuild):**
- **Song study: two open PRs.** PR #2 "Add native Song Study" (900 lines, mergeable)
  and older PR #1 "Add native Song Study (P1)" (3859 lines, mergeable UNKNOWN).
  Determine canonical (likely #2 supersedes #1 — confirm, then close the loser with
  a supersede link). #2's say-it-back uses typed text; current web records audio and
  transcribes (`web src/app/authenticated-routes/study-route.tsx:608`) — needs a
  recording upgrade.
- **Native karaoke: PR #3** (draft, 2170 lines, 11 commits, 6 tests, reconnect
  handling, mic capture, playback, timed lyrics, server events). Blocker is
  interactive device QA, not absence. The WebView karaoke spike branch is a single
  viability commit — WebView is the FALLBACK, not the plan.

**Release-compliance gaps (Google Play UGC/social requirements):**
- "Report post" and "Save post" are rendered **disabled** with no-op handlers
  (`home/HomeScreen.kt:~1251`); no user-block flow anywhere. Play UGC policy
  requires content reporting AND user blocking (incl. for DMs). The API already
  exposes post/comment report endpoints (api openapi spec) — reporting is
  immediately actionable client-side; blocking needs a backend (or persisted
  local-block) design.
- No in-app account-deletion path. Web already serves `/delete-account`
  (`web src/worker.tsx:493`). Play requires an in-app path + web resource.
- USDC checkout buys access to digital content (songs/live rooms). Play Payments
  policy requires Play Billing (or enrolled alternative-billing / geo-gating) for
  in-app digital goods — a **decision is required** before this flow can be called
  production-ready on Play. Flag to owner; do not silently ship.
- Play listing overstatement is broader than the F-Droid flavor: full_description
  advertises playlist flows, Pirate-assistant chat, study and streak features
  (`fastlane/metadata/android/en-US/full_description.txt`) plus Learn/Schedule
  screenshots — none on main. Audit every listing string/screenshot.

**Correctness/robustness gaps:**
- Composer reads entire videos/images/song artifacts into `ByteArray` before upload
  (`PostComposerScreen.kt:644` area, `readBytes()`); no size limits, progress,
  cancellation, or resume → OOM risk on large videos.
- Publish idempotency keys are regenerated per attempt
  (`UUID.randomUUID()` at `PostComposerScreen.kt:424,433,603`) → duplicate posts
  after ambiguous timeouts. Need stable per-draft keys + partial-publish recovery.
- Wallet screen shows **hard-coded zero balances for 9 assets**, routes Send/Receive
  to wallet-connect, disabled royalty claim (`wallet/WalletScreen.kt:56`). This is
  fake data in prod — hide or label the shell immediately.
- Session tokens stored plaintext in DataStore (`api/SessionStore.kt:16`); encrypt
  via AndroidKeyStore AES/GCM (pattern: `security/LocalSecp256k1Store.kt`) with
  migration + corruption tests.
- No crash/ANR reporting or analytics of any kind.
- No FCM. Two separate push systems are needed: API-event push (requires an api-side
  device-token + send pipeline that does not exist — sibling backend task) and
  XMTP DM push (XMTP traffic doesn't transit the Pirate API; separate mechanism).
- Settings: **Domains and Agents sections are explicit stubs**
  (`settings/SettingsScreen.kt:779` "web-only for this v0"); moderation dashboard is
  a stub except `namespace` (web has 22 sections).
- Empty/inline error states largely exist — the missing layer is skeletons,
  consistent action feedback, and pagination polish, not a wholesale rebuild.
- Nav transitions disabled, no haptics, dark-only theme, manual "Load more" on home
  (`home/HomeScreen.kt:972`), Coil defaults (no crossfade/placeholders).

---

## Prioritized todo plan

### P0 — Release safety & correctness

1. **UGC compliance:** wire "Report post" (+ comment report) to the existing API
   endpoints; design+implement user blocking (backend sibling task or persisted
   local block, spec first); terms-of-service acceptance before first UGC upload.
   **Android local enforcement is implemented; the documented server sibling task
   remains open for cross-device and query-layer enforcement. Android's versioned
   local Terms gate is also implemented; the API sibling task and the published
   Terms' explicit counsel-finalization note remain release blockers.**
2. **Account deletion:** in-app entry point (settings) linking the flow; reuse web's
   `/delete-account` resource; meet Play's in-app + web requirement.
3. **Payments decision (owner decision, blocks Play production):** Play Billing vs
   alternative-billing enrollment vs geo-gating for USDC digital-content checkout.
   Surface options; do not ship silently.
4. **Kill fake wallet data:** hide the zero-balance asset list and disabled royalty
   claim, or label clearly as placeholder. Audit all fastlane listing text +
   screenshots against main (playlist/assistant/study/streak/Learn/Schedule/F-Droid).
5. **Encrypt session storage** with plaintext→ciphertext migration and corruption
   tests (keystore pattern in `security/LocalSecp256k1Store.kt`).
6. **Upload robustness:** streaming (not `ByteArray`) uploads, file-size limits,
   progress UI, cancellation, stable per-draft idempotency keys, partial-publish
   recovery.
7. **CI gate:** run `:app:testDebugUnitTest` + `:app:lintDebug` in PR and release
   workflows; add ktlint/detekt with baseline.
8. **Crash/ANR reporting** (Sentry or Crashlytics) with release symbolication in
   Blacksmith workflows and PII scrubbing. (Product analytics is a SEPARATE later
   item — needs event taxonomy, privacy-policy/Data Safety updates, credentials.)
9. **Verified HTTPS App Links** for web post/community URLs (assetlinks.json) +
   native share sheet; today only custom `pirate://` schemes exist.
10. **Push spec:** write the API device-token/send-pipeline contract (backend
    sibling task) and the XMTP DM push design as two separate specs; implement FCM
    client once the API side exists.

### P1 — Daily product feel

11. **Auto-pagination** for home/community/inbox and top-level comments (replace
    the "Load more" button; `snapshotFlow` pattern exists at
    `community/CommunityScreen.kt:528`); keep nested "more replies" explicit.
12. **Expose existing home sort controls** (state+plumbing already present); do NOT
    rebuild community sort; `/popular` on web is just home with `initialSort="best"`
    — a trivial entry point, not a rock.
13. **Composer autosave + upload recovery** (builds on P0 item 6).
14. **Loading polish:** skeleton shimmer for feed/post/profile, stable media aspect
    ratios (no layout jump), Coil crossfade/placeholder/error painters + cache
    sizing, media prefetch.
15. **Action feedback:** targeted Snackbars over Toasts; wire share/delete/report/
    block into post overflow (report/block from P0).
16. **Comment thread UX:** collapse/expand, smooth own-comment insertion,
    scroll-to-new.
17. **Motion + haptics:** contextual nav transitions (currently disabled,
    `PirateNavHost.kt:108-111`), correct bottom-tab back behavior, restrained
    haptics on vote/join/refresh.
18. **Accessibility pass:** font scaling, RTL verification, touch targets,
    adaptive layouts.

### P2 — Feature parity

19. **Song study:** pick canonical study PR (#2 likely; supersede-close #1 with a
    link), rebase onto main, add native audio recording + transcription for
    say-it-back to match web, focused tests. Target only what's live on web prod;
    streak GA is dark server-side — gate identically.
20. **Native karaoke:** rebase PR #3, run interactive device QA (mic capture,
    reconnect, scoring round-trip); WebView spike only as fallback if native QA
    fails.
21. **Wallet in honest slices:** Receive (address/QR) → balances for supported
    chains (note: web reads balances largely from direct chain RPC + price data,
    NOT one reusable endpoint — implement per-chain incrementally) → limited Send →
    royalty claim.
22. **Agents:** agent settings/signing first (currently stubbed), THEN agent-author
    composer identity. (Anonymous identity already exists — do not rebuild.)
23. **Royalty allocations in publish settings**, crosspost, replay publishing.
24. **Bookings slices:** free browsing/host discovery/non-paid flows are fine to
    build; **paid settlement stays gated** — the prod canary runs Base Sepolia with
    unattended settlement disabled (`api docs/runbooks/paid-bookings-prod-canary.md`)
    and real-money session-ops defects remain open.
25. **Moderation subset:** queue, requests (approve/deny), rules; keep
    FeatureStubScreen + "use web" pointer for the rest of the 22 sections.

### P3 — Structural long tail

26. Room/offline read layer (replaces in-memory-only caches).
27. Light/system theme + dynamic color (tokens centralized in `theme/PirateTokens.kt`).
28. Broader moderation sections; Domains settings.
29. Baseline profiles, Compose compiler metrics, tighten over-broad proguard keeps.
30. Fuller localization; non-EVM wallet support.
31. Product analytics (taxonomy + privacy/Data Safety updates), UI test harness in CI.

### Cautions for the implementing agent
- Do NOT add a global OkHttp disk cache/retry interceptor for authenticated APIs
  without explicit server cache semantics, logout eviction, and per-endpoint retry
  rules (idempotent-GET-only at most).
- Verify "absent" claims with a grep before building — this area moves fast, and
  rev 1 of this audit had several false gaps (sorting, anonymous identity) caught
  in review.
- Every phase: Blacksmith CI + on-device install
  (`scripts/install-blacksmith-apk.sh --launch`) before calling it done. Keep
  independently-valuable phases on separate branches/PRs.
