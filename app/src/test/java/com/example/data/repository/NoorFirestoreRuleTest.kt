package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NoorFirestoreRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun savedVerse_authenticatedOwner_canCreateObserveUpdateAndDelete() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NoorFirestoreRepository(firestore, auth)
        val docId = "quran_94_5_${UUID.randomUUID().toString().take(8)}"

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVerse(
                docIdHint = docId,
                sourceType = "QURAN",
                reference = "Surah Ash-Sharh 94:5",
                arabicText = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا",
                translation = "So, surely with hardship comes ease.",
                sourceUrl = "https://quran.com/94/5",
                tafsirUrl = "https://quran.com/94:5/tafsirs/en-tafisr-ibn-kathir",
                reflectionNote = "Patience brings relief."
            )
        }
        assertTrue(saveResult.isSuccess)

        val verses = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSavedVerses(uid).first { list -> list.any { it.id == docId } }
        }
        assertTrue(verses.any { it.id == docId && it.reflectionNote == "Patience brings relief." })

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateReflectionNote(docId, "Updated reflection note")
        }
        assertTrue(updateResult.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteSavedVerse(docId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun savedVerse_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NoorFirestoreRepository(firestore, auth)
        val docId = "hadith_1_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveVerse(
                docIdHint = docId,
                sourceType = "HADITH",
                reference = "Sahih al-Bukhari 1",
                arabicText = "إنما الأعمال بالنيات",
                translation = "Actions are judged by intentions.",
                sourceUrl = "https://sunnah.com/bukhari:1",
                tafsirUrl = "https://sunnah.com/bukhari:1",
                reflectionNote = "Sincerity first."
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NoorFirestoreRepository(firestore, auth)
        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeSavedVerses(aliceUid).first()
            }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun observeSavedVerses_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = NoorFirestoreRepository(firestore, auth)

        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSavedVerses("alice_unauth").first()
            }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun dhikrLogAndProfile_authenticatedOwner_createAndUpdateSucceed() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NoorFirestoreRepository(firestore, auth)
        val dateKey = "2026-11-12"

        val logCreate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDhikrLog(
                dateKey = dateKey,
                salawatCount = 33L,
                tasbihCount = 33L,
                dailyTarget = 100L,
                completedPrayers = listOf("Fajr", "Dhuhr")
            )
        }
        assertTrue(logCreate.isSuccess)

        val logUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDhikrLog(
                dateKey = dateKey,
                salawatCount = 100L,
                tasbihCount = 99L,
                dailyTarget = 100L,
                completedPrayers = listOf("Fajr", "Dhuhr", "Asr")
            )
        }
        assertTrue(logUpdate.isSuccess)

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDhikrLogs(uid).first { it.any { log -> log.dateKey == dateKey } }
        }
        val today = logs.first { it.dateKey == dateKey }
        assertEquals(100L, today.salawatCount)
        assertEquals(3, today.completedPrayers.size)

        val profileSave = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDhikrProfile(
                lifetimeSalawat = 100L,
                currentStreakDays = 1L,
                lastActiveDate = dateKey,
                unlockedMilestones = listOf("first_33", "hundred_club")
            )
        }
        assertTrue(profileSave.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_noor@test.com"
        const val BOB_EMAIL = "bob_noor@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
