package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddMediaBody(val source: String, val external_id: String, val type: String)

/** La fiche telle que `POST /media` la rend — sans `tracking`, c'est vérifié. */
@Serializable
data class AddedMedia(
    val id: String,
    val title: String,
    val cover_url: String? = null,
    val release_date: String? = null,
    val metadata: SearchMetadata = SearchMetadata(),
)

@Serializable
data class AddMediaResponse(val created: Boolean, val media: AddedMedia)
