# Pirate Android Package Ownership

This is the working ownership map for native Kotlin feature work.

## Feature Packages

- `auth/`
  Native auth UI, Privy entry, session exchange orchestration.

- `onboarding/`
  Onboarding state, Reddit import flow, rename flow, onboarding-specific screens.

- `community/`
  Community detail, community feed, community-specific post entry points.

- `communities/`
  Multi-community surfaces such as "Your communities".

- `createcommunity/`
  Native create-community flow and future creation state.

- `post/`
  Post thread, composer, and post-specific state.

- `profile/`
  `me`, viewed-user, and public-profile surfaces.

- `inbox/`
  Notifications and inbox-specific surfaces.

- `settings/`
  Settings screen entry and section-specific screens.

- `moderation/`
  Moderation index and moderation section flows.

- `submit/`
  Global submit entry and community-picking flow.

- `verification/`
  Verification providers and verification-driven flows.

## Shared Packages

- `shared/api/`
  Repository interfaces, app-level repository container, API-facing orchestration boundaries.

- `navigation/`
  Route definitions, route builders, and nav host composition.

- `ui/`
  Shared app chrome and reusable native UI primitives.

- `theme/`
  Tokens, theme, and visual system defaults.

## Rules

- New feature work should land in an owning feature package first, not directly under `app/`.
- Do not put new product logic into `MainActivity`.
- Do not add new direct `ApiClient.*` calls in screens or view models; use the repository boundary instead.
- Stub screens should live with their eventual feature owner so they can be replaced in place.
