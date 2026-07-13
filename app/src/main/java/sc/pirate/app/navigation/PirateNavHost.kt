package sc.pirate.app.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import androidx.lifecycle.viewmodel.compose.viewModel
import sc.pirate.app.PirateApp
import sc.pirate.app.auth.AuthViewModel
import sc.pirate.app.auth.AuthUiState
import sc.pirate.app.auth.SignInDrawer
import sc.pirate.app.booking.BookingAvailabilityScreen
import sc.pirate.app.chat.ChatScreen
import sc.pirate.app.communities.YourCommunitiesScreen
import sc.pirate.app.community.CommunityScreen
import sc.pirate.app.community.CommunityViewModel
import sc.pirate.app.createcommunity.CreateCommunityScreen
import sc.pirate.app.home.HomeScreen
import sc.pirate.app.inbox.InboxScreen
import sc.pirate.app.karaoke.KaraokeScreen
import sc.pirate.app.live.LiveRoomBroadcasterScreen
import sc.pirate.app.live.LiveRoomWebViewScreen
import sc.pirate.app.live.ReplayDraftScreen
import sc.pirate.app.legal.TermsAcceptanceDialog
import sc.pirate.app.moderation.CommunityModerationScreen
import sc.pirate.app.onboarding.OnboardingScreen
import sc.pirate.app.onboarding.OnboardingViewModel
import sc.pirate.app.post.DerivativeSourceSearchScreen
import sc.pirate.app.post.DerivativeSourceSearchViewModel
import sc.pirate.app.post.PostComposerScreen
import sc.pirate.app.post.PostComposerViewModel
import sc.pirate.app.study.StudyScreen
import sc.pirate.app.post.PostScreen
import sc.pirate.app.profile.MeProfileScreen
import sc.pirate.app.profile.MeProfileViewModel
import sc.pirate.app.profile.PublicProfileScreen
import sc.pirate.app.profile.UserProfileScreen
import sc.pirate.app.profile.UserProfileViewModel
import sc.pirate.app.settings.SettingsScreen
import sc.pirate.app.submit.GlobalSubmitScreen
import sc.pirate.app.verification.SelfVerificationScreen
import sc.pirate.app.wallet.WalletScreen
import sc.pirate.app.wallet.WalletViewModel
import kotlinx.coroutines.launch

private const val TAG = "PirateNavHost"
private const val DERIVATIVE_SOURCE_RESULT_UNSET = "__unset__"
private const val DERIVATIVE_SOURCE_INITIAL_KEY = "initial_derivative_source_refs"
private const val DERIVATIVE_SOURCE_SELECTED_KEY = "selected_derivative_source_refs"
private const val CREATE_COMMUNITY_AGE_VERIFIED_KEY = "create_community_age_verified"

@Composable
fun PirateNavHost(
    navController: NavHostController,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PirateApp
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    val session by app.sessionStore.observe().collectAsState(initial = null)
    val termsPrompt by app.termsAcceptanceManager.prompt.collectAsState()
    val hasSession = session != null
    val activeAddress = session?.walletAttachments
        ?.firstOrNull { it.isPrimary }
        ?.walletAddress
        ?: session?.walletAttachments?.firstOrNull()?.walletAddress

    LaunchedEffect(hasSession, activeAddress) {
        if (!hasSession) {
            app.chatService.disconnect()
            return@LaunchedEffect
        }
        val address = activeAddress
        if (address.isNullOrBlank()) return@LaunchedEffect
        runCatching { app.chatService.connect(address) }
            .onFailure { Log.w(TAG, "XMTP bootstrap connect failed", it) }
        runCatching {
            app.chatService.currentInboxId()?.let { inboxId ->
                app.repositories.profileRepository.publishXmtpInbox(inboxId)
            }
        }.onFailure {
            Log.w(TAG, "XMTP inbox publish failed", it)
        }
    }

    NavHost(
        navController = navController,
        startDestination = PirateRoute.Home.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(180)) + slideInHorizontally(tween(220)) { width -> width / 12 }
        },
        exitTransition = {
            fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { width -> -width / 20 }
        },
        popEnterTransition = {
            fadeIn(tween(160)) + slideInHorizontally(tween(200)) { width -> -width / 16 }
        },
        popExitTransition = {
            fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { width -> width / 12 }
        },
    ) {
        composable(PirateRoute.Auth.route) {
            val vm: AuthViewModel = viewModel()
            val state by vm.state.collectAsState()
            val walletConnectState by app.reownManager.state.collectAsState()

            if (state is AuthUiState.Authenticated) {
                LaunchedEffect(Unit) {
                navController.navigate(PirateRoute.Onboarding.route) {
                    popUpTo(PirateRoute.Auth.route) { inclusive = true }
                }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize())
                SignInDrawer(
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
                    onDismiss = {
                        if (!navController.popBackStack()) {
                            navController.navigate(PirateRoute.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    },
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
            val authVm: AuthViewModel = viewModel()
            val authState by authVm.state.collectAsState()
            val walletConnectState by app.reownManager.state.collectAsState()
            HomeScreen(
                hasSession = hasSession,
                onNavigateToCommunity = { id ->
                    navController.navigate(PirateRoute.Community.buildRoute(id))
                },
                onNavigateToPost = { id ->
                    navController.navigate(PirateRoute.Post.buildRoute(id))
                },
                onNavigateToCrosspost = { sourceCommunityId, sourcePostId, sourceTitle ->
                    navController.navigate(
                        PirateRoute.CrosspostSelectCommunity.buildRoute(
                            sourceCommunityId,
                            sourcePostId,
                            sourceTitle,
                        ),
                    )
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
                    navController.navigate(PirateRoute.Notifications.route)
                },
                onNavigateToProfile = {
                    navController.navigate(PirateRoute.Me.route)
                },
                onNavigateToCreateCommunity = {
                    navController.navigate(PirateRoute.CreateCommunity.route)
                },
                onOpenNavigation = onOpenNavigation,
                signInDrawer = { onDismiss ->
                    SignInDrawer(
                        state = authState,
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
                        onLoginWallet = authVm::loginWithConnectedWallet,
                        onLoginGoogle = authVm::loginWithGoogle,
                        onLoginTwitter = authVm::loginWithTwitter,
                        onSendEmailCode = authVm::sendEmailCode,
                        onLoginEmail = authVm::loginWithEmail,
                        onLogout = authVm::logout,
                        onDismiss = onDismiss,
                    )
                },
            )
        }

        composable(PirateRoute.Chat.route) {
            AuthGate(hasSession, navController) {
                ChatScreen(
                    chatService = app.chatService,
                    isAuthenticated = hasSession,
                    userAddress = activeAddress,
                    onShowMessage = showMessage,
                    onConnected = { inboxId ->
                        runCatching { app.repositories.profileRepository.publishXmtpInbox(inboxId) }
                            .onFailure { Log.w(TAG, "XMTP inbox publish failed", it) }
                    },
                    onOpenPeerProfile = { walletAddress ->
                        navController.navigate(PirateRoute.PublicProfileByWallet.buildRoute(walletAddress)) {
                            launchSingleTop = true
                        }
                    },
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
                viewerUserId = session?.user?.userId,
                onNavigateToPost = { postId ->
                    navController.navigate(PirateRoute.Post.buildRoute(postId))
                },
                onNavigateToCompose = {
                    navController.navigate(PirateRoute.ComposePost.buildRoute(communityId))
                },
                onVerifyWithSelf = { intent, capabilities ->
                    navController.navigate(PirateRoute.VerifySelf.buildRoute(intent, capabilities))
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
            val authVm: AuthViewModel = viewModel()
            val authState by authVm.state.collectAsState()
            val walletConnectState by app.reownManager.state.collectAsState()
            PostScreen(
                postId = postId,
                hasSession = hasSession,
                onNavigateToCrosspost = { sourceCommunityId, sourcePostId, sourceTitle ->
                    navController.navigate(
                        PirateRoute.CrosspostSelectCommunity.buildRoute(
                            sourceCommunityId,
                            sourcePostId,
                            sourceTitle,
                        ),
                    )
                },
                onNavigateToCommunity = { communityId ->
                    navController.navigate(PirateRoute.Community.buildRoute(communityId))
                },
                onWatchLiveRoom = {
                    navController.navigate(PirateRoute.LiveRoomWeb.buildRoute(postId)) {
                        launchSingleTop = true
                    }
                },
                onSing = { communityId ->
                    navController.navigate(PirateRoute.Karaoke.buildRoute(communityId, postId)) {
                        launchSingleTop = true
                    }
                },
                onBroadcastLiveRoom = { communityId, liveRoomId, role ->
                    navController.navigate(PirateRoute.LiveRoomBroadcast.buildRoute(communityId, liveRoomId, role)) {
                        launchSingleTop = true
                    }
                },
                onManageReplay = { communityId, liveRoomId ->
                    navController.navigate(PirateRoute.ReplayDraft.buildRoute(communityId, liveRoomId))
                },
                onVerifyAge = {
                    navController.navigate(
                        PirateRoute.VerifySelf.buildRoute(
                            intent = "post_access_18_plus",
                            capabilities = listOf("age_over_18"),
                        ),
                    )
                },
                onStudy = { communityId ->
                    navController.navigate(PirateRoute.Study.buildRoute(communityId, postId)) {
                        launchSingleTop = true
                    }
                },
                signInDrawer = { onDismiss ->
                    SignInDrawer(
                        state = authState,
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
                        onLoginWallet = authVm::loginWithConnectedWallet,
                        onLoginGoogle = authVm::loginWithGoogle,
                        onLoginTwitter = authVm::loginWithTwitter,
                        onSendEmailCode = authVm::sendEmailCode,
                        onLoginEmail = authVm::loginWithEmail,
                        onLogout = authVm::logout,
                        onDismiss = onDismiss,
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.LiveRoomWeb.route,
            arguments = listOf(navArgument(PirateRoute.LiveRoomWeb.ARG_POST_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString(PirateRoute.LiveRoomWeb.ARG_POST_ID).orEmpty()
            LiveRoomWebViewScreen(
                app = app,
                postId = postId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.Karaoke.route,
            arguments = listOf(
                navArgument(PirateRoute.Karaoke.ARG_COMMUNITY_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.Karaoke.ARG_POST_ID) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.Karaoke.ARG_COMMUNITY_ID).orEmpty()
            val karaokePostId = backStackEntry.arguments?.getString(PirateRoute.Karaoke.ARG_POST_ID).orEmpty()
            KaraokeScreen(
                communityId = communityId,
                postId = karaokePostId,
                hasSession = hasSession,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.LiveRoomBroadcast.route,
            arguments = listOf(
                navArgument(PirateRoute.LiveRoomBroadcast.ARG_COMMUNITY_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.LiveRoomBroadcast.ARG_LIVE_ROOM_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.LiveRoomBroadcast.ARG_ROLE) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.LiveRoomBroadcast.ARG_COMMUNITY_ID).orEmpty()
            val liveRoomId = backStackEntry.arguments?.getString(PirateRoute.LiveRoomBroadcast.ARG_LIVE_ROOM_ID).orEmpty()
            val role = backStackEntry.arguments?.getString(PirateRoute.LiveRoomBroadcast.ARG_ROLE).orEmpty()
            AuthGate(hasSession, navController) {
                LiveRoomBroadcasterScreen(
                    communityId = communityId,
                    liveRoomId = liveRoomId,
                    role = role,
                    onEnded = {
                        navController.navigate(PirateRoute.ReplayDraft.buildRoute(communityId, liveRoomId)) {
                            popUpTo(PirateRoute.LiveRoomBroadcast.buildRoute(communityId, liveRoomId, role)) {
                                inclusive = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = PirateRoute.ReplayDraft.route,
            arguments = listOf(
                navArgument(PirateRoute.ReplayDraft.ARG_COMMUNITY_ID) { type = NavType.StringType },
                navArgument(PirateRoute.ReplayDraft.ARG_LIVE_ROOM_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.ReplayDraft.ARG_COMMUNITY_ID).orEmpty()
            val liveRoomId = backStackEntry.arguments?.getString(PirateRoute.ReplayDraft.ARG_LIVE_ROOM_ID).orEmpty()
            AuthGate(hasSession, navController) {
                ReplayDraftScreen(
                    communityId = communityId,
                    liveRoomId = liveRoomId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = PirateRoute.Study.route,
            arguments = listOf(
                navArgument(PirateRoute.Study.ARG_COMMUNITY_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.Study.ARG_POST_ID) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString(PirateRoute.Study.ARG_COMMUNITY_ID).orEmpty()
            val studyPostId = backStackEntry.arguments?.getString(PirateRoute.Study.ARG_POST_ID).orEmpty()
            StudyScreen(
                communityId = communityId,
                postId = studyPostId,
                hasSession = hasSession,
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
            val selectedDerivativeRefs by backStackEntry.savedStateHandle
                .getStateFlow(DERIVATIVE_SOURCE_SELECTED_KEY, DERIVATIVE_SOURCE_RESULT_UNSET)
                .collectAsState()
            LaunchedEffect(selectedDerivativeRefs) {
                if (selectedDerivativeRefs == DERIVATIVE_SOURCE_RESULT_UNSET) return@LaunchedEffect
                val refs = selectedDerivativeRefs
                    .split(",")
                    .mapNotNull { it.trim().takeIf { value -> value.isNotBlank() } }
                vm.setDerivativeRefs(refs)
                backStackEntry.savedStateHandle.set(DERIVATIVE_SOURCE_SELECTED_KEY, DERIVATIVE_SOURCE_RESULT_UNSET)
            }
            PostComposerScreen(
                viewModel = vm,
                communityId = communityId,
                hasSession = hasSession,
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
                onOpenDerivativeSourceSearch = { selectedRefs ->
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        DERIVATIVE_SOURCE_INITIAL_KEY,
                        selectedRefs.joinToString(","),
                    )
                    navController.navigate(PirateRoute.DerivativeSourceSearch.buildRoute(communityId))
                },
                onPosted = { postId ->
                    navController.navigate(PirateRoute.Post.buildRoute(postId)) {
                        popUpTo(PirateRoute.ComposePost.buildRoute(communityId)) {
                            inclusive = true
                        }
                    }
                },
                onOpenCommunity = { selectedCommunityId ->
                    navController.navigate(PirateRoute.Community.buildRoute(selectedCommunityId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.DerivativeSourceSearch.route,
            arguments = listOf(navArgument(PirateRoute.DerivativeSourceSearch.ARG_COMMUNITY_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments
                ?.getString(PirateRoute.DerivativeSourceSearch.ARG_COMMUNITY_ID)
                .orEmpty()
            val selectedRefs = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(DERIVATIVE_SOURCE_INITIAL_KEY)
                ?.takeIf { it != DERIVATIVE_SOURCE_RESULT_UNSET }
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf { value -> value.isNotBlank() } }
                .orEmpty()
            val vm: DerivativeSourceSearchViewModel = viewModel()
            AuthGate(hasSession, navController) {
                DerivativeSourceSearchScreen(
                    communityId = communityId,
                    initialSelectedIds = selectedRefs,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onDone = { refs ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            DERIVATIVE_SOURCE_SELECTED_KEY,
                            refs.joinToString(","),
                        )
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(
            route = PirateRoute.ComposeCrosspost.route,
            arguments = listOf(
                navArgument(PirateRoute.ComposeCrosspost.ARG_COMMUNITY_ID) { type = NavType.StringType },
                navArgument(PirateRoute.ComposeCrosspost.ARG_SOURCE_COMMUNITY_ID) { type = NavType.StringType },
                navArgument(PirateRoute.ComposeCrosspost.ARG_SOURCE_POST_ID) { type = NavType.StringType },
                navArgument(PirateRoute.ComposeCrosspost.ARG_SOURCE_TITLE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val communityId = backStackEntry.arguments
                ?.getString(PirateRoute.ComposeCrosspost.ARG_COMMUNITY_ID)
                .orEmpty()
            val sourceCommunityId = backStackEntry.arguments
                ?.getString(PirateRoute.ComposeCrosspost.ARG_SOURCE_COMMUNITY_ID)
                .orEmpty()
            val sourcePostId = backStackEntry.arguments
                ?.getString(PirateRoute.ComposeCrosspost.ARG_SOURCE_POST_ID)
                .orEmpty()
            val sourceTitle = backStackEntry.arguments
                ?.getString(PirateRoute.ComposeCrosspost.ARG_SOURCE_TITLE)
                ?.takeIf { it.isNotBlank() }
            val vm: PostComposerViewModel = viewModel()
            LaunchedEffect(communityId, sourceCommunityId, sourcePostId, sourceTitle) {
                vm.configureCrosspost(
                    targetCommunityId = communityId,
                    sourcePostId = sourcePostId,
                    sourceCommunityId = sourceCommunityId,
                    sourceTitle = sourceTitle,
                )
            }
            PostComposerScreen(
                viewModel = vm,
                communityId = communityId,
                hasSession = hasSession,
                onSignIn = { navController.navigate(PirateRoute.Auth.route) },
                onOpenDerivativeSourceSearch = {},
                onPosted = { postId ->
                    navController.navigate(PirateRoute.Post.buildRoute(postId)) {
                        popUpTo(
                            PirateRoute.CrosspostSelectCommunity.buildRoute(
                                sourceCommunityId,
                                sourcePostId,
                                sourceTitle,
                            ),
                        ) { inclusive = true }
                    }
                },
                onOpenCommunity = { selectedCommunityId ->
                    navController.navigate(PirateRoute.Community.buildRoute(selectedCommunityId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        fun androidx.navigation.NavGraphBuilder.notificationsDestination(route: String) {
            composable(route) {
            AuthGate(hasSession, navController) {
                InboxScreen(
                    onOpenPost = { postId ->
                        navController.navigate(PirateRoute.Post.buildRoute(postId))
                    },
                    onOpenCommunityNamespace = { communityId ->
                        navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, "namespace"))
                    },
                    onOpenCommunityRequests = { communityId ->
                        navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, "requests"))
                    },
                    onOpenProfileSettings = {
                        navController.navigate(PirateRoute.SettingsIndex.route)
                    },
                    onSignIn = {
                        navController.navigate(PirateRoute.Auth.route)
                    },
                )
            }
            }
        }

        notificationsDestination(PirateRoute.Notifications.route)
        notificationsDestination(PirateRoute.Inbox.route)

        composable(PirateRoute.Wallet.route) {
            val walletConnectState by app.reownManager.state.collectAsState()
            val vm: WalletViewModel = viewModel()
            val walletUiState by vm.state.collectAsState()
            val authVm: AuthViewModel = viewModel()
            val authState by authVm.state.collectAsState()
            var showSignInDrawer by remember { mutableStateOf(false) }
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
                    showSignInDrawer = true
                },
                onLoadNativeBalance = vm::loadNativeBalance,
                onSendNativeAsset = vm::sendNativeAsset,
                onClearSendFeedback = vm::clearSendFeedback,
            )
            if (showSignInDrawer) {
                SignInDrawer(
                    state = authState,
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
                    onLoginWallet = authVm::loginWithConnectedWallet,
                    onLoginGoogle = authVm::loginWithGoogle,
                    onLoginTwitter = authVm::loginWithTwitter,
                    onSendEmailCode = authVm::sendEmailCode,
                    onLoginEmail = authVm::loginWithEmail,
                    onLogout = authVm::logout,
                    onDismiss = { showSignInDrawer = false },
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
                    onEditProfile = {
                        navController.navigate(PirateRoute.SettingsIndex.route)
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

        composable(PirateRoute.CreateCommunity.route) { backStackEntry ->
            AuthGate(hasSession, navController) {
                val ageVerificationCompleted by backStackEntry.savedStateHandle
                    .getStateFlow(CREATE_COMMUNITY_AGE_VERIFIED_KEY, false)
                    .collectAsState()
                CreateCommunityScreen(
                    onBack = { navController.popBackStack() },
                    onVerifyAge = {
                        navController.navigate(
                            PirateRoute.VerifySelf.buildRoute(
                                intent = "community_creation",
                                capabilities = listOf("age_over_18"),
                            ),
                        )
                    },
                    onCreated = { communityId ->
                        navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, "namespace")) {
                            popUpTo(PirateRoute.CreateCommunity.route) {
                                inclusive = true
                            }
                        }
                    },
                    ageVerificationCompleted = ageVerificationCompleted,
                    onAgeVerificationConsumed = {
                        backStackEntry.savedStateHandle.set(CREATE_COMMUNITY_AGE_VERIFIED_KEY, false)
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
                hasSession = hasSession,
                onBack = { navController.popBackStack() },
                onSignIn = {
                    navController.navigate(PirateRoute.Auth.route)
                },
                onSelectCommunity = { communityId ->
                    navController.navigate(PirateRoute.ComposePost.buildRoute(communityId))
                },
            )
        }

        composable(
            route = PirateRoute.CrosspostSelectCommunity.route,
            arguments = listOf(
                navArgument(PirateRoute.CrosspostSelectCommunity.ARG_SOURCE_COMMUNITY_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.CrosspostSelectCommunity.ARG_SOURCE_POST_ID) {
                    type = NavType.StringType
                },
                navArgument(PirateRoute.CrosspostSelectCommunity.ARG_SOURCE_TITLE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val sourceCommunityId = backStackEntry.arguments
                ?.getString(PirateRoute.CrosspostSelectCommunity.ARG_SOURCE_COMMUNITY_ID)
                .orEmpty()
            val sourcePostId = backStackEntry.arguments
                ?.getString(PirateRoute.CrosspostSelectCommunity.ARG_SOURCE_POST_ID)
                .orEmpty()
            val sourceTitle = backStackEntry.arguments
                ?.getString(PirateRoute.CrosspostSelectCommunity.ARG_SOURCE_TITLE)
                ?.takeIf { it.isNotBlank() }
            GlobalSubmitScreen(
                hasSession = hasSession,
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(PirateRoute.Auth.route) },
                onSelectCommunity = { communityId ->
                    navController.navigate(
                        PirateRoute.ComposeCrosspost.buildRoute(
                            communityId,
                            sourceCommunityId,
                            sourcePostId,
                            sourceTitle,
                        ),
                    )
                },
            )
        }

        composable(
            route = PirateRoute.VerifySelf.route,
            arguments = listOf(navArgument(PirateRoute.VerifySelf.ARG_INTENT) {
                type = NavType.StringType
            }, navArgument(PirateRoute.VerifySelf.ARG_CAPABILITIES) {
                defaultValue = ""
                nullable = true
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val intent = backStackEntry.arguments
                ?.getString(PirateRoute.VerifySelf.ARG_INTENT)
                ?.takeIf { it in PirateRouteSections.verificationIntents }
                ?: PirateRoute.VerifySelf.DEFAULT_INTENT
            val requestedCapabilities = backStackEntry.arguments
                ?.getString(PirateRoute.VerifySelf.ARG_CAPABILITIES)
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it in PirateRouteSections.selfCapabilities }
                .distinct()
            AuthGate(hasSession, navController) {
                SelfVerificationScreen(
                    verificationIntent = intent,
                    requestedCapabilities = requestedCapabilities,
                    onVerified = when {
                        intent == "post_access_18_plus" -> {
                            { navController.popBackStack() }
                        }
                        intent == "community_creation" && requestedCapabilities.contains("age_over_18") -> {
                            {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(CREATE_COMMUNITY_AGE_VERIFIED_KEY, true)
                                navController.popBackStack()
                            }
                        }
                        else -> null
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(PirateRoute.SettingsIndex.route) {
            AuthGate(hasSession, navController) {
                SettingsScreen(
                    section = null,
                    onBack = { navController.popBackStack() },
                    onClose = {
                        navController.navigate(PirateRoute.Me.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSection = { section ->
                        navController.navigate(PirateRoute.Settings.buildRoute(section))
                    },
                )
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
                    onClose = {
                        navController.navigate(PirateRoute.Me.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSection = { nextSection ->
                        navController.navigate(PirateRoute.Settings.buildRoute(nextSection))
                    },
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
                    onNavigateToSection = { section ->
                        navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, section)) {
                            launchSingleTop = true
                        }
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
                    onNavigateToSection = { nextSection ->
                        navController.navigate(PirateRoute.CommunityModerationSection.buildRoute(communityId, nextSection)) {
                            launchSingleTop = true
                        }
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
                onViewAvailability = { hostUserId ->
                    navController.navigate(PirateRoute.BookingAvailability.buildRoute(hostUserId))
                },
                onNavigateToCommunity = {
                    navController.navigate(PirateRoute.Community.buildRoute(it))
                },
                onMessage = { address ->
                    scope.launch {
                        runCatching {
                            val selfAddress = activeAddress ?: throw IllegalStateException("Missing chat auth context")
                            if (!app.chatService.connected.value) app.chatService.connect(selfAddress)
                            val dmId = app.chatService.newDm(address)
                            app.chatService.openConversation(dmId)
                            navController.navigate(PirateRoute.Chat.route) { launchSingleTop = true }
                        }.onFailure {
                            showMessage("Message failed: ${it.message ?: "unknown error"}")
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.PublicProfileByWallet.route,
            arguments = listOf(navArgument(PirateRoute.PublicProfileByWallet.ARG_WALLET_ADDRESS) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val walletAddress = backStackEntry.arguments?.getString(PirateRoute.PublicProfileByWallet.ARG_WALLET_ADDRESS).orEmpty()
            PublicProfileScreen(
                handleLabel = walletAddress,
                walletAddress = walletAddress,
                onViewAvailability = { hostUserId ->
                    navController.navigate(PirateRoute.BookingAvailability.buildRoute(hostUserId))
                },
                onNavigateToCommunity = {
                    navController.navigate(PirateRoute.Community.buildRoute(it))
                },
                onMessage = { address ->
                    scope.launch {
                        runCatching {
                            val selfAddress = activeAddress ?: throw IllegalStateException("Missing chat auth context")
                            if (!app.chatService.connected.value) app.chatService.connect(selfAddress)
                            val dmId = app.chatService.newDm(address)
                            app.chatService.openConversation(dmId)
                            navController.navigate(PirateRoute.Chat.route) { launchSingleTop = true }
                        }.onFailure {
                            showMessage("Message failed: ${it.message ?: "unknown error"}")
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = PirateRoute.BookingAvailability.route,
            arguments = listOf(navArgument(PirateRoute.BookingAvailability.ARG_HOST_USER_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val hostUserId = backStackEntry.arguments
                ?.getString(PirateRoute.BookingAvailability.ARG_HOST_USER_ID)
                .orEmpty()
            BookingAvailabilityScreen(
                hostUserId = hostUserId,
                onBack = { navController.popBackStack() },
            )
        }

        appKitGraph(navController)
    }

    termsPrompt?.let { prompt ->
        TermsAcceptanceDialog(
            state = prompt,
            onAccept = { scope.launch { app.termsAcceptanceManager.accept() } },
            onDismiss = { scope.launch { app.termsAcceptanceManager.dismiss() } },
        )
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
        val context = LocalContext.current
        val app = context.applicationContext as PirateApp
        val authVm: AuthViewModel = viewModel()
        val authState by authVm.state.collectAsState()
        val walletConnectState by app.reownManager.state.collectAsState()

        Box(modifier = Modifier.fillMaxSize())
        SignInDrawer(
            state = authState,
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
            onLoginWallet = authVm::loginWithConnectedWallet,
            onLoginGoogle = authVm::loginWithGoogle,
            onLoginTwitter = authVm::loginWithTwitter,
            onSendEmailCode = authVm::sendEmailCode,
            onLoginEmail = authVm::loginWithEmail,
            onLogout = authVm::logout,
            onDismiss = {
                navController.navigate(PirateRoute.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = false
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}
