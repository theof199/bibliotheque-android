package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.TicketUtiliseResponse
import fr.mediatheque.journal.api.dto.TicketVoyage
import fr.mediatheque.journal.api.dto.VoyageTicketsResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Le portefeuille de tickets (décision 3 du brief du 21 septembre 2026, « le ticket ») : charge
 * ses propres données, triées, et encaisse un ticket sur demande.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PortefeuilleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    @Test
    fun `refresh charge et trie le portefeuille`() = runTest(dispatcher) {
        api.onVoyageTickets = {
            VoyageTicketsResponse(
                listOf(
                    TicketVoyage(1943, "Motif 1943", "2026-09-01T00:00:00.000Z", utilise_le = null),
                    TicketVoyage(1938, "Motif 1938", "2026-01-01T00:00:00.000Z", utilise_le = "2026-06-04T00:00:00.000Z"),
                ),
            )
        }
        val vm = PortefeuilleViewModel(api) { expire++ }

        vm.refresh()
        runCurrent()

        assertEquals(listOf(1943, 1938), vm.ui.value.tickets?.map { it.annee })
    }

    @Test
    fun `un 401 sur le portefeuille previent la session`() = runTest(dispatcher) {
        api.onVoyageTickets = { throw FakeJournalApi.unauthorized() }
        val vm = PortefeuilleViewModel(api) { expire++ }

        vm.refresh()
        runCurrent()

        assertEquals(1, expire)
    }

    @Test
    fun `utiliser encaisse le ticket, recharge le portefeuille puis appelle le rappel`() = runTest(dispatcher) {
        var encaisse = false
        api.onUtiliserTicket = { encaisse = true; TicketUtiliseResponse(annee_en_cours = 1943) }
        api.onVoyageTickets = {
            VoyageTicketsResponse(
                listOf(TicketVoyage(1943, "Motif 1943", "2026-09-01T00:00:00.000Z", utilise_le = if (encaisse) "2026-09-21T00:00:00.000Z" else null)),
            )
        }
        val vm = PortefeuilleViewModel(api) { expire++ }
        vm.refresh()
        runCurrent()
        assertEquals(listOf(null), vm.ui.value.tickets?.map { it.utiliseLe })

        var rappelee = false
        vm.utiliser(1943) { rappelee = true }
        runCurrent()

        assertEquals(listOf("2026-09-21T00:00:00.000Z"), vm.ui.value.tickets?.map { it.utiliseLe })
        assertTrue(rappelee)
        assertEquals(listOf("utiliserTicket 1943"), api.calls.filter { it.startsWith("utiliserTicket") })
    }

    @Test
    fun `utiliser en echec n'appelle pas le rappel, ni ne recharge`() = runTest(dispatcher) {
        api.onUtiliserTicket = { throw ApiError("HTTP_500", "Erreur imprevue.", retryable = true, status = 500) }
        var appelsTickets = 0
        api.onVoyageTickets = { appelsTickets++; VoyageTicketsResponse() }
        val vm = PortefeuilleViewModel(api) { expire++ }
        vm.refresh()
        runCurrent()
        val appelsAvant = appelsTickets

        var rappelee = false
        vm.utiliser(1943) { rappelee = true }
        runCurrent()

        assertFalse(rappelee)
        assertEquals(appelsAvant, appelsTickets)
    }
}
