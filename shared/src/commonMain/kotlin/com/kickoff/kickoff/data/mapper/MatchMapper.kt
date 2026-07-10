@file:OptIn(ExperimentalTime::class)

package com.kickoff.kickoff.data.mapper

import com.kickoff.kickoff.data.model.FixtureItemDto
import com.kickoff.kickoff.domain.model.MatchUiModel
import com.kickoff.kickoff.domain.model.TeamUiModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun FixtureItemDto.toUiModel(): MatchUiModel = MatchUiModel(
    fixtureId = fixture.id,
    leagueName = league.name,
    leagueLogoUrl = league.logo,
    kickOffTime = formatKickOffTime(fixture.date),
    statusShort = fixture.status?.shortName ?: "NS",
    homeTeam = teams.home.toUiModel(),
    awayTeam = teams.away.toUiModel(),
    homeGoals = goals?.home,
    awayGoals = goals?.away,
)

private fun com.kickoff.kickoff.data.model.TeamDto.toUiModel(): TeamUiModel =
    TeamUiModel(id = id, name = name, logoUrl = logo)

/** ISO-8601 tarihini ("2026-07-10T19:00:00+00:00") cihazin saat dilimine gore "HH:mm" formatina cevirir. */
private fun formatKickOffTime(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return "--:--"
    return try {
        val local = Instant.parse(isoDate).toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        "$hour:$minute"
    } catch (_: IllegalArgumentException) {
        "--:--"
    }
}

/**
 * Herhangi bir mac listesini Gemini prompt'una gomulecek okunabilir bir ozete cevirir.
 * Ornek satir: "- 2025-11-02 | Super Lig: Galatasaray 2 - 1 Fenerbahce"
 */
fun List<FixtureItemDto>.toFixtureSummaryText(): String {
    if (isEmpty()) return "Kayitli mac bulunamadi."
    return joinToString(separator = "\n") { item ->
        val date = item.fixture.date?.take(10) ?: "?"
        val homeGoals = item.goals?.home?.toString() ?: "-"
        val awayGoals = item.goals?.away?.toString() ?: "-"
        "- $date | ${item.league.name}: ${item.teams.home.name} $homeGoals - $awayGoals ${item.teams.away.name}"
    }
}

/**
 * Bir takimin son maclarini o takimin perspektifinden ozetler:
 * G/B/M form dizisi, atilan/yenilen gol toplamlari ve mac bazinda detay satirlari.
 * LLM'in seri ve gol trendi analizi yapabilmesi icin ham listeden daha sindirilebilir bir formattir.
 */
fun List<FixtureItemDto>.toFormSummaryText(teamId: Long): String {
    // Skoru henuz olusmamis (oynanmamis/ertelenmis) maclari ele.
    val playedMatches = mapNotNull { item ->
        val homeGoals = item.goals?.home
        val awayGoals = item.goals?.away
        if (homeGoals == null || awayGoals == null) null else Triple(item, homeGoals, awayGoals)
    }
    if (playedMatches.isEmpty()) return "Guncel form verisi bulunamadi."

    var totalScored = 0
    var totalConceded = 0
    val resultLetters = mutableListOf<String>()

    val detailLines = playedMatches.map { (item, homeGoals, awayGoals) ->
        val isHome = item.teams.home.id == teamId
        val goalsFor = if (isHome) homeGoals else awayGoals
        val goalsAgainst = if (isHome) awayGoals else homeGoals
        totalScored += goalsFor
        totalConceded += goalsAgainst

        val resultLetter = when {
            goalsFor > goalsAgainst -> "G"
            goalsFor == goalsAgainst -> "B"
            else -> "M"
        }
        resultLetters += resultLetter

        val opponent = if (isHome) item.teams.away.name else item.teams.home.name
        val venue = if (isHome) "ev" else "deplasman"
        val date = item.fixture.date?.take(10) ?: "?"
        "  - [$resultLetter] $date | $opponent'e karsi ($venue): $goalsFor-$goalsAgainst"
    }

    return buildString {
        appendLine("Form dizisi (G=galibiyet, B=beraberlik, M=maglubiyet): ${resultLetters.joinToString("-")}")
        appendLine("Toplam: ${playedMatches.size} macta $totalScored gol atti, $totalConceded gol yedi.")
        append(detailLines.joinToString(separator = "\n"))
    }
}

/**
 * Ev sahibi son maclari + deplasman son maclari + H2H verisini,
 * Gemini'ye gonderilecek tek bir zengin baglam metninde birlestirir.
 */
fun buildPredictionContext(
    homeTeamName: String,
    homeTeamId: Long,
    homeLastFixtures: List<FixtureItemDto>,
    awayTeamName: String,
    awayTeamId: Long,
    awayLastFixtures: List<FixtureItemDto>,
    h2hFixtures: List<FixtureItemDto>,
): String = buildString {
    appendLine("== EV SAHIBI: $homeTeamName — son maclari ==")
    appendLine(homeLastFixtures.toFormSummaryText(homeTeamId))
    appendLine()
    appendLine("== DEPLASMAN: $awayTeamName — son maclari ==")
    appendLine(awayLastFixtures.toFormSummaryText(awayTeamId))
    appendLine()
    appendLine("== ARALARINDAKI GECMIS KARSILASMALAR (H2H) ==")
    append(h2hFixtures.toFixtureSummaryText())
}
