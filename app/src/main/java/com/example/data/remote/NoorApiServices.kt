package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class QuranRandomResponse(
    val verse: QuranVerseDto? = null
)

@JsonClass(generateAdapter = true)
data class QuranVerseDto(
    val id: Int = 0,
    @Json(name = "verse_key") val verseKey: String = "",
    @Json(name = "text_uthmani") val textUthmani: String? = null,
    val translations: List<QuranTranslationDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class QuranTranslationDto(
    val id: Int = 0,
    val text: String = ""
)

@JsonClass(generateAdapter = true)
data class QuranChapterResponse(
    val chapter: QuranChapterDto? = null
)

@JsonClass(generateAdapter = true)
data class QuranChapterDto(
    val id: Int = 0,
    @Json(name = "name_simple") val nameSimple: String = "",
    @Json(name = "name_arabic") val nameArabic: String = "",
    @Json(name = "revelation_place") val revelationPlace: String = ""
)

interface QuranComApiService {
    @GET("verses/random")
    suspend fun getRandomVerse(
        @Query("language") language: String = "en",
        @Query("words") words: Boolean = false,
        @Query("translations") translations: String = "131",
        @Query("fields") fields: String = "text_uthmani,verse_key"
    ): QuranRandomResponse

    @GET("chapters/{chapterId}")
    suspend fun getChapter(
        @Path("chapterId") chapterId: Int,
        @Query("language") language: String = "en"
    ): QuranChapterResponse
}

@JsonClass(generateAdapter = true)
data class HadithEditionResponse(
    val hadiths: List<HadithItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HadithItemDto(
    val hadithnumber: Double = 0.0,
    val text: String = ""
)

interface HadithCdnApiService {
    @GET("editions/{edition}/{number}.json")
    suspend fun getHadithByNumber(
        @Path("edition") edition: String,
        @Path("number") number: Int
    ): HadithEditionResponse
}

@JsonClass(generateAdapter = true)
data class AlAdhanTimingsResponse(
    val code: Int = 0,
    val data: AlAdhanDataDto? = null
)

@JsonClass(generateAdapter = true)
data class AlAdhanDataDto(
    val timings: AlAdhanTimingsDto? = null,
    val date: AlAdhanDateDto? = null,
    val meta: AlAdhanMetaDto? = null
)

@JsonClass(generateAdapter = true)
data class AlAdhanTimingsDto(
    val Fajr: String = "",
    val Sunrise: String = "",
    val Dhuhr: String = "",
    val Asr: String = "",
    val Maghrib: String = "",
    val Isha: String = ""
)

@JsonClass(generateAdapter = true)
data class AlAdhanDateDto(
    val hijri: AlAdhanHijriDto? = null
)

@JsonClass(generateAdapter = true)
data class AlAdhanHijriDto(
    val day: String = "",
    val year: String = "",
    val month: AlAdhanHijriMonthDto? = null
)

@JsonClass(generateAdapter = true)
data class AlAdhanHijriMonthDto(
    val en: String = ""
)

@JsonClass(generateAdapter = true)
data class AlAdhanMetaDto(
    val timezone: String = ""
)

interface AlAdhanApiService {
    @GET("v1/timings/{timestamp}")
    suspend fun getTimings(
        @Path("timestamp") timestampSeconds: Long,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int
    ): AlAdhanTimingsResponse
}

@JsonClass(generateAdapter = true)
data class JakimSolatResponse(
    val status: String = "",
    val zone: String = "",
    val prayerTime: List<JakimPrayerDayDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JakimPrayerDayDto(
    val hijri: String = "",
    val date: String = "",
    val fajr: String = "",
    val syuruk: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = ""
)

interface JakimESolatApiService {
    @GET("index.php")
    suspend fun getTodayTakwim(
        @Query("r") route: String = "esolatApi/takwimsolat",
        @Query("period") period: String = "today",
        @Query("zone") zone: String
    ): JakimSolatResponse
}

object NoorNetworkClient {
    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    val quranComApi: QuranComApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.quran.com/api/v4/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(QuranComApiService::class.java)
    }

    val hadithCdnApi: HadithCdnApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(HadithCdnApiService::class.java)
    }

    val alAdhanApi: AlAdhanApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.aladhan.com/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AlAdhanApiService::class.java)
    }

    val jakimApi: JakimESolatApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.e-solat.gov.my/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(JakimESolatApiService::class.java)
    }
}
