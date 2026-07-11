package com.kickoff.kickoff.network

import com.kickoff.kickoff.data.model.GeminiContent
import com.kickoff.kickoff.data.model.GeminiPart
import com.kickoff.kickoff.data.model.GeminiRequest
import com.kickoff.kickoff.data.model.GeminiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Google Gemini API'si ile konusan Ktor istemcisi.
 * Mac tahmini uretmek icin serbest metin prompt'lari [generateContent] uzerinden gonderilir.
 */
class GeminiApiClient(apiKeys: ApiKeys) {

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true

        // Pro modeli reasoning yaptigi icin Flash'a gore belirgin sekilde yavastir;
        // varsayilan 15 sn'lik CIO zaman asimi kesinlikle yetmez.
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
        }

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
            header(API_KEY_HEADER, apiKeys.geminiApiKey)
        }
    }

    /** Verilen prompt icin Gemini'den icerik uretir. */
    suspend fun generateContent(
        prompt: String,
        model: String = DEFAULT_MODEL,
    ): GeminiResponse =
        httpClient.post("v1beta/models/$model:generateContent") {
            contentType(ContentType.Application.Json)
            setBody(
                GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = prompt)),
                        )
                    )
                )
            )
        }.body()

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"
        private const val API_KEY_HEADER = "x-goog-api-key"
        private const val DEFAULT_MODEL = "gemini-3.1-pro-preview"
    }
}
