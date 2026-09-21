package fr.mediatheque.journal.ui.realisateur

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `RealisateurViewModel` (brief du 21 septembre 2026, « la page réalisateur ») : suivre et retirer
 * partagent un seul chemin avec les autres écritures de cette page — l'appel, puis un rechargement
 * complet qui fait apparaître `suivi` à jour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealisateurViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private fun vm() = RealisateurViewModel(525, api, { expire++ })

    // « Suivre » : le `POST`, puis la page se recharge — c'est ce rechargement qui fait passer
    // `suivi` à vrai, le back seul en décidant. Mutation : ne pas recharger après le `POST` laisse
    // `suivi` à faux.
    @Test
    fun `suivre appelle le back puis recharge la page, suivi passe a vrai`() = runTest(dispatcher) {
        var suivi = false
        api.onPageRealisateur = { id -> FakeJournalApi.pageRealisateur(id, "Christopher Nolan", suivi = suivi) }
        api.onSuivreRealisateur = { id -> suivi = true; FakeJournalApi.realisateur(id, "Christopher Nolan") }

        val vm = vm()
        vm.charger()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, (vm.ui.value.etat as EtatPageRealisateur.Pret).page.suivi)

        vm.suivre()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("suivreRealisateur 525"), api.calls.filter { it.startsWith("suivreRealisateur") })
        assertEquals(true, (vm.ui.value.etat as EtatPageRealisateur.Pret).page.suivi)
    }

    // « Suivi », un tap le retire : le `DELETE`, puis la page se recharge — jumeau de `suivre`,
    // l'autre sens.
    @Test
    fun `retirer appelle le back puis recharge la page, suivi passe a faux`() = runTest(dispatcher) {
        var suivi = true
        api.onPageRealisateur = { id -> FakeJournalApi.pageRealisateur(id, "Christopher Nolan", suivi = suivi) }
        api.onRetirerRealisateur = { suivi = false }

        val vm = vm()
        vm.charger()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, (vm.ui.value.etat as EtatPageRealisateur.Pret).page.suivi)

        vm.retirer()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("retirerRealisateur 525"), api.calls.filter { it.startsWith("retirerRealisateur") })
        assertEquals(false, (vm.ui.value.etat as EtatPageRealisateur.Pret).page.suivi)
    }

    // Un échec de « Suivre » : bandeau avec le message du back, et rien d'autre ne bouge — pas de
    // second appel à la page. Mutation : rechargerait quand même (l'appel hors du `catch`) ferait
    // apparaître un second `pageRealisateur` dans `api.calls`.
    @Test
    fun `un echec de suivre annonce un bandeau, rien ne recharge`() = runTest(dispatcher) {
        api.onPageRealisateur = { id -> FakeJournalApi.pageRealisateur(id, "Christopher Nolan") }
        api.onSuivreRealisateur = { throw FakeJournalApi.network() }

        val recus = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.messages.collect { recus += it } }
        vm.charger()
        dispatcher.scheduler.advanceUntilIdle()

        vm.suivre()
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("L’API est injoignable."), recus)
        assertEquals(listOf("pageRealisateur 525"), api.calls.filter { it.startsWith("pageRealisateur") })
    }

    @Test
    fun `un 401 sur suivre previent la session`() = runTest(dispatcher) {
        api.onPageRealisateur = { id -> FakeJournalApi.pageRealisateur(id, "Christopher Nolan") }
        api.onSuivreRealisateur = { throw FakeJournalApi.unauthorized() }

        val vm = vm()
        vm.charger()
        dispatcher.scheduler.advanceUntilIdle()

        vm.suivre()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, expire)
    }

    // Une page indisponible : `EtatPageRealisateur.Indisponible`, pas d'exception qui remonte.
    @Test
    fun `charger dit indisponible sur une panne`() = runTest(dispatcher) {
        api.onPageRealisateur = { throw FakeJournalApi.network() }
        val vm = vm()
        vm.charger()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.etat is EtatPageRealisateur.Indisponible)
    }
}
