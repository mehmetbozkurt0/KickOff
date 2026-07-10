package com.kickoff.kickoff.di

import com.kickoff.kickoff.presentation.MatchViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule: Module = module {
    viewModelOf(::MatchViewModel)
}
