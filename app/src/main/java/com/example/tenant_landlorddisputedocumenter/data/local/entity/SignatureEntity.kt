package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Signature
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole

@Entity(
    tableName = "signatures",
    indices = [Index(value = ["propertyId"]), Index(value = ["signerUid"])],
)
data class SignatureEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val signerUid: String,
    val signerRole: UserRole,
    val phase: InspectionPhase,
    val pngBase64: String,
    val remoteUrl: String?,
    val signedAtMillis: Long,
) {
    fun toDomain(): Signature = Signature(
        id = id,
        propertyId = propertyId,
        signerUid = signerUid,
        signerRole = signerRole,
        phase = phase,
        pngBase64 = pngBase64,
        remoteUrl = remoteUrl,
        signedAtMillis = signedAtMillis,
    )

    companion object {
        fun from(s: Signature): SignatureEntity = SignatureEntity(
            id = s.id,
            propertyId = s.propertyId,
            signerUid = s.signerUid,
            signerRole = s.signerRole,
            phase = s.phase,
            pngBase64 = s.pngBase64,
            remoteUrl = s.remoteUrl,
            signedAtMillis = s.signedAtMillis,
        )
    }
}
