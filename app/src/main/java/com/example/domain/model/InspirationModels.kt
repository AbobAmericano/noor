package com.example.domain.model

enum class QuoteSourceType(val raw: String, val displayLabel: String) {
    QURAN("QURAN", "Al-Quran"),
    HADITH("HADITH", "Hadith")
}

data class InspirationQuote(
    val id: String,
    val sourceType: QuoteSourceType,
    val reference: String,
    val subtitle: String,
    val arabicText: String,
    val translation: String,
    val sourceUrl: String,
    val tafsirUrl: String,
    val sourceDomain: String
)

data class ResearchResource(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val url: String,
    val domainBadge: String
)

data class SalawatPhrase(
    val id: String,
    val title: String,
    val arabic: String,
    val transliteration: String,
    val translation: String
)

data class DhikrMilestone(
    val id: String,
    val title: String,
    val targetCount: Long,
    val description: String
)

data class RegionalPreset(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val methodName: String,
    val jakimZone: String? = null
)
