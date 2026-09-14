package fr.mediatheque.journal.ui.form

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.senscritique.FakeExternalRatingService
import fr.mediatheque.journal.senscritique.InMemorySensCritiqueStore
import fr.mediatheque.journal.senscritique.SensCritiqueAuth
import fr.mediatheque.journal.senscritique.SensCritiqueSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Important 4 de la revue du 14 septembre 2026 : `syncSensCritique` ne doit jamais tenir `busy`
 * indéfiniment si le service externe ne répond pas — jusqu'à quatre allers-retours à 10 s chacun
 * sans ce plafond. Un `StandardTestDispatcher` dédié, partagé entre `Dispatchers.Main` et le corps
 * du test (`runTest(dispatcher)`) : contrairement au reste de `FormViewModelTest`
 * (`UnconfinedTestDispatcher`, qui exécute tout jusqu'au bout sans jamais laisser un vrai delai se
 * poser), celui-ci permet d'avancer le temps virtuel à la milliseconde près.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FormViewModelSensCritiqueTimeoutTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val api = FakeJournalApi()
    private val chihiro = SearchResult("tmdb", "129", "movie", "Le Voyage de Chihiro", 2001)

    private fun vmQuiNeReponJamais(store: InMemorySensCritiqueStore): FormViewModel {
        val service = FakeExternalRatingService(onSearch = { awaitCancellation() })
        return FormViewModel(api, FormMode.Create(chihiro), SensCritiqueSync(service, store)) {}
    }

    // Mutation : baisser `SENSCRITIQUE_SYNC_TIMEOUT_MS` sous 6000 fait echouer cette assertion
    // (le geste serait deja termine, `done` non nul, avant les 5999 ms testes ici).
    @Test
    fun `sous 6s, le geste attend encore — busy vrai, done encore nul`() = runTest(dispatcher) {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", "2099-01-01T00:00:00Z", "TheofB")) }
        val vm = vmQuiNeReponJamais(store)
        vm.toggleRating(8)
        vm.save()
        runCurrent()

        advanceTimeBy(5_999)
        runCurrent()

        assertNull(vm.ui.value.done)
        assertTrue(vm.ui.value.busy)
    }

    // Mutation : retirer le `withTimeoutOrNull` (revenir a un appel direct de `syncAfterSave`) fait
    // echouer cette assertion — `done` resterait nul indefiniment, le service ne repondant jamais.
    @Test
    fun `au dela de 6s, la poussee part en file et le message dit reessai`() = runTest(dispatcher) {
        val store = InMemorySensCritiqueStore().apply { writeAuth(SensCritiqueAuth("cookie-1", "2099-01-01T00:00:00Z", "TheofB")) }
        val vm = vmQuiNeReponJamais(store)
        vm.toggleRating(8)
        vm.save()
        runCurrent()

        advanceTimeBy(6_001)
        runCurrent()

        assertEquals("Enregistré · SensCritique : réessai au prochain lancement", vm.ui.value.done)
        assertEquals(1, store.readQueue().size)
        assertTrue("le compte est en file sans productId (jamais resolu)", store.readQueue().values.single().productId == null)
    }
}
