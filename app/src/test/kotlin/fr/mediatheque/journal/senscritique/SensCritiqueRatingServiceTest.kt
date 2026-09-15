package fr.mediatheque.journal.senscritique

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * `SensCritiqueRatingService` : plus de renouvellement (brief du 14 septembre 2026, après un
 * premier essai réel) — le `cookieRef` se relit tel quel depuis `store`. Depuis le correctif du
 * 15 septembre 2026, `dateExpiration` n'est plus comparée à l'horloge avant une poussée (échoue
 * ouvert) : elle part toujours, seul un refus de session rendu par GraphQL déconnecte.
 */
class SensCritiqueRatingServiceTest {
    private val graphql = FakeSensCritiqueGraphQLClient()
    private val uneDateExpiration = "2026-10-14T10:00:00Z"

    private fun service(store: SensCritiqueStore) = SensCritiqueRatingService(store, graphql)

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
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", uneDateExpiration, "TheofB")) }
        val service = service(store)

        service.search("chihiro")
        assertEquals(listOf("cookie-1" to "chihiro"), graphql.searchCalls)

        service.push(42, 8, LocalDate.parse("2026-09-10"))
        assertEquals(listOf(Triple("cookie-1", 42L, 8)), graphql.rateCalls)
        assertEquals(listOf("cookie-1" to 42L), graphql.markDoneCalls)
        assertEquals(listOf(Triple("cookie-1", 42L, "2026-09-10")), graphql.setDateCalls)
    }

    // Correctif du 15 septembre 2026 (echoue ouvert) : une expiration locale depassee ne bloque
    // plus la poussee ni ne deconnecte — elle part quand meme en reseau, exactement comme une
    // expiration encore lointaine. Mutation : reintroduire une comparaison de `dateExpiration` a
    // l'horloge avant l'appel ferait echouer la premiere assertion (rateCalls resterait vide, le
    // resultat serait Unauthenticated) ou la seconde (le magasin serait deconnecte a tort).
    @Test
    fun `une expiration locale deja passee n empeche pas la poussee, l appel part quand meme`() = runTest {
        val expireeLocalement = "2026-09-14T09:59:59Z" // une date passee, jamais comparee a l horloge desormais
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", expireeLocalement, "TheofB")) }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Success, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertEquals(listOf(Triple("cookie-1", 42L, 8)), graphql.rateCalls)
        assertEquals("le magasin reste connecte : rien n a refuse la session", SensCritiqueAuth("cookie-1", expireeLocalement, "TheofB"), store.readAuth())
    }

    @Test
    fun `push s arrete a productRate si celui-ci echoue, markDone n est jamais appele`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", uneDateExpiration, "TheofB")) }
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
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", uneDateExpiration, "TheofB")) }
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
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", uneDateExpiration, "TheofB")) }
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
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", uneDateExpiration, "TheofB")) }
        graphql.onRate = { _, _, _ -> ExternalPushOutcome.Unauthenticated }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Unauthenticated, service.push(42, 8, LocalDate.parse("2026-09-10")))
        // Le service lui-meme ne deconnecte pas ce cas (ce n'est pas l'expiration locale) : c'est
        // `SensCritiqueSync`, en aval, qui le fait — voir `SensCritiqueSyncTest`.
        assertEquals(SensCritiqueAuth("cookie-1", uneDateExpiration, "TheofB"), store.readAuth())
    }
}
