package sc.pirate.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class EncryptedSessionPayload(val iv: ByteArray, val ciphertext: ByteArray) {
    fun encode(): String = listOf(
        VERSION,
        Base64.getEncoder().encodeToString(iv),
        Base64.getEncoder().encodeToString(ciphertext),
    ).joinToString(":")

    companion object {
        private const val VERSION = "v1"

        fun decode(value: String): EncryptedSessionPayload {
            val parts = value.split(":", limit = 3)
            require(parts.size == 3 && parts[0] == VERSION) { "Unsupported encrypted session format" }
            val iv = Base64.getDecoder().decode(parts[1])
            val ciphertext = Base64.getDecoder().decode(parts[2])
            require(iv.isNotEmpty() && ciphertext.isNotEmpty()) { "Encrypted session payload is empty" }
            return EncryptedSessionPayload(iv, ciphertext)
        }
    }
}

internal interface SessionCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(payload: String): String
}

internal class AndroidKeystoreSessionCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SessionCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedSessionPayload(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
        ).encode()
    }

    override fun decrypt(payload: String): String {
        val decoded = EncryptedSessionPayload.decode(payload)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, decoded.iv),
        )
        return String(cipher.doFinal(decoded.ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "pirate_session_v1"
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
