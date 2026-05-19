package sc.pirate.app.api

import sc.pirate.app.api.model.AltchaChallenge
import sc.pirate.app.api.model.AltchaSolution
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AltchaSolver {
    fun solve(
        challenge: AltchaChallenge,
        maxCounter: Int = 1_000_000,
        isCancelled: () -> Boolean = { false },
    ): AltchaSolution? {
        val params = challenge.parameters
        require(params.algorithm == "PBKDF2/SHA-256") {
            "Unsupported proof-of-work algorithm: ${params.algorithm}"
        }
        val nonce = params.nonce.hexToBytes()
        val salt = params.salt.hexToBytes()
        val prefix = params.keyPrefix.lowercase()

        for (counter in 0..maxCounter) {
            if (counter % 1000 == 0 && isCancelled()) return null
            val counterBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(counter)
                .array()
            val derivedKey = pbkdf2HmacSha256(
                password = nonce + counterBytes,
                salt = salt,
                iterations = params.cost,
                keyLength = params.keyLength,
            )
            val hex = derivedKey.toHex()
            if (hex.startsWith(prefix)) {
                return AltchaSolution(counter = counter, derivedKey = hex)
            }
        }

        return null
    }

    private fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int,
    ): ByteArray {
        require(iterations > 0) { "PBKDF2 iterations must be positive." }
        require(keyLength > 0) { "PBKDF2 key length must be positive." }

        val hLen = 32
        val blocks = (keyLength + hLen - 1) / hLen
        val derived = ByteArray(blocks * hLen)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))

        for (block in 1..blocks) {
            val blockIndex = ByteBuffer.allocate(4).putInt(block).array()
            var u = mac.doFinal(salt + blockIndex)
            val f = u.copyOf()

            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (index in u.indices) {
                    f[index] = (f[index].toInt() xor u[index].toInt()).toByte()
                }
            }

            System.arraycopy(f, 0, derived, (block - 1) * hLen, hLen)
        }

        return derived.copyOf(keyLength)
    }
}

private val hexDigits = "0123456789abcdef".toCharArray()

private fun String.hexToBytes(): ByteArray {
    val normalized = trim()
    require(normalized.length % 2 == 0) { "Invalid hex value length." }
    return ByteArray(normalized.length / 2) { index ->
        val high = Character.digit(normalized[index * 2], 16)
        val low = Character.digit(normalized[index * 2 + 1], 16)
        require(high >= 0 && low >= 0) { "Invalid hex value." }
        ((high shl 4) + low).toByte()
    }
}

private fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        chars[index * 2] = hexDigits[value ushr 4]
        chars[index * 2 + 1] = hexDigits[value and 0x0f]
    }
    return String(chars)
}
