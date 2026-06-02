package com.example.tenant_landlorddisputedocumenter.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemNotificationBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.AppNotification
import com.example.tenant_landlorddisputedocumenter.ui.util.NotificationUi
import com.example.tenant_landlorddisputedocumenter.util.DateUtils
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val onNotificationClick: (AppNotification) -> Unit,
) : ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder>(NotifDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position), onNotificationClick)
    }

    class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: AppNotification, onClick: (AppNotification) -> Unit) {
            binding.textNotificationTitle.text = notification.title
            binding.textNotificationBody.text = notification.body
            binding.textNotificationTime.text = formatRelative(notification.createdAtMillis)

            NotificationUi.bind(
                binding.iconNotification,
                binding.unreadAccent,
                notification.read,
                notification.type,
            )

            val ctx = binding.root.context
            if (notification.read) {
                binding.textNotificationTitle.setTextColor(
                    ContextCompat.getColor(ctx, R.color.proofnest_on_surface_muted),
                )
                binding.root.background = null
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.notification_read_surface),
                )
            } else {
                binding.textNotificationTitle.setTextColor(
                    ContextCompat.getColor(ctx, R.color.proofnest_on_surface),
                )
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.proofnest_surface),
                )
                binding.root.background = ContextCompat.getDrawable(ctx, R.drawable.bg_card_unread)
            }

            binding.root.setOnClickListener { onClick(notification) }
        }

        private fun formatRelative(millis: Long): String {
            val diff = System.currentTimeMillis() - millis
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val m = (diff / TimeUnit.MINUTES.toMillis(1)).toInt()
                    "$m min ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val h = (diff / TimeUnit.HOURS.toMillis(1)).toInt()
                    if (h < 2) "1 hour ago" else "$h hours ago"
                }
                diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
                else -> DateUtils.formatReadable(millis)
            }
        }
    }
}

class NotifDiffCallback : DiffUtil.ItemCallback<AppNotification>() {
    override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem == newItem
}
