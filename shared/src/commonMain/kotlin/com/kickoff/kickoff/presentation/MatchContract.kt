package com.kickoff.kickoff.presentation

import com.kickoff.kickoff.domain.model.MatchUiModel
import kotlinx.datetime.LocalDate

/** Mac listesi ekraninin MVI sozlesmesi: State + Intent + Effect. */
object MatchContract {

    data class State(
        val selectedDate: LocalDate,
        val dateLabel: String = "Bugün",
        val isLoading: Boolean = false,
        val matches: List<MatchUiModel> = emptyList(),
        val error: String? = null,
        val selectedMatch: MatchUiModel? = null,
        val isPredictionLoading: Boolean = false,
        val selectedMatchPrediction: String? = null,
        val predictionError: String? = null,
    ) {
        /** Bottom sheet, bir mac secildigi surece acik kalir. */
        val isPredictionSheetVisible: Boolean
            get() = selectedMatch != null
    }

    sealed interface Intent {
        data object LoadMatches : Intent
        data object Retry : Intent
        data object PreviousDay : Intent
        data object NextDay : Intent
        data class MatchClicked(val match: MatchUiModel) : Intent
        data object PredictionDismissed : Intent
    }

    sealed interface Effect {
        data class ShowError(val message: String) : Effect
    }
}
