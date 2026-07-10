package com.kickoff.kickoff.data.repository

import com.kickoff.kickoff.data.mapper.buildPredictionContext
import com.kickoff.kickoff.data.mapper.toUiModel
import com.kickoff.kickoff.data.model.FixtureItemDto
import com.kickoff.kickoff.domain.model.MatchUiModel
import com.kickoff.kickoff.network.FootballApiClient
import com.kickoff.kickoff.network.GeminiApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MatchRepositoryImpl(
    private val footballApiClient: FootballApiClient,
    private val geminiApiClient: GeminiApiClient,
) : MatchRepository {

    override suspend fun getMatchesByDate(date: String): Result<List<MatchUiModel>> =
        resultOf {
            footballApiClient.getFixturesByDate(date).response.map { it.toUiModel() }
        }

    override suspend fun getMatchPrediction(
        fixtureId: Long,
        homeTeamId: Long,
        awayTeamId: Long,
    ): Result<String> = resultOf {
        // Uc veri seti birbirinden bagimsiz oldugu icin paralel cekilir;
        // toplam bekleme suresi en yavas istegin suresine iner.
        val (homeLastFixtures, awayLastFixtures, h2hFixtures) = coroutineScope {
            val homeDeferred = async { footballApiClient.getTeamLastFixtures(homeTeamId).response }
            val awayDeferred = async { footballApiClient.getTeamLastFixtures(awayTeamId).response }
            val h2hDeferred = async { footballApiClient.getHeadToHead("$homeTeamId-$awayTeamId").response }
            Triple(homeDeferred.await(), awayDeferred.await(), h2hDeferred.await())
        }

        // Takim adlarini oncelikle kendi son-mac verisinden, olmazsa H2H'dan cikar.
        val homeTeamName = homeLastFixtures.teamNameOf(homeTeamId)
            ?: h2hFixtures.teamNameOf(homeTeamId)
            ?: "Ev sahibi takim"
        val awayTeamName = awayLastFixtures.teamNameOf(awayTeamId)
            ?: h2hFixtures.teamNameOf(awayTeamId)
            ?: "Deplasman takimi"

        val context = buildPredictionContext(
            homeTeamName = homeTeamName,
            homeTeamId = homeTeamId,
            homeLastFixtures = homeLastFixtures,
            awayTeamName = awayTeamName,
            awayTeamId = awayTeamId,
            awayLastFixtures = awayLastFixtures,
            h2hFixtures = h2hFixtures,
        )

        val prompt = buildPredictionPrompt(
            homeTeamName = homeTeamName,
            awayTeamName = awayTeamName,
            context = context,
        )

        geminiApiClient.generateContent(prompt).text
            ?: error("Yapay zeka analizi bos dondu, lutfen tekrar deneyin.")
    }

    private fun buildPredictionPrompt(
        homeTeamName: String,
        awayTeamName: String,
        context: String,
    ): String = """
        Sen elit bir futbol analisti ve taktik dehasısın. Sana Ev Sahibi takımın son 5 maçı, Deplasman takımının son 5 maçı ve aralarındaki geçmiş maçları veriyorum:

        Maç: $homeTeamName (ev sahibi) - $awayTeamName (deplasman)

        $context

        Bu verilerdeki galibiyet serilerini, atılan/yenilen gol trendlerini analiz et.
        SAKIN her maça ezbere 2-1 veya 1-1 gibi standart tahminler verme.
        Eğer bir takım formsuzsa farklı yenileceğini veya maçın golsüz geçeceğini söylemekten çekinme.
        Analizin; 1. Form Durumu, 2. Taktiksel Beklentiler, 3. Net Skor Tahmini başlıklarını (Markdown formatında) içersin.
        Cevabını Türkçe yaz.
    """.trimIndent()

    /** Listedeki herhangi bir mactan verilen ID'ye ait takimin adini bulur. */
    private fun List<FixtureItemDto>.teamNameOf(teamId: Long): String? =
        firstNotNullOfOrNull { item ->
            when (teamId) {
                item.teams.home.id -> item.teams.home.name
                item.teams.away.id -> item.teams.away.name
                else -> null
            }
        }

    /** runCatching'in aksine coroutine iptalini yutmaz, diger hatalari Result'a sarar. */
    private inline fun <T> resultOf(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
