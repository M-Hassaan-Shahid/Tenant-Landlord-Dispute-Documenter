package com.example.tenant_landlorddisputedocumenter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.tenant_landlorddisputedocumenter.domain.model.User
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val phone: String,
    val cnic: String,
    val role: UserRole,
    val createdAtMillis: Long,
    val fcmToken: String?,
    val photoUrl: String? = null,
) {
    fun toDomain(): User = User(
        uid, email, displayName, phone, cnic, role, createdAtMillis, fcmToken, photoUrl,
    )

    companion object {
        fun from(user: User): UserEntity = UserEntity(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            phone = user.phone,
            cnic = user.cnic,
            role = user.role,
            createdAtMillis = user.createdAtMillis,
            fcmToken = user.fcmToken,
            photoUrl = user.photoUrl,
        )
    }
}
