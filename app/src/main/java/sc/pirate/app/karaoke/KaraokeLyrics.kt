package sc.pirate.app.karaoke

import sc.pirate.app.api.model.SongKaraokeLine

fun activeKaraokeLineIndex(lines: List<SongKaraokeLine>, positionMs: Long): Int? {
    if (lines.isEmpty() || positionMs < 0) return null
    val direct = lines.indexOfFirst { line ->
        val start = line.startMs
        val end = line.endMs
        start != null && end != null && positionMs in start..end
    }
    if (direct >= 0) return direct

    val next = lines.indexOfFirst { line ->
        val start = line.startMs
        start != null && start > positionMs
    }
    return when {
        next > 0 -> next - 1
        next == 0 -> 0
        else -> lines.lastIndex
    }
}
