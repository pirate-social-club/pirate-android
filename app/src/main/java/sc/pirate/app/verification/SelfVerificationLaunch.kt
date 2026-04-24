package sc.pirate.app.verification

import android.net.Uri
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import sc.pirate.app.api.model.SelfVerificationLaunch
import sc.pirate.app.api.model.VerificationSession

fun VerificationSession.buildSelfLaunchUri(callbackUri: Uri? = null): Uri? {
    val launch = launch?.selfApp ?: return null
    return launch.buildLaunchUri(callbackUri)
}

fun SelfVerificationLaunch.buildLaunchUri(callbackUri: Uri? = null): Uri? {
    val endpointValue = endpoint.trim()
    val sessionIdValue = sessionId.trim()
    val scopeValue = scope.trim()
    val userIdValue = userId.trim()

    if (endpointValue.isBlank() || sessionIdValue.isBlank() || scopeValue.isBlank() || userIdValue.isBlank()) {
        return null
    }

    val disclosuresPayload = buildJsonObject {
        if (disclosures.issuingState == true) put("issuing_state", JsonPrimitive(true))
        if (disclosures.name == true) put("name", JsonPrimitive(true))
        if (disclosures.passportNumber == true) put("passport_number", JsonPrimitive(true))
        if (disclosures.nationality == true) put("nationality", JsonPrimitive(true))
        if (disclosures.dateOfBirth == true) put("date_of_birth", JsonPrimitive(true))
        if (disclosures.gender == true) put("gender", JsonPrimitive(true))
        if (disclosures.expiryDate == true) put("expiry_date", JsonPrimitive(true))
        if (disclosures.ofac == true) put("ofac", JsonPrimitive(true))
        disclosures.excludedCountries?.takeIf { it.isNotEmpty() }?.let { countries ->
            put(
                "excludedCountries",
                buildJsonArray {
                    countries.forEach { country -> add(JsonPrimitive(country)) }
                },
            )
        }
        disclosures.minimumAge?.let { minimumAge ->
            put("minimumAge", JsonPrimitive(minimumAge))
        }
    }

    val selfAppPayload = buildJsonObject {
        put("appName", JsonPrimitive(appName))
        put("chainID", JsonPrimitive(chainId ?: defaultChainId(endpointType)))
        put("deeplinkCallback", JsonPrimitive(callbackUri?.toString() ?: deeplinkCallback.orEmpty()))
        put("devMode", JsonPrimitive(devMode ?: false))
        put("endpoint", JsonPrimitive(endpointValue))
        put("endpointType", JsonPrimitive(endpointType))
        put("header", JsonPrimitive(header.orEmpty()))
        put("logoBase64", JsonPrimitive(logoBase64.orEmpty()))
        put("disclosures", disclosuresPayload)
        put("scope", JsonPrimitive(scopeValue))
        put("sessionId", JsonPrimitive(sessionIdValue))
        put("userDefinedData", JsonPrimitive(userDefinedData.orEmpty()))
        put("userId", JsonPrimitive(userIdValue))
        put("userIdType", JsonPrimitive(userIdType))
        put("version", JsonPrimitive(version ?: 2))
    }

    return Uri.parse(SELF_REDIRECT_URL)
        .buildUpon()
        .appendQueryParameter("selfApp", selfAppPayload.toString())
        .build()
}

private fun defaultChainId(endpointType: String): Int =
    if (endpointType == "staging_celo" || endpointType == "staging_https") 11142220 else 42220

private const val SELF_REDIRECT_URL = "https://redirect.self.xyz"
