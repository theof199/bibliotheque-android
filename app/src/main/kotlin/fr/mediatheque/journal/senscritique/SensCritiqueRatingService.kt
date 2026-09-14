package fr.mediatheque.journal.senscritique

import java.time.LocalDate

/** `ExternalRatingService` pour SensCritique : jeton géré par `SensCritiqueAuthProvider`, appels par `SensCritiqueGraphQLClient`. */
class SensCritiqueRatingService(
    private val tokens: SensCritiqueAuthProvider,
    private val graphql: SensCritiqueGraphQLClient,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
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
     * `productRate`, puis `productDone`, puis `setProductDateDone` — dans cet ordre (revue du
     * 14 septembre 2026, troisième essai réel, point 5) : `productDone` marque « vu », la date se
     * pose ensuite, jamais avant. Un échec de `productRate` ou `productDone` arrête tout (la note ou
     * le « vu » ne sont pas posés, la poussée échoue). Un échec de `setProductDateDone` *seul* ne
     * défait pas la poussée (point 4) : la note et le « vu » sont déjà acquis, seule la date manque
     * — journalisé (par `KtorSensCritiqueGraphQLClient`, jamais le jeton) mais la poussée compte
     * comme réussie.
     */
    override suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome {
        val token = when (val t = tokens.idToken()) {
            is TokenOutcome.Available -> t.idToken
            TokenOutcome.Unauthenticated -> return ExternalPushOutcome.Unauthenticated
            TokenOutcome.Unreachable -> return ExternalPushOutcome.Failed
        }
        val rated = graphql.rate(token, productId, rating)
        if (rated !is ExternalPushOutcome.Success) return rated
        val marque = graphql.markDone(token, productId)
        if (marque !is ExternalPushOutcome.Success) return marque

        if (graphql.setDate(token, productId, watchedOn.toString()) !is ExternalPushOutcome.Success) {
            logger.d("date non posee pour $productId (setProductDateDone a echoue) : poussee comptee reussie quand meme")
        }
        return ExternalPushOutcome.Success
    }
}
