package fr.mediatheque.journal.senscritique

import java.time.LocalDate

/** `ExternalRatingService` pour SensCritique : jeton géré par `SensCritiqueAuthProvider`, appels par `SensCritiqueGraphQLClient`. */
class SensCritiqueRatingService(
    private val tokens: SensCritiqueAuthProvider,
    private val graphql: SensCritiqueGraphQLClient,
) : ExternalRatingService {
    override val name: String = "SensCritique"

    override suspend fun search(keywords: String): ExternalSearchOutcome =
        when (val token = tokens.idToken()) {
            is TokenOutcome.Available -> graphql.search(token.idToken, keywords)
            TokenOutcome.Unauthenticated -> ExternalSearchOutcome.Unauthenticated
            // Panne transitoire du renouvellement (important 3) : jamais un `Unauthenticated`, qui
            // effacerait le magasin en aval (`SensCritiqueSync.resolve`) pour une simple coupure.
            TokenOutcome.Unreachable -> ExternalSearchOutcome.Failed
        }

    /**
     * `productRate` puis `productDone` (brief §3) — une suppression ou une note retirée n'appelle
     * jamais cette fonction.
     *
     * `watchedOn` n'est pas encore transmis à `productDone` (important 7 de la revue du
     * 14 septembre 2026, README « SensCritique ») : son argument de date, s'il existe, n'est pas
     * connu (brief) — `DONE_MUTATION`, dans `SensCritiqueGraphQLClient`, n'envoie que `productId`.
     * Le paramètre reste dans cette signature pour le jour où cet argument sera découvert (le
     * premier essai connecté du propriétaire, journalisé par `bin/logs`), plutôt que de devoir
     * changer la forme de `ExternalRatingService` à ce moment-là.
     */
    override suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome {
        val token = when (val t = tokens.idToken()) {
            is TokenOutcome.Available -> t.idToken
            TokenOutcome.Unauthenticated -> return ExternalPushOutcome.Unauthenticated
            TokenOutcome.Unreachable -> return ExternalPushOutcome.Failed
        }
        val rated = graphql.rate(token, productId, rating)
        if (rated !is ExternalPushOutcome.Success) return rated
        return graphql.markDone(token, productId)
    }
}
