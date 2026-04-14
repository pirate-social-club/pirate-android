package sc.pirate.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import sc.pirate.app.auth.AuthScreen
import sc.pirate.app.auth.AuthViewModel
import sc.pirate.app.auth.AuthUiState
import sc.pirate.app.community.CommunityScreen
import sc.pirate.app.community.CommunityViewModel
import sc.pirate.app.home.HomeScreen
import sc.pirate.app.onboarding.OnboardingScreen
import sc.pirate.app.onboarding.OnboardingViewModel
import sc.pirate.app.post.PostComposerScreen
import sc.pirate.app.post.PostComposerViewModel
import sc.pirate.app.post.PostScreen
import sc.pirate.app.profile.ProfileScreen
import sc.pirate.app.profile.ProfileViewModel
import sc.pirate.app.verification.VeryVerificationScreen

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
            val vm: AuthViewModel = viewModel()
            val state = vm.state.value

            if (state is AuthUiState.Authenticated) {
                navController.navigate(PirateRoute.Onboarding.route) {
                    popUpTo(PirateRoute.Auth.route) { inclusive = true }
                }
            } else {
                AuthScreen(
                    state = state,
                    onLoginGoogle = vm::loginWithGoogle,
                    onLoginTwitter = vm::loginWithTwitter,
                    onSendEmailCode = vm::sendEmailCode,
                    onLoginEmail = vm::loginWithEmail,
                    onLogout = vm::logout,
                )
            }
        }

        composable(PirateRoute.Onboarding.route) {
            val vm: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = vm,
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
            route = PirateRoute.Community.route,
            arguments = listOf(navArgument(PirateRoute.Community.ARG_COMMUNITY_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.Community.ARG_COMMUNITY_ID).orEmpty()
            val vm: CommunityViewModel = viewModel()
            CommunityScreen(
                viewModel = vm,
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
            route = PirateRoute.Post.route,
            arguments = listOf(navArgument(PirateRoute.Post.ARG_POST_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString(PirateRoute.Post.ARG_POST_ID).orEmpty()
            PostScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.ComposePost.route,
            arguments = listOf(navArgument(PirateRoute.ComposePost.ARG_COMMUNITY_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.ComposePost.ARG_COMMUNITY_ID).orEmpty()
            val vm: PostComposerViewModel = viewModel()
            PostComposerScreen(
                viewModel = vm,
                communityId = communityId,
                onPosted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(PirateRoute.Inbox.route) {
            sc.pirate.app.ui.EmptyFeedState(message = "Inbox is empty.")
        }

        composable(PirateRoute.Me.route) {
            val vm: ProfileViewModel = viewModel()
            ProfileScreen(viewModel = vm)
        }

        composable(
            route = PirateRoute.User.route,
            arguments = listOf(navArgument(PirateRoute.User.ARG_USER_ID) {
                type = NavType.StringType
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
