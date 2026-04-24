package sc.pirate.app.createcommunity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(
    onBack: () -> Unit,
    onVerifyWithId: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Create community", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PirateTokens.colors.bgPage,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            PirateCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Community creation is still in progress.",
                    color = PirateTokens.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The first working prerequisite is unique-human verification. Complete that flow now, then we can wire native community creation on top of it.",
                    color = PirateTokens.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PirateButton(
                    text = "Verify with ID",
                    onClick = onVerifyWithId,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
