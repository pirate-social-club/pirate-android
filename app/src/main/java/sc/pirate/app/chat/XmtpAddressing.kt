package sc.pirate.app.chat

import android.content.Context
import android.util.Log
import sc.pirate.app.security.LocalSecp256k1Store

private const val TAG = "XmtpAddressing"

internal fun getOrCreateLocalSigner(
    appContext: Context,
    address: String,
): LocalSigningKey {
    val identity = LocalSecp256k1Store.getOrCreateIdentity(appContext, address)
    Log.d(TAG, "XMTP local signer: userAddress=$address xmtpAddress=${identity.signerAddress}")
    return LocalSigningKey(identity.keyPair, identity.signerAddress)
}

internal fun normalizeEthAddressOrNull(value: String): String? =
    runCatching { normalizeEthAddress(value) }.getOrNull()

internal fun normalizeEthAddress(value: String): String {
    val trimmed = value.trim()
    val withPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) trimmed else "0x$trimmed"
    val lower = withPrefix.lowercase()
    require(lower.length == 42) { "Invalid Ethereum address length: $value" }
    require(lower.startsWith("0x")) { "Invalid Ethereum address: $value" }
    require(lower.drop(2).all { it.isDigit() || it in 'a'..'f' }) { "Invalid Ethereum address: $value" }
    return lower
}

internal fun looksLikeEthereumAddress(value: String): Boolean =
    normalizeEthAddressOrNull(value) != null

internal fun looksLikeXmtpInboxId(value: String): Boolean =
    value.length == 64 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
