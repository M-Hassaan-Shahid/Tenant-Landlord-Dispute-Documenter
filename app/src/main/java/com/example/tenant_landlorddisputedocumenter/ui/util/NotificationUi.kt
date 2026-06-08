package com.example.tenant_landlorddisputedocumenter.ui.util

import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType

object NotificationUi {

    fun iconFor(type: NotificationType): Int = when (type) {
        NotificationType.SIGNATURE_REQUESTED,
        NotificationType.INSPECTION_SUBMITTED -> R.drawable.ic_edit
        NotificationType.TENANT_JOINED,
        NotificationType.TENANT_APPROVED,
        NotificationType.TENANT_REJECTED -> R.drawable.ic_person_add
        NotificationType.DISPUTE_RAISED,
        NotificationType.DISPUTE_RESOLUTION_PROPOSED,
        NotificationType.DISPUTE_RESOLVED -> R.drawable.ic_warning
        NotificationType.LEASE_ENDING -> R.drawable.ic_description
        NotificationType.GENERIC -> R.drawable.ic_notifications
    }

    fun bind(iconView: ImageView, unreadAccent: View, read: Boolean, type: NotificationType) {
        val ctx = iconView.context
        unreadAccent.visibility = if (read) View.GONE else View.VISIBLE
        iconView.setBackgroundResource(
            if (read) R.drawable.bg_notif_icon_muted else R.drawable.bg_notif_icon,
        )
        iconView.setImageResource(iconFor(type))
        iconView.setColorFilter(ContextCompat.getColor(ctx, R.color.notif_icon_tint))
    }
}
