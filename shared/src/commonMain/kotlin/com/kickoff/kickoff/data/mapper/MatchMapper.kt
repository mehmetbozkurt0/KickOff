@file:OptIn(ExperimentalTime::class)

package com.kickoff.kickoff.data.mapper

import com.kickoff.kickoff.data.model.FixtureItemDto
import com.kickoff.kickoff.data.model.TeamStatisticsDto
import com.kickoff.kickoff.domain.model.MatchUiModel
import com.kickoff.kickoff.domain.model.TeamUiModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun FixtureItemDto.toUiModel(): MatchUiModel = MatchUiModel(
    fixtureId = fixture.id,
    leagueId = league.id,
    season = league.season,
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
 * Bir takimin sezon geneli istatistiklerini LLM'in okuyabilecegi ozet metne cevirir:
 * O/G/B/M, gol atma-yeme ortalamalari, clean sheet, en cok kullanilan formasyon
 * ve gol dakika dagilimlari.
 */
fun TeamStatisticsDto.toSeasonSummaryText(): String {
    val played = fixtures?.played?.total
    if (played == null || played == 0) return "Sezon istatistigi bulunamadi."

    return buildString {
        appendLine(
            "Sezon ozeti: $played mac | " +
                "${fixtures.wins?.total ?: 0} galibiyet, " +
                "${fixtures.draws?.total ?: 0} beraberlik, " +
                "${fixtures.loses?.total ?: 0} maglubiyet"
        )
        form?.takeIf { it.isNotBlank() }?.let {
            appendLine("Sezon form dizisi (W/D/L): ${it.takeLast(10)}")
        }
        goals?.scored?.let { scored ->
            appendLine(
                "Atilan gol: toplam ${scored.total?.total ?: 0} | " +
                    "mac basi ortalama ${scored.average?.total ?: "?"} " +
                    "(evde ${scored.average?.home ?: "?"}, deplasmanda ${scored.average?.away ?: "?"})"
            )
        }
        goals?.against?.let { against ->
            appendLine(
                "Yenilen gol: toplam ${against.total?.total ?: 0} | " +
                    "mac basi ortalama ${against.average?.total ?: "?"} " +
                    "(evde ${against.average?.home ?: "?"}, deplasmanda ${against.average?.away ?: "?"})"
            )
        }
        appendLine("Clean sheet (gol yemeden bitirilen mac): ${cleanSheet?.total ?: 0}")
        appendLine("Gol atamadan bitirilen mac: ${failedToScore?.total ?: 0}")

        lineups.maxByOrNull { it.played ?: 0 }?.formation?.let {
            appendLine("En cok kullanilan formasyon: $it")
        }

        val scoredMinutes = goals?.scored?.minute.toMinuteSummary()
        if (scoredMinutes.isNotBlank()) appendLine("Gol atma dakikalari: $scoredMinutes")
        val concededMinutes = goals?.against?.minute.toMinuteSummary()
        if (concededMinutes.isNotBlank()) append("Gol yeme dakikalari: $concededMinutes")
    }.trimEnd()
}

/** Dakika dagilimini "0-15: %10, 16-30: %25" formatinda tek satira indirger. */
private fun Map<String, com.kickoff.kickoff.data.model.StatsMinuteDto>?.toMinuteSummary(): String {
    if (this == null) return ""
    return entries
        .filter { (it.value.total ?: 0) > 0 }
        .joinToString(separator = ", ") { (range, stat) ->
            "$range: ${stat.percentage ?: "${stat.total} gol"}"
        }
}

/**
 * Ev sahibi son maclari + deplasman son maclari + H2H + sezon istatistiklerini,
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
    homeSeasonStats: TeamStatisticsDto? = null,
    awaySeasonStats: TeamStatisticsDto? = null,
): String = buildString {
    appendLine("== EV SAHIBI: $homeTeamName — son maclari ==")
    appendLine(homeLastFixtures.toFormSummaryText(homeTeamId))
    appendLine()
    appendLine("== DEPLASMAN: $awayTeamName — son maclari ==")
    appendLine(awayLastFixtures.toFormSummaryText(awayTeamId))
    appendLine()
    appendLine("== EV SAHIBI: $homeTeamName — sezon istatistikleri ==")
    appendLine(homeSeasonStats?.toSeasonSummaryText() ?: "Sezon istatistigi bulunamadi.")
    appendLine()
    appendLine("== DEPLASMAN: $awayTeamName — sezon istatistikleri ==")
    appendLine(awaySeasonStats?.toSeasonSummaryText() ?: "Sezon istatistigi bulunamadi.")
    appendLine()
    appendLine("== ARALARINDAKI GECMIS KARSILASMALAR (H2H) ==")
    append(h2hFixtures.toFixtureSummaryText())
}
