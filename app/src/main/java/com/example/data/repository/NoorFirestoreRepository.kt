package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DhikrLog
import com.example.data.model.DhikrProfile
import com.example.data.model.SavedVerse
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NoorFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before writing to Firestore.")
    }

    private fun sanitizeId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100)
        return cleaned.ifBlank { UUID.randomUUID().toString() }
    }

    fun observeSavedVerses(userId: String): Flow<List<SavedVerse>> {
        val path = "users/$userId/saved_verses"
        return db.collection("users")
            .document(userId)
            .collection("saved_verses")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SavedVerse::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                }
                throw error
            }
    }

    suspend fun saveVerse(
        docIdHint: String,
        sourceType: String,
        reference: String,
        arabicText: String,
        translation: String,
        sourceUrl: String,
        tafsirUrl: String,
        reflectionNote: String = ""
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docId = sanitizeId(docIdHint)
        val docRef = db.collection("users").document(uid)
            .collection("saved_verses").document(docId)
        val payload = mapOf(
            "userId" to uid,
            "sourceType" to sourceType,
            "reference" to reference.take(200),
            "arabicText" to arabicText.take(4000),
            "translation" to translation.take(4000),
            "sourceUrl" to sourceUrl.take(500),
            "tafsirUrl" to tafsirUrl.take(500),
            "reflectionNote" to reflectionNote.take(2000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateReflectionNote(verseId: String, note: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid)
            .collection("saved_verses").document(sanitizeId(verseId))
        return try {
            docRef.update(
                mapOf(
                    "reflectionNote" to note.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedVerse(verseId: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid)
            .collection("saved_verses").document(sanitizeId(verseId))
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeDhikrLogs(userId: String): Flow<List<DhikrLog>> {
        val path = "users/$userId/dhikr_logs"
        return db.collection("users")
            .document(userId)
            .collection("dhikr_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DhikrLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.dateKey }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                }
                throw error
            }
    }

    suspend fun saveDhikrLog(
        dateKey: String,
        salawatCount: Long,
        tasbihCount: Long,
        dailyTarget: Long,
        completedPrayers: List<String>
    ): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docId = sanitizeId(dateKey)
        val docRef = db.collection("users").document(uid)
            .collection("dhikr_logs").document(docId)
        val boundedPrayers = completedPrayers.distinct().take(6)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "salawatCount" to salawatCount.coerceIn(0L, 1_000_000L),
                        "tasbihCount" to tasbihCount.coerceIn(0L, 1_000_000L),
                        "dailyTarget" to dailyTarget.coerceIn(1L, 100_000L),
                        "completedPrayers" to boundedPrayers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey.take(20),
                        "salawatCount" to salawatCount.coerceIn(0L, 1_000_000L),
                        "tasbihCount" to tasbihCount.coerceIn(0L, 1_000_000L),
                        "dailyTarget" to dailyTarget.coerceIn(1L, 100_000L),
                        "completedPrayers" to boundedPrayers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeDhikrProfile(userId: String): Flow<DhikrProfile?> {
        val path = "users/$userId/dhikr_profile/summary"
        return db.collection("users")
            .document(userId)
            .collection("dhikr_profile")
            .document("summary")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        DhikrProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path, auth)
                }
                throw error
            }
    }

    suspend fun saveDhikrProfile(
        lifetimeSalawat: Long,
        currentStreakDays: Long,
        lastActiveDate: String,
        unlockedMilestones: List<String>
    ): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid)
            .collection("dhikr_profile").document("summary")
        val boundedMilestones = unlockedMilestones.distinct().take(20)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "lifetimeSalawat" to lifetimeSalawat.coerceIn(0L, 100_000_000L),
                        "currentStreakDays" to currentStreakDays.coerceIn(0L, 100_000L),
                        "lastActiveDate" to lastActiveDate.take(20),
                        "unlockedMilestones" to boundedMilestones,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "lifetimeSalawat" to lifetimeSalawat.coerceIn(0L, 100_000_000L),
                        "currentStreakDays" to currentStreakDays.coerceIn(0L, 100_000L),
                        "lastActiveDate" to lastActiveDate.take(20),
                        "unlockedMilestones" to boundedMilestones,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
