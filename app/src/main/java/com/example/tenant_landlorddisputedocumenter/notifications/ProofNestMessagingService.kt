package com.example.tenant_landlorddisputedocumenter.notifications

import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM pushes. Shows a system notification and refreshes the in-app notification center.
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
        val title = message.notification?.title ?: message.data["title"] ?: "ProofNest"
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val propertyId = message.data["propertyId"]?.takeIf { it.isNotBlank() }
        val notificationId = message.data["notificationId"]?.takeIf { it.isNotBlank() }

        SystemNotificationHelper.showFromPush(
            context = applicationContext,
            title = title,
            body = body,
            propertyId = propertyId,
            notificationId = notificationId,
        )

        val app = applicationContext as? ProofNestApplication ?: return
        val uid = app.container.firebaseAuth.currentUser?.uid ?: return
        scope.launch {
            app.container.notificationRepository.syncForUser(uid)
            propertyId?.let { id ->
                runCatching { app.container.syncCoordinator.refreshPropertyData(id, force = true) }
            }
        }
    }
}
