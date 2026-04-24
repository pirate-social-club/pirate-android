# Pirate Android Phase 1 Contract Gaps

Status: working checklist for implementing the logged-in read/create loop.

This file tracks the API surface Android needs before Phase 1 can be called product-real. The source of truth is `api/services/contracts/src/index.ts`; web usage in `web/src/lib/api` shows how those contracts are consumed today.

## Current Android Gap Summary

Android currently has narrow DTOs for `Community`, `Post`, `LocalizedPostResponse`, profile, onboarding, and verification. Those are enough for a prototype, but they do not cover the web app's Phase 1 state:

- home feed items and top communities
- community preview/sidebar metadata
- join eligibility and gated membership
- localized feed sorting and pagination params
- post vote state
- thread comments, replies, and comment votes
- richer post types and thread snapshots
- notification/inbox feed - first-pass Android tasks/feed/dismiss/pagination is now wired

Until Kotlin DTO generation exists, each Phase 1 feature should explicitly add the missing contract types and endpoint methods listed below.

## Required Endpoints

| Feature | Endpoint | Web reference | Android status |
| --- | --- | --- | --- |
| Home feed | `GET /feed/home?cursor&locale&sort&time_range` | `client.feed.home` | Repository/client added; UI first pass added |
| Community detail | `GET /communities/:communityId` | `client.communities.get` | Partial DTO |
| Community preview | `GET /communities/:communityId/preview?locale` | `client.communities.preview` | Repository/client added; community UI first pass added |
| Join eligibility | `GET /communities/:communityId/join-eligibility` | `client.communities.getJoinEligibility` | Repository/client added; community UI first pass added |
| Join community | `POST /communities/:communityId/join` | `client.communities.join` | Present, thin response |
| Community posts | `GET /communities/:communityId/posts?cursor&flair_id&limit&locale&sort` | `client.communities.listPosts` | Present, missing params and rich DTOs |
| Create post | `POST /communities/:communityId/posts` | `client.communityContent.createPost` | Text/link first pass; media/song/flair/policy remain |
| Post detail | `GET /posts/:postId?locale` | `client.posts.get` | Present, missing locale param |
| Post vote | `POST /posts/:postId/vote` | `client.posts.vote` | Repository/client added; home feed UI wired |
| Top-level comments | `GET /communities/:communityId/posts/:postId/comments?cursor&limit&locale&sort` | `client.communityContent.listComments` | DTO/client/repository added; post thread UI first pass added |
| Create top-level comment | `POST /communities/:communityId/posts/:postId/comments` | `client.communityContent.createComment` | DTO/client/repository added; post thread composer first pass added |
| Comment replies | `GET /comments/:commentId/replies?cursor&limit&locale&sort` | `client.comments.listReplies` | DTO/client/repository added; post thread reply loading first pass added |
| Create reply | `POST /comments/:commentId/replies` | `client.comments.createReply` | DTO/client/repository added; post thread reply composer first pass added |
| Comment vote | `POST /comments/:commentId/vote` | `client.comments.vote` | DTO/client/repository added; post thread top-level vote UI first pass added |

## DTOs To Add Or Expand

### Home Feed

Add:

- `HomeFeedCommunitySummary` - added
- `HomeFeedItem` - added
- `HomeFeedResponse` - added
- `HomeFeedSort` - represented as string pending typed enum/sealed class

Important fields:

- `items`
- `top_communities`
- `next_cursor`
- post vote fields nested inside `LocalizedPostResponse`

### Post And Feed Items

Expand current `Post` and `LocalizedPostResponse` to include at least:

- post type: `text`, `image`, `video`, `link`, `song`
- visibility and status
- title/body/caption/link metadata
- media/embed references
- author identity mode and anonymous scope fields
- `thread_snapshot`
- `upvote_count`
- `downvote_count`
- `viewer_vote`
- `source_language`
- `translation_state`
- `machine_translated`
- translated text fields where supplied

Phase 1 can render text/link/video fields conservatively, but it should not discard response data needed for voting, comments, or localization.

### Community

Expand current `Community` and add `CommunityPreview`.

Phase 1 needs:

- display name, description, avatar/banner refs
- membership mode
- member/follower counts
- viewer membership/following state
- membership gate summaries
- rules and sidebar-equivalent metadata
- post policy bits needed by composer
- label/flair fields if returned by preview/detail

### Join Eligibility

Add `JoinEligibility` and supporting gate summary types.

Required fields:

- `community_id`
- `membership_mode`
- `human_verification_lane`
- `joinable_now`
- `status`
- `membership_gate_summaries`
- `missing_capabilities`
- `suggested_verification_provider`
- `suggested_verification_intent`
- `failure_reason`
- `wallet_score_status`

Android community and create-post flows should treat `already_joined` as the only state that enables posting.

### Comments

Added enough for top-level thread rendering:

- `Comment`
- `CommentListItem`
- `CommentListResponse`
- `CreateCommentRequest`

Still missing or pending:

- `CommentThreadSnapshot` as a distinct type; Android currently reuses `ThreadSnapshot`
- `CommentContext`
- `PostVoteResponse` - added
- `CommentVoteResponse` - added

Required behavior:

- render top-level comments on post thread - first pass added
- create top-level comments - first pass added
- load replies for a comment - first pass added
- create replies - first pass added
- vote on top-level comments - first pass added
- preserve `viewer_vote`, `upvote_count`, `downvote_count`, `score`, `depth`, and reply counts

## Repository Additions

Add or expand repository interfaces:

- `FeedRepository.home(...)`
- `CommunityRepository.getPreview(...)`
- `CommunityRepository.getJoinEligibility(...)`
- `CommunityRepository.listPosts(...)` with cursor, flair, limit, locale, sort
- `PostRepository.getPost(...)` with locale
- `PostRepository.votePost(...)`
- `PostRepository.listComments(...)` - added for top-level comments
- `PostRepository.createComment(...)` - added for top-level comments
- `PostRepository.listReplies(...)` - added for replies pending a dedicated repository
- `PostRepository.createReply(...)` - added for replies pending a dedicated repository
- `PostRepository.voteComment(...)` - added for comment votes pending a dedicated repository
- `CommentRepository.listTopLevel(...)` or equivalent dedicated comment repository, if the API surface grows enough to split from `PostRepository`
- `CommentRepository.listReplies(...)`
- `CommentRepository.createReply(...)`
- `CommentRepository.voteComment(...)`

Do not call `ApiClient.*` directly from new screens or view models.

## Phase 1 Implementation Order

1. Add DTOs and repositories for home feed and post vote. Started for both.
2. Implement native home feed with loading, empty, error, retry, sort, top communities, and vote state. First pass has loading/empty/error/retry/top communities and vote UI; sort controls remain.
3. Expand community DTOs and add preview + join eligibility. Started.
4. Upgrade community screen with gated join state and post list parity. First pass added with post pagination, Self launch, and preview metadata; verification retry states remain.
5. Add comment DTOs and repository methods. Started for top-level comments and replies.
6. Upgrade post screen into a real thread view. First pass added for post body, paginated top-level comments, top-level composer, top-level comment voting, reply loading, and reply creation.
7. Upgrade create-post flow only after eligibility and community policy data are available. First pass supports text/link posts, blocks posting unless join eligibility is `already_joined`, and opens the created post after submit; policy/flair/media fields remain.

## Audit Follow-Up Status

The April 2026 Android v0 audit identified several foundation issues. Current status:

- API calls now execute blocking OkHttp work on `Dispatchers.IO` inside `ApiClient.request`.
- `SessionStore` now caches the decoded session/access token after first read, set, clear, or observation.
- `Communities.listPosts` now uses the shared query builder and accepts `cursor`, `flair_id`, `limit`, `locale`, and `sort`.
- `CommunityScreen` now uses a single `LazyColumn` for community metadata and posts, avoiding nested scroll containers.
- `CommunityViewModel.loadCommunity` now fetches detail, preview, join eligibility, and first post page concurrently.
- `PostComposerScreen` now uses the view model as the only owner of title/body state.
- `SelfVerificationScreen` accepts a `verificationIntent` parameter and clears callback data only after backend completion succeeds.
- Self verification navigation now carries a validated intent path segment, and community join gates can launch Self with `community_join`.
- `PostScreen` reconciles successful comment votes from the pre-optimistic comment item before replacing the row.
- Home feed, community posts, and top-level comments now preserve `next_cursor` and expose explicit load-more actions.
- Comment replies now have first-pass list, pagination, and create support on the post thread screen.
- Community preview rules, reference links, and membership gate summaries now render as first-pass sidebar-equivalent sections.
- Global submit now has a first-pass community picker backed by public-profile created communities plus home feed top communities.
- Your communities now has a first-pass created communities list from the user's public profile handle.
- Public profile now has a first-pass handle-backed screen with profile details, canonical handle notice, and created communities.
- Create community now sends the public v0 `POST /communities` payload for standard centralized open/request communities and navigates to the accepted community.
- Settings now has first-pass profile editing, handle rename, and preferred-locale updates backed by profile endpoints.
- Inbox now consumes notification tasks/feed, marks activity read on load, supports task dismiss, opens thread activity, and paginates feed activity.
- Namespace verification now has a first-pass Android happy path: create community opens namespace verification, starts/saves pending sessions, checks completion, attaches verified namespaces, and returns to the community.
- The mobile shell footer now mirrors mobile web with Home, Wallet, Create, Inbox, and Profile; Wallet is still an owned placeholder.
- The app now starts on Home instead of forcing auth before rendering the shell.
- Post composer now checks join eligibility and blocks submission unless the viewer is already joined.
- Post composer now navigates to the created post using the returned `LocalizedPostResponse`.
- Post composer now sends contract-aligned `idempotency_key`, identity, translation policy, visibility, and link fields for text/link posts.

Still pending:

- deeper post composer policy parity with web community rules/flair/media fields
- a dedicated joined/recent communities endpoint or local known-community store for global submit beyond created/top communities
- focused view-model tests once a careful single-worker compile passes

## Build Policy

Use Blacksmith-backed GitHub Actions as the default Android verification path. The compile-only workflow is [android-compile.yml](/home/t42/Documents/pirate-workspace/android/.github/workflows/android-compile.yml), runs on `blacksmith-4vcpu-ubuntu-2404`, and checks `:app:compileDebugKotlin`.

Manual trigger:

```bash
rtk gh workflow run android-compile.yml --ref main
```

Keep local validation narrow on this machine. Only run local Gradle when remote CI is not practical and swap pressure is low. When SDK config exists, use:

```bash
rtk timeout 240 env \
  PIRATE_ANDROID_SLOW=1 \
  PIRATE_ANDROID_MAX_WORKERS=1 \
  GRADLE_OPTS="-Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1 -Dorg.gradle.priority=low -Dorg.gradle.vfs.watch=false" \
  ./scripts/androidw.sh --no-daemon --console=plain --offline :app:compileDebugKotlin
```

Use heavier builds only through Blacksmith CI when a feature slice is ready for artifact validation.
