package fr.mediatheque.journal.senscritique

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * `SensCritiqueRatingService` : plus de renouvellement (brief du 14 septembre 2026, après un
 * premier essai réel) — le `cookieRef` se relit tel quel depuis `store`, et seule l'expiration se
 * vérifie, avant une poussée, sans appel réseau.
 */
class SensCritiqueRatingServiceTest {
    private val graphql = FakeSensCritiqueGraphQLClient()
    private val maintenant = Instant.parse("2026-09-14T10:00:00Z")
    private val nonExpiree = "2026-10-14T10:00:00Z"

    private fun service(store: SensCritiqueStore) =
        SensCritiqueRatingService(store, graphql, clock = { maintenant })

    @Test
    fun `sans jeton stocke, search et push rendent Unauthenticated sans appeler GraphQL`() = runTest {
        val service = service(InMemorySensCritiqueStore())
        assertEquals(ExternalSearchOutcome.Unauthenticated, service.search("chihiro"))
        assertEquals(ExternalPushOutcome.Unauthenticated, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertTrue(graphql.searchCalls.isEmpty())
        assertTrue(graphql.rateCalls.isEmpty())
    }

    @Test
    fun `un jeton disponible appelle GraphQL avec ce cookieRef`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", nonExpiree, "TheofB")) }
        val service = service(store)

        service.search("chihiro")
        assertEquals(listOf("cookie-1" to "chihiro"), graphql.searchCalls)

        service.push(42, 8, LocalDate.parse("2026-09-10"))
        assertEquals(listOf(Triple("cookie-1", 42L, 8)), graphql.rateCalls)
        assertEquals(listOf("cookie-1" to 42L), graphql.markDoneCalls)
        assertEquals(listOf(Triple("cookie-1", 42L, "2026-09-10")), graphql.setDateCalls)
    }

    // Le coeur du brief §3 : une expiration passee est traitee comme un refus, deconnexion
    // comprise, avant meme d'essayer le reseau. Mutation : retirer la comparaison `estExpiree` (ou
    // inverser son sens) fait echouer soit cette assertion (la poussee partirait quand meme en
    // reseau), soit la suivante (le magasin resterait connecte).
    @Test
    fun `une expiration deja passee deconnecte avant une poussee, sans appel reseau`() = runTest {
        val expiree = "2026-09-14T09:59:59Z" // une seconde avant `maintenant`
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", expiree, "TheofB")) }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Unauthenticated, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertTrue("aucun appel reseau : l'expiration se lit localement", graphql.rateCalls.isEmpty())
        assertNull("le magasin doit avoir ete deconnecte", store.readAuth())
    }

    // Une expiration encore a venir, elle, ne deconnecte rien et laisse la poussee partir. Borne :
    // une expiration exactement egale a l'horloge n'est pas encore consideree passee (mutation :
    // comparer par `!isBefore` — plutot que `isAfter` — ferait echouer cette assertion, l'egalite
    // deviendrait un refus).
    @Test
    fun `une expiration pas encore atteinte, y compris a l egalite exacte, laisse la poussee partir`() = runTest {
        val pasEncoreExpiree = "2026-09-14T10:00:00Z" // exactement `maintenant`
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", pasEncoreExpiree, "TheofB")) }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Success, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertEquals(SensCritiqueAuth("cookie-1", pasEncoreExpiree, "TheofB"), store.readAuth())
    }

    @Test
    fun `push s arrete a productRate si celui-ci echoue, markDone n est jamais appele`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", nonExpiree, "TheofB")) }
        graphql.onRate = { _, _, _ -> ExternalPushOutcome.Failed }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Failed, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertTrue(graphql.markDoneCalls.isEmpty())
    }

    // Mutation : arreter push() a markDone (ne jamais appeler setDate) fait echouer la premiere
    // assertion du test precedent (setDateCalls vide) — celui-ci verifie en plus que markDone en
    // echec arrete tout avant setDate, jumeau du test productRate ci-dessus.
    @Test
    fun `push s arrete a productDone si celui-ci echoue, setDate n est jamais appele`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", nonExpiree, "TheofB")) }
        graphql.onMarkDone = { _, _ -> ExternalPushOutcome.Failed }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Failed, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertTrue(graphql.setDateCalls.isEmpty())
    }

    // Point 4 de la revue du 14 septembre 2026 : un echec de setProductDateDone seul ne defait pas
    // la poussee — note et « vu » sont deja acquis. Mutation : propager l'echec de setDate en
    // `ExternalPushOutcome.Failed` fait echouer cette assertion (elle attend `Success`).
    @Test
    fun `un echec de setDate seul ne fait pas echouer la poussee`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", nonExpiree, "TheofB")) }
        graphql.onSetDate = { _, _, _ -> ExternalPushOutcome.Failed }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Success, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertEquals(listOf(Triple("cookie-1", 42L, "2026-09-10")), graphql.setDateCalls)
    }

    // Un refus de session rendu par GraphQL (code de refus, brief §3) deconnecte tout autant qu'une
    // expiration locale — deja garanti par `SensCritiqueSync.pushAndRecord` (`store.writeAuth(null)`
    // sur `ExternalPushOutcome.Unauthenticated`), prouve ici au niveau du service : `push` transmet
    // fidelement l'issue de `graphql.rate` sans la transformer.
    @Test
    fun `un refus de session rendu par GraphQL rend Unauthenticated, transmis tel quel`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", nonExpiree, "TheofB")) }
        graphql.onRate = { _, _, _ -> ExternalPushOutcome.Unauthenticated }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Unauthenticated, service.push(42, 8, LocalDate.parse("2026-09-10")))
        // Le service lui-meme ne deconnecte pas ce cas (ce n'est pas l'expiration locale) : c'est
        // `SensCritiqueSync`, en aval, qui le fait — voir `SensCritiqueSyncTest`.
        assertEquals(SensCritiqueAuth("cookie-1", nonExpiree, "TheofB"), store.readAuth())
    }
}
