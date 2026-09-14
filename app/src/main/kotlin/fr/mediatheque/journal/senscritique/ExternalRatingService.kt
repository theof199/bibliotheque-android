package fr.mediatheque.journal.senscritique

import java.time.LocalDate

/**
 * Ce qu'un service externe (SensCritique, demain Cinoche — brief du 14 septembre 2026) sait faire
 * avec un visionnage noté : chercher un produit chez lui, y pousser une note et une date. Rien ici
 * n'est spécifique à SensCritique : GraphQL, la connexion et l'appariement des titres vivent dans
 * les classes qui l'implémentent (`SensCritiqueRatingService`) ou s'en servent (`SensCritiqueSync`).
 */
interface ExternalRatingService {
    val name: String
    suspend fun search(keywords: String): ExternalSearchOutcome
    suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome
}

/** Un film de notre côté, à faire correspondre chez le service externe. */
data class MatchableFilm(val title: String, val originalTitle: String?, val year: Int?)

/** Un candidat rendu par la recherche du service externe. */
data class ExternalCandidate(
    val productId: Long,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val pictureUrl: String? = null,
    /** `directors[0].name` (revue du 14 septembre 2026, troisième essai réel — vérifié, ex. « Georges Méliès »). */
    val director: String? = null,
)

sealed interface ExternalSearchOutcome {
    data class Success(val candidates: List<ExternalCandidate>) : ExternalSearchOutcome
    data object Unauthenticated : ExternalSearchOutcome
    data object Failed : ExternalSearchOutcome
}

sealed interface ExternalPushOutcome {
    data object Success : ExternalPushOutcome
    data object Unauthenticated : ExternalPushOutcome
    data object Failed : ExternalPushOutcome
}
