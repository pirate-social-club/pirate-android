package sc.pirate.app.profile

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import sc.pirate.app.api.model.Profile

class PublicProfileModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun profile_decodesPublicAuthorProjection() {
        val profile = json.decodeFromString<Profile>(
            """{
                "user":"usr_usr_4c7fa8e05f6643a3bbc194b0ee4bd798",
                "display_name":"Technohippie 🪄",
                "global_handle":{
                    "id":"gh_ghd_0157c343b3a24c6ca1956e58cd469545",
                    "label":"swift-fox-7721.pirate",
                    "tier":"generated",
                    "status":"active"
                }
            }""".trimIndent(),
        )

        assertEquals("usr_usr_4c7fa8e05f6643a3bbc194b0ee4bd798", profile.userId)
        assertEquals("Technohippie 🪄", profile.displayName)
        assertEquals("swift-fox-7721.pirate", profile.displayHandle())
    }
}
