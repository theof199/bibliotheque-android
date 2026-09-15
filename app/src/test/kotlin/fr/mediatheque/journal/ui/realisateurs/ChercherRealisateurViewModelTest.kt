package fr.mediatheque.journal.ui.realisateurs

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.PersonneResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChercherRealisateurViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private val kubrick = PersonneResult(240, "Stanley Kubrick", null)

    private fun vm() = ChercherRealisateurViewModel(api) { expire++ }

    // 400 ms de silence avant la requête (brief du 15 septembre 2026). Mutation : retirer le
    // `debounce`, ou le régler sur les 300 ms de la recherche de films, fait partir l'appel avant
    // la première assertion.
    @Test
    fun `une frappe ne part qu apres quatre cents millisecondes de silence`() = runTest(dispatcher) {
        api.onChercherPersonnes = { listOf(kubrick) }
        val vm = vm()

        vm.onQueryChange("Kubrick")
        testScheduler.advanceTimeBy(399)
        testScheduler.runCurrent()
        assertEquals(emptyList<String>(), api.calls)

        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        assertEquals(listOf("chercherPersonnes Kubrick"), api.calls)
        assertEquals(listOf(kubrick), vm.ui.value.results)
        assertEquals("Kubrick", vm.ui.value.searched)
    }

    // Le back refuse un `q` vide (`400`) : une saisie effacée ne doit rien envoyer — et les
    // résultats d'avant s'en vont avec elle, plutôt que de rester sous un champ vide. Mutation :
    // envoyer la saisie telle quelle fait apparaître un appel dans `api.calls`.
    @Test
    fun `une saisie vide ou faite d espaces n envoie rien et vide les resultats`() = runTest(dispatcher) {
        api.onChercherPersonnes = { listOf(kubrick) }
        val vm = vm()

        vm.onQueryChange("Kubrick")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(kubrick), vm.ui.value.results)

        vm.onQueryChange("   ")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("chercherPersonnes Kubrick"), api.calls)
        assertEquals(emptyList<PersonneResult>(), vm.ui.value.results)
        assertEquals("", vm.ui.value.searched)
    }

    // Le message du back, tel quel, dans son bloc — et rien qui déconnecte. Mutation : traiter
    // toute erreur comme une expiration de session ferait passer `expire` à 1.
    @Test
    fun `une panne reseau se montre sans deconnecter`() = runTest(dispatcher) {
        api.onChercherPersonnes = { throw FakeJournalApi.network() }
        val vm = vm()

        vm.onQueryChange("Kubrick")
        testScheduler.advanceUntilIdle()

        assertEquals("L’API est injoignable.", vm.ui.value.error?.message)
        assertEquals(0, expire)
    }

    @Test
    fun `un 401 previent la session sans afficher d erreur`() = runTest(dispatcher) {
        api.onChercherPersonnes = { throw FakeJournalApi.unauthorized() }
        val vm = vm()

        vm.onQueryChange("Kubrick")
        testScheduler.advanceUntilIdle()

        assertEquals(1, expire)
        assertNull(vm.ui.value.error)
    }

    // Jumeau de `SearchViewModel.reset()` : l'instance est indexée sur l'Activité, et une
    // seconde ouverture de l'écran doit repartir d'un champ vide. Mutation : ne remettre que
    // `_ui` sans remettre `query` laisse la requête d'avant dans le flux — l'écran a l'air vide,
    // mais `distinctUntilChanged` avale la re-frappe du même nom et la dernière assertion tombe
    // (un seul appel au lieu de deux).
    @Test
    fun `reset vide la saisie, les resultats, et la requete du flux`() = runTest(dispatcher) {
        api.onChercherPersonnes = { listOf(kubrick) }
        val vm = vm()

        vm.onQueryChange("Kubrick")
        testScheduler.advanceUntilIdle()

        vm.reset()
        testScheduler.advanceUntilIdle()

        assertEquals("", vm.ui.value.query)
        assertTrue(vm.ui.value.results.isEmpty())
        assertEquals(listOf("chercherPersonnes Kubrick"), api.calls)

        vm.onQueryChange("Kubrick")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("chercherPersonnes Kubrick", "chercherPersonnes Kubrick"), api.calls)
    }
}
