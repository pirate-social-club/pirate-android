package sc.pirate.app.booking

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.BookingSlotsResponse
import sc.pirate.app.api.model.Profile

class BookingAvailabilityModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun bookingSlots_decodeLiveCamelCaseSlotContract() {
        val response = json.decodeFromString<BookingSlotsResponse>(
            """{
                "host_timezone":"America/New_York",
                "viewer_timezone":"Asia/Tbilisi",
                "slots":[{
                    "startUtc":"2026-07-14T14:00:00.000Z",
                    "endUtc":"2026-07-14T14:30:00.000Z",
                    "priceCents":2500,
                    "available":true
                }]
            }""".trimIndent(),
        )

        assertEquals("America/New_York", response.hostTimezone)
        assertEquals("Asia/Tbilisi", response.viewerTimezone)
        assertEquals(2500, response.slots.single().priceCents)
        assertTrue(response.slots.single().available)
    }

    @Test
    fun profileBookableFlag_defaultsFalseAndDecodesTrue() {
        val base = """{"user_id":"usr_host"}"""

        assertFalse(json.decodeFromString<Profile>(base).isBookable)
        assertTrue(json.decodeFromString<Profile>("""{"user_id":"usr_host","is_bookable":true}""").isBookable)
    }
}
