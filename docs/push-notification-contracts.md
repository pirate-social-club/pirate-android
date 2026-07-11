# Android push notification contracts

Pirate needs two independent push systems. They must not share delivery
assumptions: API events originate in Pirate services, while XMTP messages do
not pass through the Pirate API.

## 1. Pirate API event push

### Android client contract

- Obtain an FCM registration token only after notification consent.
- Register it with `POST /devices/push-tokens` using an authenticated Pirate
  session. Body: `token`, `platform: "android"`, `app_version`, `locale`, and a
  stable installation identifier generated locally (not an advertising ID).
- Refresh registration on FCM token rotation and authenticated-user changes.
- Revoke with `DELETE /devices/push-tokens/{installation_id}` on logout and
  when notifications are disabled.
- Never put access tokens, message bodies, wallet addresses, email addresses,
  or other private content in notification payloads.

### Backend sibling contract

- Store token hashes plus encrypted token values, user ID, installation ID,
  platform, app version, locale, created/last-seen timestamps, and revoked time.
- Enforce one active token per user/installation pair and idempotent upserts.
- Send from durable jobs for notification/activity records after database
  commit. Collapse replaceable counters; do not collapse distinct mentions.
- Payload contains only `event`, opaque object ID, canonical route, and badge
  count. Android fetches display content through authenticated APIs.
- Revoke tokens on FCM `UNREGISTERED`/invalid responses. Retry transient
  failures with bounded exponential backoff and record delivery metrics.
- Required events for parity: comment/reply, mention, moderation result,
  community membership request/result, live-room reminder/start, purchase and
  royalty-settlement state. Marketing push is out of scope.

## 2. XMTP direct-message push

XMTP delivery requires an XMTP-supported notification/subscription mechanism;
the Pirate API cannot detect new ciphertext reliably and must not receive DM
plaintext.

- The Android client registers its FCM token/subscription with the supported
  XMTP notification service for the active inbox and installation.
- Subscribe/unsubscribe as conversations are created, consent changes, inboxes
  rotate, users log out, or FCM tokens rotate.
- Notification payloads contain conversation/inbox identifiers only. The app
  opens XMTP locally to decrypt and render content.
- Lock-screen text defaults to “New message” unless the user explicitly opts
  into previews; previews must be derived on-device after decryption.
- Multiple Pirate accounts on one installation require isolated subscription
  sets. Logout deletes only that account's subscriptions.

## Shared acceptance criteria

- Android 13+ runtime permission flow with a pre-permission explanation.
- Separate preference toggles for Pirate activity, live reminders, and DMs.
- Deep-link allowlist uses native post/community/chat routes only.
- Cold-start, background, foreground, token-rotation, logout, reinstall, and
  revoked-token tests are required before enabling production sends.
- Data Safety and privacy policy updates must name notification token handling.
