package sc.pirate.app.moderation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.pirate.app.api.model.MembershipRequestListResponse
import sc.pirate.app.api.model.MembershipRequestSummary

class MembershipRequestsModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun membershipRequestList_decodesLiveContract() {
        val response = json.decodeFromString<MembershipRequestListResponse>(
            """{
                "items":[{
                    "id":"mrq_123",
                    "object":"membership_request_summary",
                    "community":"com_456",
                    "applicant_user":"usr_789",
                    "applicant_handle":"captain.pirate",
                    "applicant_avatar_ref":"media/avatar.png",
                    "status":"pending",
                    "note":"I make sea shanties.",
                    "created":1783872000
                }],
                "next_cursor":"cursor_2"
            }""".trimIndent(),
        )

        val request = response.items.single()
        assertEquals("mrq_123", request.id)
        assertEquals("captain.pirate", request.applicantHandle)
        assertEquals("I make sea shanties.", request.note)
        assertEquals("cursor_2", response.nextCursor)
    }

    @Test
    fun membershipRequestList_allowsNullableProfileFieldsAndCursor() {
        val response = json.decodeFromString<MembershipRequestListResponse>(
            """{
                "items":[{
                    "id":"mrq_123",
                    "community":"com_456",
                    "applicant_user":"usr_789",
                    "status":"pending",
                    "created":1783872000
                }],
                "next_cursor":null
            }""".trimIndent(),
        )

        assertNull(response.items.single().applicantHandle)
        assertNull(response.items.single().applicantAvatarRef)
        assertNull(response.nextCursor)
    }

    @Test
    fun reviewedRequest_isRemovedWithoutDisturbingQueueOrder() {
        val requests = listOf(request("mrq_1"), request("mrq_2"), request("mrq_3"))

        val remaining = removeReviewedMembershipRequest(requests, "mrq_2")

        assertEquals(listOf("mrq_1", "mrq_3"), remaining.map { it.id })
    }

    @Test
    fun applicantPresentation_handlesFallbackAndStableRelativeAge() {
        assertEquals("Member", membershipApplicantLabel(request("mrq_1")))
        assertEquals("2h", formatMembershipRequestAge(created = 1_000, nowSeconds = 8_200))
    }

    private fun request(id: String) = MembershipRequestSummary(
        id = id,
        community = "com_456",
        applicantUser = "usr_$id",
        status = "pending",
        created = 1_000,
    )
}
