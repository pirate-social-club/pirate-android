package sc.pirate.app.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import sc.pirate.app.api.model.Profile
import sc.pirate.app.shared.buildDefaultProfileCoverSrc
import sc.pirate.app.shared.buildDefaultUserAvatarSrc
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.shortAddress

enum class ProfilePageTab { Overview, Posts, Comments, Wallet }

data class ProfileStat(
    val label: String,
    val value: String,
)

data class ProfilePageData(
    val profile: Profile,
    val viewerContext: ViewerContext,
    val stats: List<ProfileStat> = emptyList(),
    val walletAddress: String? = null,
)

enum class ViewerContext { Self, Public }

@Composable
fun PirateProfilePage(
    data: ProfilePageData,
    modifier: Modifier = Modifier,
    onEditProfile: (() -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null,
    onBook: (() -> Unit)? = null,
    isBlocked: Boolean = false,
    blockUpdating: Boolean = false,
    onToggleBlock: (() -> Unit)? = null,
) {
    val hasWalletTab = !data.walletAddress.isNullOrBlank()
    val tabs = buildList {
        add(ProfilePageTab.Overview)
        add(ProfilePageTab.Posts)
        add(ProfilePageTab.Comments)
        if (hasWalletTab) add(ProfilePageTab.Wallet)
    }
    var selectedTab by remember { mutableStateOf(ProfilePageTab.Overview) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ProfileIdentityHero(
                data = data,
                onEditProfile = onEditProfile,
                onMessage = onMessage,
                onBook = onBook,
                isBlocked = isBlocked,
                blockUpdating = blockUpdating,
                onToggleBlock = onToggleBlock,
            )
        }
        item {
            FlatIconTabs(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
        item {
            when (selectedTab) {
                ProfilePageTab.Overview -> ProfilePanelShell(emptyCopy = "No activity yet")
                ProfilePageTab.Posts -> ProfilePanelShell(emptyCopy = "No activity yet")
                ProfilePageTab.Comments -> ProfilePanelShell(emptyCopy = "No activity yet")
                ProfilePageTab.Wallet -> WalletPanel(walletAddress = data.walletAddress)
            }
        }
    }
}

@Composable
private fun ProfileIdentityHero(
    data: ProfilePageData,
    onEditProfile: (() -> Unit)?,
    onMessage: ((String) -> Unit)?,
    onBook: (() -> Unit)?,
    isBlocked: Boolean,
    blockUpdating: Boolean,
    onToggleBlock: (() -> Unit)?,
) {
    val profile = data.profile
    val displayHandle = profile.displayHandle()
    val displayName = profile.displayName ?: displayHandle.ifBlank { "Profile" }
    val profileSeed = profile.userId.ifBlank { displayHandle.ifBlank { displayName } }
    val coverSrc = resolvePublicMediaSrc(profile.coverRef)
    val avatarSrc = resolvePublicMediaSrc(profile.avatarRef)
        ?: buildDefaultUserAvatarSrc(profileSeed)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .background(PirateTokens.colors.bgElevated),
        ) {
            if (coverSrc == null) {
                DefaultProfileCover(
                    displayName = displayName,
                    handle = displayHandle,
                    userId = profileSeed,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DefaultProfileCover(
                    displayName = displayName,
                    handle = displayHandle,
                    userId = profileSeed,
                    modifier = Modifier.fillMaxSize(),
                )
                AsyncImage(
                    model = coverSrc,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-40).dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProfileAvatar(
                    model = avatarSrc,
                    displayName = displayName,
                    modifier = Modifier.size(80.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.stats.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        data.stats.take(3).forEach { stat ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stat.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PirateTokens.colors.textSecondary,
                                    maxLines = 1,
                                )
                                Text(
                                    text = stat.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PirateTokens.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                MessagingStatusLine(data = data)
                if (data.viewerContext == ViewerContext.Self && onEditProfile != null) {
                    PirateButton(
                        text = "Edit profile",
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (data.viewerContext == ViewerContext.Public) {
                    if (!isBlocked && data.profile.isBookable && onBook != null) {
                        PirateButton(
                            text = "View availability",
                            onClick = onBook,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!isBlocked && onMessage != null && !data.messageTarget().isNullOrBlank()) {
                        PirateButton(
                            text = "Message",
                            onClick = { data.messageTarget()?.let(onMessage) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = PhosphorIcons.ChatCircle,
                        )
                    }
                    if (onToggleBlock != null) {
                        PirateButton(
                            text = if (isBlocked) "Unblock user" else "Block user",
                            onClick = onToggleBlock,
                            modifier = Modifier.fillMaxWidth(),
                            loading = blockUpdating,
                            variant = sc.pirate.app.ui.ButtonVariant.Outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagingStatusLine(data: ProfilePageData) {
    val inbox = data.profile.xmtpInbox?.trim()?.takeIf { it.isNotBlank() } ?: return
    val label =
        if (data.viewerContext == ViewerContext.Self) {
            "Messaging enabled"
        } else {
            "Can receive encrypted messages"
        }
    Text(
        text = "$label · ${shortAddress(inbox)}",
        style = MaterialTheme.typography.bodySmall,
        color = PirateTokens.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun ProfilePageData.messageTarget(): String? =
    profile.xmtpInbox?.trim()?.takeIf { it.isNotBlank() }
        ?: walletAddress?.trim()?.takeIf { it.isNotBlank() }

@Composable
private fun ProfileAvatar(
    model: String,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(4.dp, PirateTokens.colors.bgPage, CircleShape)
            .clip(CircleShape)
            .background(PirateTokens.colors.bgElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.headlineSmall,
            color = PirateTokens.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DefaultProfileCover(
    displayName: String,
    handle: String,
    userId: String,
    modifier: Modifier = Modifier,
) {
    val seed = "$userId:${displayName.ifBlank { handle }}:profile-cover"
    val colors = defaultCoverColors[Math.floorMod(stableHash(seed), defaultCoverColors.size)]
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(colors.first, colors.second),
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(72.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(132.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
    }
}

private val defaultCoverColors = listOf(
    Color(0xFF174A53) to Color(0xFFB56B34),
    Color(0xFF51335F) to Color(0xFF1F7A6D),
    Color(0xFF25476A) to Color(0xFF8A3D4F),
    Color(0xFF5A3F2B) to Color(0xFF27635F),
    Color(0xFF6E3A46) to Color(0xFF2E5A77),
)

private fun stableHash(value: String): Int {
    var hash = -2128831035
    for (char in value) {
        hash = hash xor char.code
        hash *= 16777619
    }
    return hash ushr 0
}

@Composable
private fun FlatIconTabs(
    tabs: List<ProfilePageTab>,
    selectedTab: ProfilePageTab,
    onSelect: (ProfilePageTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) PirateTokens.colors.textPrimary else PirateTokens.colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (selected) {
                                    PirateTokens.colors.accentBrand
                                } else {
                                    Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
    }
}

@Composable
private fun ProfilePanelShell(emptyCopy: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
        Text(
            text = emptyCopy,
            style = MaterialTheme.typography.bodyLarge,
            color = PirateTokens.colors.textSecondary,
            modifier = Modifier.padding(vertical = 28.dp),
        )
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
    }
}

@Composable
private fun WalletPanel(walletAddress: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Wallet",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
            Text(
                text = walletAddress?.let(::shortAddress).orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = PirateTokens.colors.textPrimary,
            )
        }
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
    }
}

private val ProfilePageTab.icon: ImageVector
    get() = when (this) {
        ProfilePageTab.Overview -> PhosphorIcons.SquaresFour
        ProfilePageTab.Posts -> PhosphorIcons.Article
        ProfilePageTab.Comments -> PhosphorIcons.ChatCircle
        ProfilePageTab.Wallet -> PhosphorIcons.Wallet
    }

private val ProfilePageTab.label: String
    get() = when (this) {
        ProfilePageTab.Overview -> "Overview"
        ProfilePageTab.Posts -> "Posts"
        ProfilePageTab.Comments -> "Comments"
        ProfilePageTab.Wallet -> "Wallet"
    }

fun Profile.displayHandle(): String {
    val label = primaryPublicHandle?.label ?: globalHandle?.label.orEmpty()
    if (label.isBlank()) return ""
    return if (label.contains(".")) label else "$label.pirate"
}
