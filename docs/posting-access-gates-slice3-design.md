# Android Posting Access Gates Design

Status: design lock for the next implementation slice. No app code in this
document.

Scope: add native posting-access gates to the Android post composer so the app
matches the newer web composer policy flow before submit. This corresponds to
the "Access, Roles, And Proof-Of-Work" slice in
`create-post-mobile-web-parity-plan.md`, but the current branch series calls it
Slice 3 because contract and draft plumbing were split out first.

## Problem

Android currently lets the composer build a draft, validates local post fields,
then attempts submit. Proof-of-work is handled by `PoWGate.execute(...)` only
after the backend rejects the request. Other access states are not modeled in
the composer path:

- join required
- join request required
- Self or Very verification required
- membership gates failed
- post proof-of-work required
- owner/admin/mod posting-role bypass
- draft preservation through those detours

The community screen already has a first-pass join gate surface. The post
composer needs its own pre-submit access gate because submit policy is not the
same as browsing or joining, and because the composer must preserve the draft.

## Goals

- Block publish before submit when posting access is known to be missing.
- Keep the post draft intact through join, verification, PoW, and retry flows.
- Separate join proof-of-work from post proof-of-work.
- Keep PoW solving out of draft state; draft state should only describe the
  current gate and UI state.
- Reuse existing community join helpers and text where possible.
- Preserve current basic posting behavior for already-joined users and community
  posting roles.

## Non-Goals

- No user-agent submit wiring. API issue `pirate-social-club/api#38` remains the
  blocker for agent-authored writes.
- No audience or qualifier UI. That is the later identity/audience settings
  slice.
- No full redesign of community join. The composer may call existing join and
  verification routes, but it should not replace the community screen.
- No local Gradle verification. Use Blacksmith `android-compile.yml` after code
  lands.

## Existing Building Blocks

- `PostComposerUiState.draft`: preserves cross-mode draft state from Slice 2.
- `PostComposerScreen.loadEligibility(...)`: already loads `JoinEligibility`,
  `CommunityPreview`, profile, and posting role.
- `PoWGate`: solves ALTCHA after a backend `gate_failed` response, but currently
  has no explicit modal/progress model.
- `CommunityScreen`: has join eligibility copy, gate summary formatting, Self
  launch path, and join PoW behavior that can be extracted or mirrored.
- `MembershipGateSummary`: already in API models and rendered on the community
  screen.
- Verification screens:
  - `SelfVerificationScreen`
  - `VeryVerificationLauncher`
  - `VerificationCoordinator`

## State Model

Add composer-owned gate state, not mode-owned gate state.

```kotlin
data class ComposerGateState(
    val status: ComposerGateStatus = ComposerGateStatus.NeedsCommunity,
    val joinEligibility: JoinEligibility? = null,
    val gateSummaries: List<MembershipGateSummary> = emptyList(),
    val postProofOfWorkRequired: Boolean = false,
    val postProofOfWorkSolving: Boolean = false,
    val joinProofOfWorkSolving: Boolean = false,
    val verificationProvider: String? = null,
    val verificationIntent: String? = null,
    val message: String? = null,
)

enum class ComposerGateStatus {
    Unknown,
    Loading,
    Allowed,
    NeedsCommunity,
    NeedsSignIn,
    NeedsJoin,
    NeedsJoinRequest,
    JoinRequestPending,
    NeedsVerification,
    NeedsJoinProofOfWork,
    NeedsPostProofOfWork,
    GateFailed,
    Banned,
}
```

This should live on `PostComposerUiState`, not inside
`CreatePostDraftState`. Gate state is contextual to the selected community and
current session, while the draft is the user's content.

`verificationIntent` is the backend-provided intent passed to the selected
verification detour. `message` is resolver-owned status copy, except that a
load or backend failure may replace it with the returned failure reason.

Computed helpers should decide:

- `viewerHasPostingAccess(state)`
- `submitBlockedByGate(state)`
- `primaryGateAction(state)`
- `gateStatusText(state)`
- `gateRequirementRows(state)`

## Access Decision

Posting is allowed when any of these are true:

- viewer has owner/admin/mod role in the community
- eligibility status is `already_joined`
- eligibility status is otherwise known allowed by backend policy for posting

Posting is blocked when:

- no authenticated session
- no selected community
- eligibility is loading
- eligibility status is `requestable`
- eligibility status is `pending_request`
- eligibility status is `verification_required`
- eligibility status is `gate_failed`
- eligibility status is `banned`
- post PoW is required and not yet satisfied for the pending submit

Proof-of-work is special:

- Join PoW belongs to the join flow and should not be treated as post PoW.
- Post PoW belongs to publish and must be surfaced after local post validation,
  before or during the publish attempt.

## UI Shape

Use one composer gate panel on the publish step.

Panel contents:

- short title, for example `Posting access required`
- status copy based on `JoinEligibility.status`
- requirement rows from `membershipGateSummaries`
- primary action button
- secondary retry or refresh action when useful

The submit button should remain visible but disabled when a known gate blocks
posting. The disabled reason should match the panel copy.

Primary action mapping:

- `NeedsCommunity`: open community selection
- `NeedsSignIn`: navigate to auth
- `NeedsJoin`: join community
- `NeedsJoinRequest`: submit join request
- `JoinRequestPending`: disabled informational state
- `NeedsVerification`: launch Self or Very based on `suggestedVerificationProvider`
- `NeedsJoinProofOfWork`: run join PoW path
- `NeedsPostProofOfWork`: run post PoW path
- `GateFailed`: no primary action unless provider-specific recovery is known
- `Banned`: no primary action

Do not combine join PoW and post PoW in one UI. They should have separate
progress labels and separate state fields.

## Verification Detours

Self and Very should behave like detours, not separate drafts.

Before navigating away:

- leave `PostComposerUiState` intact
- keep selected community id
- keep current composer step
- set gate status to `NeedsVerification`

After returning:

- refresh eligibility
- keep draft fields unchanged
- if eligible, gate status becomes `Allowed`
- if still blocked, show the refreshed gate panel

The design assumes verification completion is session-backed by the backend.
Android should not invent local proof state.

## Proof-of-Work

Keep `PoWGate` as the solver/use-case layer, but do not let it be the only user
feedback.

Implementation target:

- `ComposerGateState.joinProofOfWorkSolving`
- `ComposerGateState.postProofOfWorkSolving`
- explicit progress label in the gate panel
- retry after PoW payload is available
- clear solving flags in `finally`

For the first implementation, it is acceptable for `PoWGate` to still fetch,
solve, and retry internally. The composer must surface which PoW is being solved
and preserve the draft while that happens.

## Submit Flow

Target sequence:

1. Run local draft validation.
2. Resolve current gate state.
3. If blocked, show gate panel and stop.
4. If post PoW is known required, show post PoW progress and run post PoW flow.
5. Build request and submit.
6. If backend returns a gate response that was not predicted, update gate state
   and keep the draft.
7. Only clear/navigate after a successful post response.

The current `createPostWithProofOfWork(...)` helper can stay during the first
implementation, but submit must no longer feel like a generic loading spinner
when the real operation is PoW.

## Reuse And Extraction

Good extraction candidates from `CommunityScreen`:

- `gateSummaryText(...)`
- `verificationProviderLabel(...)`
- `formatCountryRequirement(...)`
- `requiresProofOfWork(...)`
- `communityAltchaAction(...)`
- Self capability mapping

Prefer moving those to a small shared helper file rather than copying them into
`PostComposerScreen.kt`.

## Acceptance Criteria

- Already-joined users can publish unchanged.
- Owners/admins/moderators can publish when web allows posting-role bypass.
- Requestable communities show request copy and action before submit.
- Pending requests cannot publish and preserve the draft.
- Self/Very-required communities show provider-specific action and preserve the
  draft through return.
- Join PoW and post PoW are visually distinct states.
- Backend gate rejection after submit updates gate state instead of losing draft.
- Draft survives all gate detours, including title/body/media/song/live state.
- No agent-authored submit behavior is added.

## Test Plan

Unit tests first:

- posting-role bypass resolver
- gate status resolver for each `JoinEligibility.status`
- PoW-required resolver
- primary action resolver
- draft-preservation reducer paths
- gate-failed state preserves the draft and surfaces `failureReason`

Compose smoke tests can follow once the panel lands:

- publish step shows gate panel for blocked eligibility
- submit button is disabled with blocked reason
- proof-of-work solving label appears for post PoW

Manual verification:

- already joined text post still publishes
- requestable community blocks publish and shows request action
- Self-required community launches verification and returns to same draft
- post PoW community shows solving progress before publish

## Implementation Slices

Recommended split:

1. Shared gate helper extraction plus unit tests.
2. Composer gate state and resolver tests.
3. Publish-step gate panel UI, no verification/PoW actions yet.
4. Join/request/Self/Very actions.
5. Explicit post PoW progress and retry handling.

Keep each slice Blacksmith-verified before moving to the next.
