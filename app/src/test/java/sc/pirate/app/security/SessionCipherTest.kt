package sc.pirate.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionCipherTest {
    @Test
    fun `encrypted payload round trips binary fields`() {
        val payload = EncryptedSessionPayload(
            iv = byteArrayOf(0, 1, 2, 127, -1),
            ciphertext = byteArrayOf(9, 8, 7, 0, -128),
        )

        val decoded = EncryptedSessionPayload.decode(payload.encode())

        assertArrayEquals(payload.iv, decoded.iv)
        assertArrayEquals(payload.ciphertext, decoded.ciphertext)
    }

    @Test
    fun `encrypted payload rejects corruption and unknown versions`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedSessionPayload.decode("not-an-encrypted-session")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedSessionPayload.decode("v2:AA==:AA==")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedSessionPayload.decode("v1:not-base64:still-not-base64")
        }
    }
}
