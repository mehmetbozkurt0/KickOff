@file:OptIn(ExperimentalTime::class)

package com.kickoff.kickoff.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kickoff.kickoff.data.repository.MatchRepository
import com.kickoff.kickoff.domain.model.MatchUiModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class MatchViewModel(
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchContract.State(selectedDate = today()))
    val state: StateFlow<MatchContract.State> = _state.asStateFlow()

    private val _effect = Channel<MatchContract.Effect>(Channel.BUFFERED)
    val effect: Flow<MatchContract.Effect> = _effect.receiveAsFlow()

    init {
        onIntent(MatchContract.Intent.LoadMatches)
    }

    /** MVI'nin tek giris kapisi: UI tum kullanici etkilesimlerini Intent olarak buraya yollar. */
    fun onIntent(intent: MatchContract.Intent) {
        when (intent) {
            MatchContract.Intent.LoadMatches -> loadMatches()

            // Retry de cache'i atlar: hata sonrasi "Tekrar Dene" bayat veriye carpmamali.
            MatchContract.Intent.Retry -> loadMatches(forceRefresh = true)

            MatchContract.Intent.RefreshMatches -> loadMatches(forceRefresh = true, isPullRefresh = true)

            MatchContract.Intent.PreviousDay -> changeDay(offsetDays = -1)

            MatchContract.Intent.NextDay -> changeDay(offsetDays = 1)

            is MatchContract.Intent.SelectLeague ->
                _state.update { it.copy(selectedLeague = intent.leagueName) }

            is MatchContract.Intent.MatchClicked -> loadPrediction(intent.match)

            MatchContract.Intent.PredictionDismissed -> dismissPrediction()
        }
    }

    private fun changeDay(offsetDays: Int) {
        val newDate = _state.value.selectedDate.plus(offsetDays, DateTimeUnit.DAY)
        _state.update { it.copy(selectedDate = newDate, dateLabel = labelFor(newDate)) }
        loadMatches()
    }

    private fun loadMatches(forceRefresh: Boolean = false, isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            // Pull-to-refresh sirasinda liste ekranda kalir, tam ekran spinner gosterilmez.
            _state.update {
                it.copy(isLoading = !isPullRefresh, isRefreshing = isPullRefresh, error = null)
            }

            matchRepository.getMatchesByDate(_state.value.selectedDate.toString(), forceRefresh)
                .onSuccess { matches ->
                    val leagues = sortLeagues(matches.map { match -> match.leagueName }.distinct())
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            matches = matches,
                            availableLeagues = leagues,
                            // Onceki secim yeni listede yoksa (gun degisti vb.) "Tumu"ye don.
                            selectedLeague = it.selectedLeague?.takeIf { selected -> selected in leagues },
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = throwable.message ?: "Maclar yuklenirken bir hata olustu."
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = message) }
                    _effect.send(MatchContract.Effect.ShowError(message))
                }
        }
    }

    private fun loadPrediction(match: MatchUiModel) {
        _state.update {
            it.copy(
                selectedMatch = match,
                isPredictionLoading = true,
                selectedMatchPrediction = null,
                predictionError = null,
            )
        }
        viewModelScope.launch {
            matchRepository.getMatchPrediction(
                fixtureId = match.fixtureId,
                homeTeamId = match.homeTeam.id,
                awayTeamId = match.awayTeam.id,
                leagueId = match.leagueId,
                season = match.season,
            )
                .onSuccess { prediction ->
                    _state.update { it.copy(isPredictionLoading = false, selectedMatchPrediction = prediction) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isPredictionLoading = false,
                            predictionError = throwable.message ?: "Analiz alinamadi, lutfen tekrar deneyin.",
                        )
                    }
                }
        }
    }

    private fun dismissPrediction() {
        _state.update {
            it.copy(
                selectedMatch = null,
                isPredictionLoading = false,
                selectedMatchPrediction = null,
                predictionError = null,
            )
        }
    }

    /**
     * Buyuk turnuva/ligleri [LEAGUE_PRIORITY] sirasiyla one alir; listede olmayanlar
     * kendi iclerinde alfabetik olarak sona eklenir.
     */
    private fun sortLeagues(leagues: List<String>): List<String> =
        leagues.sortedWith(compareBy({ priorityIndexOf(it) }, { normalizeLeagueName(it) }))

    /** Ligin oncelik listesindeki sirasi; listede yoksa hepsinden buyuk bir deger doner. */
    private fun priorityIndexOf(leagueName: String): Int {
        val normalized = normalizeLeagueName(leagueName)
        val index = LEAGUE_PRIORITY.indexOfFirst { keyword -> keyword in normalized }
        return if (index == -1) LEAGUE_PRIORITY.size else index
    }

    /**
     * Buyuk/kucuk harf ve Turkce aksan farklarini sadelestirir; boylece
     * "Trendyol Süper Lig" gibi sponsorlu varyantlar da "super lig" ile eslesir.
     */
    private fun normalizeLeagueName(name: String): String =
        name.lowercase()
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ç', 'c')
            .replace('ğ', 'g')

    /** Secili tarihi kullanici dostu etikete cevirir: Bugun/Dun/Yarin ya da "12 Eyl". */
    private fun labelFor(date: LocalDate): String {
        val today = today()
        return when (date) {
            today -> "Bugün"
            today.minus(1, DateTimeUnit.DAY) -> "Dün"
            today.plus(1, DateTimeUnit.DAY) -> "Yarın"
            else -> "${date.day} ${TURKISH_MONTHS_SHORT[date.month.number - 1]}"
        }
    }

    private fun today(): LocalDate =
        Clock.System.todayIn(TimeZone.currentSystemDefault())

    companion object {
        private val TURKISH_MONTHS_SHORT = listOf(
            "Oca", "Şub", "Mar", "Nis", "May", "Haz",
            "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
        )

        // Cip siralamasinda one alinacak turnuva/ligler; anahtar kelimeler
        // normalizeLeagueName ciktisiyla `contains` uzerinden eslestirilir.
        private val LEAGUE_PRIORITY = listOf(
            "world cup",
            "euro championship",
            "uefa champions league",
            "uefa europa league",
            "premier league",
            "la liga",
            "serie a",
            "bundesliga",
            "ligue 1",
            "super lig",
        )
    }
}
