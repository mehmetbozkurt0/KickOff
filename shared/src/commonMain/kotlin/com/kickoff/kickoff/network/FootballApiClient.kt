package com.kickoff.kickoff.network

import com.kickoff.kickoff.data.model.FixtureResponse
import com.kickoff.kickoff.data.model.HeadToHeadResponse
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

/**
 * API-Football (v3.football.api-sports.io) ile konusan Ktor istemcisi.
 * Tum isteklere [ApiKeys.footballApiKey] degeri `x-apisports-key` header'i olarak eklenir.
 */
class FootballApiClient(apiKeys: ApiKeys) {

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }
            )
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

    /** Iki takim arasindaki gecmis karsilasmalari getirir. Format: "takimId1-takimId2". */
    suspend fun getHeadToHead(teamIds: String, last: Int = DEFAULT_H2H_COUNT): HeadToHeadResponse =
        httpClient.get("fixtures/headtohead") {
            parameter("h2h", teamIds)
            parameter("last", last)
        }.body()

    /** Bir takimin oynadigi son [last] maci getirir (form analizi icin). */
    suspend fun getTeamLastFixtures(teamId: Long, last: Int = DEFAULT_FORM_COUNT): FixtureResponse =
        httpClient.get("fixtures") {
            parameter("team", teamId)
            parameter("last", last)
        }.body()

    companion object {
        private const val BASE_URL = "https://v3.football.api-sports.io/"
        private const val API_KEY_HEADER = "x-apisports-key"
        private const val DEFAULT_H2H_COUNT = 10
        private const val DEFAULT_FORM_COUNT = 5
    }
}
