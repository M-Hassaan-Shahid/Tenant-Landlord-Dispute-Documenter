package com.example.tenant_landlorddisputedocumenter.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tenant_landlorddisputedocumenter.MainActivity
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.AppNotification

/** Posts Android system-tray notifications with deduplication. */
object SystemNotificationHelper {

    private const val PREFS = "proofnest_notification_delivery"
    private const val KEY_SHOWN_IDS = "shown_ids"
    private const val MAX_TRACKED_IDS = 200

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showIfNew(context: Context, notification: AppNotification) {
        if (!canPost(context)) return
        if (wasShown(context, notification.id)) return
        show(
            context = context,
            notificationId = notification.id.hashCode(),
            title = notification.title,
            body = notification.body,
            propertyId = notification.propertyId,
        )
        markShown(context, notification.id)
    }

    fun showFromPush(
        context: Context,
        title: String,
        body: String,
        propertyId: String?,
        notificationId: String?,
    ) {
        if (!canPost(context)) return
        if (notificationId != null && wasShown(context, notificationId)) return
        show(
            context = context,
            notificationId = (notificationId ?: title + body).hashCode(),
            title = title,
            body = body,
            propertyId = propertyId,
        )
        notificationId?.let { markShown(context, it) }
    }

    private fun show(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        propertyId: String?,
    ) {
        val channelId = context.getString(R.string.default_notification_channel_id)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            propertyId?.let { putExtra(MainActivity.EXTRA_PROPERTY_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, built)
    }

    private fun wasShown(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.contains(id) == true
    }

    private fun markShown(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf())
            .apply { add(id) }
        if (updated.size > MAX_TRACKED_IDS) {
            updated.remove(updated.first())
        }
        prefs.edit().putStringSet(KEY_SHOWN_IDS, updated).apply()
    }
}
