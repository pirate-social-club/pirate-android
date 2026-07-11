package sc.pirate.app.security

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bouncycastle.jce.provider.BouncyCastleProvider

class AgentActionProofTest {
    private val provider = BouncyCastleProvider()

    @Test
    fun `canonicalization matches web ordering and encoding`() {
        val canonical = AgentActionProofSigner.canonicalizeRequest(
            method = "post",
            url = "https://pirate.test/communities/cmt_test/posts?z=1&%C3%A4=2",
            body = """{"title":"Test","nested":{"ä":2,"z":1},"body":"Hello","post_type":"text"}""",
        )
        assertEquals(
            listOf(
                "pirate-agent-action-proof-v2",
                "POST",
                "https://pirate.test",
                "/communities/cmt_test/posts",
                "z=1&%C3%A4=2",
                """{"body":"Hello","nested":{"z":1,"ä":2},"post_type":"text","title":"Test"}""",
            ).joinToString("\n"),
            canonical,
        )
    }

    @Test
    fun `creates verifiable Ed25519 action proof`() {
        val keys = KeyPairGenerator.getInstance("Ed25519", provider).generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(keys.private.encoded)}\n-----END PRIVATE KEY-----"
        val proof = AgentActionProofSigner.sign(
            method = "POST",
            url = "https://pirate.test/communities/cmt_test/posts",
            body = """{"title":"Ship log"}""",
            privateKeyPem = pem,
            signedAt = 1_700_000_000,
            nonce = "nonce-test",
        )
        val verifier = Signature.getInstance("Ed25519", provider).apply {
            initVerify(keys.public)
            update(
                AgentActionProofSigner.signaturePayload(
                    proof.nonce,
                    proof.signedAt,
                    proof.canonicalRequestHash,
                ).toByteArray(),
            )
        }
        assertTrue(verifier.verify(Base64.getDecoder().decode(proof.signature)))
    }
}
