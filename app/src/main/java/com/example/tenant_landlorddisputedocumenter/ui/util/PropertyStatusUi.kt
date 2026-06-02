package com.example.tenant_landlorddisputedocumenter.ui.util

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.google.android.material.chip.Chip

object PropertyStatusUi {

    fun apply(chip: Chip, status: PropertyStatus) {
        val ctx = chip.context
        val (bgRes, textRes) = when (status) {
            PropertyStatus.ACTIVE, PropertyStatus.MOVE_OUT ->
                R.color.chip_active_bg to R.color.status_active
            PropertyStatus.OCCUPIED, PropertyStatus.CLOSED ->
                R.color.chip_occupied_bg to R.color.status_occupied
            PropertyStatus.PENDING, PropertyStatus.PENDING_APPROVAL ->
                R.color.chip_pending_bg to R.color.status_pending
            PropertyStatus.REJECTED ->
                R.color.chip_pending_bg to R.color.status_rejected
            else ->
                R.color.proofnest_surface_variant to R.color.proofnest_on_surface_muted
        }
        chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, bgRes))
        chip.setTextColor(ContextCompat.getColor(ctx, textRes))
        chip.text = status.label
        chip.isClickable = false
    }
}
