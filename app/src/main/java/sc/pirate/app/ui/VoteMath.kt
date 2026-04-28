package sc.pirate.app.ui

fun adjustedVoteCount(
    current: Int,
    previousValue: Int?,
    nextValue: Int,
    targetValue: Int,
): Int =
    current + (if (nextValue == targetValue) 1 else 0) - (if (previousValue == targetValue) 1 else 0)
