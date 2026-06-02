package com.example.tenant_landlorddisputedocumenter.data.local

import androidx.room.TypeConverter
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole

/**
 * Room type converters. We deliberately use simple string serialization for lists so the
 * generated SQL stays human-readable when debugging.
 */
class Converters {
    @TypeConverter fun roleToString(role: UserRole?): String? = role?.name
    @TypeConverter fun stringToRole(value: String?): UserRole? = value?.let { UserRole.from(it) }

    @TypeConverter fun statusToString(status: PropertyStatus?): String? = status?.name
    @TypeConverter fun stringToStatus(value: String?): PropertyStatus? = value?.let { PropertyStatus.from(it) }

    @TypeConverter fun phaseToString(phase: InspectionPhase?): String? = phase?.name
    @TypeConverter fun stringToPhase(value: String?): InspectionPhase? = value?.let { InspectionPhase.from(it) }

    @TypeConverter fun ratingToString(rating: ConditionRating?): String? = rating?.name
    @TypeConverter fun stringToRating(value: String?): ConditionRating? = value?.let { ConditionRating.from(it) }

    @TypeConverter fun disputeToString(status: DisputeStatus?): String? = status?.name
    @TypeConverter fun stringToDispute(value: String?): DisputeStatus? = value?.let { DisputeStatus.from(it) }

    @TypeConverter fun notifTypeToString(type: NotificationType?): String? = type?.name
    @TypeConverter fun stringToNotifType(value: String?): NotificationType? = value?.let { NotificationType.from(it) }

    @TypeConverter
    fun listToString(list: List<String>?): String? = list?.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun stringToList(value: String?): List<String> = when {
        value.isNullOrEmpty() -> emptyList()
        else -> value.split(LIST_SEPARATOR)
    }

    companion object {
        // U+001F INFORMATION SEPARATOR ONE — never appears in user-entered text.
        const val LIST_SEPARATOR = ""
    }
}
