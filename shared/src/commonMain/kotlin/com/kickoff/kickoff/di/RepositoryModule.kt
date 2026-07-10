package com.kickoff.kickoff.di

import com.kickoff.kickoff.data.repository.MatchRepository
import com.kickoff.kickoff.data.repository.MatchRepositoryImpl
import org.koin.core.module.Module
import org.koin.dsl.module

val repositoryModule: Module = module {
    single<MatchRepository> { MatchRepositoryImpl(get(), get()) }
}
