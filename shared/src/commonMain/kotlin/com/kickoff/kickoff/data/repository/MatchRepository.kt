package com.kickoff.kickoff.data.repository

import com.kickoff.kickoff.domain.model.MatchUiModel

interface MatchRepository {

    /**
     * Verilen tarihteki (YYYY-MM-DD) maclari UI modeline donusturerek getirir.
     * Sonuclar bellek ici cache'lenir; [forceRefresh] true ise cache atlanip API'ye gidilir.
     */
    suspend fun getMatchesByDate(date: String, forceRefresh: Boolean = false): Result<List<MatchUiModel>>

    /**
     * Iki takimin son maclarini, H2H verisini ve sezon istatistiklerini cekip
     * Gemini'ye analiz ettirir; yapay zeka tahminini duz metin olarak dondurur.
     * [season] null ise (ornegin bazi kupa maclari) sezon istatistigi adimi atlanir.
     */
    suspend fun getMatchPrediction(
        fixtureId: Long,
        homeTeamId: Long,
        awayTeamId: Long,
        leagueId: Long,
        season: Int?,
    ): Result<String>
}
