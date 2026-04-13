package sc.pirate.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import sc.pirate.app.auth.AuthScreen
import sc.pirate.app.auth.AuthViewModel
import sc.pirate.app.community.CommunityScreen
import sc.pirate.app.home.HomeScreen
import sc.pirate.app.onboarding.OnboardingScreen
import sc.pirate.app.post.PostComposerScreen
import sc.pirate.app.post.PostScreen
import sc.pirate.app.profile.ProfileScreen
import sc.pirate.app.verification.VeryVerificationScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import sc.pirate.app.auth.AuthUiState
import sc.pirate.app.community.CommunityViewModel
import sc.pirate.app.onboarding.OnboardingViewModel
import sc.pirate.app.post.PostComposerViewModel
import sc.pirate.app.profile.ProfileViewModel

@Composable
fun PirateNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = PirateRoute.Auth.route,
        modifier = modifier,
    ) {
        composable(PirateRoute.Auth.route) {
            val viewModel: AuthViewModel = viewModel()
            val state = viewModel.state.value

            if (state is AuthUiState.Authenticated) {
                navController.navigate(PirateRoute.Onboarding.route) {
                    popUpTo(PirateRoute.Auth.route) { inclusive = true }
                }
            } else {
                AuthScreen(
                    state = state,
                    onLoginGoogle = viewModel::loginWithGoogle,
                    onLoginTwitter = viewModel::loginWithTwitter,
                    onSendEmailCode = viewModel::sendEmailCode,
                    onLoginEmail = viewModel::loginWithEmail,
                    onLogout = viewModel::logout,
                )
            }
        }

        composable(PirateRoute.Onboarding.route) {
            val viewModel: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = viewModel,
                onComplete = {
                    navController.navigate(PirateRoute.Home.route) {
                        popUpTo(PirateRoute.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        composable(PirateRoute.Home.route) {
            HomeScreen(
                onNavigateToCommunity = { id ->
                    navController.navigate(PirateRoute.Community.buildRoute(id))
                },
            )
        }

        composable(
            PirateRoute.Community.route,
            arguments = listOf(androidx.navigation.argument(PirateRoute.Community.ARG_COMMUNITY_ID) {
                type = androidx.navigation.NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.Community.ARG_COMMUNITY_ID).orEmpty()
            val viewModel: CommunityViewModel = viewModel()
            CommunityScreen(
                viewModel = viewModel,
                communityId = communityId,
                onNavigateToPost = { postId ->
                    navController.navigate(PirateRoute.Post.buildRoute(postId))
                },
                onNavigateToCompose = {
                    navController.navigate(PirateRoute.ComposePost.buildRoute(communityId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            PirateRoute.Post.route,
            arguments = listOf(androidx.navigation.argument(PirateRoute.Post.ARG_POST_ID) {
                type = androidx.navigation.NavType.StringType
            }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString(PirateRoute.Post.ARG_POST_ID).orEmpty()
            PostScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            PirateRoute.ComposePost.route,
            arguments = listOf(androidx.navigation.argument(PirateRoute.ComposePost.ARG_COMMUNITY_ID) {
                type = androidx.navigation.NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.ComposePost.ARG_COMMUNITY_ID).orEmpty()
            val viewModel: PostComposerViewModel = viewModel()
            PostComposerScreen(
                viewModel = viewModel,
                communityId = communityId,
                onPosted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(PirateRoute.Inbox.route) {
            sc.pirate.app.ui.EmptyFeedState(message = "Inbox is empty.")
        }

        composable(PirateRoute.Me.route) {
            val viewModel: ProfileViewModel = viewModel()
            ProfileScreen(viewModel = viewModel)
        }

        composable(
            PirateRoute.User.route,
            arguments = listOf(androidx.navigation.argument(PirateRoute.User.ARG_USER_ID) {
                type = androidx.navigation.NavType.StringType
            }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(PirateRoute.User.ARG_USER_ID).orEmpty()
            ProfileScreen(userId = userId)
        }

        composable(PirateRoute.CreateCommunity.route) {
            VeryVerificationScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(PirateRoute.YourCommunities.route) {
            sc.pirate.app.ui.EmptyFeedState(message = "Join communities to see them here.")
        }
    }
}
