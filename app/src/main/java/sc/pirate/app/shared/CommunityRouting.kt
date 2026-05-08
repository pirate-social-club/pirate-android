package sc.pirate.app.shared

private const val PUNYCODE_BASE = 36
private const val PUNYCODE_T_MIN = 1
private const val PUNYCODE_T_MAX = 26
private const val PUNYCODE_INITIAL_BIAS = 72
private const val PUNYCODE_INITIAL_N = 128

fun formatCommunityRouteLabel(
    communityId: String,
    routeSlug: String? = null,
): String {
    val routeSegment = formatCommunityRouteSegment(routeSlug?.takeIf { it.isNotBlank() } ?: communityId)
    return if (routeSegment.lowercase().startsWith("c/")) routeSegment else "c/$routeSegment"
}

private fun formatCommunityRouteSegment(value: String): String {
    val trimmedInput = value.trim()
    val trimmed = if (trimmedInput.lowercase().startsWith("c/")) trimmedInput.drop(2) else trimmedInput
    if (trimmed.isBlank()) return "community"

    return if (trimmed.startsWith("@")) {
        "@${decodePunycodeLabel(trimmed.drop(1))}"
    } else {
        decodePunycodeLabel(trimmed)
    }
}

private fun decodePunycodeLabel(value: String): String {
    if (!value.lowercase().startsWith("xn--")) return value
    return try {
        decodePunycode(value.drop(4))
    } catch (_: Exception) {
        value
    }
}

private fun decodePunycode(input: String): String {
    val output = mutableListOf<Int>()
    var n = PUNYCODE_INITIAL_N
    var i = 0
    var bias = PUNYCODE_INITIAL_BIAS
    val basicEnd = input.lastIndexOf('-')

    if (basicEnd > -1) {
        for (index in 0 until basicEnd) {
            val codePoint = input[index].code
            require(codePoint < 0x80)
            output += codePoint
        }
    }

    var index = if (basicEnd > -1) basicEnd + 1 else 0
    while (index < input.length) {
        val oldI = i
        var w = 1
        var k = PUNYCODE_BASE

        while (true) {
            require(index < input.length)
            val digit = decodePunycodeDigit(input[index].code)
            index += 1
            require(digit < PUNYCODE_BASE)
            i += digit * w
            val t = when {
                k <= bias -> PUNYCODE_T_MIN
                k >= bias + PUNYCODE_T_MAX -> PUNYCODE_T_MAX
                else -> k - bias
            }
            if (digit < t) break
            w *= PUNYCODE_BASE - t
            k += PUNYCODE_BASE
        }

        val outputLength = output.size + 1
        bias = adaptPunycodeBias(i - oldI, outputLength, oldI == 0)
        n += i / outputLength
        i %= outputLength
        output.add(i, n)
        i += 1
    }

    return buildString {
        output.forEach { appendCodePoint(it) }
    }
}

private fun decodePunycodeDigit(codePoint: Int): Int =
    when (codePoint) {
        in '0'.code..'9'.code -> codePoint - '0'.code + 26
        in 'A'.code..'Z'.code -> codePoint - 'A'.code
        in 'a'.code..'z'.code -> codePoint - 'a'.code
        else -> PUNYCODE_BASE
    }

private fun adaptPunycodeBias(deltaInput: Int, numPoints: Int, firstTime: Boolean): Int {
    var delta = if (firstTime) deltaInput / 700 else deltaInput / 2
    delta += delta / numPoints
    var k = 0
    while (delta > ((PUNYCODE_BASE - PUNYCODE_T_MIN) * PUNYCODE_T_MAX) / 2) {
        delta /= PUNYCODE_BASE - PUNYCODE_T_MIN
        k += PUNYCODE_BASE
    }
    return k + (((PUNYCODE_BASE - PUNYCODE_T_MIN + 1) * delta) / (delta + 38))
}
