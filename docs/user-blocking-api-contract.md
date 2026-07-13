# User blocking API contract

Status: proposed backend sibling task. Android currently provides an encrypted,
account-scoped local safety fallback; this contract is required for cross-device
enforcement and complete web parity.

## Endpoints

All endpoints require the authenticated Pirate user. Pirate user IDs, not handles,
wallets, or XMTP inbox IDs, are the durable relationship keys.

### `GET /me/blocks?cursor=<opaque>&limit=<1..100>`

```json
{
  "items": [
    {
      "blocked_user_id": "usr_123",
      "created_at": "2026-07-13T12:00:00Z"
    }
  ],
  "next_cursor": null
}
```

### `PUT /me/blocks/{blocked_user_id}`

Idempotently creates the block. Return `200` with the block record whether it was
new or already existed. Reject self-blocks with `400`. A missing or inaccessible
target returns the same `404` shape used by public profile reads.

### `DELETE /me/blocks/{blocked_user_id}`

Idempotently removes the block and returns `204`, including when no relationship
exists.

## Enforcement invariants

The API must enforce blocks before pagination and ranking, in the shared read/query
layer. Filtering a completed page creates short pages, cursor gaps, and content
leaks through counts or enrichment calls.

While either user blocks the other:

- Exclude both users' posts and comments from home, popular, community, profile,
  search, and recommendation reads presented to the other party.
- Return the normal not-found response for direct post, comment, and profile reads
  across the relationship. Do not reveal which side created the block.
- Suppress anonymous or persona-authored content using the private canonical author
  ID. Never expose the persona-to-user mapping to either client.
- Reject new follows, membership invitations, mentions, booking requests, chat
  identity resolution, and other direct interactions across the relationship.
- Remove or deactivate existing follow relationships and pending direct-interaction
  notifications transactionally when the block is created.
- Do not enqueue new push, inbox, email, or realtime events from either party to the
  other. Existing notification reads must also filter the relationship.
- Exclude blocked actors from aggregate viewer-facing lists where their presence
  would disclose activity. Public global counts may remain aggregate and anonymous.

Moderation and legal-preservation reads are exempt only for authorized staff tools;
consumer endpoints are not.

## XMTP boundary

The API owns the Pirate-user-to-XMTP-inbox mapping and must refuse cross-block
identity resolution. It cannot revoke an already known decentralized inbox. Clients
must additionally set existing DM consent to `DENIED`, hide the conversation, reject
new local DM creation, and re-apply denied consent after reconnecting.

Unblocking permits future interaction but must not automatically send a message,
restore a follow, or recreate deleted notifications.

## Android synchronization follow-up

When these endpoints ship, Android should migrate without weakening existing local
blocks:

1. Upload the signed-in account's encrypted local block IDs with idempotent `PUT`s.
2. Fetch every server page and replace that account's local snapshot only after all
   uploads and reads succeed.
3. Resolve handles and XMTP inboxes through authenticated profile reads for display
   and local XMTP consent; never use those mutable values as relationship keys.
4. Apply successful block/unblock changes optimistically, queue retries with stable
   operation IDs, and reconcile with the server on sign-in and foreground refresh.

The server is authoritative after migration, but a failed sync must retain the local
safety filter until reconciliation succeeds.
