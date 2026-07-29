package sc.pirate.app.shared

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import sc.pirate.app.BuildConfig

fun sharePost(context: Context, postId: String, title: String? = null) {
    val url = postShareUrl(postId)
    val text = title?.trim()?.takeIf { it.isNotBlank() }?.let { "$it\n$url" } ?: url
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title ?: "Pirate post")
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}

fun copyPostLink(context: Context, postId: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pirate post", postShareUrl(postId)))
}

fun postShareUrl(postId: String): String =
    "${BuildConfig.WEB_BASE_URL.trimEnd('/')}/p/$postId?share=1"
