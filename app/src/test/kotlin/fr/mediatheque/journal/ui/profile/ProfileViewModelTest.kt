package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.Counts
import fr.mediatheque.journal.api.dto.Dashboard
import fr.mediatheque.journal.api.dto.Periods
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.Totals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// `StandardTestDispatcher` (pas l'`UnconfinedTestDispatcher` par defaut) : les deux
// nouveaux tests ci-dessous ont besoin de controler quand les coroutines avancent, pour
// observer un chargement encore « en vol » avant d'en lancer un second (jumeau de
// `FilmsViewModelTest`, revue du tour de correction 1). Chaque `retry()` est desormais
// explicite : `ProfileViewModel` n'a plus d'`init { load() }`, seul l'ecran declenche le
// premier chargement (via son `LaunchedEffect(Unit)`), que ces tests miment.
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    @Test
    fun `lit les deux chiffres sous dashboard periods`() = runTest(dispatcher) {
        api.onStats = {
            StatsResponse(Dashboard(Periods(
                year = Totals(Counts(mapOf("movie" to 12, "book" to 3))),
                all = Totals(Counts(mapOf("movie" to 87))),
            )))
        }
        val vm = ProfileViewModel(api) { expire++ }
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(87, vm.ui.value.total)
        assertEquals(12, vm.ui.value.thisYear)
    }

    @Test
    fun `le message du back sur une panne, et Reessayer`() = runTest(dispatcher) {
        api.onStats = { throw FakeJournalApi.network() }
        val vm = ProfileViewModel(api) { expire++ }
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals("L’API est injoignable.", vm.ui.value.error?.message)
        api.onStats = { StatsResponse(Dashboard(Periods(Totals(Counts(mapOf())), Totals(Counts(mapOf()))))) }
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.ui.value.total)
    }

    @Test
    fun `un 401 previent la session`() = runTest(dispatcher) {
        api.onStats = { throw FakeJournalApi.unauthorized() }
        val vm = ProfileViewModel(api) { expire++ }
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
    }

    // Sans `init { load() }`, la seule construction du `ViewModel` n'appelle plus rien :
    // c'est `retry()` (le `LaunchedEffect` de l'ecran) qui ouvre. Mutation : remettre
    // `init { load() }` fait echouer la premiere assertion (`api.calls` ne serait plus
    // vide avant tout appel a `retry()`).
    @Test
    fun `rien ne se charge avant que l ecran ne le demande`() = runTest(dispatcher) {
        api.onStats = { StatsResponse(Dashboard(Periods(Totals(Counts(mapOf())), Totals(Counts(mapOf()))))) }
        val vm = ProfileViewModel(api) { expire++ }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), api.calls)

        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("stats"), api.calls)
    }

    // Un chargement bloque (la `porte`) puis deux `retry()` rapproches : le premier, en
    // vol, est annule ; le second, encore en file, l'est aussi ; seul le troisieme aboutit.
    // Mutation : retirer `enCours?.cancel()` dans `load()` fait tomber l'egalite (3 appels
    // au lieu de 2).
    @Test
    fun `deux Reessayer rapproches n appellent stats qu une fois de plus`() = runTest(dispatcher) {
        val porte = CompletableDeferred<StatsResponse>()
        api.onStats = { porte.await() }
        val vm = ProfileViewModel(api) { expire++ }
        vm.retry()
        testScheduler.advanceUntilIdle()

        vm.retry()
        vm.retry()
        porte.complete(StatsResponse(Dashboard(Periods(Totals(Counts(mapOf("movie" to 5))), Totals(Counts(mapOf("movie" to 9)))))))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("stats", "stats"), api.calls)
        assertEquals(9, vm.ui.value.total)
    }
}
