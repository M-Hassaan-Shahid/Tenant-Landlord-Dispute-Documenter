package com.example.tenant_landlorddisputedocumenter.di

import android.content.Context
import com.example.tenant_landlorddisputedocumenter.data.SyncCache
import com.example.tenant_landlorddisputedocumenter.data.SyncCoordinator
import com.example.tenant_landlorddisputedocumenter.data.local.ProofNestDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.DisputeRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.InspectionRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.NotificationRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.ReportRepository
import com.example.tenant_landlorddisputedocumenter.data.remote.CloudinaryUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Lightweight service locator. Lives on the [com.example.tenant_landlorddisputedocumenter.ProofNestApplication]
 * and is consumed by `viewModel(factoryProducer)` calls inside each screen.
 *
 * We deliberately avoid Hilt to keep the build simple for a course project — every dependency is
 * still constructed in exactly one place.
 */
class ServiceContainer(context: Context) {
    private val appContext = context.applicationContext

    /** IO scope for repository background work (auth role refresh, etc.). */
    val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val syncCache: SyncCache by lazy { SyncCache() }
    val syncCoordinator: SyncCoordinator by lazy { SyncCoordinator(this) }

    val db: ProofNestDatabase by lazy { ProofNestDatabase.get(appContext) }
    val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val cloudinary: CloudinaryUploader by lazy { CloudinaryUploader() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(firebaseAuth, firestore, db.userDao(), repositoryScope)
    }
    val inspectionRepository: InspectionRepository by lazy {
        InspectionRepository(
            appContext,
            db.propertyDao(),
            db.roomDao(),
            db.itemDao(),
            db.photoDao(),
            db.signatureDao(),
            cloudinary,
            notificationRepository,
            firestore,
        )
    }
    val disputeRepository: DisputeRepository by lazy {
        DisputeRepository(db.disputeDao(), firestore, syncCache)
    }
    val propertyRepository: PropertyRepository by lazy {
        PropertyRepository(
            firestore,
            db.propertyDao(),
            notificationRepository,
            inspectionRepository,
            db.disputeDao(),
            syncCache,
        )
    }
    val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(
            appContext,
            db.notificationDao(),
            firestore,
            currentUserId = { firebaseAuth.currentUser?.uid },
        )
    }
    val reportRepository: ReportRepository by lazy {
        ReportRepository(
            appContext,
            db.propertyDao(),
            db.roomDao(),
            db.itemDao(),
            db.photoDao(),
            db.signatureDao(),
            db.disputeDao(),
            db.userDao(),
        )
    }
}
