package sc.pirate.app.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.shortAddress
import sc.pirate.app.walletconnect.ReownUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    state: AuthUiState,
    walletConnectState: ReownUiState,
    onOpenWalletConnect: () -> Unit,
    onLoginWallet: () -> Unit,
    onLoginGoogle: () -> Unit,
    onLoginTwitter: () -> Unit,
    onSendEmailCode: (String) -> Unit,
    onLoginEmail: (String, String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SignInContent(
        state = state,
        walletConnectState = walletConnectState,
        onOpenWalletConnect = onOpenWalletConnect,
        onLoginWallet = onLoginWallet,
        onLoginGoogle = onLoginGoogle,
        onLoginTwitter = onLoginTwitter,
        onSendEmailCode = onSendEmailCode,
        onLoginEmail = onLoginEmail,
        onLogout = onLogout,
        modifier = modifier.fillMaxSize(),
        centered = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInDrawer(
    state: AuthUiState,
    walletConnectState: ReownUiState,
    onOpenWalletConnect: () -> Unit,
    onLoginWallet: () -> Unit,
    onLoginGoogle: () -> Unit,
    onLoginTwitter: () -> Unit,
    onSendEmailCode: (String) -> Unit,
    onLoginEmail: (String, String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var attemptedConnectedWalletLogin by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthUiState.Authenticated) {
            onDismiss()
        }
    }

    LaunchedEffect(walletConnectState.isConnected, state) {
        if (
            walletConnectState.isConnected &&
            state is AuthUiState.Idle &&
            !attemptedConnectedWalletLogin
        ) {
            attemptedConnectedWalletLogin = true
            onLoginWallet()
        }
    }

    if (
        walletConnectState.isConnected &&
        (state is AuthUiState.Idle || state is AuthUiState.Loading)
    ) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PirateTokens.colors.bgPage,
    ) {
        SignInContent(
            state = state,
            walletConnectState = walletConnectState,
            onOpenWalletConnect = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                    onOpenWalletConnect()
                }
            },
            onLoginWallet = onLoginWallet,
            onLoginGoogle = onLoginGoogle,
            onLoginTwitter = onLoginTwitter,
            onSendEmailCode = onSendEmailCode,
            onLoginEmail = onLoginEmail,
            onLogout = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            centered = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignInContent(
    state: AuthUiState,
    walletConnectState: ReownUiState,
    onOpenWalletConnect: () -> Unit,
    onLoginWallet: () -> Unit,
    onLoginGoogle: () -> Unit,
    onLoginTwitter: () -> Unit,
    onSendEmailCode: (String) -> Unit,
    onLoginEmail: (String, String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = if (centered) {
            modifier
                .imePadding()
                .verticalScroll(scrollState)
                .padding(24.dp)
        } else {
            modifier.verticalScroll(scrollState)
        },
        verticalArrangement = if (centered) Arrangement.Center else Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is AuthUiState.Loading -> {
                CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
            }
            is AuthUiState.Unavailable -> {
                PirateCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Sign-in is not configured",
                        color = PirateTokens.colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = PirateTokens.colors.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            is AuthUiState.Error -> {
                ErrorMessage(message = state.message)
                Spacer(modifier = Modifier.height(16.dp))
                LoginButtons(
                    walletConnectState = walletConnectState,
                    onOpenWalletConnect = onOpenWalletConnect,
                    onLoginWallet = onLoginWallet,
                    onLoginGoogle = onLoginGoogle,
                    onLoginTwitter = onLoginTwitter,
                )
                Spacer(modifier = Modifier.height(24.dp))
                EmailLoginForm(onSendEmailCode, onLoginEmail)
            }
            is AuthUiState.Authenticated -> {
                Text(
                    text = "Signed in",
                    color = PirateTokens.colors.accentSuccess,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PirateTokens.colors.surfaceDanger,
                    ),
                ) {
                    Text("Log out")
                }
            }
            is AuthUiState.Idle -> {
                LoginButtons(
                    walletConnectState = walletConnectState,
                    onOpenWalletConnect = onOpenWalletConnect,
                    onLoginWallet = onLoginWallet,
                    onLoginGoogle = onLoginGoogle,
                    onLoginTwitter = onLoginTwitter,
                )

                Spacer(modifier = Modifier.height(24.dp))

                EmailLoginForm(onSendEmailCode, onLoginEmail)
            }
        }
        if (!centered) Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = PirateTokens.colors.accentDanger,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LoginButtons(
    walletConnectState: ReownUiState,
    onOpenWalletConnect: () -> Unit,
    onLoginWallet: () -> Unit,
    onLoginGoogle: () -> Unit,
    onLoginTwitter: () -> Unit,
) {
    AuthProviderButton(
        icon = PhosphorIcons.Wallet,
        text = when {
            !walletConnectState.available -> "Wallet unavailable"
            walletConnectState.isConnected -> "Continue with Wallet"
            else -> "Connect Wallet"
        },
        onClick = if (walletConnectState.isConnected) onLoginWallet else onOpenWalletConnect,
        enabled = walletConnectState.available || walletConnectState.isConnected,
    )

    walletConnectState.statusMessage
        ?.takeIf { !walletConnectState.available && it.isNotBlank() }
        ?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }

    walletConnectState.connectedAddress?.takeIf { it.isNotBlank() }?.let { address ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connected: ${shortAddress(address)}",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    AuthProviderButton(
        icon = PhosphorIcons.TwitterLogo,
        text = "X",
        onClick = onLoginTwitter,
    )

    Spacer(modifier = Modifier.height(10.dp))

    AuthProviderButton(
        icon = PhosphorIcons.GoogleLogo,
        text = "Google",
        onClick = onLoginGoogle,
        emphasized = true,
    )
}

@Composable
private fun AuthProviderButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) PirateTokens.colors.accentBrand else PirateTokens.colors.surfaceInteractive,
            contentColor = PirateTokens.colors.textPrimary,
            disabledContainerColor = PirateTokens.colors.surfaceDisabled,
            disabledContentColor = PirateTokens.colors.textSecondary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = text)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmailLoginForm(
    onSendCode: (String) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val emailBringIntoView = remember { BringIntoViewRequester() }
    val codeBringIntoView = remember { BringIntoViewRequester() }

    Text(
        text = "Or sign in with email",
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textSecondary,
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(emailBringIntoView)
            .onFocusEvent { focusState ->
                if (focusState.isFocused) {
                    scope.launch {
                        delay(250)
                        emailBringIntoView.bringIntoView()
                    }
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = if (codeSent) ImeAction.Next else ImeAction.Done,
        ),
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (codeSent) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Verification code") },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(codeBringIntoView)
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(250)
                            codeBringIntoView.bringIntoView()
                        }
                    }
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onLogin(email, code) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = code.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PirateTokens.colors.accentBrand,
            ),
        ) {
            Text("Verify and sign in")
        }
    } else {
        Button(
            onClick = {
                onSendCode(email)
                codeSent = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = email.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PirateTokens.colors.surfaceInteractive,
                contentColor = PirateTokens.colors.textPrimary,
            ),
        ) {
            Text("Send code")
        }
    }
}
