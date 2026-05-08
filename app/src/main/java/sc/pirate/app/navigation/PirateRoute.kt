package sc.pirate.app.navigation

import android.net.Uri

object PirateRouteSections {
    val settings = setOf("profile", "domains", "preferences", "agents")
    val verificationIntents = setOf(
        "profile_verification",
        "community_creation",
        "community_join",
        "post_access_18_plus",
        "commerce_pricing",
        "qualifier_disclosure",
    )
    val communityModeration = setOf(
        "profile",
        "rules",
        "links",
        "labels",
        "donations",
        "pricing",
        "namespace",
        "gates",
        "safety",
        "agents",
    )
}

sealed class PirateRoute(val route: String) {
    data object Auth : PirateRoute("auth")
    data object Onboarding : PirateRoute("onboarding")
    data object Home : PirateRoute("home")
    data object Chat : PirateRoute("chat")
    data object YourCommunities : PirateRoute("your_communities")
    data object Community : PirateRoute("community/{communityId}") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}"
    }
    data object CreateCommunity : PirateRoute("communities/new")
    data object GlobalSubmit : PirateRoute("submit")
    data object VerifySelf : PirateRoute("verification/self/{intent}") {
        const val ARG_INTENT = "intent"
        const val DEFAULT_INTENT = "community_creation"
        fun buildRoute(intent: String = DEFAULT_INTENT): String {
            require(intent in PirateRouteSections.verificationIntents) {
                "Unknown verification intent: $intent"
            }
            return "verification/self/${Uri.encode(intent)}"
        }
    }
    data object VerifyVery : PirateRoute("verification/very")
    data object Post : PirateRoute("post/{postId}") {
        const val ARG_POST_ID = "postId"
        fun buildRoute(postId: String): String = "post/${Uri.encode(postId)}"
    }
    data object ComposePost : PirateRoute("community/{communityId}/compose") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}/compose"
    }
    data object Notifications : PirateRoute("notifications")
    data object Inbox : PirateRoute("inbox")
    data object Wallet : PirateRoute("wallet")
    data object Me : PirateRoute("me")
    data object SettingsIndex : PirateRoute("settings")
    data object Settings : PirateRoute("settings/{section}") {
        const val ARG_SECTION = "section"
        const val DEFAULT_SECTION = "profile"
        fun buildRoute(section: String = DEFAULT_SECTION): String {
            require(section in PirateRouteSections.settings) { "Unknown settings section: $section" }
            return "settings/${Uri.encode(section)}"
        }
    }
    data object CommunityModerationIndex : PirateRoute("community/{communityId}/mod") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}/mod"
    }
    data object CommunityModerationSection : PirateRoute("community/{communityId}/mod/{section}") {
        const val ARG_COMMUNITY_ID = "communityId"
        const val ARG_SECTION = "section"
        fun buildRoute(communityId: String, section: String): String {
            require(section in PirateRouteSections.communityModeration) {
                "Unknown community moderation section: $section"
            }
            return "community/${Uri.encode(communityId)}/mod/${Uri.encode(section)}"
        }
    }
    data object User : PirateRoute("user/{userId}") {
        const val ARG_USER_ID = "userId"
        fun buildRoute(userId: String): String = "user/${Uri.encode(userId)}"
    }
    data object PublicProfile : PirateRoute("public-profile/{handleLabel}") {
        const val ARG_HANDLE_LABEL = "handleLabel"
        fun buildRoute(handleLabel: String): String = "public-profile/${Uri.encode(handleLabel)}"
    }
    data object PublicProfileByWallet : PirateRoute("public-profile/wallet/{walletAddress}") {
        const val ARG_WALLET_ADDRESS = "walletAddress"
        fun buildRoute(walletAddress: String): String = "public-profile/wallet/${Uri.encode(walletAddress)}"
    }
}
