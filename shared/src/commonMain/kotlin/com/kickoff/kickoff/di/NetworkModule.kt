package com.kickoff.kickoff.di

import com.kickoff.kickoff.network.ApiKeys
import com.kickoff.kickoff.network.FootballApiClient
import com.kickoff.kickoff.network.GeminiApiClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Ag katmani bagimliliklarini saglayan Koin modulu.
 * [apiKeys] platform tarafindan (Android'de BuildConfig) saglanir.
 */
fun networkModule(apiKeys: ApiKeys): Module = module {
    single { apiKeys }
    single { FootballApiClient(get()) }
    single { GeminiApiClient(get()) }
}
