package com.kickoff.kickoff.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
data class GeminiPart(
    val text: String,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
) {
    /** Ilk adayin metnini duz string olarak verir; yanit bossa null. */
    val text: String?
        get() = candidates.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
}

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)
