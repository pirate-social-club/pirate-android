# Terms acceptance contract

Status: proposed backend sibling task. Android's encrypted, account-scoped gate
prevents its native UGC actions from proceeding without acceptance, but server
enforcement is required for cross-device integrity and non-Android clients.

## Release prerequisite

The published Terms at `/terms` currently have an effective date of March 23,
2026, but section 18 still contains an internal choice between litigation,
arbitration, class-action waiver, and an informal dispute period followed by
"This section should be finalized with counsel." Product/legal must finalize and
publish that section before treating any acceptance as release-ready. Android uses
the effective-date version `2026-03-23` only to exercise the technical versioning
path; it does not resolve this legal-content blocker.

## Endpoints

### `GET /me/legal-acceptances`

```json
{
  "terms": {
    "version": "2026-03-23",
    "accepted_at": "2026-07-13T12:00:00Z"
  },
  "required_terms_version": "2026-03-23"
}
```

### `PUT /me/legal-acceptances/terms`

```json
{
  "version": "2026-03-23",
  "idempotency_key": "stable-client-operation-id"
}
```

Return the persisted acceptance record. Reject any version other than the
server's currently required version. Store the authenticated user, version,
server timestamp, and enough policy provenance to audit which immutable document
was accepted; do not trust a client-provided timestamp.

## Server enforcement

The API must reject UGC mutations with `428 Precondition Required` and a stable
error code such as `terms_acceptance_required` when the authenticated user has not
accepted the required version. At minimum this includes:

- posts, media uploads, comments, replies, crossposts, songs, live rooms, and
  replay publication;
- profile names, bios, avatars, covers, handles, and community creation/content;
- direct-message identity/bootstrap operations owned by Pirate and notification
  fan-out associated with newly created UGC.

Upload-slot creation must be gated before bytes are accepted. Finalization-only
checks leave orphaned content in storage. Read-only browsing, reporting abuse,
blocking users, account deletion, and access to the legal documents must remain
available without acceptance.

The required version must come from server configuration tied to an immutable
document hash or revision. Changing it should immediately gate later UGC writes;
it must not silently rewrite historical acceptance records.

## Android synchronization follow-up

After the endpoints ship, Android should upload a matching local acceptance with
a stable idempotency key, reconcile against the server on sign-in/foreground, and
treat the server-required version as authoritative. A server `428` should reopen
the same native dialog and retry only after explicit acceptance; it must never
auto-accept or infer consent from continued use.
