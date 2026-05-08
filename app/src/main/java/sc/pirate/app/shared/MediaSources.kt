package sc.pirate.app.shared

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import sc.pirate.app.BuildConfig

fun resolvePublicMediaSrc(value: String?): String? {
    val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalized = trimmed.lowercase()
    if (
        normalized.startsWith("data:") ||
        normalized.startsWith("blob:") ||
        normalized.startsWith("http://") ||
        normalized.startsWith("https://")
    ) {
        return trimmed
    }
    if (Regex("^[a-z][a-z0-9+.-]*:").containsMatchIn(trimmed)) {
        return null
    }

    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
}

fun buildDefaultUserAvatarSrc(seedSource: String): String {
    val seed = seedSource.trim()
    if (seed.isBlank()) return ""
    val background = userAvatarBackgroundColors[Math.floorMod(hashSeed(seed), userAvatarBackgroundColors.size)]
    return "https://api.dicebear.com/9.x/thumbs/svg" +
        "?seed=${encodeQuery(seed)}" +
        "&size=128" +
        "&radius=50" +
        "&scale=92" +
        "&backgroundColor=$background" +
        "&eyesColor=111111" +
        "&mouthColor=111111" +
        "&shapeColor=f7f5f0,fffdf7,f6f3eb"
}

fun buildDefaultProfileCoverSrc(
    displayName: String,
    handle: String?,
    userId: String,
): String {
    val label = normalizeLabel(displayName.ifBlank { handle.orEmpty().ifBlank { userId } })
    val seed = "${userId.trim()}:$label:profile-cover"
    val hash = Math.abs(hashSeed(seed))
    val hue = hash % 360
    val warmHue = (hue + 36) % 360
    val coolHue = (hue + 154) % 360
    val xOffset = 180 + (hash % 340)
    val yOffset = 42 + (hash % 72)

    val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 420" role="img" aria-label="${escapeXml(label)} cover">
          <defs>
            <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="hsl($hue 58% 18%)" />
              <stop offset="46%" stop-color="hsl($warmHue 70% 26%)" />
              <stop offset="100%" stop-color="hsl($coolHue 62% 16%)" />
            </linearGradient>
            <radialGradient id="soft" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="rgba(255,255,255,0.28)" />
              <stop offset="100%" stop-color="rgba(255,255,255,0)" />
            </radialGradient>
          </defs>
          <rect width="1600" height="420" fill="url(#bg)" />
          <circle cx="$xOffset" cy="$yOffset" r="220" fill="url(#soft)" />
          <circle cx="1330" cy="86" r="170" fill="rgba(255,255,255,0.08)" />
          <path d="M0 278C178 218 336 210 518 236C724 266 856 328 1088 300C1282 278 1438 204 1600 152V420H0Z" fill="rgba(255,255,255,0.08)" />
          <path d="M0 326C188 266 402 260 614 292C820 324 1006 384 1218 356C1368 336 1492 286 1600 236V420H0Z" fill="rgba(0,0,0,0.18)" />
        </svg>
    """.trimIndent()

    return "data:image/svg+xml;charset=utf-8,${encodeUriComponent(svg)}"
}

private val userAvatarBackgroundColors = listOf(
    "d9a441",
    "2f80ed",
    "27ae60",
    "eb5757",
    "9b51e0",
    "56ccf2",
    "f2994a",
    "219653",
    "bb6bd9",
    "f2c94c",
)

private fun hashSeed(seed: String): Int {
    var hash = -2128831035
    for (char in seed) {
        hash = hash xor char.code
        hash *= 16777619
    }
    return hash ushr 0
}

private fun normalizeLabel(value: String): String =
    value.trim().replace(Regex("\\s+"), " ")

private fun escapeXml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun encodeUriComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

fun encodeQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
