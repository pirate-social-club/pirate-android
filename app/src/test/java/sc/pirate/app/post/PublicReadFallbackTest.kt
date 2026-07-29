package sc.pirate.app.post

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.ApiError

class PublicReadFallbackTest {
    @Test
    fun signedOutUsesPublicReadOnly() = runBlocking {
        var authenticatedCalls = 0
        var publicCalls = 0

        val result = readWithPublicNotFoundFallback(
            hasSession = false,
            authenticatedRead = {
                authenticatedCalls += 1
                "authenticated"
            },
            publicRead = {
                publicCalls += 1
                "public"
            },
        )

        assertEquals("public", result.value)
        assertFalse(result.usedAuthenticatedRead)
        assertEquals(0, authenticatedCalls)
        assertEquals(1, publicCalls)
    }

    @Test
    fun signedInKeepsSuccessfulAuthenticatedRead() = runBlocking {
        var publicCalls = 0

        val result = readWithPublicNotFoundFallback(
            hasSession = true,
            authenticatedRead = { "authenticated" },
            publicRead = {
                publicCalls += 1
                "public"
            },
        )

        assertEquals("authenticated", result.value)
        assertTrue(result.usedAuthenticatedRead)
        assertEquals(0, publicCalls)
    }

    @Test
    fun signedInFallsBackToPublicReadOnNotFound() = runBlocking {
        var publicCalls = 0

        val result = readWithPublicNotFoundFallback(
            hasSession = true,
            authenticatedRead = {
                throw ApiError(code = "not_found", message = "Post not found", status = 404)
            },
            publicRead = {
                publicCalls += 1
                "public"
            },
        )

        assertEquals("public", result.value)
        assertFalse(result.usedAuthenticatedRead)
        assertEquals(1, publicCalls)
    }

    @Test
    fun signedInDoesNotMaskOtherApiErrors() = runBlocking {
        val expected = ApiError(code = "forbidden", message = "Forbidden", status = 403)
        var publicCalls = 0

        val actual = runCatching {
            readWithPublicNotFoundFallback(
                hasSession = true,
                authenticatedRead = { throw expected },
                publicRead = {
                    publicCalls += 1
                    "public"
                },
            )
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertEquals(0, publicCalls)
    }
}
