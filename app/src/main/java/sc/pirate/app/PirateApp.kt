package sc.pirate.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.request.CachePolicy
import io.sentry.android.core.SentryAndroid
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.SessionRefresher
import sc.pirate.app.api.SessionStore
import sc.pirate.app.shared.api.ApiAuthRepository
import sc.pirate.app.shared.api.ApiCommunityRepository
import sc.pirate.app.shared.api.ApiFeedRepository
import sc.pirate.app.shared.api.ApiNotificationRepository
import sc.pirate.app.shared.api.ApiOnboardingRepository
import sc.pirate.app.shared.api.ApiPostRepository
import sc.pirate.app.shared.api.ApiProfileRepository
import sc.pirate.app.shared.api.ApiVerificationRepository
import sc.pirate.app.shared.api.AppRepositories
import sc.pirate.app.chat.XmtpChatService
import sc.pirate.app.communities.KnownCommunitiesStore
import sc.pirate.app.home.HomeFeedCache
import sc.pirate.app.post.PostPreviewCache
import sc.pirate.app.song.SongPlaybackController
import sc.pirate.app.video.VideoPlaybackController
import sc.pirate.app.verification.VerificationCoordinator
import sc.pirate.app.walletconnect.ReownManager

class PirateApp : Application(), ImageLoaderFactory {
    val sessionStore by lazy { SessionStore(this) }
    val apiClient by lazy { ApiClient(sessionStore) }
    val repositories by lazy {
        AppRepositories(
            authRepository = ApiAuthRepository(apiClient),
            onboardingRepository = ApiOnboardingRepository(apiClient),
            feedRepository = ApiFeedRepository(apiClient),
            communityRepository = ApiCommunityRepository(apiClient),
            postRepository = ApiPostRepository(apiClient),
            profileRepository = ApiProfileRepository(apiClient),
            verificationRepository = ApiVerificationRepository(apiClient),
            notificationRepository = ApiNotificationRepository(apiClient),
        )
    }
    val verificationCoordinator by lazy { VerificationCoordinator(this) }
    val sessionRefresher by lazy { SessionRefresher(this) }
    val reownManager by lazy { ReownManager(this) }
    val chatService by lazy { XmtpChatService(this) }
    val knownCommunitiesStore by lazy { KnownCommunitiesStore(this) }
    val homeFeedCache by lazy { HomeFeedCache() }
    val postPreviewCache by lazy { PostPreviewCache() }
    val songPlaybackController: SongPlaybackController by lazy {
        SongPlaybackController(this) { videoPlaybackController.pause() }
    }
    val videoPlaybackController: VideoPlaybackController by lazy {
        VideoPlaybackController(this) { songPlaybackController.pause() }
    }

    override fun onCreate() {
        super.onCreate()
        initializeCrashReporting()
        reownManager.initialize()
        sessionRefresher.start()
    }

    private fun initializeCrashReporting() {
        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isEmpty()) return
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.tracesSampleRate = 0.0
            options.isAnrEnabled = true
            options.isEnableUserInteractionBreadcrumbs = false
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
}
