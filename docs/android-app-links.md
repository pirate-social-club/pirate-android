# Android App Links deployment

Android accepts canonical Pirate post and community URLs (`/p/{post_id}` and
`/c/{community_id_or_slug}`). Verification additionally requires the web deploy
to serve `https://pirate.sc/.well-known/assetlinks.json` with:

- package name `sc.pirate.mobile`
- relation `delegate_permission/common.handle_all_urls`
- the SHA-256 certificate fingerprint from Google Play Console's **App signing**
  certificate (not the local upload certificate)

The canonical web checkout was intentionally not edited with this Android
change because it contains unrelated uncommitted work. Verification remains a
web deployment task until the production Play fingerprint is supplied.
