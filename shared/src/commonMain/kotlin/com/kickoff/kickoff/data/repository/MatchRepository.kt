package com.kickoff.kickoff.data.repository

import com.kickoff.kickoff.domain.model.MatchUiModel

interface MatchRepository {

    /** Verilen tarihteki (YYYY-MM-DD) maclari UI modeline donusturerek getirir. */
    suspend fun getMatchesByDate(date: String): Result<List<MatchUiModel>>

    /**
     * Iki takimin H2H verisini cekip Gemini'ye analiz ettirir ve
     * yapay zeka tahminini duz metin olarak dondurur.
     */
    suspend fun getMatchPrediction(
        fixtureId: Long,
        homeTeamId: Long,
        awayTeamId: Long,
    ): Result<String>
}
