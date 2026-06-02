package com.example.tenant_landlorddisputedocumenter.domain.model

/** Role assigned to a user at signup. Drives permissions across every screen. */
enum class UserRole {
    LANDLORD,
    TENANT;

    companion object {
        fun from(raw: String?): UserRole = when (raw?.uppercase()) {
            LANDLORD.name -> LANDLORD
            TENANT.name -> TENANT
            else -> TENANT
        }
    }
}
