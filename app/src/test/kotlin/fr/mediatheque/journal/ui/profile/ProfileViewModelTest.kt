package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.Counts
import fr.mediatheque.journal.api.dto.Dashboard
import fr.mediatheque.journal.api.dto.Periods
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.Totals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val api = FakeJournalApi()
    private var expire = 0

    @Test
    fun `lit les deux chiffres sous dashboard periods`() {
        api.onStats = {
            StatsResponse(Dashboard(Periods(
                year = Totals(Counts(mapOf("movie" to 12, "book" to 3))),
                all = Totals(Counts(mapOf("movie" to 87))),
            )))
        }
        val vm = ProfileViewModel(api) { expire++ }
        assertEquals(87, vm.ui.value.total)
        assertEquals(12, vm.ui.value.thisYear)
    }

    @Test
    fun `le message du back sur une panne, et Reessayer`() {
        api.onStats = { throw FakeJournalApi.network() }
        val vm = ProfileViewModel(api) { expire++ }
        assertEquals("L’API est injoignable.", vm.ui.value.error?.message)
        api.onStats = { StatsResponse(Dashboard(Periods(Totals(Counts(mapOf())), Totals(Counts(mapOf()))))) }
        vm.retry()
        assertEquals(0, vm.ui.value.total)
    }

    @Test
    fun `un 401 previent la session`() {
        api.onStats = { throw FakeJournalApi.unauthorized() }
        ProfileViewModel(api) { expire++ }
        assertEquals(1, expire)
    }
}
