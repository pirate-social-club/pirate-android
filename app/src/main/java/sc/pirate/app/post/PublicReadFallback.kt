package sc.pirate.app.post

import kotlinx.coroutines.CancellationException
import sc.pirate.app.api.ApiError

internal data class PostReadResult<T>(
    val value: T,
    val usedAuthenticatedRead: Boolean,
)

internal suspend fun <T> readWithPublicNotFoundFallback(
    hasSession: Boolean,
    authenticatedRead: suspend () -> T,
    publicRead: suspend () -> T,
): PostReadResult<T> {
    if (!hasSession) {
        return PostReadResult(
            value = publicRead(),
            usedAuthenticatedRead = false,
        )
    }

    return try {
        PostReadResult(
            value = authenticatedRead(),
            usedAuthenticatedRead = true,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApiError) {
        if (error.status != 404) throw error
        PostReadResult(
            value = publicRead(),
            usedAuthenticatedRead = false,
        )
    }
}
