package com.example.tenant_landlorddisputedocumenter.domain.model

/**
 * A timestamped + geo-stamped photo. Every photo always knows who took it, when, and where —
 * those three things are the tamper-evidence guarantees the app makes.
 */
data class Photo(
    val id: String,
    val itemId: String,
    val propertyId: String,
    val phase: InspectionPhase,
    val capturedByUid: String,
    val capturedAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    /** Local content URI as a string, when the photo is cached on-device. */
    val localUri: String? = null,
    /** Public Firebase Storage download URL, once uploaded. */
    val remoteUrl: String? = null,
    /** True once the file has been successfully uploaded to Firebase Storage. */
    val uploaded: Boolean = false,
)
