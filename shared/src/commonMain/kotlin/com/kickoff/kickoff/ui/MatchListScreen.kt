package com.kickoff.kickoff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kickoff.kickoff.presentation.MatchContract
import com.kickoff.kickoff.presentation.MatchViewModel
import com.kickoff.kickoff.ui.components.EmptyStateMessage
import com.kickoff.kickoff.ui.components.MatchCard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(viewModel: MatchViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MatchContract.Effect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = state.dateLabel, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onIntent(MatchContract.Intent.PreviousDay) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Önceki gün",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(MatchContract.Intent.NextDay) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Sonraki gün",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.availableLeagues.isNotEmpty()) {
                LeagueFilterRow(
                    leagues = state.availableLeagues,
                    selectedLeague = state.selectedLeague,
                    onLeagueSelected = { league ->
                        viewModel.onIntent(MatchContract.Intent.SelectLeague(league))
                    },
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.onIntent(MatchContract.Intent.RefreshMatches) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.error != null -> ErrorContent(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.onIntent(MatchContract.Intent.Retry) },
                        modifier = Modifier.align(Alignment.Center),
                    )

                    // Bos durumda da asagi cekerek yenileme calissin diye kaydirilabilir sarici gerekir;
                    // fillParentMaxSize sayesinde mesaj ekranin ortasinda kalir.
                    state.matches.isEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize()) {
                                EmptyStateMessage(
                                    title = "Bu tarihte planlanan maç bulunamadı",
                                    subtitle = "Okları kullanarak başka bir güne göz atın.",
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = state.filteredMatches, key = { it.fixtureId }) { match ->
                            MatchCard(
                                match = match,
                                onClick = {
                                    viewModel.onIntent(MatchContract.Intent.MatchClicked(match))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val selectedMatch = state.selectedMatch
    if (selectedMatch != null) {
        PredictionBottomSheet(
            match = selectedMatch,
            isLoading = state.isPredictionLoading,
            prediction = state.selectedMatchPrediction,
            error = state.predictionError,
            onDismiss = { viewModel.onIntent(MatchContract.Intent.PredictionDismissed) },
        )
    }
}

/** TopAppBar altinda yatay kaydirilabilir lig filtresi cipleri; ilk cip "Tümü"dür. */
@Composable
private fun LeagueFilterRow(
    leagues: List<String>,
    selectedLeague: String?,
    onLeagueSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all__") {
            FilterChip(
                selected = selectedLeague == null,
                onClick = { onLeagueSelected(null) },
                label = { Text(text = "Tümü") },
            )
        }
        items(items = leagues, key = { it }) { league ->
            val isSelected = league == selectedLeague
            FilterChip(
                selected = isSelected,
                // Secili cipe tekrar dokunmak filtreyi kaldirip "Tümü"ye doner.
                onClick = { onLeagueSelected(if (isSelected) null else league) },
                label = { Text(text = league) },
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = "Tekrar Dene")
        }
    }
}
