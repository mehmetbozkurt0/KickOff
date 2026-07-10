package com.kickoff.kickoff.network

/**
 * API anahtarlarini platform katmanindan (Android'de BuildConfig) ortak koda tasiyan kopru.
 * Koin graph'ine uygulama baslangicinda enjekte edilir.
 */
data class ApiKeys(
    val footballApiKey: String,
    val geminiApiKey: String,
)
