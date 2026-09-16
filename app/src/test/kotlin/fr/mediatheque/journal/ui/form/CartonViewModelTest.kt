package fr.mediatheque.journal.ui.form

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.CartonFilmResponse
import fr.mediatheque.journal.ui.frise.CHRONIQUE_ESSAIS_MAX
import fr.mediatheque.journal.ui.frise.EtatChronique
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Le carton « Et pendant ce temps… » (brief du 16 septembre 2026) : prêt, en préparation (relu
 * toutes les trois secondes), abandon après dix essais — et l'absence de sondage en bas de
 * `Screen.Edit` (`poll = false`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CartonViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val api = FakeJournalApi()

    @Test
    fun `pret des le premier appel`() = runTest(dispatcher) {
        api.onCartonFilm = { CartonFilmResponse(configure = true, statut = "prete", tmdb_id = it, contexte = "Contexte", faits = listOf("a", "b", "c")) }
        val vm = CartonViewModel(api, 27205, poll = true) {}
        runCurrent()

        assertEquals(EtatChronique.PRETE, vm.ui.value.etat)
        assertEquals("Contexte", vm.ui.value.contexte)
        // Un seul appel : prêt du premier coup, la boucle ne redemande rien.
        assertEquals(listOf("cartonFilm 27205"), api.calls)
    }

    @Test
    fun `en preparation puis pret, relu toutes les trois secondes`() = runTest(dispatcher) {
        var appel = 0
        api.onCartonFilm = {
            appel++
            if (appel < 3) CartonFilmResponse(configure = true, statut = "en_preparation")
            else CartonFilmResponse(configure = true, statut = "prete", contexte = "Prêt", faits = listOf("a", "b", "c"))
        }
        val vm = CartonViewModel(api, 27205, poll = true) {}
        runCurrent()
        assertEquals(EtatChronique.EN_PREPARATION, vm.ui.value.etat)
        assertEquals(1, vm.ui.value.essais)

        advanceTimeBy(CartonViewModel.POLL_INTERVAL_MS)
        runCurrent()
        assertEquals(EtatChronique.EN_PREPARATION, vm.ui.value.etat)
        assertEquals(2, vm.ui.value.essais)

        advanceTimeBy(CartonViewModel.POLL_INTERVAL_MS)
        runCurrent()
        assertEquals(EtatChronique.PRETE, vm.ui.value.etat)
        assertEquals("Prêt", vm.ui.value.contexte)
        assertEquals(3, appel)
    }

    // Mutation : retirer le `delay(POLL_INTERVAL_MS)` de `CartonViewModel` ferait boucler sans
    // jamais suspendre, et `runCurrent()` n'avancerait alors jamais au-delà du premier essai
    // (StandardTestDispatcher exécute une coroutine qui ne suspend pas jusqu'au bout, en boucle
    // infinie) — ce test échouerait par un dépassement de délai plutôt qu'une assertion inexacte.
    @Test
    fun `abandon apres dix essais en preparation, sans appel de plus ensuite`() = runTest(dispatcher) {
        api.onCartonFilm = { CartonFilmResponse(configure = true, statut = "en_preparation") }
        val vm = CartonViewModel(api, 27205, poll = true) {}
        runCurrent()

        repeat(8) {
            advanceTimeBy(CartonViewModel.POLL_INTERVAL_MS)
            runCurrent()
        }
        assertEquals(EtatChronique.EN_PREPARATION, vm.ui.value.etat)
        assertEquals(9, vm.ui.value.essais)

        advanceTimeBy(CartonViewModel.POLL_INTERVAL_MS)
        runCurrent()
        assertEquals(EtatChronique.ABANDON, vm.ui.value.etat)
        assertEquals(CHRONIQUE_ESSAIS_MAX, vm.ui.value.essais)

        val appelsAvant = api.calls.size
        advanceTimeBy(CartonViewModel.POLL_INTERVAL_MS * 5)
        runCurrent()
        assertEquals(appelsAvant, api.calls.size)
    }

    @Test
    fun `sans sondage, une seule lecture meme si en preparation`() = runTest(dispatcher) {
        api.onCartonFilm = { CartonFilmResponse(configure = true, statut = "en_preparation") }
        val vm = CartonViewModel(api, 27205, poll = false) {}
        runCurrent()

        assertEquals(EtatChronique.EN_PREPARATION, vm.ui.value.etat)
        val appelsApres = api.calls.size

        advanceTimeBy(CartonViewModel.POLL_INTERVAL_MS * 3)
        runCurrent()
        assertEquals(appelsApres, api.calls.size)
    }

    @Test
    fun `sans cle Anthropic cote back, muette d'emblee`() = runTest(dispatcher) {
        api.onCartonFilm = { CartonFilmResponse(configure = false) }
        val vm = CartonViewModel(api, 27205, poll = true) {}
        runCurrent()

        assertEquals(EtatChronique.NON_CONFIGURE, vm.ui.value.etat)
    }
}
