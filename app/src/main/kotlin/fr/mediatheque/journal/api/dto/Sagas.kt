package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Les sagas (brief du 15 septembre 2026), jumelles des réalisateurs
 * (`Realisateurs.kt`) : mêmes formes, à `profile_url` près, qui devient ici
 * `cover_url` — une collection TMDB porte une affiche, pas une photo de
 * personne. Les films d'une saga suivent le DTO partagé de `Suivis.kt`
 * (`FilmSuivi`, `FilmsResponse`).
 *
 * Le mot « saga » désigne aussi, côté back, la veille sur une collection déjà
 * en bibliothèque (`PUT`/`DELETE /sagas/{id}/watch`, tables `sagas` /
 * `saga_items`) — un usage sans rapport, que cette application ne consomme
 * pas ; voir `docs/api-reference.md` (dépôt `biblio-back`).
 */

/** Un résultat de `GET /reference/sagas?q=` : une collection TMDB. */
@Serializable
data class CollectionResult(val tmdb_id: Int, val name: String, val cover_url: String? = null)

@Serializable
data class CollectionsResponse(val results: List<CollectionResult> = emptyList())

/**
 * Une saga suivie (`GET /me/sagas`, et la réponse de `POST`). `name` et
 * `cover_url` sont ceux que TMDB donnait au moment de l'ajout : le back ne le
 * rappelle pas pour rendre cette liste.
 */
@Serializable
data class Saga(
    val tmdb_id: Int,
    val name: String,
    val cover_url: String? = null,
    val ajoute_le: String,
)

@Serializable
data class SuivreSagaBody(val tmdb_id: Int)
