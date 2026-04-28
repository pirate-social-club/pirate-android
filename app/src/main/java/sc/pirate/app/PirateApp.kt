package sc.pirate.app

import android.app.Application
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
import sc.pirate.app.verification.VerificationCoordinator
import sc.pirate.app.walletconnect.ReownManager

class PirateApp : Application() {
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

    override fun onCreate() {
        super.onCreate()
        reownManager.initialize()
        sessionRefresher.start()
    }
}
