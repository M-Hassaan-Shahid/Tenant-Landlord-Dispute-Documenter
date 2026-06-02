package com.example.tenant_landlorddisputedocumenter.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.tenant_landlorddisputedocumenter.MainActivity
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM pushes. We do two things:
 *  1. Show a system notification (so the user sees it whether or not the app is open).
 *  2. Persist it via [NotificationRepository] so it shows up in the in-app notification center.
 */
class ProofNestMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = applicationContext as? ProofNestApplication ?: return
        val uid = app.container.firebaseAuth.currentUser?.uid ?: return
        scope.launch { app.container.authRepository.updateFcmToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = applicationContext as? ProofNestApplication ?: return
        val uid = app.container.firebaseAuth.currentUser?.uid ?: return

        val title = message.notification?.title ?: message.data["title"] ?: "ProofNest"
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val propertyId = message.data["propertyId"]

        showSystemNotification(title, body, propertyId)
        scope.launch {
            app.container.notificationRepository.syncForUser(uid)
        }
    }

    private fun showSystemNotification(title: String, body: String, propertyId: String?) {
        val channelId = getString(R.string.default_notification_channel_id)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            propertyId?.let { putExtra(MainActivity.EXTRA_PROPERTY_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
