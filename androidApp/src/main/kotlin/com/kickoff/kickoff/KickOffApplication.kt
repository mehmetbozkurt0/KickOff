package com.kickoff.kickoff

import android.app.Application
import com.kickoff.kickoff.di.appModules
import com.kickoff.kickoff.network.ApiKeys
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KickOffApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@KickOffApplication)
            modules(
                appModules(
                    ApiKeys(
                        footballApiKey = BuildConfig.FOOTBALL_API_KEY,
                        geminiApiKey = BuildConfig.GEMINI_API_KEY,
                    )
                )
            )
        }
    }
}
