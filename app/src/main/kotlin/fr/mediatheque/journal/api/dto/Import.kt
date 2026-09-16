package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/** Le corps de `POST /me/journal/import/letterboxd` : JSON, jamais `text/csv` (brief du 16 septembre 2026). */
@Serializable
data class ImportLetterboxdBody(val csv: String)

/** Un candidat TMDB parmi lesquels une ligne n'a pas pu être tranchée. */
@Serializable
data class ImportLetterboxdCandidate(val tmdb_id: String, val title: String, val year: Int? = null)

/** Une ligne non importée, faute d'un candidat net — vide ou pluriel dans `candidats` selon le cas. */
@Serializable
data class ImportLetterboxdUnrecognized(
    val ligne: Int,
    val name: String,
    val year: Int? = null,
    val candidats: List<ImportLetterboxdCandidate> = emptyList(),
)

/** Une ligne dont le traitement a échoué. */
@Serializable
data class ImportLetterboxdError(val ligne: Int, val message: String)

/** Le bilan de l'import — la réponse n'arrive qu'une fois le fichier entièrement traité. */
@Serializable
data class ImportLetterboxdResponse(
    val importes: Int,
    val deja_presents: Int,
    val non_reconnus: List<ImportLetterboxdUnrecognized> = emptyList(),
    val erreurs: List<ImportLetterboxdError> = emptyList(),
)
