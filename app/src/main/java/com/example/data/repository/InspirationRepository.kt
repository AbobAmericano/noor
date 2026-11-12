package com.example.data.repository

import com.example.data.remote.NoorNetworkClient
import com.example.domain.model.DhikrMilestone
import com.example.domain.model.InspirationQuote
import com.example.domain.model.QuoteSourceType
import com.example.domain.model.RegionalPreset
import com.example.domain.model.ResearchResource
import com.example.domain.model.SalawatPhrase
import com.example.domain.solat.CalculationMethod
import com.example.domain.solat.SolatCalculator
import com.example.domain.solat.SolatSchedule
import com.example.domain.solat.SolatTime
import com.example.domain.solat.SolatTimeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

class InspirationRepository {

    suspend fun fetchLiveQuote(preferredType: QuoteSourceType? = null): InspirationQuote =
        withContext(Dispatchers.IO) {
            val pickQuran = when (preferredType) {
                QuoteSourceType.QURAN -> true
                QuoteSourceType.HADITH -> false
                null -> System.currentTimeMillis() % 2L == 0L
            }

            if (pickQuran) {
                fetchLiveQuranVerse() ?: curatedQuotes.filter { it.sourceType == QuoteSourceType.QURAN }.random()
            } else {
                fetchLiveHadith() ?: curatedQuotes.filter { it.sourceType == QuoteSourceType.HADITH }.random()
            }
        }

    fun getDailySeededQuote(dayOfYear: Int): InspirationQuote {
        return curatedQuotes[dayOfYear % curatedQuotes.size]
    }

    fun getCuratedByType(type: QuoteSourceType?): List<InspirationQuote> {
        return if (type == null) curatedQuotes else curatedQuotes.filter { it.sourceType == type }
    }

    private suspend fun fetchLiveQuranVerse(): InspirationQuote? = runCatching {
        val randomResp = NoorNetworkClient.quranComApi.getRandomVerse()
        val verse = randomResp.verse ?: return@runCatching null
        val key = verse.verseKey
        val parts = key.split(":")
        val surahNum = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
        val ayahNum = parts.getOrNull(1)?.toIntOrNull() ?: return@runCatching null
        val arabic = verse.textUthmani?.trim().orEmpty()
        val rawTranslation = verse.translations.firstOrNull()?.text.orEmpty()
        val cleanTranslation = rawTranslation
            .replace(Regex("<sup[^>]*>.*?</sup>"), "")
            .replace(Regex("<[^>]+>"), "")
            .trim()
        if (arabic.isBlank() || cleanTranslation.isBlank()) return@runCatching null

        val chapterName = runCatching {
            val ch = NoorNetworkClient.quranComApi.getChapter(surahNum).chapter
            if (ch != null) "Surah ${ch.nameSimple} (${ch.revelationPlace.replaceFirstChar { it.uppercase() }})" else "Surah $surahNum"
        }.getOrDefault("Surah $surahNum")

        InspirationQuote(
            id = "quran_${surahNum}_$ayahNum",
            sourceType = QuoteSourceType.QURAN,
            reference = "$chapterName $key",
            subtitle = "Quran.com • The Clear Quran",
            arabicText = arabic,
            translation = cleanTranslation,
            sourceUrl = "https://quran.com/$surahNum/$ayahNum",
            tafsirUrl = "https://quran.com/$key/tafsirs/en-tafisr-ibn-kathir",
            sourceDomain = "quran.com"
        )
    }.getOrNull()

    private suspend fun fetchLiveHadith(): InspirationQuote? = runCatching {
        val collections = listOf(
            Triple("bukhari", "Sahih al-Bukhari", 1..40),
            Triple("muslim", "Sahih Muslim", 1..40),
            Triple("nawawi", "40 Hadith Nawawi", 1..42)
        )
        val (slug, title, range) = collections.random()
        val num = range.random()
        val engResp = NoorNetworkClient.hadithCdnApi.getHadithByNumber("eng-$slug", num)
        val araResp = runCatching {
            NoorNetworkClient.hadithCdnApi.getHadithByNumber("ara-$slug", num)
        }.getOrNull()

        val engText = engResp.hadiths.firstOrNull()?.text?.trim().orEmpty()
        if (engText.isBlank() || engText.length > 900) return@runCatching null
        val araText = araResp?.hadiths?.firstOrNull()?.text?.trim().orEmpty()
        val sunnahBook = if (slug == "nawawi") "nawawi40" else slug

        InspirationQuote(
            id = "hadith_${slug}_$num",
            sourceType = QuoteSourceType.HADITH,
            reference = "$title #$num",
            subtitle = "Sunnah.com • Authentic Narration",
            arabicText = araText,
            translation = engText,
            sourceUrl = "https://sunnah.com/$sunnahBook:$num",
            tafsirUrl = "https://sunnah.com/$sunnahBook:$num",
            sourceDomain = "sunnah.com"
        )
    }.getOrNull()

    suspend fun fetchPrayerSchedule(
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        locationLabel: String,
        method: CalculationMethod,
        jakimZone: String? = null
    ): SolatSchedule = withContext(Dispatchers.IO) {
        val tz = TimeZone.getTimeZone(timeZoneId)
        val cal = Calendar.getInstance(tz)
        val fallback = SolatCalculator.calculateDaySchedule(
            calendar = cal,
            latitude = latitude,
            longitude = longitude,
            timeZone = tz,
            locationLabel = locationLabel,
            method = method
        )

        // 1. Prefer official JAKIM e-Solat API when JAKIM method & Malaysian zone apply
        val activeJakimZone = jakimZone ?: if (method == CalculationMethod.JAKIM) {
            SolatCalculator.inferDefaultJakimZone(latitude, longitude)
        } else null

        if (method == CalculationMethod.JAKIM && activeJakimZone != null) {
            val jakimSchedule = runCatching {
                val resp = NoorNetworkClient.jakimApi.getTodayTakwim(zone = activeJakimZone)
                val day = resp.prayerTime.firstOrNull() ?: return@runCatching null
                val fajr = SolatTime.parseHHmm(day.fajr) ?: return@runCatching null
                val syuruk = SolatTime.parseHHmm(day.syuruk) ?: return@runCatching null
                val dhuhr = SolatTime.parseHHmm(day.dhuhr) ?: return@runCatching null
                val asr = SolatTime.parseHHmm(day.asr) ?: return@runCatching null
                val maghrib = SolatTime.parseHHmm(day.maghrib) ?: return@runCatching null
                val isha = SolatTime.parseHHmm(day.isha) ?: return@runCatching null
                fallback.copy(
                    hijriLabel = day.hijri.ifBlank { fallback.hijriLabel },
                    entries = listOf(
                        SolatTimeEntry("Fajr", "الفجر", fajr, true),
                        SolatTimeEntry("Syuruk", "الشروق", syuruk, false),
                        SolatTimeEntry("Dhuhr", "الظهر", dhuhr, true),
                        SolatTimeEntry("Asr", "العصر", asr, true),
                        SolatTimeEntry("Maghrib", "المغرب", maghrib, true),
                        SolatTimeEntry("Isha", "العشاء", isha, true)
                    ),
                    sourceLabel = "JAKIM e-Solat Official ($activeJakimZone)"
                )
            }.getOrNull()
            if (jakimSchedule != null) return@withContext jakimSchedule
        }

        // 2. Otherwise try AlAdhan API for global geolocation accuracy
        val alAdhanSchedule = runCatching {
            val epochSec = System.currentTimeMillis() / 1000L
            val resp = NoorNetworkClient.alAdhanApi.getTimings(
                timestampSeconds = epochSec,
                latitude = latitude,
                longitude = longitude,
                method = method.aladhanMethodId
            )
            val data = resp.data ?: return@runCatching null
            val t = data.timings ?: return@runCatching null
            val fajr = SolatTime.parseHHmm(t.Fajr) ?: return@runCatching null
            val sunrise = SolatTime.parseHHmm(t.Sunrise) ?: return@runCatching null
            val dhuhr = SolatTime.parseHHmm(t.Dhuhr) ?: return@runCatching null
            val asr = SolatTime.parseHHmm(t.Asr) ?: return@runCatching null
            val maghrib = SolatTime.parseHHmm(t.Maghrib) ?: return@runCatching null
            val isha = SolatTime.parseHHmm(t.Isha) ?: return@runCatching null
            val hijri = data.date?.hijri?.let { h ->
                val mName = h.month?.en.orEmpty()
                if (h.day.isNotBlank() && mName.isNotBlank() && h.year.isNotBlank()) {
                    "${h.day} $mName ${h.year} AH"
                } else fallback.hijriLabel
            } ?: fallback.hijriLabel
            val resolvedTz = data.meta?.timezone?.takeIf { it.isNotBlank() } ?: timeZoneId
            fallback.copy(
                hijriLabel = hijri,
                timeZoneId = resolvedTz,
                entries = listOf(
                    SolatTimeEntry("Fajr", "الفجر", fajr, true),
                    SolatTimeEntry("Syuruk", "الشروق", sunrise, false),
                    SolatTimeEntry("Dhuhr", "الظهر", dhuhr, true),
                    SolatTimeEntry("Asr", "العصر", asr, true),
                    SolatTimeEntry("Maghrib", "المغرب", maghrib, true),
                    SolatTimeEntry("Isha", "العشاء", isha, true)
                ),
                sourceLabel = "AlAdhan Global API (${method.shortLabel})"
            )
        }.getOrNull()

        alAdhanSchedule ?: fallback
    }

    companion object {
        val salawatPhrases = listOf(
            SalawatPhrase(
                id = "salawat_short",
                title = "Salawat Nabawiyyah",
                arabic = "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ",
                transliteration = "Allahumma salli 'ala Muhammad wa 'ala ali Muhammad",
                translation = "O Allah, send peace and blessings upon Muhammad and upon the family of Muhammad."
            ),
            SalawatPhrase(
                id = "salawat_salam",
                title = "Salawat & Salam",
                arabic = "صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ",
                transliteration = "Sallallahu 'alayhi wa sallam",
                translation = "May the peace and blessings of Allah be upon him."
            ),
            SalawatPhrase(
                id = "tasbih_subhanallah",
                title = "Tasbih",
                arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                transliteration = "SubhanAllahi wa bihamdihi",
                translation = "Glory be to Allah and praise be to Him."
            ),
            SalawatPhrase(
                id = "istighfar",
                title = "Istighfar",
                arabic = "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ",
                transliteration = "Astaghfirullaha al-'Azim",
                translation = "I seek forgiveness from Allah, the Almighty."
            )
        )

        val dhikrMilestones = listOf(
            DhikrMilestone("first_33", "Tasbih Dawn", 33L, "Complete 33 daily Salawat"),
            DhikrMilestone("hundred_club", "Centurion Heart", 100L, "Reach 100 lifetime Salawat"),
            DhikrMilestone("three_hundred", "Steadfast Remembrance", 300L, "Reach 300 lifetime Salawat"),
            DhikrMilestone("thousand_light", "Light Upon Light", 1000L, "Reach 1,000 lifetime Salawat"),
            DhikrMilestone("five_thousand", "Devoted Companion", 5000L, "Reach 5,000 lifetime Salawat")
        )

        val regionalPresets = listOf(
            RegionalPreset("Kuala Lumpur, Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur", CalculationMethod.JAKIM.name, "WLY01"),
            RegionalPreset("Singapore", 1.3521, 103.8198, "Asia/Singapore", CalculationMethod.SINGAPORE.name),
            RegionalPreset("Makkah, Saudi Arabia", 21.4225, 39.8262, "Asia/Riyadh", CalculationMethod.MAKKAH.name),
            RegionalPreset("Jakarta, Indonesia", -6.2088, 106.8456, "Asia/Jakarta", CalculationMethod.MWL.name),
            RegionalPreset("London, United Kingdom", 51.5072, -0.1276, "Europe/London", CalculationMethod.MWL.name),
            RegionalPreset("New York, United States", 40.7128, -74.0060, "America/New_York", CalculationMethod.ISNA.name),
            RegionalPreset("Cairo, Egypt", 30.0444, 31.2357, "Africa/Cairo", CalculationMethod.EGYPT.name)
        )

        val researchResources = listOf(
            ResearchResource(
                id = "quran_tafsir",
                title = "Quran.com & Tafsir Ibn Kathir",
                category = "Tafsir & Exegesis",
                description = "Read word-by-word Quranic Arabic, multiple scholarly translations, and classical Tafsir commentaries.",
                url = "https://quran.com/tafsirs",
                domainBadge = "quran.com"
            ),
            ResearchResource(
                id = "altafsir",
                title = "Altafsir.com Classical Compendium",
                category = "Tafsir & Exegesis",
                description = "Royal Aal al-Bayt Institute archive of classical Sunni and linguistic tafsir works.",
                url = "https://www.altafsir.com/",
                domainBadge = "altafsir.com"
            ),
            ResearchResource(
                id = "sunnah_com",
                title = "Sunnah.com Hadith Library",
                category = "Hadith & Sciences",
                description = "Search Sahih al-Bukhari, Sahih Muslim, Riyad as-Salihin, and authenticated grading chains.",
                url = "https://sunnah.com/",
                domainBadge = "sunnah.com"
            ),
            ResearchResource(
                id = "jakim_portal",
                title = "JAKIM Malaysia Official Portal",
                category = "JAKIM Malaysia",
                description = "Department of Islamic Development Malaysia: fatwa references, Islamic publications, and guidance.",
                url = "https://www.islam.gov.my/",
                domainBadge = "islam.gov.my"
            ),
            ResearchResource(
                id = "jakim_esolat",
                title = "JAKIM e-Solat & Taqwim",
                category = "JAKIM Malaysia",
                description = "Official Malaysian prayer time zones, Qibla azimuths, and Hijri calendar calculations.",
                url = "https://www.e-solat.gov.my/",
                domainBadge = "e-solat.gov.my"
            ),
            ResearchResource(
                id = "jakim_sumber",
                title = "Sumber Maklumat Al-Quran & Hadis (JAKIM)",
                category = "JAKIM Malaysia",
                description = "Verified JAKIM Hadith authentication and e-Musykil scholarly Q&A repository.",
                url = "https://emusykil.islam.gov.my/",
                domainBadge = "emusykil.islam.gov.my"
            ),
            ResearchResource(
                id = "yaqeen",
                title = "Yaqeen Institute Academic Papers",
                category = "Academic Research",
                description = "Peer-reviewed research papers on Prophetic Seerah, spirituality, theology, and modern ethics.",
                url = "https://yaqeeninstitute.org/read/papers",
                domainBadge = "yaqeeninstitute.org"
            ),
            ResearchResource(
                id = "jqs_edinburgh",
                title = "Journal of Qur'anic Studies",
                category = "Academic Research",
                description = "Academic scholarship covering Qur'anic hermeneutics, manuscript history, and exegesis.",
                url = "https://www.euppublishing.com/journal/jqs",
                domainBadge = "euppublishing.com"
            ),
            ResearchResource(
                id = "iium_journal",
                title = "IIUM Journal of Islam in Asia",
                category = "Academic Research",
                description = "Open-access peer-reviewed Islamic civilization and Southeast Asian scholarship from IIUM.",
                url = "https://journals.iium.edu.my/jislam/",
                domainBadge = "journals.iium.edu.my"
            )
        )

        val curatedQuotes = listOf(
            InspirationQuote(
                id = "quran_94_5",
                sourceType = QuoteSourceType.QURAN,
                reference = "Surah Ash-Sharh 94:5–6",
                subtitle = "Quran.com • The Relief",
                arabicText = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا ۝ إِنَّ مَعَ الْعُسْرِ يُسْرًا",
                translation = "So, surely with hardship comes ease. Surely with that hardship comes more ease.",
                sourceUrl = "https://quran.com/94/5",
                tafsirUrl = "https://quran.com/94:5/tafsirs/en-tafisr-ibn-kathir",
                sourceDomain = "quran.com"
            ),
            InspirationQuote(
                id = "hadith_bukhari_1",
                sourceType = QuoteSourceType.HADITH,
                reference = "Sahih al-Bukhari 1",
                subtitle = "Sunnah.com • Book of Revelation",
                arabicText = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى",
                translation = "The reward of deeds depends upon the intentions and every person will get the reward according to what he has intended.",
                sourceUrl = "https://sunnah.com/bukhari:1",
                tafsirUrl = "https://sunnah.com/bukhari:1",
                sourceDomain = "sunnah.com"
            ),
            InspirationQuote(
                id = "quran_2_152",
                sourceType = QuoteSourceType.QURAN,
                reference = "Surah Al-Baqarah 2:152",
                subtitle = "Quran.com • The Cow",
                arabicText = "فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ",
                translation = "Remember Me; I will remember you. And thank Me, and never be ungrateful.",
                sourceUrl = "https://quran.com/2/152",
                tafsirUrl = "https://quran.com/2:152/tafsirs/en-tafisr-ibn-kathir",
                sourceDomain = "quran.com"
            ),
            InspirationQuote(
                id = "hadith_muslim_908",
                sourceType = QuoteSourceType.HADITH,
                reference = "Sahih Muslim 384 (Salawat)",
                subtitle = "Sunnah.com • Book of Prayer",
                arabicText = "مَنْ صَلَّى عَلَيَّ وَاحِدَةً صَلَّى اللَّهُ عَلَيْهِ عَشْرًا",
                translation = "He who sends blessings on me once, Allah sends blessings on him ten times.",
                sourceUrl = "https://sunnah.com/muslim:408",
                tafsirUrl = "https://sunnah.com/muslim:408",
                sourceDomain = "sunnah.com"
            ),
            InspirationQuote(
                id = "quran_13_28",
                sourceType = QuoteSourceType.QURAN,
                reference = "Surah Ar-Ra'd 13:28",
                subtitle = "Quran.com • The Thunder",
                arabicText = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
                translation = "Surely in the remembrance of Allah do hearts find comfort.",
                sourceUrl = "https://quran.com/13/28",
                tafsirUrl = "https://quran.com/13:28/tafsirs/en-tafisr-ibn-kathir",
                sourceDomain = "quran.com"
            ),
            InspirationQuote(
                id = "hadith_bukhari_6464",
                sourceType = QuoteSourceType.HADITH,
                reference = "Sahih al-Bukhari 6464",
                subtitle = "Sunnah.com • Book of Heart-Melting Traditions",
                arabicText = "أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ",
                translation = "The most beloved deeds to Allah are those that are most consistent, even if they are small.",
                sourceUrl = "https://sunnah.com/bukhari:6464",
                tafsirUrl = "https://sunnah.com/bukhari:6464",
                sourceDomain = "sunnah.com"
            ),
            InspirationQuote(
                id = "quran_2_286",
                sourceType = QuoteSourceType.QURAN,
                reference = "Surah Al-Baqarah 2:286",
                subtitle = "Quran.com • The Cow",
                arabicText = "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
                translation = "Allah does not require of any soul more than what it can afford.",
                sourceUrl = "https://quran.com/2/286",
                tafsirUrl = "https://quran.com/2:286/tafsirs/en-tafisr-ibn-kathir",
                sourceDomain = "quran.com"
            ),
            InspirationQuote(
                id = "hadith_nawawi_19",
                sourceType = QuoteSourceType.HADITH,
                reference = "40 Hadith Nawawi 19",
                subtitle = "Sunnah.com • Guarding Boundaries",
                arabicText = "احْفَظِ اللَّهَ يَحْفَظْكَ، احْفَظِ اللَّهَ تَجِدْهُ تُجَاهَكَ",
                translation = "Be mindful of Allah and He will protect you. Be mindful of Allah and you will find Him before you.",
                sourceUrl = "https://sunnah.com/nawawi40:19",
                tafsirUrl = "https://sunnah.com/nawawi40:19",
                sourceDomain = "sunnah.com"
            ),
            InspirationQuote(
                id = "quran_33_56",
                sourceType = QuoteSourceType.QURAN,
                reference = "Surah Al-Ahzab 33:56",
                subtitle = "Quran.com • Salawat Command",
                arabicText = "إِنَّ اللَّهَ وَمَلَائِكَتَهُ يُصَلُّونَ عَلَى النَّبِيِّ ۚ يَا أَيُّهَا الَّذِينَ آمَنُوا صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا",
                translation = "Indeed, Allah showers His blessings upon the Prophet, and His angels pray for him. O believers! Invoke Allah's blessings upon him, and salute him with worthy greetings of peace.",
                sourceUrl = "https://quran.com/33/56",
                tafsirUrl = "https://quran.com/33:56/tafsirs/en-tafisr-ibn-kathir",
                sourceDomain = "quran.com"
            ),
            InspirationQuote(
                id = "hadith_riyad_290",
                sourceType = QuoteSourceType.HADITH,
                reference = "Riyad as-Salihin 245",
                subtitle = "Sunnah.com • Kindness & Mercy",
                arabicText = "الرَّاحِمُونَ يَرْحَمُهُمُ الرَّحْمَنُ، ارْحَمُوا مَنْ فِي الأَرْضِ يَرْحَمْكُمْ مَنْ فِي السَّمَاءِ",
                translation = "The merciful are shown mercy by the All-Merciful. Show mercy to those on earth, and the One above the heavens will show mercy to you.",
                sourceUrl = "https://sunnah.com/tirmidhi:1924",
                tafsirUrl = "https://sunnah.com/tirmidhi:1924",
                sourceDomain = "sunnah.com"
            )
        )
    }
}
