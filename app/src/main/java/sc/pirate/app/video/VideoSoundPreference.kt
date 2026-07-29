package sc.pirate.app.video

import android.content.Context

/**
 * Whether the viewer wants sound in the video feed, remembered across sessions.
 *
 * Autoplay always *starts* muted regardless of this: an unmuted autoplay is the thing that makes
 * a feed feel hostile when it opens in public. This preference decides whether the feed unmutes
 * itself once the viewer has already told us, once, that they want sound.
 */
class VideoSoundPreference(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var muted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MUTED, value).apply()
        }

    private companion object {
        // Mirrors the web feed's localStorage key so the two surfaces stay conceptually paired.
        const val PREFS_NAME = "pirate.video-feed"
        const val KEY_MUTED = "muted"
    }
}
