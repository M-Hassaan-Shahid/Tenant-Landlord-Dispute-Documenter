package com.example.tenant_landlorddisputedocumenter.data.repository

import com.example.tenant_landlorddisputedocumenter.data.local.dao.UserDao
import com.example.tenant_landlorddisputedocumenter.data.local.entity.UserEntity
import com.example.tenant_landlorddisputedocumenter.data.remote.FirestorePaths
import com.example.tenant_landlorddisputedocumenter.data.remote.firestoreWrite
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.User
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.util.FirebaseAuthErrors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/**
 * Handles signup, login, profile fetch, and session tracking.
 *
 * Firebase Auth owns the credentials; Firestore owns the profile metadata; Room caches the profile
 * for offline cold-starts so the dashboard isn't blank on launch.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val scope: CoroutineScope,
) {
    private val _currentUser = MutableStateFlow(auth.currentUser?.uid)
    val currentUserId: StateFlow<String?> = _currentUser.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserRole: StateFlow<UserRole?> = currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else userDao.observe(uid).map { it?.role }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.uid }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentProfile(): Flow<User?> = currentUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else userDao.observe(uid).map { it?.toDomain() }
    }

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        phone: String,
        cnic: String,
        role: UserRole,
    ): Outcome<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = result.user ?: error("Firebase returned no uid")
        val uid = firebaseUser.uid
        val user = User(
            uid = uid,
            email = email.trim(),
            displayName = displayName.trim(),
            phone = phone.trim(),
            cnic = cnic.trim(),
            role = role,
        )
        try {
            writeProfile(user)
        } catch (e: Exception) {
            runCatching { firebaseUser.delete().await() }
            throw e
        }
        user
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure(it, FirebaseAuthErrors.userMessage(it)) },
    )

    suspend fun signIn(email: String, password: String): Outcome<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: error("Firebase returned no uid")
        refreshProfile(uid) ?: error("Profile missing for $uid")
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure(it, FirebaseAuthErrors.userMessage(it)) },
    )

    suspend fun sendPasswordReset(email: String): Outcome<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure(it, FirebaseAuthErrors.userMessage(it)) },
    )

    fun signOut() = auth.signOut()

    /** Pull the latest profile from Firestore into Room and return it. */
    suspend fun refreshProfile(uid: String): User? {
        val snap = runCatching {
            firestore.collection(FirestorePaths.USERS).document(uid).get().await()
        }.getOrNull() ?: return null
        if (!snap.exists()) return null
        val user = User(
            uid = uid,
            email = snap.getString("email").orEmpty(),
            displayName = snap.getString("displayName").orEmpty(),
            phone = snap.getString("phone").orEmpty(),
            cnic = snap.getString("cnic").orEmpty(),
            role = UserRole.from(snap.getString("role")),
            createdAtMillis = snap.getLong("createdAtMillis") ?: System.currentTimeMillis(),
            fcmToken = snap.getString("fcmToken"),
            photoUrl = snap.getString("photoUrl"),
        )
        userDao.upsert(UserEntity.from(user))
        return user
    }

    /** Persist a new/updated profile to both Firestore and Room. */
    suspend fun writeProfile(user: User) {
        userDao.upsert(UserEntity.from(user))
        firestoreWrite("user profile") {
            firestore.collection(FirestorePaths.USERS).document(user.uid).set(
                mapOf(
                    "email" to user.email,
                    "displayName" to user.displayName,
                    "phone" to user.phone,
                    "cnic" to user.cnic,
                    "role" to user.role.name,
                    "createdAtMillis" to user.createdAtMillis,
                    "fcmToken" to user.fcmToken,
                    "photoUrl" to user.photoUrl,
                ),
            ).await()
        }
    }

    suspend fun updatePhotoUrl(uid: String, photoUrl: String): Outcome<Unit> = runCatching {
        val entity = userDao.get(uid)
            ?: refreshProfile(uid)?.let { userDao.get(uid) }
            ?: error("Profile not found.")
        val updated = entity.toDomain().copy(photoUrl = photoUrl)
        userDao.upsert(UserEntity.from(updated))
        firestoreWrite("profile photo") {
            firestore.collection(FirestorePaths.USERS).document(uid)
                .update("photoUrl", photoUrl).await()
        }
        Unit
    }.fold(
        onSuccess = { Outcome.Success(it) },
        onFailure = { Outcome.Failure(it, it.localizedMessage ?: "Could not save photo.") },
    )

    suspend fun updateFcmToken(uid: String, token: String) {
        runCatching {
            firestore.collection(FirestorePaths.USERS).document(uid)
                .update("fcmToken", token).await()
        }
        userDao.get(uid)?.let { userDao.upsert(it.copy(fcmToken = token)) }
    }
}
