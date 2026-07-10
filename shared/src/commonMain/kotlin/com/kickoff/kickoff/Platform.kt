package com.kickoff.kickoff

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform