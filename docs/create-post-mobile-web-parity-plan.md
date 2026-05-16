# Android Create Post Mobile Web Parity Plan

Status: implementation plan from the May 2026 create-post audit.

Goal: close the behavioral gap between Android create post and mobile web without
porting the full web composer in one change. Android should first reach robust
text/link parity, then add media modes behind real upload and policy support.

## Current Gap Summary

Android currently ships a single-screen composer in
`app/src/main/java/sc/pirate/app/post/PostComposerScreen.kt`.

Confirmed gaps:

- Android shows `text`, `image`, `link`, and `song` tabs, but only `text` and
  `link` can submit.
- Android has no mobile web stepper equivalent: write, details, settings, and
  publish preview.
- Android blocks create-post unless `joinEligibility.status == "already_joined"`;
  mobile web also allows community posting roles and routes users through gate,
  verification, join request, and proof-of-work flows.
- Android always sends public identity and public visibility.
- Android validates link posts with non-blank only; mobile web requires a valid
  HTTP URL.
- Android global submit selects a community before composing; mobile web lets a
  draft exist before or during community selection.

Relevant web references:

- `web/src/app/authenticated-routes/create-post-route.tsx`
- `web/src/app/authenticated-state/create-post-state.tsx`
- `web/src/components/compositions/posts/post-composer/post-composer.tsx`
- `web/src/components/compositions/posts/post-composer/post-composer-write-step.tsx`
- `web/src/components/compositions/posts/post-composer/post-composer-submit-actions.tsx`
- `web/src/components/compositions/posts/post-composer/post-composer-publish-settings.tsx`

## Non-Goals

- Do not port the full web composer state tree in one Android PR.
- Do not enable image, video, song, or live submission before native upload,
  preview, and request payload support exist.
- Do not claim route parity while placeholder tabs remain nonfunctional.
- Do not add broad build/type verification on this workstation; follow
  `AGENTS.md` and prefer narrow Kotlin compile only when needed.

## Slice 1: Text And Link Parity

Objective: make Android text/link behavior match mobile web closely enough that
the same draft succeeds or fails for the same reasons.

Implementation:

- Replace string `postType` usage with a local typed model for supported modes.
- Hide or hard-disable `image` and `song` tabs until their implementation slices
  land. Keep the copy explicit if product wants them visible.
- Add valid HTTP URL validation for link posts. Match web's `isValidHttpUrl`
  behavior: only `http://` and `https://` URLs should advance.
- Centralize submit enablement in a pure resolver, equivalent in spirit to web's
  `canAdvanceComposerWriteStep` and `resolveComposerSubmitState`.
- Add title max length and trimming behavior aligned with web where the API
  contract requires it.
- Normalize `linkUrl` before request submission, matching web's
  `normalizeHttpUrl` behavior if Android should allow protocol-less input.

Acceptance:

- Text posts require a nonblank title.
- Link posts require a valid HTTP URL, not just nonblank input.
- Image/song/video/live are not selectable as publishable modes.
- Submit enablement and submit guard use the same resolver.
- A malformed link such as `example` is blocked before the API call.

Suggested files:

- `app/src/main/java/sc/pirate/app/post/PostComposerScreen.kt`
- `app/src/main/java/sc/pirate/app/post/PostComposerState.kt` or equivalent
- `app/src/test/.../PostComposerStateTest.kt`

## Slice 2: Native Mobile Stepper

Objective: align Android's flow shape with mobile web while staying text/link
only.

Implementation:

- Introduce composer steps: `write`, `settings`, and `publish`. Defer `details`
  until a mode needs it.
- Move the existing single-screen fields into a write step.
- Add Android back/close behavior that mirrors mobile web:
  - close exits only from `write`
  - back returns to the previous composer step
  - next advances when the current step is valid
- Replace immediate bottom `Post` on the write step with `Next`.
- Show final publish action only on the publish step.
- Preserve draft state while moving between steps and when access gates open.

Acceptance:

- Tapping Next from write does not submit.
- Publish is unavailable until the publish step.
- Back from publish returns to settings; back from settings returns to write.
- Closing from write returns to the previous route without losing unrelated app
  state.

Suggested files:

- `PostComposerScreen.kt`
- new small composables under `app/src/main/java/sc/pirate/app/post/`

## Slice 3: Preview Step

Objective: show a publish preview before submission, matching the mobile web
expectation that the user sees the post card shape before publishing.

Implementation:

- Reuse the existing Android post-card rendering primitives where possible.
- Build a local preview model from title, body, link URL, identity, and
  visibility.
- For link posts, show a conservative link preview state if Android has preview
  data. If not, show canonical URL/title only and leave richer oEmbed preview
  for a follow-up.
- Surface submit errors on the publish step, not only below the editor.

Acceptance:

- Publish step renders a post-like preview for text and link posts.
- Preview reflects public vs community visibility once Slice 5 lands.
- Submit errors are visible without navigating back to write.

Suggested files:

- `PostComposerScreen.kt`
- `PostScreen.kt` shared post-card components if extraction is warranted

## Slice 4: Access, Roles, And Proof-Of-Work

Objective: close the policy gap where Android only permits already-joined users.

Implementation:

- Load enough community metadata to determine owner/admin/moderator posting
  roles, matching web's `viewerHasCommunityPostingRole` behavior.
- Allow community posting roles to submit even when join eligibility is not
  `already_joined`.
- Add post proof-of-work flow for communities with `altcha_pow` gates.
- Route blocked users to native join, Self, Very, request, or proof-of-work flows
  from create post instead of sending everyone back to the community screen.
- Preserve the draft through all gate and verification detours.

Acceptance:

- Community owners/admins/moderators can publish when web allows them.
- Proof-of-work required communities cannot publish until the proof payload is
  present.
- Verification or join detours return to the same draft.
- Gate failure copy is specific, not a generic "Join this community" message.

Dependencies:

- Android-native Altcha/proof-of-work support or a scoped webview bridge.
- Verified route behavior for Self and Very flows.

## Slice 5: Identity And Audience

Objective: support the identity and audience fields that mobile web sends for
text/link posts.

Implementation:

- Expand Android community DTOs for:
  - `allow_anonymous_identity`
  - `anonymous_identity_scope`
  - `allow_qualifiers_on_anonymous_posts`
  - `allowed_disclosed_qualifiers`
  - gate rules or other fields needed to decide whether public audience is
    allowed
- Add audience selection: public vs community/members-only.
- Add identity selection when anonymous posting is allowed.
- Support stable anonymous label display when the app has enough user/community
  data to derive it.
- Send `anonymous_identity_scope` and disclosed qualifier IDs when selected.
- Keep song, live, and monetized video forced public in later media slices,
  matching web behavior.

Acceptance:

- Communities that disallow anonymous identity force public identity.
- Communities that disallow public audience default to community visibility.
- Text/link request payloads match the web shape for identity and visibility.
- Identity changes affect new post payloads only.

Dependencies:

- Android DTO expansion in `ApiModels.kt`.
- Request model expansion for create-post payload fields.

## Slice 6: Global Submit Draft Parity

Objective: make Android `/submit` behave like a draft-first composer with
community selection, not only a community picker.

Implementation:

- Introduce a create-post draft state that can exist before community selection.
- Let the global submit entry open the composer with `Choose a community`.
- Preserve draft state when the user selects or switches communities.
- Use a known/recent/joined community source rather than only created
  communities plus home top communities.
- If no community is selected, block publish and open the community picker.

Acceptance:

- User can start a text/link draft from global submit before choosing a
  community.
- Switching community does not erase the draft.
- Publish without a community shows a targeted community-required state.

Dependencies:

- A reliable joined/recent communities source, or a local known-community store.

## Slice 7: Media Foundations

Objective: add upload primitives before enabling richer tabs.

Implementation:

- Add native file/photo/video/audio pickers with MIME and size handling.
- Add repository methods for media/artifact upload endpoints used by web.
- Add preview models for image and video.
- Add request helpers for image and video posts.
- Keep song and live split into separate slices because they carry licensing,
  derivative, monetization, setlist, and room-specific behavior.

Acceptance:

- Image tab is publishable only after upload and request support exist.
- Video tab is publishable only after video upload and poster handling exist.
- Media submit helpers have focused unit tests for payload construction.

## Slice 8: Song And Live

Objective: bring advanced post types over after core media is stable.

Implementation:

- Song: audio upload, metadata, lyrics, cover extraction/selection, derivative
  source selection, licensing, monetization, donation share, and submit bundle
  behavior.
- Live: room creation, access mode, schedule, setlist, performer allocations,
  optional cover upload, publish flow, and anchor post navigation.

Acceptance:

- Song and live are absent or explicitly disabled until each path can complete a
  publish flow.
- Advanced monetization and license payloads match web submit helpers.

## Testing Strategy

Prefer pure state and request-construction tests before Compose tests.

Unit tests:

- submit enablement for text/link and disabled media modes
- URL validation and normalization
- step transitions and back/close behavior
- request payload construction for public/community visibility
- anonymous identity payload construction once supported
- owner/mod posting allowance resolver

Compose smoke tests:

- write step renders title/body/link fields
- Next appears before Publish
- publish preview renders title/body/link
- blocked gate state preserves draft

Manual verification:

- create text post from community route
- create link post from community route
- malformed link is blocked locally
- global submit draft survives community selection
- create-post detours through verification/proof flows without draft loss

## Recommended PR Order

1. Text/link validation and disabled media tabs.
2. Stepper state and navigation.
3. Preview step.
4. Access roles and proof-of-work.
5. Identity and audience request parity.
6. Global submit draft parity.
7. Image upload and submit.
8. Video upload and submit.
9. Song.
10. Live.

Each PR should update this document's status notes or remove completed checklist
items from the relevant slice.
