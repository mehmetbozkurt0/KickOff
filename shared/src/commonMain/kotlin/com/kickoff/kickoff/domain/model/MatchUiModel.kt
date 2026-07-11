package com.kickoff.kickoff.domain.model

/**
 * UI katmaninin ihtiyac duydugu sadelestirilmis mac modeli.
 * API-Football DTO'lari UI'a sizmasin diye repository katmaninda bu modele donusturulur.
 */
data class MatchUiModel(
    val fixtureId: Long,
    val leagueId: Long,
    // Kupa/ozel maclarda API sezon dondurmezse null kalir; sezon istatistigi adimi atlanir.
    val season: Int?,
    val leagueName: String,
    val leagueLogoUrl: String?,
    val kickOffTime: String,
    val statusShort: String,
    val homeTeam: TeamUiModel,
    val awayTeam: TeamUiModel,
    val homeGoals: Int?,
    val awayGoals: Int?,
) {
    /** Skor varsa "2 - 1", mac baslamadiysa "vs" gosterilir. */
    val scoreText: String
        get() = if (homeGoals != null && awayGoals != null) "$homeGoals - $awayGoals" else "vs"
}

data class TeamUiModel(
    val id: Long,
    val name: String,
    val logoUrl: String?,
)
