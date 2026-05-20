package sc.pirate.app.createcommunity

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.CreateCommunityRequest

class CreateCommunityStateTest {
    @Test
    fun upsertGateDraftForMatchMode_removesPowWhenExclusiveGateIsAddedInAllMode() {
        val drafts = upsertGateDraftForMatchMode(
            drafts = defaultGatedGateDrafts,
            nextDraft = UniqueHumanGateDraft(),
            gateMatchMode = CommunityGateMatchMode.All,
        )

        assertEquals(listOf(UniqueHumanGateDraft()), drafts)
    }

    @Test
    fun upsertGateDraftForMatchMode_keepsPowFallbackInAnyMode() {
        val drafts = upsertGateDraftForMatchMode(
            drafts = listOf(UniqueHumanGateDraft()),
            nextDraft = AltchaPowGateDraft,
            gateMatchMode = CommunityGateMatchMode.Any,
        )

        assertEquals(listOf(UniqueHumanGateDraft(), AltchaPowGateDraft), drafts)
    }

    @Test
    fun normalizeGateDraftsForMatchMode_removesPowExclusiveConflictOnlyForAllMode() {
        val drafts = listOf(AltchaPowGateDraft, WalletScoreGateDraft(minimumScore = 20))

        assertEquals(
            listOf(WalletScoreGateDraft(minimumScore = 20)),
            normalizeGateDraftsForMatchMode(drafts, CommunityGateMatchMode.All),
        )
        assertEquals(
            drafts,
            normalizeGateDraftsForMatchMode(drafts, CommunityGateMatchMode.Any),
        )
    }

    @Test
    fun effectiveDefaultAgeGatePolicy_onlyRequiresCreatorAgeForExplicitOrAdultMinimumAge() {
        assertEquals(
            CommunityDefaultAgeGatePolicy.None,
            effectiveDefaultAgeGatePolicy(
                membershipMode = CommunityMembershipMode.Gated,
                defaultAgeGatePolicy = CommunityDefaultAgeGatePolicy.None,
                gateDrafts = listOf(AltchaPowGateDraft),
            ),
        )
        assertEquals(
            CommunityDefaultAgeGatePolicy.EighteenPlus,
            effectiveDefaultAgeGatePolicy(
                membershipMode = CommunityMembershipMode.Request,
                defaultAgeGatePolicy = CommunityDefaultAgeGatePolicy.EighteenPlus,
                gateDrafts = emptyList(),
            ),
        )
        assertEquals(
            CommunityDefaultAgeGatePolicy.EighteenPlus,
            effectiveDefaultAgeGatePolicy(
                membershipMode = CommunityMembershipMode.Gated,
                defaultAgeGatePolicy = CommunityDefaultAgeGatePolicy.None,
                gateDrafts = listOf(MinimumAgeGateDraft(minimumAge = 21)),
            ),
        )
    }

    @Test
    fun canAdvanceCreateCommunityStep_doesNotBlockAccessForAgeVerification() {
        assertTrue(
            canAdvanceCreateCommunityStep(
                step = CreateCommunityStep.Access,
                displayName = "Adults",
                membershipMode = CommunityMembershipMode.Gated,
                gateDrafts = listOf(MinimumAgeGateDraft(minimumAge = 21)),
            ),
        )
    }

    @Test
    fun serializeIdentityGateDrafts_buildsOrPolicyForPowFallback() {
        val policy = serializeIdentityGateDrafts(
            gateDrafts = listOf(AltchaPowGateDraft, UniqueHumanGateDraft()),
            gateMatchMode = CommunityGateMatchMode.Any,
        )

        assertNotNull(policy)
        assertEquals("or", policy?.expression?.op)
        assertEquals("altcha_pow", policy?.expression?.children?.get(0)?.gate?.type)
        assertEquals("unique_human", policy?.expression?.children?.get(1)?.gate?.type)
        assertEquals("very", policy?.expression?.children?.get(1)?.gate?.provider)
    }

    @Test
    fun serializeIdentityGateDrafts_mapsSelfAndWalletGates() {
        val policy = serializeIdentityGateDrafts(
            gateDrafts = listOf(
                NationalityGateDraft(requiredValues = listOf("USA", "CAN")),
                GenderGateDraft(requiredValue = "F"),
                WalletScoreGateDraft(minimumScore = 30),
            ),
            gateMatchMode = CommunityGateMatchMode.All,
        )

        val children = policy?.expression?.children.orEmpty()
        assertEquals("and", policy?.expression?.op)
        assertEquals(listOf("USA", "CAN"), children[0].gate?.allowed)
        assertEquals(listOf("F"), children[1].gate?.allowed)
        assertEquals(30, children[2].gate?.minimumScore)
    }

    @Test
    fun serializeIdentityGateDrafts_returnsNullForRequestMembershipGatePayload() {
        assertNull(serializeIdentityGateDrafts(emptyList(), CommunityGateMatchMode.All))
    }

    @Test
    fun createCommunityRequest_serializesWebParityFields() {
        val json = Json { encodeDefaults = false }
        val request = CreateCommunityRequest(
            avatarRef = "community-media/avatar/a.png",
            bannerRef = "community-media/banner/b.png",
            displayName = "Vinyl Club",
            databaseRegion = "aws-us-west-2",
            description = "Good posts only.",
            membershipMode = "gated",
            defaultAgeGatePolicy = "18_plus",
            allowAnonymousIdentity = false,
            anonymousIdentityScope = "community_stable",
            gatePolicy = serializeIdentityGateDrafts(
                gateDrafts = listOf(MinimumAgeGateDraft(minimumAge = 21)),
                gateMatchMode = CommunityGateMatchMode.All,
            ),
        )

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"avatar_ref\""))
        assertTrue(encoded.contains("\"banner_ref\""))
        assertTrue(encoded.contains("\"database_region\""))
        assertTrue(encoded.contains("\"anonymous_identity_scope\""))
        assertTrue(encoded.contains("\"gate_policy\""))
        assertFalse(encoded.contains("\"community_bootstrap\""))
    }
}
