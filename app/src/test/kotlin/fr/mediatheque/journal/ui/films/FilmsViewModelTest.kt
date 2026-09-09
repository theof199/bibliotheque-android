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

    @Test
    fun `charge la premiere page, puis la suivante, et s arrete sur null`() = runTest(dispatcher) {
        api.onJournal = { cursor -> if (cursor == null) page("2026-09-03", "2026-09-02", next = "c1") else page("2026-09-01", next = null) }
        val vm = FilmsViewModel(api) { expire++ }
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
        testScheduler.advanceUntilIdle()
        api.onJournal = { page("2026-09-04", "2026-09-03", next = null) }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("2026-09-04", "2026-09-03"), vm.ui.value.items.map { it.entry.finished_at })
    }

    @Test
    fun `un 401 previent la session`() = runTest(dispatcher) {
        api.onJournal = { throw FakeJournalApi.unauthorized() }
        FilmsViewModel(api) { expire++ }
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
    }
}
