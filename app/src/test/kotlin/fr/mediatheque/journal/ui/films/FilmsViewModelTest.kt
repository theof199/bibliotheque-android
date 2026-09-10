package fr.mediatheque.journal.ui.films

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.JournalResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilmsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private fun page(vararg dates: String, next: String?) =
        JournalResponse(dates.map { FakeJournalApi.item("m", it, null, emptyList(), null) }, next)

    // `Root.kt` declenche le premier chargement par `LaunchedEffect(Unit) { films.refresh() }` a
    // l'entree sur l'ecran (jumeau de `ProfileScreen`) : le `ViewModel` ne doit rien charger tout
    // seul a la construction, sous peine d'un `GET /me/journal` en double a la premiere ouverture,
    // sans ordre garanti entre les deux reponses (revue de la vague finale, Important 3).
    @Test
    fun `la construction ne charge rien, seul refresh declenche le premier appel`() = runTest(dispatcher) {
        api.onJournal = { page("2026-09-03", next = null) }
        val vm = FilmsViewModel(api) { expire++ }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), api.calls)

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("journal null"), api.calls)
    }

    @Test
    fun `charge la premiere page, puis la suivante, et s arrete sur null`() = runTest(dispatcher) {
        api.onJournal = { cursor -> if (cursor == null) page("2026-09-03", "2026-09-02", next = "c1") else page("2026-09-01", next = null) }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(2, vm.ui.value.items.size)
        assertFalse(vm.ui.value.endReached)

        vm.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.ui.value.items.size)
        assertTrue(vm.ui.value.endReached)

        vm.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("journal null", "journal c1"), api.calls)
    }

    @Test
    fun `ne lance pas deux chargements en meme temps`() = runTest(dispatcher) {
        val porte = CompletableDeferred<JournalResponse>()
        api.onJournal = { porte.await() }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        vm.loadMore(); vm.loadMore()
        porte.complete(page("2026-09-03", next = null))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("journal null"), api.calls)
    }

    @Test
    fun `refresh repart de zero`() = runTest(dispatcher) {
        api.onJournal = { page("2026-09-03", next = null) }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        api.onJournal = { page("2026-09-04", "2026-09-03", next = null) }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("2026-09-04", "2026-09-03"), vm.ui.value.items.map { it.entry.finished_at })
    }

    // Le test ci-dessus laisse toujours `cursor` a` `null` avant `refresh()` (la premiere
    // page y touche deja la fin) : retirer `cursor = null` dans `refresh()` ne le ferait
    // pas tomber. Celui-ci charge une deuxieme page dont le curseur n'est *pas* epuise,
    // pour que `refresh()` reparte bien de zero et non du dernier curseur connu (revue du
    // tour de correction 1).
    @Test
    fun `refresh remet aussi le curseur a zero, pas seulement la liste`() = runTest(dispatcher) {
        api.onJournal = { cursor ->
            when (cursor) {
                null -> page("2026-09-03", next = "c1")
                else -> page("2026-09-02", next = "c2")
            }
        }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        vm.loadMore()
        testScheduler.advanceUntilIdle()

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("journal null", "journal c1", "journal null"), api.calls)
    }

    @Test
    fun `un 401 previent la session`() = runTest(dispatcher) {
        api.onJournal = { throw FakeJournalApi.unauthorized() }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
    }

    // Le chemin d'erreur n'etait teste nulle part : une panne non-401 doit remonter dans
    // `ui.error` (message et `retryable` du back), sans effacer les films deja charges.
    @Test
    fun `une erreur non 401 remonte dans ui error, la liste reste`() = runTest(dispatcher) {
        api.onJournal = { cursor -> if (cursor == null) page("2026-09-03", next = "c1") else throw FakeJournalApi.rateLimited(30) }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        vm.loadMore()
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.ui.value.items.size)
        assertEquals("Trop de tentatives.", vm.ui.value.error?.message)
        assertTrue(vm.ui.value.error?.retryable == true)
        assertEquals(0, expire)
    }

    // `FilmsViewModel` est partage entre l'accueil et "Mes films" (meme cle "films" dans
    // Root.kt) : chacun rappelle refresh() a chaque entree sur son ecran. Un refresh() qui vide
    // l'etat d'un coup (`_ui.value = FilmsUi()`) montrait donc un ecran vide et le rond de
    // chargement a chaque retour, meme quand rien n'avait change (revue de la branche "l'accueil
    // montre les films vus", correction 5).
    @Test
    fun `refresh garde les jaquettes affichees, remplacees seulement a l arrivee de la premiere page`() = runTest(dispatcher) {
        api.onJournal = { page("2026-09-03", next = null) }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val porte = CompletableDeferred<JournalResponse>()
        api.onJournal = { porte.await() }
        vm.refresh()

        // L'ancienne jaquette reste affichee pendant l'aller-retour, avant meme que la nouvelle
        // page ne soit arrivee.
        assertEquals(listOf("2026-09-03"), vm.ui.value.items.map { it.entry.finished_at })

        porte.complete(page("2026-09-05", next = null))
        testScheduler.advanceUntilIdle()

        // La page arrivee remplace la liste, elle ne s'ajoute pas a l'ancienne.
        assertEquals(listOf("2026-09-05"), vm.ui.value.items.map { it.entry.finished_at })
    }

    // Jumeau du test ci-dessus, sur `endReached` plutot que sur les jaquettes : une liste deja
    // entierement chargee ne doit pas empecher un refresh() de relancer un appel.
    @Test
    fun `refresh relance meme si la liste precedente avait atteint sa fin`() = runTest(dispatcher) {
        api.onJournal = { page("2026-09-03", next = null) }
        val vm = FilmsViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val porte = CompletableDeferred<JournalResponse>()
        api.onJournal = { porte.await() }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        // Un appel a bien ete relance (la garde de loadMore() n'a pas bloque sur l'ancien
        // endReached), et le chargement reste vrai le temps qu'il aboutisse.
        assertEquals(listOf("journal null", "journal null"), api.calls)
        assertTrue(vm.ui.value.loading)

        porte.complete(page("2026-09-05", next = null))
        testScheduler.advanceUntilIdle()
    }
}
