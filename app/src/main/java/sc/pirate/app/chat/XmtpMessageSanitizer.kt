package sc.pirate.app.chat

import org.xmtp.android.library.libxmtp.DecodedMessage
import uniffi.xmtpv3.FfiConversationMessageKind

internal fun sanitizeXmtpBody(message: DecodedMessage): String {
    val kind = runCatching { message.kind }.getOrNull()
    if (kind != null && kind != FfiConversationMessageKind.APPLICATION) return ""

    val fallback = runCatching { message.fallback }.getOrDefault("").trim()
    val body = runCatching { message.body }.getOrDefault("").trim()
    val text = fallback.ifBlank { body }
    if (text.isBlank() || looksLikeProtocolPrefixedPayload(text)) return ""
    return text
}

private fun looksLikeProtocolPrefixedPayload(value: String): Boolean {
    if (!value.startsWith("@")) return false
    val firstWhitespace = value.indexOfFirst { it.isWhitespace() }
    val token = if (firstWhitespace == -1) value.drop(1) else value.substring(1, firstWhitespace)
    if (token.length < 20 || token.contains('.') || token.any { it.isWhitespace() }) return false
    if (!token.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '=' || it == '+' || it == '/' }) {
        return false
    }
    if (firstWhitespace == -1) return true
    return looksLikeEncodedPayloadRemainder(value.substring(firstWhitespace).trim())
}

private fun looksLikeEncodedPayloadRemainder(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.contains(' ')) {
        val tokens = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.size <= 3 && tokens.all(::looksLikeBlobToken)
    }
    return looksLikeBlobToken(value)
}

private fun looksLikeBlobToken(value: String): Boolean {
    if (value.length < 16) return false
    if (value.endsWith(".pirate", ignoreCase = true) || value.endsWith(".heaven", ignoreCase = true)) return false
    val allowedCount = value.count {
        it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' || it == '+' || it == '/' || it == '.'
    }
    if (allowedCount != value.length) return false
    return value.count { it.isLetterOrDigit() }.toDouble() / value.length.toDouble() >= 0.85
}
