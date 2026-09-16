package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.EssentielVoyage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * La page d'année du Voyage (brief du 16 septembre 2026, phase 1) : l'état initial repris du
 * fragment déjà chargé par `FriseViewModel`, la demande sur Seerr (succès → pastille « demandé »,
 * erreur → bandeau) et le marquage « introuvable » (et l'inverse — restaure l'état d'avant, pas un
 * état par défaut).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnneeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val api = FakeJournalApi()

    private fun essentiel(tmdbId: Int, etat: String) = EssentielVoyage(
        rang = 1,
        tmdb_id = tmdbId,
        title = "Film $tmdbId",
        year = 1941,
        cover_url = null,
        realisateur = "Un réalisateur",
        pourquoi = "Parce que.",
        etat = etat,
        note = null,
    )

    @Test
    fun `anneeUiInitiale reprend le fragment deja charge par FriseViewModel`() {
        val snapshot = AnneeVoyage(
            annee = 1941,
            statut = "ouverte",
            vus = 3,
            essentiels_total = 2,
            essentiels_faits = 1,
            essentiels = listOf(essentiel(1, "vu"), essentiel(2, "a_trouver")),
        )

        val ui = anneeUiInitiale(1941, snapshot)

        assertEquals(StatutAnneeVoyage.OUVERTE, ui.statutVoyage)
        assertEquals(3, ui.vus)
        assertEquals(2, ui.essentielsTotal)
        assertEquals(1, ui.essentielsFaits)
        assertEquals(listOf("vu", "a_trouver"), ui.essentiels.map { it.etat })
    }

    // Mutation : une année sans fragment (snapshot nul, verrouillée) doit rester sans essentiel ni
    // statut — retourner `StatutAnneeVoyage.VERROUILLEE` par défaut ferait échouer cette assertion.
    @Test
    fun `anneeUiInitiale sans fragment reste vide, statut inconnu`() {
        val ui = anneeUiInitiale(1950, null)
        assertEquals(null, ui.statutVoyage)
        assertTrue(ui.essentiels.isEmpty())
    }

    @Test
    fun `demander reussit et pose la pastille demande, seulement sur le bon film`() = runTest(dispatcher) {
        val snapshot = AnneeVoyage(1941, "ouverte", essentiels = listOf(essentiel(500, "a_trouver"), essentiel(600, "a_trouver")))
        api.onDemanderVoyage = { DemanderVoyageResponse(demande = true) }
        val vm = AnneeViewModel(api, 1941, snapshot) {}
        runCurrent()

        vm.demander(500)
        runCurrent()

        val essentiels = vm.ui.value.essentiels.associateBy { it.tmdbId }
        assertTrue(essentiels.getValue(500).demande)
        assertFalse(essentiels.getValue(600).demande)
        assertEquals(listOf("demanderVoyage 500"), api.calls.filter { it.startsWith("demanderVoyage") })
    }

    @Test
    fun `demander en echec envoie le message du back au bandeau, sans poser la pastille`() = runTest(dispatcher) {
        val snapshot = AnneeVoyage(1941, "ouverte", essentiels = listOf(essentiel(500, "a_trouver")))
        api.onDemanderVoyage = { throw ApiError("UPSTREAM_UNAVAILABLE", "Seerr ne répond pas. Réessaie plus tard.", retryable = true, status = 503) }
        val vm = AnneeViewModel(api, 1941, snapshot) {}
        runCurrent()

        val messages = mutableListOf<String>()
        val job = launch { vm.messages.collect { messages += it } }

        vm.demander(500)
        runCurrent()

        assertEquals(listOf("Seerr ne répond pas. Réessaie plus tard."), messages)
        assertFalse(vm.ui.value.essentiels.first().demande)
        job.cancel()
    }

    @Test
    fun `marquer introuvable puis le remettre a voir restaure l'etat d'avant, pas un defaut`() = runTest(dispatcher) {
        // « sur_le_plex », pas « a_trouver » : si `retirerIntrouvable` retombait sur un état par
        // défaut au lieu de l'état mémorisé, ce test le verrait.
        val snapshot = AnneeVoyage(1941, "ouverte", essentiels = listOf(essentiel(500, "sur_le_plex")))
        val vm = AnneeViewModel(api, 1941, snapshot) {}
        runCurrent()

        vm.marquerIntrouvable(500)
        runCurrent()
        assertEquals("introuvable", vm.ui.value.essentiels.first().etat)
        assertEquals(listOf("marquerIntrouvable 500"), api.calls.filter { it.startsWith("marquerIntrouvable") })

        vm.retirerIntrouvable(500)
        runCurrent()
        assertEquals("sur_le_plex", vm.ui.value.essentiels.first().etat)
    }
}
