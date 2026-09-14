package fr.mediatheque.journal.senscritique

import java.time.Duration
import java.time.Instant

/** Une marge de sécurité : un jeton encore valide 60 s ne vaut pas la peine d'être réutilisé. */
private val MARGIN = Duration.ofSeconds(60)

data class CachedToken(val idToken: String, val expiresAt: Instant)

sealed interface TokenDecision {
    data class Reuse(val idToken: String) : TokenDecision
    data object Refresh : TokenDecision
}

/**
 * Pure : réutiliser un jeton encore frais, ou en demander un nouveau. Séparée de
 * `SensCritiqueAuthProvider` pour se tester sans horloge réelle ni réseau.
 */
fun decideToken(now: Instant, cached: CachedToken?, margin: Duration = MARGIN): TokenDecision =
    if (cached != null && now.isBefore(cached.expiresAt.minus(margin))) {
        TokenDecision.Reuse(cached.idToken)
    } else {
        TokenDecision.Refresh
    }

/**
 * Ce que vaut la peine de savoir un appelant qui voulait un jeton : l'avoir, ou pas — et si non,
 * pourquoi. La distinction compte en aval (`SensCritiqueRatingService`) : `Unauthenticated` doit
 * déconnecter et mettre la poussée en file « le jeton est refusé » ; `Unreachable` (une panne
 * transitoire du renouvellement) doit seulement mettre la poussée en file « à réessayer », sans
 * jamais toucher au magasin (revue du 14 septembre 2026, important 3) — une confusion des deux
 * déconnecterait le compte à la moindre coupure réseau pendant un renouvellement.
 */
sealed interface TokenOutcome {
    data class Available(val idToken: String) : TokenOutcome
    data object Unauthenticated : TokenOutcome
    data object Unreachable : TokenOutcome
}

/**
 * Le jeton à présenter à l'API GraphQL de SensCritique : un `idToken` frais réutilisé tant qu'il ne
 * touche pas à son expiration, sinon renouvelé via `refreshToken`. Un renouvellement refusé
 * déconnecte (efface `SensCritiqueStore.readAuth()`) — brief : « la décision de jeton ». Une panne
 * transitoire du renouvellement, elle, ne touche pas au magasin (important 3 de la revue).
 */
class SensCritiqueAuthProvider(
    private val authClient: SensCritiqueAuthClient,
    private val store: SensCritiqueStore,
    private val clock: () -> Instant = Instant::now,
) {
    private var cached: CachedToken? = null

    suspend fun idToken(): TokenOutcome {
        val auth = store.readAuth() ?: return TokenOutcome.Unauthenticated
        return when (val decision = decideToken(clock(), cached)) {
            is TokenDecision.Reuse -> TokenOutcome.Available(decision.idToken)
            TokenDecision.Refresh -> refresh(auth)
        }
    }

    private suspend fun refresh(auth: SensCritiqueAuth): TokenOutcome =
        when (val outcome = authClient.refresh(auth.refreshToken)) {
            is RefreshOutcome.Success -> {
                cached = CachedToken(outcome.idToken, clock().plusSeconds(outcome.expiresInSeconds))
                if (outcome.refreshToken != auth.refreshToken) store.writeAuth(auth.copy(refreshToken = outcome.refreshToken))
                TokenOutcome.Available(outcome.idToken)
            }
            RefreshOutcome.Refused -> {
                cached = null
                store.writeAuth(null)
                TokenOutcome.Unauthenticated
            }
            RefreshOutcome.Unreachable -> TokenOutcome.Unreachable
        }
}
