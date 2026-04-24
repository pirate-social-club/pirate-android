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
import sc.pirate.app.communities.YourCommunitiesScreen
import sc.pirate.app.community.CommunityScreen
import sc.pirate.app.community.CommunityViewModel
import sc.pirate.app.createcommunity.CreateCommunityScreen
import sc.pirate.app.home.HomeScreen
import sc.pirate.app.inbox.InboxScreen
import sc.pirate.app.moderation.CommunityModerationScreen
import sc.pirate.app.onboarding.OnboardingScreen
import sc.pirate.app.onboarding.OnboardingViewModel
import sc.pirate.app.post.PostComposerScreen
import sc.pirate.app.post.PostComposerViewModel
import sc.pirate.app.post.PostScreen
import sc.pirate.app.profile.MeProfileScreen
import sc.pirate.app.profile.MeProfileViewModel
import sc.pirate.app.profile.PublicProfileScreen
import sc.pirate.app.profile.UserProfileScreen
import sc.pirate.app.profile.UserProfileViewModel
import sc.pirate.app.settings.SettingsScreen
import sc.pirate.app.submit.GlobalSubmitScreen
import sc.pirate.app.verification.SelfVerificationScreen
import sc.pirate.app.verification.VeryVerificationScreen
import sc.pirate.app.wallet.WalletScreen

@Composable
fun PirateNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = PirateRoute.Home.route,
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
                onNavigateToPost = { id ->
                    navController.navigate(PirateRoute.Post.buildRoute(id))
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
                onVerifyWithSelf = { intent ->
                    navController.navigate(PirateRoute.VerifySelf.buildRoute(intent))
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
                onPosted = { postId ->
                    navController.navigate(PirateRoute.Post.buildRoute(postId)) {
                        popUpTo(PirateRoute.ComposePost.buildRoute(communityId)) {
                            inclusive = true
                        }
                    }
                },
                onOpenCommunity = {
                    navController.navigate(PirateRoute.Community.buildRoute(communityId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(PirateRoute.Inbox.route) {
            InboxScreen(
                onOpenPost = { postId ->
                    navController.navigate(PirateRoute.Post.buildRoute(postId))
                },
                onOpenCommunity = { communityId ->
                    navController.navigate(PirateRoute.Community.buildRoute(communityId))
                },
                onOpenCommunityNamespace = { communityId ->
                    navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, "namespace"))
                },
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
            )
        }

        composable(PirateRoute.Wallet.route) {
            WalletScreen(
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
            )
        }

        composable(PirateRoute.Me.route) {
            val vm: MeProfileViewModel = viewModel()
            MeProfileScreen(
                viewModel = vm,
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
            )
        }

        composable(
            route = PirateRoute.User.route,
            arguments = listOf(navArgument(PirateRoute.User.ARG_USER_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(PirateRoute.User.ARG_USER_ID).orEmpty()
            val vm: UserProfileViewModel = viewModel()
            UserProfileScreen(userId = userId, viewModel = vm)
        }

        composable(PirateRoute.CreateCommunity.route) {
            CreateCommunityScreen(
                onBack = { navController.popBackStack() },
                onVerifyWithId = {
                    navController.navigate(PirateRoute.VerifySelf.buildRoute("community_creation"))
                },
                onCreated = { communityId ->
                    navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, "namespace")) {
                        popUpTo(PirateRoute.CreateCommunity.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(PirateRoute.YourCommunities.route) {
            YourCommunitiesScreen(
                onNavigateToCommunity = { communityId ->
                    navController.navigate(PirateRoute.Community.buildRoute(communityId))
                },
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
            )
        }

        composable(PirateRoute.GlobalSubmit.route) {
            GlobalSubmitScreen(
                onBack = { navController.popBackStack() },
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
                onComposeInCommunity = { communityId ->
                    navController.navigate(PirateRoute.ComposePost.buildRoute(communityId))
                },
            )
        }

        composable(
            route = PirateRoute.VerifySelf.route,
            arguments = listOf(navArgument(PirateRoute.VerifySelf.ARG_INTENT) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val intent = backStackEntry.arguments
                ?.getString(PirateRoute.VerifySelf.ARG_INTENT)
                ?.takeIf { it in PirateRouteSections.verificationIntents }
                ?: PirateRoute.VerifySelf.DEFAULT_INTENT
            SelfVerificationScreen(
                verificationIntent = intent,
                onBack = { navController.popBackStack() },
            )
        }

        composable(PirateRoute.VerifyVery.route) {
            VeryVerificationScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = PirateRoute.Settings.route,
            arguments = listOf(navArgument(PirateRoute.Settings.ARG_SECTION) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val section = backStackEntry.arguments
                ?.getString(PirateRoute.Settings.ARG_SECTION)
                ?.takeIf { it in PirateRouteSections.settings }
                ?: PirateRoute.Settings.DEFAULT_SECTION
            SettingsScreen(
                section = section,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.CommunityModerationIndex.route,
            arguments = listOf(navArgument(PirateRoute.CommunityModerationIndex.ARG_COMMUNITY_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.CommunityModerationIndex.ARG_COMMUNITY_ID).orEmpty()
            CommunityModerationScreen(
                communityId = communityId,
                section = null,
                onBack = { navController.popBackStack() },
                onOpenCommunity = {
                    navController.navigate(PirateRoute.Community.buildRoute(it))
                },
            )
        }

        composable(
            route = PirateRoute.CommunityModerationSection.route,
            arguments = listOf(
                navArgument(PirateRoute.CommunityModerationSection.ARG_COMMUNITY_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.CommunityModerationSection.ARG_SECTION) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.CommunityModerationSection.ARG_COMMUNITY_ID).orEmpty()
            val section = backStackEntry.arguments
                ?.getString(PirateRoute.CommunityModerationSection.ARG_SECTION)
                ?.takeIf { it in PirateRouteSections.communityModeration }
            CommunityModerationScreen(
                communityId = communityId,
                section = section,
                onBack = { navController.popBackStack() },
                onOpenCommunity = {
                    navController.navigate(PirateRoute.Community.buildRoute(it))
                },
            )
        }

        composable(
            route = PirateRoute.PublicProfile.route,
            arguments = listOf(navArgument(PirateRoute.PublicProfile.ARG_HANDLE_LABEL) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val handleLabel = backStackEntry.arguments?.getString(PirateRoute.PublicProfile.ARG_HANDLE_LABEL).orEmpty()
            PublicProfileScreen(
                handleLabel = handleLabel,
                onNavigateToCommunity = {
                    navController.navigate(PirateRoute.Community.buildRoute(it))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
