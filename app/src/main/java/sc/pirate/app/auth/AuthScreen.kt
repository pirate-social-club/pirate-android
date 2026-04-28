package sc.pirate.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.shortAddress
import sc.pirate.app.walletconnect.ReownUiState

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

    LaunchedEffect(state) {
        if (state is AuthUiState.Authenticated) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PirateTokens.colors.bgPage,
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
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            centered = false,
        )
    }
}

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
    Column(
        modifier = if (centered) modifier.padding(24.dp) else modifier,
        verticalArrangement = if (centered) Arrangement.Center else Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign in to Pirate",
            style = MaterialTheme.typography.headlineMedium,
            color = PirateTokens.colors.textPrimary,
        )

        if (centered) Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Use Privy to continue.",
            style = MaterialTheme.typography.bodyLarge,
            color = PirateTokens.colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(if (centered) 32.dp else 12.dp))

        when (state) {
            is AuthUiState.Loading -> {
                CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
            }
            is AuthUiState.Unavailable -> {
                PirateCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Privy is not configured",
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
        if (!centered) Spacer(modifier = Modifier.height(8.dp))
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
    Button(
        onClick = if (walletConnectState.isConnected) onLoginWallet else onOpenWalletConnect,
        modifier = Modifier.fillMaxWidth(),
        enabled = walletConnectState.available || walletConnectState.isConnected,
        colors = ButtonDefaults.buttonColors(
            containerColor = PirateTokens.colors.surfaceInteractive,
            contentColor = PirateTokens.colors.textPrimary,
        ),
    ) {
        Text(
            when {
                !walletConnectState.available -> "Wallet connect unavailable"
                walletConnectState.isConnected -> "Continue with connected wallet"
                else -> "Continue with wallet"
            },
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

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

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onLoginGoogle,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = PirateTokens.colors.accentBrand,
        ),
    ) {
        Text("Continue with Google", modifier = Modifier.padding(vertical = 4.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onLoginTwitter,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = PirateTokens.colors.surfaceInteractive,
            contentColor = PirateTokens.colors.textPrimary,
        ),
    ) {
        Text("Continue with X", modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun EmailLoginForm(
    onSendCode: (String) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }

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
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (codeSent) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Verification code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onLogin(email, code) },
            modifier = Modifier.fillMaxWidth(),
            enabled = code.isNotBlank(),
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
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PirateTokens.colors.surfaceInteractive,
                contentColor = PirateTokens.colors.textPrimary,
            ),
        ) {
            Text("Send code")
        }
    }
}
