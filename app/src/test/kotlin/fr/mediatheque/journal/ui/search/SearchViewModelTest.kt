package fr.mediatheque.journal.ui.search

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeJournalApi()
    private var expire = 0
    private val chihiro = SearchResult("tmdb", "129", "movie", "Le Voyage de Chihiro", 2001)

    private fun vm() = SearchViewModel(api, onUnauthenticated = { expire++ })

    @Test
    fun `attend 300 ms et ne cherche que la derniere frappe`() = runTest(dispatcher) {
        api.onSearch = { q -> if (q == "chihiro") listOf(chihiro) else emptyList() }
        val vm = vm()
        vm.onQueryChange("chi")
        advanceTimeBy(100)
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)

        assertEquals(listOf("search chihiro"), api.calls)
        assertEquals(listOf(chihiro), vm.ui.value.results)
    }

    @Test
    fun `un champ vide ne cherche rien et vide les resultats`() = runTest(dispatcher) {
        api.onSearch = { listOf(chihiro) }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        vm.onQueryChange("")
        advanceTimeBy(301)

        assertEquals(listOf("search chihiro"), api.calls)
        assertTrue(vm.ui.value.results.isEmpty())
    }

    @Test
    fun `changer la requete ne vide pas les resultats avant la fin du debounce`() = runTest(dispatcher) {
        api.onSearch = { listOf(chihiro) }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        vm.onQueryChange("chihiro 2")
        advanceTimeBy(150)

        assertEquals(listOf(chihiro), vm.ui.value.results)
    }

    @Test
    fun `pendant le chargement de la requete suivante, les resultats precedents restent affiches`() = runTest(dispatcher) {
        val mononoke = SearchResult("tmdb", "128", "movie", "Princesse Mononoke", 1997)
        // La seconde requête n'aboutit que quand le test le décide : le temps virtuel peut
        // avancer jusqu'à la fin du chargement sans que la réponse n'arrive, exactement comme un
        // réseau lent — c'est ce qui rend « pendant » observable plutôt que supposé.
        val secondeReponse = CompletableDeferred<List<SearchResult>>()
        api.onSearch = { q -> if (q == "chihiro") listOf(chihiro) else secondeReponse.await() }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        vm.onQueryChange("mononoke")
        advanceTimeBy(301)

        assertTrue(vm.ui.value.loading)
        assertEquals(listOf(chihiro), vm.ui.value.results)

        secondeReponse.complete(listOf(mononoke))
        advanceUntilIdle()

        assertFalse(vm.ui.value.loading)
        assertEquals(listOf(mononoke), vm.ui.value.results)
    }

    @Test
    fun `montre le message du back, et Reessayer si retryable`() = runTest(dispatcher) {
        api.onSearch = { throw ApiError("SERVICE_UNCONFIGURED", "La recherche TMDB n’est pas configurée.", retryable = true, status = 503) }
        val vm = vm()
        vm.onQueryChange("x")
        advanceTimeBy(301)

        assertEquals("La recherche TMDB n’est pas configurée.", vm.ui.value.error?.message)
        assertTrue(vm.ui.value.error!!.retryable)
    }

    @Test
    fun `un 401 previent la session`() = runTest(dispatcher) {
        api.onSearch = { throw FakeJournalApi.unauthorized() }
        val vm = vm()
        vm.onQueryChange("x")
        advanceTimeBy(301)

        assertEquals(1, expire)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `reset vide requete et resultats, et laisse la meme requete relancer une recherche`() = runTest(dispatcher) {
        api.onSearch = { listOf(chihiro) }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        assertEquals(listOf(chihiro), vm.ui.value.results)

        vm.reset()
        // Les dernières recherches survivent au `reset()` (point 6 de la revue du 24 septembre
        // 2026) : la recherche qui vient d'être faite y figure déjà, `reset()` ne la vide pas
        // avec le reste.
        assertEquals(SearchUi(recentes = listOf("chihiro")), vm.ui.value)
        // Laisse le "" du reset traverser le débounce à lui seul, sinon la frappe suivante
        // l'écraserait avant qu'il n'atteigne `distinctUntilChanged` et fausserait la suite.
        advanceTimeBy(301)

        // Une réouverture de l'écran (ViewModel indexé sur l'Activité, revue de la tâche 5) peut
        // retaper la même requête : si `reset()` ne remettait à zéro que `ui` et pas le flux
        // interne `query`, `distinctUntilChanged` la jugerait inchangée et ne chercherait plus.
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)

        assertEquals(listOf("search chihiro", "search chihiro"), api.calls)
        assertEquals(listOf(chihiro), vm.ui.value.results)
    }

    // Point 6 de la revue du 24 septembre 2026, « dernières recherches ». Mutation : mémoriser
    // seulement sur un résultat trouvé (pas sur l'envoi de la requête) fait tomber la deuxième
    // recherche, sans résultat, de la liste.
    @Test
    fun `une recherche envoyee rejoint les dernieres recherches, meme sans resultat`() = runTest(dispatcher) {
        api.onSearch = { q -> if (q == "chihiro") listOf(chihiro) else emptyList() }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        vm.onQueryChange("zzzz")
        advanceTimeBy(301)

        assertEquals(listOf("zzzz", "chihiro"), vm.ui.value.recentes)
    }

    @Test
    fun `effacerRecherchesRecentes vide la liste`() = runTest(dispatcher) {
        api.onSearch = { listOf(chihiro) }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        assertEquals(listOf("chihiro"), vm.ui.value.recentes)

        vm.effacerRecherchesRecentes()
        assertTrue(vm.ui.value.recentes.isEmpty())
    }
}
