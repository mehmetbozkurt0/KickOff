# KickOff

A football fixtures app that pairs live match schedules with AI-generated pre-match analysis. Browse any day's fixtures across the world's major leagues, filter by competition, and get a Gemini-powered tactical breakdown and score prediction for any match with one tap.

## Features

- **Daily fixtures browser** — Step through matches day by day, with league crests, kickoff times, and live/finished scores pulled from API-Football.
- **League filtering** — Filter a day's fixtures by competition via horizontally scrollable chips, with major tournaments (World Cup, Champions League, top five European leagues, Süper Lig) prioritized to the front of the list.
- **AI match analysis** — Tapping a fixture triggers a Gemini-powered breakdown that considers both teams' recent form, head-to-head history, and season statistics (goal averages, clean sheets, etc.), returned as a structured Markdown report: statistical comparison, tactical scenario, and a final score prediction.
- **Resilient data gathering** — The five data sources behind a prediction (home form, away form, head-to-head, and both teams' season stats) are fetched in parallel; if form or season-stats endpoints are unavailable, the analysis gracefully falls back to whatever data it does have rather than failing outright.
- **Result & prediction caching** — Fixtures for a given date and predictions for a given match are cached in memory, so revisiting a day or a previously analyzed match doesn't re-trigger API/AI calls.
- **Pull-to-refresh** — Refreshing a day bypasses the cache and re-fetches fixtures from the API.

## Architecture

KickOff is a Kotlin Multiplatform project (Android target) structured around a shared module that holds all business logic and UI, following an MVI pattern for the screen layer.

```
KickOff/
├── androidApp/            # Android application shell (Application class, MainActivity)
├── shared/                 # Shared KMP module — UI, state, and business logic
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/com/kickoff/kickoff/
│       │       ├── network/           # FootballApiClient, GeminiApiClient, ApiKeys
│       │       ├── data/
│       │       │   ├── model/          # API DTOs (fixtures, H2H, team stats, Gemini request/response)
│       │       │   ├── mapper/         # DTO → domain/UI model mapping, prediction prompt context
│       │       │   └── repository/     # MatchRepository abstraction + implementation
│       │       ├── domain/model/       # MatchUiModel, TeamUiModel
│       │       ├── presentation/       # MatchContract (MVI State/Intent/Effect), MatchViewModel
│       │       ├── ui/                 # MatchListScreen, PredictionBottomSheet, UI components
│       │       └── di/                 # Koin modules (network, repository, view model)
│       └── androidMain/     # Android-specific platform code
└── gradle/                  # Version catalog and wrapper
```

**Key abstractions:**

- `MatchRepository` — fetches and caches fixtures by date, and orchestrates the multi-source data gathering + Gemini call behind a match prediction.
- `FootballApiClient` — Ktor client for [API-Football](https://www.api-football.com/) (fixtures, head-to-head, team form, season statistics).
- `GeminiApiClient` — Ktor client for the Google Gemini API, used to turn gathered match data into a natural-language analysis.
- `MatchViewModel` / `MatchContract` — MVI-style screen state holder: a single `State`, a sealed `Intent` for all user actions, and an `Effect` channel for one-off events like error snackbars.
- Dependency injection is wired with **Koin**, with API keys (football + Gemini) injected from the platform layer (Android `BuildConfig`) at startup.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (Multiplatform) |
| UI | Compose Multiplatform (Material 3) |
| Architecture | MVI (State / Intent / Effect) |
| DI | Koin |
| Networking | Ktor Client (CIO engine) |
| Serialization | kotlinx.serialization |
| Image loading | Coil (Ktor-backed network fetcher) |
| Markdown rendering | multiplatform-markdown-renderer |
| Date/Time | kotlinx-datetime |
| Sports data | API-Football (api-sports.io) |
| AI | Google Gemini API (`gemini-3.1-pro-preview`) |
| Target | Android |

## Data Flow

1. `MatchListScreen` dispatches an `Intent` to `MatchViewModel`, which calls `MatchRepository.getMatchesByDate`.
2. The repository serves fixtures from its in-memory cache, or fetches them from API-Football and caches the result.
3. Tapping a match dispatches `MatchClicked`, which calls `MatchRepository.getMatchPrediction`.
4. The repository fetches recent form, head-to-head history, and season statistics for both teams in parallel, builds a structured prompt, and sends it to Gemini.
5. The resulting analysis is cached by fixture ID and rendered as Markdown in a bottom sheet.

## License

No license file is currently included in this repository.
