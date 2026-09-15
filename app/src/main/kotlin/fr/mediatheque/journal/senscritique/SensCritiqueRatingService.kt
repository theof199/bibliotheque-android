package fr.mediatheque.journal.senscritique

import java.time.LocalDate

/**
 * `ExternalRatingService` pour SensCritique : plus de jeton à gérer (brief du 14 septembre 2026,
 * après un premier essai réel — l'API GraphQL n'accepte que son propre `cookieRef`, relu tel quel
 * depuis `store` à chaque appel, jamais renouvelé). L'expiration locale (`dateExpiration`, rendue
 * par SensCritique) n'est plus comparée à l'horloge avant une poussée (correctif du 15 septembre
 * 2026, échoue ouvert : rien ne garantit sa fiabilité, ni qu'elle soit longue — une comparaison
 * locale erronée déconnectait l'utilisateur à tort avant même d'avoir essayé le réseau). Seul un
 * vrai refus de session, rendu par GraphQL (`ExternalSearchOutcome.Unauthenticated` /
 * `ExternalPushOutcome.Unauthenticated`, détectés par `SensCritiqueGraphQLClient`), déconnecte.
 */
class SensCritiqueRatingService(
    private val store: SensCritiqueStore,
    private val graphql: SensCritiqueGraphQLClient,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
) : ExternalRatingService {
    override val name: String = "SensCritique"

    override suspend fun search(keywords: String): ExternalSearchOutcome {
        val cookieRef = store.readAuth()?.cookieRef ?: return ExternalSearchOutcome.Unauthenticated
        return graphql.search(cookieRef, keywords)
    }

    /**
     * `productRate`, puis `productDone`, puis `setProductDateDone` — dans cet ordre (revue du
     * 14 septembre 2026, troisième essai réel, point 5) : `productDone` marque « vu », la date se
     * pose ensuite, jamais avant. Un échec de `productRate` ou `productDone` arrête tout (la note ou
     * le « vu » ne sont pas posés, la poussée échoue). Un échec de `setProductDateDone` *seul* ne
     * défait pas la poussée (point 4) : la note et le « vu » sont déjà acquis, seule la date manque
     * — journalisé (par `KtorSensCritiqueGraphQLClient`, jamais le jeton) mais la poussée compte
     * comme réussie.
     *
     * La poussée part toujours (correctif du 15 septembre 2026, voir la doc de la classe) : plus de
     * vérification de `dateExpiration` avant l'appel.
     */
    override suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome {
        val auth = store.readAuth() ?: return ExternalPushOutcome.Unauthenticated
        val cookieRef = auth.cookieRef

        val rated = graphql.rate(cookieRef, productId, rating)
        if (rated !is ExternalPushOutcome.Success) return rated
        val marque = graphql.markDone(cookieRef, productId)
        if (marque !is ExternalPushOutcome.Success) return marque

        if (graphql.setDate(cookieRef, productId, watchedOn.toString()) !is ExternalPushOutcome.Success) {
            logger.d("date non posee pour $productId (setProductDateDone a echoue) : poussee comptee reussie quand meme")
        }
        return ExternalPushOutcome.Success
    }
}
