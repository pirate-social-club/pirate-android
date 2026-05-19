package sc.pirate.app.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.Post

class AgeGateTest {
    @Test
    fun eighteenPlusPolicyRequiresProofUntilViewerIsAllowed() {
        assertTrue(
            LocalizedPostResponse(
                post = Post(ageGatePolicy = "18_plus"),
                ageGateViewerState = "proof_required",
            ).requiresAgeProof(),
        )

        assertFalse(
            LocalizedPostResponse(
                post = Post(ageGatePolicy = "18_plus"),
                ageGateViewerState = "verified_allowed",
            ).requiresAgeProof(),
        )
    }

    @Test
    fun nonAgeGatedPolicyDoesNotRequireProof() {
        assertFalse(
            LocalizedPostResponse(
                post = Post(ageGatePolicy = null),
                ageGateViewerState = "proof_required",
            ).requiresAgeProof(),
        )
    }
}
