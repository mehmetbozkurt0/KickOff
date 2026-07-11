package com.kickoff.kickoff.network

import com.kickoff.kickoff.data.model.FixtureResponse
import com.kickoff.kickoff.data.model.HeadToHeadResponse
import com.kickoff.kickoff.data.model.TeamStatisticsDto
import com.kickoff.kickoff.data.model.TeamStatisticsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * API-Football (v3.football.api-sports.io) ile konusan Ktor istemcisi.
 * Tum isteklere [ApiKeys.footballApiKey] degeri `x-apisports-key` header'i olarak eklenir.
 */
class FootballApiClient(apiKeys: ApiKeys) {

    // Hem ContentNegotiation hem de elle JsonElement -> DTO cevirimi ayni yapilandirmayi kullanir.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }

        defaultRequest {
            url(BASE_URL)
            header(API_KEY_HEADER, apiKeys.footballApiKey)
        }
    }

    /** Verilen tarihteki (YYYY-MM-DD) maclari getirir. */
    suspend fun getFixturesByDate(date: String): FixtureResponse =
        httpClient.get("fixtures") {
            parameter("date", date)
        }.body()

    /**
     * Iki takim arasindaki TUM gecmis karsilasmalari getirir. Format: "takimId1-takimId2".
     * Ucretsiz plan `last` parametresini desteklemedigi icin filtreleme istemci
     * tarafinda (repository katmaninda) yapilir.
     */
    suspend fun getHeadToHead(teamIds: String): HeadToHeadResponse =
        httpClient.get("fixtures/headtohead") {
            parameter("h2h", teamIds)
        }.body()

    /** Bir takimin oynadigi son [last] maci getirir (form analizi icin). */
    suspend fun getTeamLastFixtures(teamId: Long, last: Int = DEFAULT_FORM_COUNT): FixtureResponse =
        httpClient.get("fixtures") {
            parameter("team", teamId)
            parameter("last", last)
        }.body()

    /**
     * Bir takimin verilen lig + sezondaki genel sezon istatistiklerini getirir.
     * API hata dondururse (limit, plan kapsami vb.) `response` bos dizi `[]` gelir;
     * bu durumda null doner ve cagiran taraf "istatistik bulunamadi" olarak isler.
     */
    suspend fun getTeamSeasonStatistics(
        leagueId: Long,
        season: Int,
        teamId: Long,
    ): TeamStatisticsDto? {
        val envelope: TeamStatisticsResponse = httpClient.get("teams/statistics") {
            parameter("league", leagueId)
            parameter("season", season)
            parameter("team", teamId)
        }.body()

        val statsJson = envelope.response as? JsonObject ?: return null
        return json.decodeFromJsonElement<TeamStatisticsDto>(statsJson)
    }

    companion object {
        private const val BASE_URL = "https://v3.football.api-sports.io/"
        private const val API_KEY_HEADER = "x-apisports-key"
        private const val DEFAULT_FORM_COUNT = 5
    }
}
