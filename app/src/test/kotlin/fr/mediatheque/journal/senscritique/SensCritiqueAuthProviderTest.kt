package fr.mediatheque.journal.senscritique

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * La décision de jeton (brief du 14 septembre 2026) : un `idToken` frais réutilisé, expiré →
 * renouvelé, renouvellement refusé → déconnexion. Renouvellement injoignable (important 3 de la
 * revue du même jour) → ni l'un ni l'autre : le magasin n'est pas touché.
 */
class SensCritiqueAuthProviderTest {
    private var maintenant = Instant.parse("2026-09-14T10:00:00Z")
    private val client = FakeSensCritiqueAuthClient()

    // --- decideToken (pure) ---

    @Test
    fun `sans jeton en cache, demande un renouvellement`() {
        assertEquals(TokenDecision.Refresh, decideToken(maintenant, cached = null))
    }

    @Test
    fun `un jeton en cache encore loin de sa marge d expiration est reutilise`() {
        val cache = CachedToken("id-1", maintenant.plusSeconds(3600))
        assertEquals(TokenDecision.Reuse("id-1"), decideToken(maintenant, cache))
    }

    // Mutation : inverser `isBefore` en `!isBefore`, ou retirer la marge (comparer a `expiresAt`
    // nu), fait passer cette assertion au vert alors qu'elle doit rester en `Refresh`.
    @Test
    fun `un jeton dans sa marge d expiration n est plus reutilise`() {
        val cache = CachedToken("id-1", maintenant.plusSeconds(30)) // sous la marge de 60 s
        assertEquals(TokenDecision.Refresh, decideToken(maintenant, cache))
    }

    // --- SensCritiqueAuthProvider (impur, avec un faux client et une horloge controlee) ---

    @Test
    fun `sans jeton stocke, aucun appel reseau`() = runTest {
        val provider = SensCritiqueAuthProvider(client, InMemorySensCritiqueStore()) { maintenant }
        assertEquals(TokenOutcome.Unauthenticated, provider.idToken())
        assertTrue(client.refreshCalls.isEmpty())
    }

    @Test
    fun `un jeton frais est reutilise — un seul renouvellement pour deux appels rapproches`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        client.onRefresh = { RefreshOutcome.Success("id-1", "refresh-1", expiresInSeconds = 3600) }
        val provider = SensCritiqueAuthProvider(client, store) { maintenant }

        assertEquals(TokenOutcome.Available("id-1"), provider.idToken())
        maintenant = maintenant.plusSeconds(60)
        assertEquals(TokenOutcome.Available("id-1"), provider.idToken())
        assertEquals(1, client.refreshCalls.size)
    }

    // Mutation : ne jamais mettre a jour `cached` apres un renouvellement reussi fait echouer la
    // deuxieme assertion (deux appels reseau au lieu d'un, le second refuse toujours). Ne jamais
    // relire `store.readAuth()` fait echouer la premiere (jamais de jeton du tout).
    @Test
    fun `un jeton expire est renouvele`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        client.onRefresh = { RefreshOutcome.Success("id-1", "refresh-1", expiresInSeconds = 3600) }
        val provider = SensCritiqueAuthProvider(client, store) { maintenant }
        assertEquals(TokenOutcome.Available("id-1"), provider.idToken())

        maintenant = maintenant.plusSeconds(3600)
        client.onRefresh = { RefreshOutcome.Success("id-2", "refresh-1", expiresInSeconds = 3600) }
        assertEquals(TokenOutcome.Available("id-2"), provider.idToken())
        assertEquals(2, client.refreshCalls.size)
    }

    // Mutation : ne pas appeler `store.writeAuth(null)` sur un `Refused` fait echouer la deuxieme
    // assertion (le magasin garderait l'ancien `refreshToken`, jamais deconnecte). Rendre
    // `Unauthenticated` alors que le resultat devrait etre `Available`/`Unreachable` fait echouer
    // la premiere.
    @Test
    fun `un renouvellement refuse deconnecte`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        client.onRefresh = { RefreshOutcome.Refused }
        val provider = SensCritiqueAuthProvider(client, store) { maintenant }

        assertEquals(TokenOutcome.Unauthenticated, provider.idToken())
        assertNull(store.readAuth())
    }

    // Important 3 de la revue du 14 septembre 2026 : une panne transitoire du renouvellement (reseau,
    // 5xx, corps illisible — deja triee en `RefreshOutcome.Unreachable` par `FirebaseSensCritiqueAuthClient`)
    // ne doit jamais deconnecter. Mutation : router `RefreshOutcome.Unreachable` sur la meme branche
    // que `Refused` (donc `store.writeAuth(null)`) fait echouer la deuxieme assertion ; rendre
    // `TokenOutcome.Unauthenticated` au lieu d'`Unreachable` fait echouer la premiere.
    @Test
    fun `un renouvellement injoignable ne deconnecte pas`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        client.onRefresh = { RefreshOutcome.Unreachable }
        val provider = SensCritiqueAuthProvider(client, store) { maintenant }

        assertEquals(TokenOutcome.Unreachable, provider.idToken())
        assertEquals(SensCritiqueAuth("refresh-1", "TheofB"), store.readAuth())
    }

    // Un `refreshToken` renvoye different (rotation) doit remplacer l'ancien dans le magasin —
    // sinon le prochain renouvellement, apres redemarrage de l'appli (nouveau `SensCritiqueAuthProvider`,
    // cache reparti a zero), presenterait un jeton perime a SensCritique.
    @Test
    fun `un refreshToken renouvele (rotation) remplace l ancien dans le magasin`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        client.onRefresh = { RefreshOutcome.Success("id-1", "refresh-2", expiresInSeconds = 3600) }
        val provider = SensCritiqueAuthProvider(client, store) { maintenant }
        provider.idToken()
        assertEquals("refresh-2", store.readAuth()?.refreshToken)
    }
}
