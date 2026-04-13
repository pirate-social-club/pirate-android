package sc.pirate.app.navigation

import android.net.Uri

sealed class PirateRoute(val route: String) {
    data object Auth : PirateRoute("auth")
    data object Onboarding : PirateRoute("onboarding")
    data object Home : PirateRoute("home")
    data object YourCommunities : PirateRoute("your_communities")
    data object Community : PirateRoute("community/{communityId}") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}"
    }
    data object CreateCommunity : PirateRoute("communities/new")
    data object Post : PirateRoute("post/{postId}") {
        const val ARG_POST_ID = "postId"
        fun buildRoute(postId: String): String = "post/${Uri.encode(postId)}"
    }
    data object ComposePost : PirateRoute("community/{communityId}/compose") {
        const val ARG_COMMUNITY_ID = "communityId"
        fun buildRoute(communityId: String): String = "community/${Uri.encode(communityId)}/compose"
    }
    data object Inbox : PirateRoute("inbox")
    data object Me : PirateRoute("me")
    data object User : PirateRoute("user/{userId}") {
        const val ARG_USER_ID = "userId"
        fun buildRoute(userId: String): String = "user/${Uri.encode(userId)}"
    }
}
