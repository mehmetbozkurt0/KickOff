package com.kickoff.kickoff.data.repository

import com.kickoff.kickoff.data.mapper.buildPredictionContext
import com.kickoff.kickoff.data.mapper.toUiModel
import com.kickoff.kickoff.data.model.FixtureItemDto
import com.kickoff.kickoff.data.model.TeamStatisticsDto
import com.kickoff.kickoff.data.model.responseOrThrow
import com.kickoff.kickoff.domain.model.MatchUiModel
import com.kickoff.kickoff.network.FootballApiClient
import com.kickoff.kickoff.network.GeminiApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MatchRepositoryImpl(
    private val footballApiClient: FootballApiClient,
    private val geminiApiClient: GeminiApiClient,
) : MatchRepository {

    // KMP'de ConcurrentHashMap olmadigi icin duz map'ler Mutex ile korunur.
    // Kilit yalnizca map okuma/yazma anini kapsar; ag istekleri kilit disinda calisir.
    private val cacheMutex = Mutex()
    private val matchesCache = mutableMapOf<String, List<MatchUiModel>>()
    private val predictionsCache = mutableMapOf<Long, String>()

    override suspend fun getMatchesByDate(
        date: String,
        forceRefresh: Boolean,
    ): Result<List<MatchUiModel>> = resultOf {
        if (!forceRefresh) {
            cacheMutex.withLock { matchesCache[date] }?.let { return@resultOf it }
        }

        val matches = footballApiClient.getFixturesByDate(date).responseOrThrow().map { it.toUiModel() }
        cacheMutex.withLock { matchesCache[date] = matches }
        matches
    }

    override suspend fun getMatchPrediction(
        fixtureId: Long,
        homeTeamId: Long,
        awayTeamId: Long,
        leagueId: Long,
        season: Int?,
    ): Result<String> = resultOf {
        // Ayni mac icin analiz daha once uretildiyse 5 API istegi ve
        // Gemini bekleme suresi tamamen atlanir.
        cacheMutex.withLock { predictionsCache[fixtureId] }?.let { return@resultOf it }

        // Bes veri seti birbirinden bagimsiz oldugu icin paralel cekilir;
        // toplam bekleme suresi en yavas istegin suresine iner.
        val predictionData = coroutineScope {
            val homeDeferred = async { fetchTeamFormOrEmpty(homeTeamId) }
            val awayDeferred = async { fetchTeamFormOrEmpty(awayTeamId) }
            // H2H tum gecmisi doner (`last` ucretsiz planda kapali); oynanmis maclar
            // en yeniden eskiye siralanip son H2H_CLIENT_LIMIT tanesi alinir.
            val h2hDeferred = async {
                footballApiClient.getHeadToHead("$homeTeamId-$awayTeamId").responseOrThrow()
                    .filter { it.goals?.home != null && it.goals.away != null }
                    .sortedByDescending { it.fixture.timestamp ?: 0L }
                    .take(H2H_CLIENT_LIMIT)
            }
            // Sezon bilgisi olmayan maclarda (bazi kupa/ozel maclar) istatistik adimi atlanir.
            val homeStatsDeferred = async { season?.let { fetchSeasonStatsOrNull(leagueId, it, homeTeamId) } }
            val awayStatsDeferred = async { season?.let { fetchSeasonStatsOrNull(leagueId, it, awayTeamId) } }
            PredictionData(
                homeLastFixtures = homeDeferred.await(),
                awayLastFixtures = awayDeferred.await(),
                h2hFixtures = h2hDeferred.await(),
                homeSeasonStats = homeStatsDeferred.await(),
                awaySeasonStats = awayStatsDeferred.await(),
            )
        }
        val (homeLastFixtures, awayLastFixtures, h2hFixtures, homeSeasonStats, awaySeasonStats) = predictionData

        // Hicbir kaynaktan veri gelmediyse Gemini'ye bos baglam gonderip
        // uydurma bir tahmin almak yerine kullaniciya durumu net soyle.
        if (homeLastFixtures.isEmpty() && awayLastFixtures.isEmpty() && h2hFixtures.isEmpty()) {
            error("Bu mac icin analiz edilebilir veri bulunamadi. API planiniz bu ligi veya sezonu kapsamiyor olabilir.")
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
            homeSeasonStats = homeSeasonStats,
            awaySeasonStats = awaySeasonStats,
        )

        val prompt = buildPredictionPrompt(
            homeTeamName = homeTeamName,
            awayTeamName = awayTeamName,
            context = context,
        )

        val prediction = geminiApiClient.generateContent(prompt).text
            ?: error("Yapay zeka analizi bos dondu, lutfen tekrar deneyin.")

        cacheMutex.withLock { predictionsCache[fixtureId] = prediction }
        prediction
    }

    private fun buildPredictionPrompt(
        homeTeamName: String,
        awayTeamName: String,
        context: String,
    ): String = """
        Sen tamamen analitik ve istatistiksel çalışan elit bir futbol veri bilimcisisin. Sana iki takımın hem son maçlarını hem de bu sezonki genel istatistiklerini (gol ortalamaları, clean sheet sayıları vb.) veriyorum:

        Maç: $homeTeamName (ev sahibi) - $awayTeamName (deplasman)

        $context

        Ezbere veya ev sahibi avantajına sığınarak skor verme! Takımların gol atma dakikalarına, form durumlarına ve savunma dirençlerine bakarak (adeta Beklenen Gol - xG hesaplar gibi) maçın nasıl geçeceğini analiz et. Eğer istatistikler farklı bir skor, golsüz beraberlik veya sürpriz bir deplasman galibiyeti gösteriyorsa bunu net bir şekilde ver.
        Önemli: Eğer sana takım formu veya sezon istatistiği verilemiyorsa ("bulunamadi" notunu görürsen), elinde SADECE iki takımın geçmiş karşılaşmaları (H2H) var demektir. Bu durumda veri eksiğinden şikayet etme. Sadece geçmiş maçların skorlarına, aralarındaki psikolojik üstünlüğe ve derbi dinamiklerine bakarak bir maç senaryosu ve kesin skor tahmini üret.
        Analizin: 1. İstatistiksel Çarpışma, 2. Taktiksel Senaryo, 3. Kesin Skor Tahmini başlıklarını (Markdown) içersin.
        Cevabını Türkçe yaz.
    """.trimIndent()

    /**
     * Ucretsiz plan `last` parametresini desteklemedigi icin form istegi plan hatasiyla
     * donebilir. Form verisi zenginlestiricidir; tum tahmini dusurmek yerine bos liste
     * ile devam edilir (prompt'a "bulunamadi" olarak yansir, H2H analizi surer).
     */
    private suspend fun fetchTeamFormOrEmpty(teamId: Long): List<FixtureItemDto> = try {
        footballApiClient.getTeamLastFixtures(teamId).responseOrThrow()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Sezon istatistigi tahminin "olmazsa olmazi" degildir; bu istek basarisiz olursa
     * tum tahmini dusurmek yerine null donup kalan verilerle devam edilir.
     */
    private suspend fun fetchSeasonStatsOrNull(
        leagueId: Long,
        season: Int,
        teamId: Long,
    ): TeamStatisticsDto? = try {
        footballApiClient.getTeamSeasonStatistics(leagueId, season, teamId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** getMatchPrediction icin paralel cekilen bes veri setini bir arada tasir. */
    private data class PredictionData(
        val homeLastFixtures: List<FixtureItemDto>,
        val awayLastFixtures: List<FixtureItemDto>,
        val h2hFixtures: List<FixtureItemDto>,
        val homeSeasonStats: TeamStatisticsDto?,
        val awaySeasonStats: TeamStatisticsDto?,
    )

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

    companion object {
        /** `last` parametresi ucretsiz planda kapali oldugu icin H2H istemci tarafinda suzulur. */
        private const val H2H_CLIENT_LIMIT = 10
    }
}
