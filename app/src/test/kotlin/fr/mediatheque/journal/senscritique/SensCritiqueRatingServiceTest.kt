package fr.mediatheque.journal.senscritique

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * `SensCritiqueRatingService` : la partie qui referme la boucle de l'important 3 de la revue du
 * 14 septembre 2026 — un jeton `Unreachable` (panne transitoire du renouvellement) doit devenir un
 * échec « à réessayer » (`Failed`), jamais `Unauthenticated`, qui déconnecterait le compte plus
 * loin dans `SensCritiqueSync.resolve`.
 */
class SensCritiqueRatingServiceTest {
    private val authClient = FakeSensCritiqueAuthClient()
    private val graphql = FakeSensCritiqueGraphQLClient()

    private fun service(store: SensCritiqueStore) = SensCritiqueRatingService(SensCritiqueAuthProvider(authClient, store), graphql)

    @Test
    fun `sans jeton stocke, search et push rendent Unauthenticated sans appeler GraphQL`() = runTest {
        val service = service(InMemorySensCritiqueStore())
        assertEquals(ExternalSearchOutcome.Unauthenticated, service.search("chihiro"))
        assertEquals(ExternalPushOutcome.Unauthenticated, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertTrue(graphql.searchCalls.isEmpty())
        assertTrue(graphql.rateCalls.isEmpty())
    }

    // Mutation : dans `SensCritiqueRatingService.search`/`push`, faire tomber
    // `TokenOutcome.Unreachable` sur la meme branche qu'`Unauthenticated` fait echouer les deux
    // assertions (elles attendent `Failed`).
    @Test
    fun `un renouvellement injoignable rend Failed, jamais Unauthenticated`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        authClient.onRefresh = { RefreshOutcome.Unreachable }
        val service = service(store)

        assertEquals(ExternalSearchOutcome.Failed, service.search("chihiro"))
        assertEquals(ExternalPushOutcome.Failed, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertTrue(graphql.searchCalls.isEmpty())
        assertTrue(graphql.rateCalls.isEmpty())
    }

    @Test
    fun `un jeton disponible appelle GraphQL avec ce jeton`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        authClient.onRefresh = { RefreshOutcome.Success("id-1", "refresh-1", 3600) }
        val service = service(store)

        service.search("chihiro")
        assertEquals(listOf("id-1" to "chihiro"), graphql.searchCalls)

        service.push(42, 8, LocalDate.parse("2026-09-10"))
        assertEquals(listOf(Triple("id-1", 42L, 8)), graphql.rateCalls)
        assertEquals(listOf("id-1" to 42L), graphql.markDoneCalls)
        assertEquals(listOf(Triple("id-1", 42L, "2026-09-10")), graphql.setDateCalls)
    }

    @Test
    fun `push s arrete a productRate si celui-ci echoue, markDone n est jamais appele`() = runTest {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        authClient.onRefresh = { RefreshOutcome.Success("id-1", "refresh-1", 3600) }
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
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        authClient.onRefresh = { RefreshOutcome.Success("id-1", "refresh-1", 3600) }
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
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("refresh-1", "TheofB")) }
        authClient.onRefresh = { RefreshOutcome.Success("id-1", "refresh-1", 3600) }
        graphql.onSetDate = { _, _, _ -> ExternalPushOutcome.Failed }
        val service = service(store)

        assertEquals(ExternalPushOutcome.Success, service.push(42, 8, LocalDate.parse("2026-09-10")))
        assertEquals(listOf(Triple("id-1", 42L, "2026-09-10")), graphql.setDateCalls)
    }
}
