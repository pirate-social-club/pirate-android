package sc.pirate.app.navigation

import android.net.Uri

object PirateRouteSections {
    val settings = setOf("profile", "domains", "preferences", "agents")
    val selfCapabilities = setOf("unique_human", "age_over_18", "nationality", "gender")
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

object PirateDeepLinks {
    fun routeFromUri(uri: Uri?): String? {
        if (uri == null || !uri.scheme.equals("pirate", ignoreCase = true)) return null
        return when (uri.host) {
            "post" -> uri.pathSegments.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(PirateRoute.Post::buildRoute)
            "community" -> uri.pathSegments.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(PirateRoute.Community::buildRoute)
            else -> null
        }
    }
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
    data object VerifySelf : PirateRoute("verification/self/{intent}?capabilities={capabilities}") {
        const val ARG_INTENT = "intent"
        const val ARG_CAPABILITIES = "capabilities"
        const val DEFAULT_INTENT = "community_creation"
        fun buildRoute(intent: String = DEFAULT_INTENT, capabilities: List<String> = emptyList()): String {
            require(intent in PirateRouteSections.verificationIntents) {
                "Unknown verification intent: $intent"
            }
            val selectedCapabilities = capabilities
                .map { it.trim() }
                .filter { it in PirateRouteSections.selfCapabilities }
                .distinct()
            val path = "verification/self/${Uri.encode(intent)}"
            if (selectedCapabilities.isEmpty()) return path
            return "$path?capabilities=${Uri.encode(selectedCapabilities.joinToString(","))}"
        }
    }
    data object Post : PirateRoute("post/{postId}") {
        const val ARG_POST_ID = "postId"
        fun buildRoute(postId: String): String = "post/${Uri.encode(postId)}"
    }
    data object LiveRoomWeb : PirateRoute("post/{postId}/live-room") {
        const val ARG_POST_ID = "postId"
        fun buildRoute(postId: String): String = "post/${Uri.encode(postId)}/live-room"
    }
    data object Karaoke : PirateRoute("community/{communityId}/post/{postId}/karaoke") {
        const val ARG_COMMUNITY_ID = "communityId"
        const val ARG_POST_ID = "postId"
        fun buildRoute(communityId: String, postId: String): String =
            "community/${Uri.encode(communityId)}/post/${Uri.encode(postId)}/karaoke"
    }
    data object LiveRoomBroadcast : PirateRoute("community/{communityId}/live-room/{liveRoomId}/broadcast/{role}") {
        const val ARG_COMMUNITY_ID = "communityId"
        const val ARG_LIVE_ROOM_ID = "liveRoomId"
        const val ARG_ROLE = "role"
        fun buildRoute(communityId: String, liveRoomId: String, role: String): String =
            "community/${Uri.encode(communityId)}/live-room/${Uri.encode(liveRoomId)}/broadcast/${Uri.encode(role)}"
    }
    data object ComposePost : PirateRoute("community/{communityId}/compose") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}/compose"
    }
    data object DerivativeSourceSearch : PirateRoute("community/{communityId}/song-source-search") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}/song-source-search"
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
