package sc.pirate.app.video

import android.content.Context
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

class VideoMediaCache(context: Context) {
    private val appContext = context.applicationContext

    /*
     * Intentionally process-lifetime: SimpleCache.release() is never called because
     * Application.onTerminate() does not fire on real devices.
     *
     * SimpleCache throws if two instances point at the same directory, so exactly one may exist
     * per process. PirateApp.onCreate runs in every process the app has, but this is `by lazy`, so
     * only a process that actually touches video constructs it — and today there is no
     * android:process in the manifest, so there is only one. Adding a download or media service in
     * its own process would break that assumption; share this instance rather than building a
     * second.
     */
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(appContext),
        )
    }

    val mediaSourceFactory: DefaultMediaSourceFactory by lazy {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(appContext, "Pirate"))
        // DefaultDataSource keeps file://, content:// and asset:// resolving. A bare HTTP factory
        // silently breaks every non-network source — a composer preview or a downloaded asset
        // would fail with a scheme error that reads as a media bug rather than a cache one.
        val upstream = DefaultDataSource.Factory(appContext, http)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            // A cache write failure — full disk, or the directory cleared mid-write — falls back
            // to streaming directly instead of failing playback outright.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        DefaultMediaSourceFactory(cacheDataSourceFactory)
    }

    private companion object {
        /**
         * Internal cache storage, so the system may clear it under pressure — which is the correct
         * behaviour for a cache. 256MiB rather than a larger round number because this shows up in
         * the app's storage figure in Settings and competes for space on low-storage devices; a
         * feed session revisits far fewer videos than that, and the eviction is LRU regardless.
         */
        const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    }
}
