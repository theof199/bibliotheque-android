package fr.mediatheque.journal.ui.search

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun vm() = SearchViewModel(api) { expire++ }

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
    fun `garde les resultats precedents pendant la requete suivante`() = runTest(dispatcher) {
        api.onSearch = { listOf(chihiro) }
        val vm = vm()
        vm.onQueryChange("chihiro")
        advanceTimeBy(301)
        vm.onQueryChange("chihiro 2")
        advanceTimeBy(150)

        assertEquals(listOf(chihiro), vm.ui.value.results)
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
}
