package com.example.tenant_landlorddisputedocumenter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.tenant_landlorddisputedocumenter.BuildConfig
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

/**
 * Application root. Holds a singleton [ServiceContainer] for repositories and sync.
 */
class ProofNestApplication : Application() {

    lateinit var container: ServiceContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        if (BuildConfig.DEBUG) {
            // Lets sign-up/sign-in work on emulators before SHA + reCAPTCHA are fully configured.
            FirebaseAuth.getInstance().firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        }
        container = ServiceContainer(this)
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            getString(R.string.default_notification_channel_id),
            getString(R.string.default_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Inspection events and lease reminders"
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
