package com.example.tenant_landlorddisputedocumenter.domain.model

/** One party's signature on one inspection phase, with the locked-in timestamp serving as legal proof. */
data class Signature(
    val id: String,
    val propertyId: String,
    val signerUid: String,
    val signerRole: UserRole,
    val phase: InspectionPhase,
    /** PNG bytes base64-encoded — kept locally and for PDF; cloud uses [remoteUrl]. */
    val pngBase64: String = "",
    val remoteUrl: String? = null,
    val signedAtMillis: Long,
)
