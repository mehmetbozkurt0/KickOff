package com.kickoff.kickoff.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * API-Football'un standart yanit zarfi.
 * `/fixtures` ve `/fixtures/headtohead` endpoint'leri ayni yapiyi doner.
 */
@Serializable
data class FixtureResponse(
    @SerialName("get") val endpoint: String? = null,
    // API-Football hata alanini bazen bos dizi `[]`, bazen obje `{}` olarak doner;
    // bu yuzden tipsiz JsonElement olarak karsilanir.
    val errors: JsonElement? = null,
    val results: Int = 0,
    val paging: PagingDto? = null,
    val response: List<FixtureItemDto> = emptyList(),
)

@Serializable
data class PagingDto(
    val current: Int = 1,
    val total: Int = 1,
)

/** Yanit dizisindeki tek bir mac kaydi. */
@Serializable
data class FixtureItemDto(
    val fixture: FixtureDto,
    val league: LeagueDto,
    val teams: TeamsDto,
    val goals: GoalsDto? = null,
    val score: ScoreDto? = null,
)

@Serializable
data class FixtureDto(
    val id: Long,
    val referee: String? = null,
    val timezone: String? = null,
    val date: String? = null,
    val timestamp: Long? = null,
    val venue: VenueDto? = null,
    val status: StatusDto? = null,
)

@Serializable
data class VenueDto(
    val id: Long? = null,
    val name: String? = null,
    val city: String? = null,
)

@Serializable
data class StatusDto(
    @SerialName("long") val longName: String? = null,
    @SerialName("short") val shortName: String? = null,
    val elapsed: Int? = null,
)

@Serializable
data class LeagueDto(
    val id: Long,
    val name: String,
    val country: String? = null,
    val logo: String? = null,
    val flag: String? = null,
    val season: Int? = null,
    val round: String? = null,
)

@Serializable
data class TeamsDto(
    val home: TeamDto,
    val away: TeamDto,
)

@Serializable
data class TeamDto(
    val id: Long,
    val name: String,
    val logo: String? = null,
    val winner: Boolean? = null,
)

@Serializable
data class GoalsDto(
    val home: Int? = null,
    val away: Int? = null,
)

@Serializable
data class ScoreDto(
    val halftime: GoalsDto? = null,
    val fulltime: GoalsDto? = null,
    val extratime: GoalsDto? = null,
    val penalty: GoalsDto? = null,
)
