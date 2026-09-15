package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.api.dto.PlexResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private fun page(vararg externalIds: String, next: String? = null) =
        JournalResponse(
            externalIds.map { FakeJournalApi.item("m-$it", "2026-01-10", null, emptyList(), null, externalId = it) },
            next,
        )

    // Jumeau de `FilmsViewModel`/`AuCineViewModel` : `Root.kt` déclenche le premier chargement,
    // pas le constructeur.
    @Test
    fun `la construction ne charge rien, seul refresh declenche les appels`() = runTest(dispatcher) {
        api.onJournal = { page("1") }
        val vm = FriseViewModel(api) { expire++ }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), api.calls)
    }

    // Une panne du Plex (Seerr injoignable, service non configuré) ne doit pas priver la Frise
    // des films déjà vus : elle garde les vus, sans `ErrorBlock` bloquant (brief du 15 septembre
    // 2026). Mutation : propager l'erreur du Plex dans `ui.error` comme celle du journal ferait
    // échouer la troisième assertion.
    @Test
    fun `une panne du plex garde les vus, sans erreur bloquante`() = runTest(dispatcher) {
        api.onJournal = { cursor -> if (cursor == null) page("1", "2") else JournalResponse(emptyList(), null) }
        api.onPlex = { throw FakeJournalApi.network() }

        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.error)
        assertEquals(1, vm.ui.value.annees.size)
        assertEquals(2, vm.ui.value.annees.first().vus.size)
        assertTrue(vm.ui.value.annees.first().aVoir.isEmpty())
        assertEquals(false, vm.ui.value.plexConfigure)
        assertEquals(0, expire)
    }

    // `refresh()` charge le journal en entier avant de construire la frise — pas seulement la
    // première page. Mutation : s'arrêter à la première page laisserait `annees` à une seule
    // entrée (année 2026 pour "1"), au lieu des deux années attendues ici.
    @Test
    fun `refresh charge toutes les pages du journal, pas seulement la premiere`() = runTest(dispatcher) {
        var appelsJournal = 0
        api.onJournal = { cursor ->
            appelsJournal++
            when (cursor) {
                null -> JournalResponse(
                    listOf(FakeJournalApi.item("m1", "2026-01-10", null, emptyList(), null, externalId = "1")),
                    "page-2",
                )
                "page-2" -> JournalResponse(
                    listOf(FakeJournalApi.item("m2", "2020-01-10", null, emptyList(), null, externalId = "2")),
                    null,
                )
                else -> error("curseur inattendu : $cursor")
            }
        }
        api.onPlex = { PlexResponse(configure = true) }

        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(2, appelsJournal) // page 1 (next_cursor = "page-2"), page 2 (next_cursor = null) : arrêt
        assertEquals(listOf(2020, 2026), vm.ui.value.annees.map { it.annee })
    }

    // Un plex configuré et peuplé construit bien la frise, `ensuite` compris — le chemin nominal,
    // par-dessus la fonction pure déjà testée dans `FriseTest`.
    @Test
    fun `refresh construit la frise et ensuite depuis un plex peuple`() = runTest(dispatcher) {
        api.onJournal = { page() }
        api.onPlex = {
            PlexResponse(
                configure = true,
                calcule_le = "2026-09-15T09:00:00.000Z",
                films = listOf(PlexFilm(tmdb_id = 27205, title = "Inception", year = 2010, demande_le = "2026-09-10T00:00:00.000Z")),
            )
        }

        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(2010, vm.ui.value.anneeEnCours)
        assertEquals(27205, vm.ui.value.ensuite?.tmdb_id)
        assertEquals(true, vm.ui.value.plexConfigure)
    }

    @Test
    fun `un 401 sur le journal previent la session`() = runTest(dispatcher) {
        api.onJournal = { throw FakeJournalApi.unauthorized() }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
        assertNull(vm.ui.value.error)
    }
}
