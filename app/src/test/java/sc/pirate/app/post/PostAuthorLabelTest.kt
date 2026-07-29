package sc.pirate.app.post

import org.junit.Assert.assertEquals
import org.junit.Test

class PostAuthorLabelTest {
    @Test
    fun unresolvedPublicProfile_doesNotExposeInternalUserId() {
        assertEquals(
            "Pirate user",
            resolveAuthorLabel(
                identityMode = "public",
                anonymousLabel = null,
                authorUserId = "usr_usr_123456789",
                authorProfile = null,
            ),
        )
    }

    @Test
    fun anonymousIdentity_keepsServerPresentationLabel() {
        assertEquals(
            "amber-sparrow-42",
            resolveAuthorLabel(
                identityMode = "anonymous",
                anonymousLabel = "amber-sparrow-42",
                authorUserId = "usr_usr_123456789",
                authorProfile = null,
            ),
        )
    }
}
