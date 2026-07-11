package com.kickoff.kickoff.presentation

import com.kickoff.kickoff.domain.model.MatchUiModel
import kotlinx.datetime.LocalDate

/** Mac listesi ekraninin MVI sozlesmesi: State + Intent + Effect. */
object MatchContract {

    data class State(
        val selectedDate: LocalDate,
        val dateLabel: String = "Bugün",
        val isLoading: Boolean = false,
        // Pull-to-refresh gostergesi; isLoading'den farkli olarak liste ekranda kalir.
        val isRefreshing: Boolean = false,
        val matches: List<MatchUiModel> = emptyList(),
        // O gunku maclarin lig isimleri (distinct); filtre cipleri buradan uretilir.
        val availableLeagues: List<String> = emptyList(),
        // null = "Tumu" secili.
        val selectedLeague: String? = null,
        val error: String? = null,
        val selectedMatch: MatchUiModel? = null,
        val isPredictionLoading: Boolean = false,
        val selectedMatchPrediction: String? = null,
        val predictionError: String? = null,
    ) {
        /** Bottom sheet, bir mac secildigi surece acik kalir. */
        val isPredictionSheetVisible: Boolean
            get() = selectedMatch != null

        /** Secili lige gore suzulmus liste; secim yoksa tum maclar. */
        val filteredMatches: List<MatchUiModel>
            get() = if (selectedLeague == null) matches else matches.filter { it.leagueName == selectedLeague }
    }

    sealed interface Intent {
        data object LoadMatches : Intent
        data object Retry : Intent

        /** Cache'i atlayip secili tarihi API'den yeniden ceker (pull-to-refresh). */
        data object RefreshMatches : Intent
        data object PreviousDay : Intent
        data object NextDay : Intent
        /** Lig filtresi secimi; null "Tumu" anlamina gelir. */
        data class SelectLeague(val leagueName: String?) : Intent

        data class MatchClicked(val match: MatchUiModel) : Intent
        data object PredictionDismissed : Intent
    }

    sealed interface Effect {
        data class ShowError(val message: String) : Effect
    }
}
