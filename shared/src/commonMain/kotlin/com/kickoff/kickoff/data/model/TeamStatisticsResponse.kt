package com.kickoff.kickoff.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `/teams/statistics` yaniti. Fixture endpoint'lerinin aksine `response` alani
 * basarida tek bir OBJE, hata durumunda ise bos dizi `[]` olarak doner.
 * Bu tutarsizlik dogrudan DTO'ya baglanamayacagi icin JsonElement olarak karsilanir;
 * obje kontrolu ve DTO'ya cevirme [FootballApiClient] icinde yapilir.
 */
@Serializable
data class TeamStatisticsResponse(
    @SerialName("get") val endpoint: String? = null,
    // API-Football hata alanini bazen bos dizi `[]`, bazen obje `{}` olarak doner.
    val errors: JsonElement? = null,
    val response: JsonElement? = null,
)

/** Bir takimin belirli lig + sezondaki genel istatistik ozeti. */
@Serializable
data class TeamStatisticsDto(
    val form: String? = null,
    val fixtures: StatsFixturesDto? = null,
    val goals: StatsGoalsDto? = null,
    @SerialName("clean_sheet") val cleanSheet: StatsCountDto? = null,
    @SerialName("failed_to_score") val failedToScore: StatsCountDto? = null,
    val lineups: List<StatsLineupDto> = emptyList(),
)

@Serializable
data class StatsCountDto(
    val home: Int? = null,
    val away: Int? = null,
    val total: Int? = null,
)

@Serializable
data class StatsFixturesDto(
    val played: StatsCountDto? = null,
    val wins: StatsCountDto? = null,
    val draws: StatsCountDto? = null,
    val loses: StatsCountDto? = null,
)

@Serializable
data class StatsGoalsDto(
    @SerialName("for") val scored: StatsGoalDetailDto? = null,
    val against: StatsGoalDetailDto? = null,
)

@Serializable
data class StatsGoalDetailDto(
    val total: StatsCountDto? = null,
    val average: StatsAverageDto? = null,
    // Anahtarlar dakika araliklaridir: "0-15", "16-30", "31-45"...
    val minute: Map<String, StatsMinuteDto> = emptyMap(),
)

/** API ortalamalari sayi degil string dondurur: "1.5". */
@Serializable
data class StatsAverageDto(
    val home: String? = null,
    val away: String? = null,
    val total: String? = null,
)

@Serializable
data class StatsMinuteDto(
    val total: Int? = null,
    val percentage: String? = null,
)

@Serializable
data class StatsLineupDto(
    val formation: String? = null,
    val played: Int? = null,
)
