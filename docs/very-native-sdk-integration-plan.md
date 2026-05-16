# Very Native SDK Integration Plan

Status: Android uses the native SDK path only; production rollout remains gated on Very confirmation for `redirect_uri` and cross-flow nullifier semantics.

Scope: replace Android's current external Very launch path with the Very Native SDK for palm scans while preserving the existing web widget path and backend uniqueness guarantees.

Implemented foundation:

- API persists `verification_sessions.provider_mode` and accepts `native_sdk`.
- API can create native Very sessions and complete `{ mode: "native_sdk", code }` payloads through backend OAuth token exchange.
- Android uses the native SDK when `VERY_SDK_KEY` is present and `VerySDK.isSupported(context)` passes.
- Android shows a hard error when native SDK support, app config, or backend native OAuth config is unavailable.
- Privacy policy now names VeryAI palm verification.

## Current Baseline

- Android starts a Pirate `very` verification session with `provider_mode = "native_sdk"`, requires a native launch response, invokes the Very SDK, and completes the session with the returned authorization code.
- The API creates Very sessions as widget sessions and completes them by sending a widget proof to the Very verifier.
- API config currently uses `VERY_APP_ID`, `VERY_API_URL`, `VERY_VERIFY_URL`, and `VERY_BRIDGE_API_URL` for the widget path.
- Android `minSdk` is currently 28. Very's native Android SDK requires Android 10/API 29+ according to the integration docs.

Android no longer exposes the widget/deep-link path. Web may remain widget-based unless product explicitly chooses a broader privacy model change.

## Gate 1: Very Confirmation

Owner: provider integration / API.

This is the blocking ticket. Do not ship native SDK completion until Very confirms these points:

- What `redirect_uri` value must be sent during backend token exchange for native SDK auth codes.
- Whether the native SDK supports `state`, `nonce`, or PKCE, and where the app passes those values.
- The expected auth code lifetime and replay behavior.
- Whether `id_token.sub` is stable enough to enforce unique-human identity for Pirate.
- Whether `id_token.sub` is the same underlying palm uniqueness namespace as the existing widget proof nullifier, or whether Very provides a cross-flow nullifier.

Acceptance:

- The selected `redirect_uri` is documented and registered with Very.
- The completion design includes either `state`/`nonce`/PKCE validation or a written reason it is unavailable.
- The nullifier source is documented as one of:
  - cross-flow stable with existing widget nullifiers
  - native-only stable, requiring a migration/linking policy
  - not stable enough, blocking native completion

## Gate 2: Persist Provider Mode

Owner: API/contracts.

Problem:

- `provider_mode` currently exists in the public contract, but the API derives it during serialization.
- Native SDK sessions need durable mode ownership so old widget sessions and new native sessions serialize correctly.

Tasks:

- Add a `provider_mode` column to `verification_sessions`.
- Backfill existing `provider = 'very'` rows with `widget` when appropriate.
- Store `provider_mode` from `StartVerificationSessionRequest`.
- Extend contract unions:
  - `StartVerificationSessionRequest.provider_mode`: add `"native_sdk"`.
  - `VerificationSession.provider_mode`: add `"native_sdk"`.
  - `VerificationSessionLaunch.mode`: add `"native_sdk"`.
- Update serializers to read stored `provider_mode` rather than inferring `widget`.
- Keep existing `self` sessions as `qr_deeplink`.

Acceptance:

- Existing widget Very tests still pass unchanged.
- A newly started native SDK Very session serializes as `provider_mode: "native_sdk"` and `launch.mode: "native_sdk"` or no provider launch payload.
- Existing serialized widget sessions still report `provider_mode: "widget"`.

## Ticket 3: API Native Completion

Owner: API.

Depends on: Gate 1 and Gate 2.

Completion payload:

```json
{
  "provider_payload_ref": {
    "mode": "native_sdk",
    "code": "AUTH_CODE_FROM_VERY_SDK"
  }
}
```

Tasks:

- Dispatch inside Very completion by stored `provider_mode` and payload mode.
- Reject native payloads for non-native sessions.
- Reject widget proofs for native sessions.
- Exchange the auth code server-side using:
  - `VERY_OAUTH_CLIENT_ID`
  - `VERY_OAUTH_CLIENT_SECRET`
  - `VERY_OAUTH_REDIRECT_URI`
  - optional `VERY_OAUTH_TOKEN_URL`, defaulting to Very production token endpoint
- Validate `id_token`:
  - signature/JWKS
  - issuer
  - audience
  - expiry
  - redirect/client binding as supported by Very
  - `nonce` or `state` when supported
- Enforce a tight native completion window, for example 5 minutes from `verification_sessions.started_at`.
- Derive the Pirate palm nullifier from the Very-confirmed stable identifier.
- Finalize through the existing `identity_nullifiers` path so cross-user reuse remains blocked.

Acceptance:

- Native success mints `unique_human` and records an active `identity_nullifiers` row.
- Replaying the same auth code fails.
- Completing a native code against another user's session fails.
- Completing after the native completion window fails.
- A second user cannot verify with the same stable Very identity.
- Widget completion remains unchanged.

## Ticket 4: Android Native SDK

Owner: Android.

Depends on: API native completion being available in the target environment.

Tasks:

- Add the Maven Central dependency:

```kotlin
implementation("org.very:sdk:1.0.29")
```

- Add `android.permission.CAMERA`.
- Add `VERY_SDK_KEY` to Android runtime config:
  - local dev: `android/local.properties`
  - release source of truth: Infisical `/services/android-release`
  - CI injection: GitHub Android build secrets
- Start sessions with `provider = "very"` and `provider_mode = "native_sdk"` when supported.
- Call `VerySDK.isSupported(context)` before showing the native scan path.
- Invoke `VerySDK.authenticate(...)` with `sdkKey`, `userId = null` for enrollment, and Pirate's configured theme.
- On success, call `/verification-sessions/{id}/complete` with the native payload shape.
- Refresh onboarding/profile verification state after completion.
- Show a hard error for unsupported devices or unavailable SDK config.

Acceptance:

- API 29+ supported physical devices use the native scan path.
- API 28 or unsupported devices show a clear native-unavailable error.
- The app never embeds `VERY_OAUTH_CLIENT_SECRET`.
- The app does not report verified until the API returns a verified Pirate session.

## Ticket 5: Privacy And Copy

Owner: web/API policy plus Android copy.

Tasks:

- Update privacy policy to explicitly name Very palm biometrics.
- Distinguish the native SDK flow from the ZK widget flow where relevant.
- State that the native path can provide Pirate with a persistent app-scoped Very identifier if that remains the confirmed design.
- Update Android copy to avoid saying the provider flow stores no data unless the exact native data model is confirmed.

Acceptance:

- Legal/privacy text covers Very native palm verification before release.
- Product copy does not imply ZK-only privacy for the native OAuth path.

## Credentials

Backend-only Infisical path: `/services/api`.

- `VERY_OAUTH_CLIENT_ID`
- `VERY_OAUTH_CLIENT_SECRET`
- `VERY_OAUTH_REDIRECT_URI`
- `VERY_OAUTH_TOKEN_URL` optional

Android release Infisical path: `/services/android-release`.

- `VERY_SDK_KEY`

Android local dev:

```properties
VERY_SDK_KEY=...
```

`VERY_SDK_KEY` is app config that ships in the APK. Treat it as controlled configuration, not as a backend secret. The OAuth client secret must never be embedded in Android or web bundles.

## Test Matrix

- Widget start and completion still pass.
- Native start serializes durable `provider_mode`.
- Native completion succeeds with a mocked token response and valid id token.
- Native completion rejects invalid issuer, audience, expiry, signature, missing subject, and mismatched nonce/state when supported.
- Native completion rejects stale auth codes by local session age before exchange.
- Native completion blocks cross-user reuse through `identity_nullifiers`.
- Android shows a hard error when `VerySDK.isSupported(context)` is false.
- Android shows a hard error when `VERY_SDK_KEY` is missing.

## Release Notes

- This is a privacy-sensitive provider-mode change, not only a UX improvement.
- The API migration should deploy before Android starts sending `provider_mode = "native_sdk"`.
- Keep the widget path configured with existing `VERY_APP_ID` and verifier vars during rollout.
