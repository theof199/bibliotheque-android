package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchMetadata(val director: String? = null)

/** Un résultat de `GET /search?type=movie`. `source` + `external_id` est la clé d'ajout. */
@Serializable
data class SearchResult(
    val source: String,
    val external_id: String,
    val type: String,
    val title: String,
    val year: Int? = null,
    val cover_url: String? = null,
    val metadata: SearchMetadata = SearchMetadata(),
)

@Serializable
data class SearchResponse(val items: List<SearchResult>, val next_cursor: String? = null)
