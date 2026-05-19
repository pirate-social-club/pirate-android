package sc.pirate.app.shared

import sc.pirate.app.api.model.LocalizedPostResponse

// The post owns the static age policy; the response owns the viewer-specific proof state.
fun LocalizedPostResponse.requiresAgeProof(): Boolean =
    post.ageGatePolicy == "18_plus" && ageGateViewerState != "verified_allowed"
