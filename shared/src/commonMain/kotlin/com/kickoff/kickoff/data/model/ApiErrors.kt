package com.kickoff.kickoff.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * API-Football, limit/plan/parametre hatalarinda bile HTTP 200 doner; gercek hata
 * govdedeki `errors` alanindadir (bos dizi `[]`, bos obje `{}` ya da mesaj objesi).
 * Bu yuzden Ktor'un `expectSuccess`'i bu hatalari yakalayamaz — govde elle okunmalidir.
 */
fun JsonElement?.toApiErrorMessages(): List<String> = when (this) {
    null -> emptyList()
    is JsonObject -> entries.map { (key, value) ->
        "$key: ${(value as? JsonPrimitive)?.contentOrNull ?: value.toString()}"
    }
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}

/**
 * Yanit bos VE `errors` doluysa, sessizce bos liste donmek yerine gercek nedeni
 * tasiyan bir hata firlatir. Boylece limit/plan hatalari "veri yok" gibi gorunmez.
 */
fun FixtureResponse.responseOrThrow(): List<FixtureItemDto> {
    val apiErrors = errors.toApiErrorMessages()
    if (response.isEmpty() && apiErrors.isNotEmpty()) {
        error("API-Football hatasi: ${apiErrors.joinToString("; ")}")
    }
    return response
}
