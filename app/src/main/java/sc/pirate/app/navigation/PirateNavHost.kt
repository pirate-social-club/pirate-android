package sc.pirate.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import androidx.lifecycle.viewmodel.compose.viewModel
import sc.pirate.app.PirateApp
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
import sc.pirate.app.wallet.WalletViewModel
import sc.pirate.app.ui.FeatureStubScreen

@Composable
fun PirateNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PirateApp
    val session by app.sessionStore.observe().collectAsState(initial = null)
    val hasSession = session != null

    NavHost(
        navController = navController,
        startDestination = PirateRoute.Home.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(PirateRoute.Auth.route) {
            val vm: AuthViewModel = viewModel()
            val state by vm.state.collectAsState()
            val walletConnectState by app.reownManager.state.collectAsState()

            if (state is AuthUiState.Authenticated) {
                navController.navigate(PirateRoute.Onboarding.route) {
                    popUpTo(PirateRoute.Auth.route) { inclusive = true }
                }
            } else {
                AuthScreen(
                    state = state,
                    walletConnectState = walletConnectState,
                    onOpenWalletConnect = {
                        navController.openAppKit(
                            shouldOpenChooseNetwork = false,
                            onError = { error ->
                                app.reownManager.refreshState(
                                    error.message ?: "Could not open wallet chooser."
                                )
                            },
                        )
                    },
                    onLoginWallet = vm::loginWithConnectedWallet,
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
                hasSession = hasSession,
                onNavigateToCommunity = { id ->
                    navController.navigate(PirateRoute.Community.buildRoute(id))
                },
                onNavigateToPost = { id ->
                    navController.navigate(PirateRoute.Post.buildRoute(id))
                },
                onNavigateToCompose = {
                    navController.navigate(PirateRoute.GlobalSubmit.route)
                },
                onNavigateToYourCommunities = {
                    navController.navigate(PirateRoute.YourCommunities.route)
                },
                onNavigateToWallet = {
                    navController.navigate(PirateRoute.Wallet.route)
                },
                onNavigateToChat = {
                    navController.navigate(PirateRoute.Chat.route)
                },
                onNavigateToInbox = {
                    navController.navigate(PirateRoute.Inbox.route)
                },
                onNavigateToProfile = {
                    navController.navigate(PirateRoute.Me.route)
                },
                onNavigateToCreateCommunity = {
                    navController.navigate(PirateRoute.CreateCommunity.route)
                },
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
            )
        }

        composable(PirateRoute.Chat.route) {
            AuthGate(hasSession, navController) {
                FeatureStubScreen(
                    title = "Chat",
                    body = "Encrypted chat is moving into the Android app. This slot now matches the mobile web footer and will host chat once the native screen lands.",
                )
            }
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
                hasSession = hasSession,
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
                hasSession = hasSession,
                onNavigateToCompose = { communityId ->
                    navController.navigate(PirateRoute.ComposePost.buildRoute(communityId))
                },
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
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
                hasSession = hasSession,
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
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
            AuthGate(hasSession, navController) {
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
        }

        composable(PirateRoute.Wallet.route) {
            AuthGate(hasSession, navController) {
                val walletConnectState by app.reownManager.state.collectAsState()
                val vm: WalletViewModel = viewModel()
                val walletUiState by vm.state.collectAsState()
                WalletScreen(
                    session = session,
                    walletConnectState = walletConnectState,
                    walletUiState = walletUiState,
                    onOpenWalletConnect = {
                        navController.openAppKit(
                            shouldOpenChooseNetwork = false,
                            onError = { error ->
                                app.reownManager.refreshState(
                                    error.message ?: "Could not open wallet chooser."
                                )
                            },
                        )
                    },
                    onLinkWallet = vm::linkConnectedWallet,
                    onClearWalletFeedback = vm::clearFeedback,
                    onDisconnectWallet = app.reownManager::disconnect,
                    onSignIn = {
                        navController.navigate(PirateRoute.Auth.route)
                    },
                )
            }
        }

        composable(PirateRoute.Me.route) {
            AuthGate(hasSession, navController) {
                val vm: MeProfileViewModel = viewModel()
                MeProfileScreen(
                    viewModel = vm,
                    onSignIn = {
                        navController.navigate(PirateRoute.Auth.route)
                    },
                )
            }
        }

        composable(
            route = PirateRoute.User.route,
            arguments = listOf(navArgument(PirateRoute.User.ARG_USER_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString(PirateRoute.User.ARG_USER_ID).orEmpty()
            AuthGate(hasSession, navController) {
                val vm: UserProfileViewModel = viewModel()
                UserProfileScreen(userId = userId, viewModel = vm)
            }
        }

        composable(PirateRoute.CreateCommunity.route) {
            AuthGate(hasSession, navController) {
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
        }

        composable(PirateRoute.YourCommunities.route) {
            AuthGate(hasSession, navController) {
                YourCommunitiesScreen(
                    onNavigateToCommunity = { communityId ->
                        navController.navigate(PirateRoute.Community.buildRoute(communityId))
                    },
                    onSignIn = {
                        navController.navigate(PirateRoute.Auth.route)
                    },
                )
            }
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
            AuthGate(hasSession, navController) {
                SelfVerificationScreen(
                    verificationIntent = intent,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(PirateRoute.VerifyVery.route) {
            AuthGate(hasSession, navController) {
                VeryVerificationScreen(onBack = { navController.popBackStack() })
            }
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
            AuthGate(hasSession, navController) {
                SettingsScreen(
                    section = section,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = PirateRoute.CommunityModerationIndex.route,
            arguments = listOf(navArgument(PirateRoute.CommunityModerationIndex.ARG_COMMUNITY_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.CommunityModerationIndex.ARG_COMMUNITY_ID).orEmpty()
            AuthGate(hasSession, navController) {
                CommunityModerationScreen(
                    communityId = communityId,
                    section = null,
                    onBack = { navController.popBackStack() },
                    onOpenCommunity = {
                        navController.navigate(PirateRoute.Community.buildRoute(it))
                    },
                )
            }
        }

        composable(
            route = PirateRoute.CommunityModerationSection.route,
            arguments = listOf(
                navArgument(PirateRoute.CommunityModerationSection.ARG_COMMUNITY_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.CommunityModerationSection.ARG_SECTION) {
                    type = NavType.StringType
                }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.CommunityModerationSection.ARG_COMMUNITY_ID).orEmpty()
            val section = backStackEntry.arguments
                ?.getString(PirateRoute.CommunityModerationSection.ARG_SECTION)
                ?.takeIf { it in PirateRouteSections.communityModeration }
            AuthGate(hasSession, navController) {
                CommunityModerationScreen(
                    communityId = communityId,
                    section = section,
                    onBack = { navController.popBackStack() },
                    onOpenCommunity = {
                        navController.navigate(PirateRoute.Community.buildRoute(it))
                    },
                )
            }
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

        appKitGraph(navController)
    }
}

@Composable
private fun AuthGate(
    hasSession: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    if (hasSession) {
        content()
    } else {
        sc.pirate.app.ui.SignInRequiredScreen(
            onSignIn = {
                navController.navigate(PirateRoute.Auth.route)
            },
        )
    }
}
