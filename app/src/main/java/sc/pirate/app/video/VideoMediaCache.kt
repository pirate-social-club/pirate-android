package sc.pirate.app.video

import android.content.Context
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

class VideoMediaCache(context: Context) {
    private val appContext = context.applicationContext

    // Intentionally process-lifetime: SimpleCache.release() is never called because
    // Application.onTerminate() does not fire on real devices.
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(appContext),
        )
    }

    val mediaSourceFactory: DefaultMediaSourceFactory by lazy {
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(appContext, "Pirate"))
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        DefaultMediaSourceFactory(cacheDataSourceFactory)
    }

    private companion object {
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
    }
}
