package com.kickoff.kickoff.di

import com.kickoff.kickoff.network.ApiKeys
import org.koin.core.module.Module

/** Uygulamanin tum Koin modullerini tek noktadan toplar; platform girisleri bunu kullanir. */
fun appModules(apiKeys: ApiKeys): List<Module> = listOf(
    networkModule(apiKeys),
    repositoryModule,
    viewModelModule,
)
