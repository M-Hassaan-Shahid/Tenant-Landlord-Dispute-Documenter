package com.example.tenant_landlorddisputedocumenter.domain.model

/** A signed-in ProofNest user. Mirrors the Firestore `users/{uid}` document. */
data class User(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val phone: String = "",
    val cnic: String = "",
    val role: UserRole = UserRole.TENANT,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val fcmToken: String? = null,
)
