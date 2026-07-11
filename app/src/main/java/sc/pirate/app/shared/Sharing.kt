package sc.pirate.app.shared

import android.content.Context
import android.content.Intent
import sc.pirate.app.BuildConfig

fun sharePost(context: Context, postId: String, title: String? = null) {
    val url = "${BuildConfig.WEB_BASE_URL.trimEnd('/')}/p/$postId"
    val text = title?.trim()?.takeIf { it.isNotBlank() }?.let { "$it\n$url" } ?: url
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title ?: "Pirate post")
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}
