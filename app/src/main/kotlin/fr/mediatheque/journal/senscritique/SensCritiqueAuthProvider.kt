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
 * Le jeton à présenter à l'API GraphQL de SensCritique : un `idToken` frais réutilisé tant qu'il ne
 * touche pas à son expiration, sinon renouvelé via `refreshToken`. Un renouvellement refusé
 * déconnecte (efface `SensCritiqueStore.readAuth()`) — brief : « la décision de jeton ».
 */
class SensCritiqueAuthProvider(
    private val authClient: SensCritiqueAuthClient,
    private val store: SensCritiqueStore,
    private val clock: () -> Instant = Instant::now,
) {
    private var cached: CachedToken? = null

    suspend fun idToken(): String? {
        val auth = store.readAuth() ?: return null
        return when (val decision = decideToken(clock(), cached)) {
            is TokenDecision.Reuse -> decision.idToken
            TokenDecision.Refresh -> refresh(auth)
        }
    }

    private suspend fun refresh(auth: SensCritiqueAuth): String? =
        when (val outcome = authClient.refresh(auth.refreshToken)) {
            is RefreshOutcome.Success -> {
                cached = CachedToken(outcome.idToken, clock().plusSeconds(outcome.expiresInSeconds))
                if (outcome.refreshToken != auth.refreshToken) store.writeAuth(auth.copy(refreshToken = outcome.refreshToken))
                outcome.idToken
            }
            RefreshOutcome.Refused -> {
                cached = null
                store.writeAuth(null)
                null
            }
        }
}
