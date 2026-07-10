package com.kickoff.kickoff

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.kickoff.kickoff.ui.MatchListScreen

@Composable
fun App() {
    // Takim/lig logolarini URL'den yukleyebilmek icin Coil'e Ktor tabanli network fetcher tanitilir.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    MaterialTheme {
        MatchListScreen()
    }
}
