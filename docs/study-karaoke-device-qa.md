# Android Study and Karaoke Device QA

Status as of 2026-07-02:

- Study is PR #2 (`codex/android-study-clean`), mergeable with green CI.
- Native karaoke is PR #3 (`codex/android-native-karaoke`), draft, mergeable with green CI.
- API `origin/main` allows the native karaoke WebSocket Origin `https://android.pirate.sc`.
- Device verification is still required before either feature is considered shipped.

## Preconditions

- Use a real Android phone, unlocked and interactive.
- Install the Blacksmith APK for the PR under test.
- Sign in with an account that can open the target community and song post.
- Use a song post with:
  - `song_presentation.alignment_status == "completed"`
  - `song_presentation.has_timed_lyrics == true`
  - a ready Study pack for Study QA

## Study PR #2

Install the PR #2 APK, then verify:

1. A ready song post shows the Learn/Study CTA.
2. Tapping the CTA opens the native Study screen without falling back to web.
3. Locked or unavailable Study returns the server-authoritative locked/unavailable state.
4. Multiple-choice exercise:
   - options render
   - submit sends `selected_option_id`
   - result/feedback renders
   - retry/next works
5. Say-it-back exercise:
   - typed answer input renders
   - submit succeeds
   - result/feedback renders
6. Relaunch/navigation:
   - back returns to the post
   - reopening Study keeps a coherent server state
   - no AndroidRuntime crash appears in logcat

## Native Karaoke PR #3

Install the PR #3 APK, then verify:

1. A timed-lyrics song post shows the Sing CTA.
2. Tapping Sing opens the native Karaoke screen without WebView.
3. Payload load succeeds:
   - title/artist render
   - timed lines render
   - instrumental audio prepares
4. Session creation succeeds with protocol version 1.
5. WebSocket connects with Origin `https://android.pirate.sc`.
6. Mic permission flow works:
   - first-run permission prompt appears if needed
   - denied permission leaves a recoverable state
   - granted permission starts capture
7. Start singing:
   - sends one `start` event
   - starts playback
   - sends PCM frames
   - sends periodic `playback_sync`
   - lyric highlight follows playback position
8. Server events render:
   - `stt_partial`
   - `stt_final`
   - `line_score`
   - `summary`
   - `session_error` is recoverable and user-visible
9. Stop/finish:
   - Stop sends final playback sync and `finish`
   - capture stops
   - playback pauses
   - a fresh next attempt is prepared
   - fresh attempt restarts transport sequence at 1
10. Reconnect:
   - interrupt network or force socket loss during capture
   - app pauses capture/playback and enters reconnecting state
   - reconnect does not resend `start`
   - reconnect keeps transport sequence monotonic
   - reconnect keeps audio chunk IDs monotonic within the same attempt
   - user can resume manually after connection restoration
11. Relaunch/navigation:
   - back/close aborts cleanly
   - reopening karaoke prepares a fresh session
   - no AndroidRuntime crash appears in logcat

## Evidence To Capture

- PR number and APK workflow run ID.
- Device model and Android version.
- Target community/post ID.
- Screenshots or short screen recording of the successful Study and Karaoke flows.
- Relevant logcat excerpt:
  - `AndroidRuntime:E`
  - `KaraokeScreen`
  - network/WebSocket errors, if any
- Any backend request IDs or session IDs shown in app/logs.
