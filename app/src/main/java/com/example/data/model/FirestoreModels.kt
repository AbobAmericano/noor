package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SavedVerse(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = "QURAN",
    val reference: String = "",
    val arabicText: String = "",
    val translation: String = "",
    val sourceUrl: String = "",
    val tafsirUrl: String = "",
    val reflectionNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DhikrLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val salawatCount: Long = 0L,
    val tasbihCount: Long = 0L,
    val dailyTarget: Long = 100L,
    val completedPrayers: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DhikrProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lifetimeSalawat: Long = 0L,
    val currentStreakDays: Long = 0L,
    val lastActiveDate: String = "",
    val unlockedMilestones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
