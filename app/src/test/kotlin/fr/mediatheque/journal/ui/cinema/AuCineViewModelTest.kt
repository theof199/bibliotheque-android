package fr.mediatheque.journal.ui.cinema

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.SortieFilm
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.SortieSemaine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuCineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private fun page(vararg dates: String, next: String? = null, externalId: String = "m") =
        JournalResponse(dates.map { FakeJournalApi.item("m", it, null, emptyList(), null, externalId = externalId) }, next)

    private fun sorties(vararg tmdbIds: Int) = SortiesResponse(
        en_cours = SortieSemaine("2026-09-14", "2026-09-20", tmdbIds.map { SortieFilm(it, "Film $it") }),
        prochaine = SortieSemaine("2026-09-21", "2026-09-27"),
    )

    // Jumeau de `FilmsViewModel` (`la construction ne charge rien...`) : `Root.kt` déclenche le
    // premier chargement par `LaunchedEffect(Unit) { cinema.refresh() }`, pas le constructeur.
    @Test
    fun `la construction ne charge rien, seul refresh declenche les deux appels`() = runTest(dispatcher) {
        api.onSorties = { sorties() }
        api.onSeances = { page("2026-09-03") }
        val vm = AuCineViewModel(api) { expire++ }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), api.calls)

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("sorties", "seances null"), api.calls)
    }

    // Brief du 14 septembre 2026 : « N séances cette année ». Compte sur les entrées déjà
    // chargées, une année à la fois — pas les autres années, même présentes dans la liste.
    @Test
    fun `seancesCetteAnnee ne compte que les entrees de l annee donnee`() {
        val ui = AuCineUi(
            seances = listOf(
                FakeJournalApi.item("m1", "2026-01-10", null, emptyList(), null),
                FakeJournalApi.item("m2", "2026-09-03", null, emptyList(), null),
                FakeJournalApi.item("m3", "2025-12-31", null, emptyList(), null),
            ),
        )
        assertEquals(2, ui.seancesCetteAnnee(2026))
        assertEquals(1, ui.seancesCetteAnnee(2025))
    }

    // Le rapprochement de la coche corail : par `tmdb_id`/`external_id`, jamais par le titre —
    // deux films homonymes ne doivent pas se confondre.
    @Test
    fun `dejaDansLeJournal rapproche par tmdb_id, pas par le titre`() {
        val ui = AuCineUi(
            seances = listOf(FakeJournalApi.item("m", "2026-09-01", null, emptyList(), null, externalId = "912649")),
        )
        assertTrue(ui.dejaDansLeJournal(SortieFilm(912649, "Les Gardiens de la nuit")))
        assertFalse(ui.dejaDansLeJournal(SortieFilm(1022789, "Les Gardiens de la nuit")))
    }

    // « toucher une affiche ouvre le formulaire pré-rempli comme depuis la recherche » : la
    // conversion doit viser le même film, avec la bonne source et le bon identifiant.
    @Test
    fun `toSearchResult vise le meme film, source tmdb et external_id le tmdb_id`() {
        val film = SortieFilm(
            tmdb_id = 912649,
            title = "Les Gardiens de la nuit",
            original_title = "Les Gardiens de la nuit",
            year = 2026,
            release_date = "2026-09-16",
            cover_url = "https://image.tmdb.org/t/p/w500/x.jpg",
            directors = listOf("Alice Fontaine"),
        )
        val result = film.toSearchResult()
        assertEquals("tmdb", result.source)
        assertEquals("912649", result.external_id)
        assertEquals("movie", result.type)
        assertEquals("Les Gardiens de la nuit", result.title)
        assertEquals(2026, result.year)
        assertEquals("https://image.tmdb.org/t/p/w500/x.jpg", result.cover_url)
        assertEquals("Alice Fontaine", result.metadata.director)
    }

    // Une panne sur les sorties (TMDB) ne doit rien devoir aux séances (le journal) : deux
    // appels indépendants, deux erreurs indépendantes.
    @Test
    fun `une panne des sorties remonte dans sortiesError, sans toucher aux seances`() = runTest(dispatcher) {
        api.onSorties = { throw FakeJournalApi.network() }
        api.onSeances = { page("2026-09-03") }
        val vm = AuCineViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(FakeJournalApi.network().message, vm.ui.value.sortiesError?.message)
        assertTrue(vm.ui.value.sortiesError?.retryable == true)
        assertNull(vm.ui.value.sorties)
        assertEquals(1, vm.ui.value.seances.size)
        assertEquals(0, expire)
    }

    // Le symétrique : une panne du journal ne doit rien devoir à TMDB.
    @Test
    fun `une panne des seances remonte dans seancesError, sans toucher aux sorties`() = runTest(dispatcher) {
        api.onSorties = { sorties(1) }
        api.onSeances = { throw FakeJournalApi.rateLimited(30) }
        val vm = AuCineViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals("Trop de tentatives.", vm.ui.value.seancesError?.message)
        assertTrue(vm.ui.value.seancesError?.retryable == true)
        assertTrue(vm.ui.value.seances.isEmpty())
        assertEquals(1, vm.ui.value.sorties?.en_cours?.films?.size)
        assertEquals(0, expire)
    }

    @Test
    fun `un 401 sur les sorties previent la session`() = runTest(dispatcher) {
        api.onSorties = { throw FakeJournalApi.unauthorized() }
        api.onSeances = { page("2026-09-03") }
        val vm = AuCineViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
        assertNull(vm.ui.value.sortiesError)
    }

    @Test
    fun `un 401 sur les seances previent la session`() = runTest(dispatcher) {
        api.onSorties = { sorties() }
        api.onSeances = { throw FakeJournalApi.unauthorized() }
        val vm = AuCineViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
        assertNull(vm.ui.value.seancesError)
    }

    // `loadMoreSeances` pagine comme `FilmsViewModel.loadMore` — implémentation distincte, donc
    // testée à part : une page 1 remplace, une page suivante étend, et `next_cursor == null` est
    // le seul signal de fin.
    @Test
    fun `loadMoreSeances charge la premiere page, puis la suivante, et s arrete sur null`() = runTest(dispatcher) {
        api.onSorties = { sorties() }
        api.onSeances = { cursor -> if (cursor == null) page("2026-09-03", "2026-09-02", next = "c1") else page("2026-09-01") }
        val vm = AuCineViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(2, vm.ui.value.seances.size)
        assertFalse(vm.ui.value.seancesEndReached)

        vm.loadMoreSeances()
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.ui.value.seances.size)
        assertTrue(vm.ui.value.seancesEndReached)
    }

    // « Réessayer » sur l'erreur des sorties ne doit pas relancer « Tes séances » depuis sa
    // première page : `retrySorties`, pas `refresh`.
    @Test
    fun `retrySorties ne touche pas au curseur des seances`() = runTest(dispatcher) {
        api.onSorties = { sorties() }
        api.onSeances = { cursor -> if (cursor == null) page("2026-09-03", next = "c1") else page("2026-09-02") }
        val vm = AuCineViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        vm.loadMoreSeances()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.ui.value.seancesEndReached)

        api.onSorties = { sorties(1, 2) }
        vm.retrySorties()
        testScheduler.advanceUntilIdle()

        // Toujours 2 séances (pas rechargées depuis la première page), et les nouvelles sorties.
        assertEquals(2, vm.ui.value.seances.size)
        assertEquals(2, vm.ui.value.sorties?.en_cours?.films?.size)
    }
}
