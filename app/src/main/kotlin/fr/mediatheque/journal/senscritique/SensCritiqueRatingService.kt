package fr.mediatheque.journal.senscritique

import java.time.Instant
import java.time.LocalDate

/**
 * `ExternalRatingService` pour SensCritique : plus de jeton à gérer (brief du 14 septembre 2026,
 * après un premier essai réel — l'API GraphQL n'accepte que son propre `cookieRef`, relu tel quel
 * depuis `store` à chaque appel, jamais renouvelé). Seule l'expiration se vérifie, et seulement
 * avant une poussée (§3 du brief) : la relire à chaque recherche coûterait un appel réseau que le
 * refus de session (`ExternalSearchOutcome.Unauthenticated`, détecté par `SensCritiqueGraphQLClient`)
 * couvre déjà.
 */
class SensCritiqueRatingService(
    private val store: SensCritiqueStore,
    private val graphql: SensCritiqueGraphQLClient,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
    private val clock: () -> Instant = Instant::now,
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
     * Avant l'appel, `dateExpiration` est comparée à l'horloge (brief §3) : dépassée, la poussée ne
     * part même pas — même traitement qu'un refus de session (déconnexion, `Unauthenticated`), sans
     * gaspiller un aller-retour dont le résultat est déjà connu.
     */
    override suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome {
        val auth = store.readAuth() ?: return ExternalPushOutcome.Unauthenticated
        if (estExpiree(auth.dateExpiration, clock())) {
            logger.d("cookieRef SensCritique expire avant la poussee, deconnexion sans appel reseau")
            store.writeAuth(null)
            return ExternalPushOutcome.Unauthenticated
        }
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

/**
 * `dateExpiration` est gardée telle que SensCritique la rend (brief §2), une chaîne ISO — jamais
 * garanti d'être un `Instant` strict (`2026-10-14T10:00:00Z`) : `OffsetDateTime` couvre aussi une
 * forme avec décalage explicite. Illisible → jamais expirée (échoue ouvert, important : voir
 * commentaire de `push` ci-dessus) — le refus de session, lui, reste détecté par l'appel réseau
 * réel si cette lecture s'est trompée.
 */
fun estExpiree(dateExpiration: String, maintenant: Instant): Boolean {
    val instant = runCatching { Instant.parse(dateExpiration) }
        .recoverCatching { java.time.OffsetDateTime.parse(dateExpiration).toInstant() }
        .getOrNull()
        ?: return false
    return maintenant.isAfter(instant)
}
