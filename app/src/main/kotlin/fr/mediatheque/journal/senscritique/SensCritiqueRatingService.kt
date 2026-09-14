package fr.mediatheque.journal.senscritique

import java.time.LocalDate

/** `ExternalRatingService` pour SensCritique : jeton géré par `SensCritiqueAuthProvider`, appels par `SensCritiqueGraphQLClient`. */
class SensCritiqueRatingService(
    private val tokens: SensCritiqueAuthProvider,
    private val graphql: SensCritiqueGraphQLClient,
) : ExternalRatingService {
    override val name: String = "SensCritique"

    override suspend fun search(keywords: String): ExternalSearchOutcome {
        val token = tokens.idToken() ?: return ExternalSearchOutcome.Unauthenticated
        return graphql.search(token, keywords)
    }

    /** `productRate` puis `productDone` (brief §3) — une suppression ou une note retirée n'appelle jamais cette fonction. */
    override suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome {
        val token = tokens.idToken() ?: return ExternalPushOutcome.Unauthenticated
        val rated = graphql.rate(token, productId, rating)
        if (rated !is ExternalPushOutcome.Success) return rated
        return graphql.markDone(token, productId)
    }
}
