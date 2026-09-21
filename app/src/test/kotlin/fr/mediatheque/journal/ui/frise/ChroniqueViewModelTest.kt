package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.ChroniqueEcritureResponse
import fr.mediatheque.journal.api.dto.ParagrapheFilmVoyage
import fr.mediatheque.journal.api.dto.ParagrapheVoyage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * « Ajouter à la chronique » depuis l'écran de correction du journal (décision 1 du brief du 21
 * septembre 2026, « la chronique et les salles ») : déjà écrit, en préparation puis trouvé, abandon
 * au plafond — jumeau de `CartonViewModelTest`, sur `AnneeViewModel.ajouterChronique`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChroniqueViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val api = FakeJournalApi()

    private fun paragraphe(texte: String) = ParagrapheVoyage(
        id = "p1",
        tmdb_id = 500,
        titre = "Un titre",
        texte = texte,
        ecrit_le = "2026-09-21T21:30:00.000Z",
        film = ParagrapheFilmVoyage("Film 500", null),
    )

    @Test
    fun `deja ecrit affiche le paragraphe tout de suite, sans relire`() = runTest(dispatcher) {
        api.onVoyageChronique = { _, _ -> ChroniqueEcritureResponse(statut = "ecrit", paragraphe = paragraphe("Le texte.")) }
        val vm = ChroniqueViewModel(api, 1941, 500) {}
        // La lecture de montage (paragraphe absent par défaut) s'écoule avant qu'on ne teste `ajouter`.
        runCurrent()
        api.calls.clear()

        vm.ajouter()
        runCurrent()

        assertEquals(EtatBoutonChronique.DANS_LA_CHRONIQUE, vm.ui.value.etat)
        assertEquals("Le texte.", vm.ui.value.texte)
        // Mutation : appeler `voyageAnnee` ici régénérerait un paragraphe déjà écrit.
        assertEquals(emptyList<String>(), api.calls.filter { it.startsWith("voyageAnnee") })
    }

    // Décision du 21 septembre 2026 (« au montage ») : sur `Screen.Edit`, le bouton part directement
    // sur « Dans la chronique » quand le paragraphe existe déjà, sans passer par `ajouter()` — jamais
    // un appel à `voyageChronique` pour un simple affichage.
    @Test
    fun `paragraphe deja present au montage part sur dans la chronique, sans appeler ajouter`() = runTest(dispatcher) {
        api.onVoyageAnnee = {
            AnneeVoyageDetailResponse(configure = true, statut = "prete", annee = 1941, paragraphes = listOf(paragraphe("Déjà là.")))
        }
        val vm = ChroniqueViewModel(api, 1941, 500) {}

        runCurrent()

        assertEquals(EtatBoutonChronique.DANS_LA_CHRONIQUE, vm.ui.value.etat)
        assertEquals("Déjà là.", vm.ui.value.texte)
        // Mutation : partir sur « Dans la chronique » par ce chemin n'appelle jamais `voyageChronique`.
        assertEquals(emptyList<String>(), api.calls.filter { it.startsWith("voyageChronique") })
    }

    // Mutation : sans le filtre par `tmdb_id`, un paragraphe d'un autre film ferait passer l'état à
    // « Dans la chronique » à tort.
    @Test
    fun `paragraphe absent au montage reste sur ajouter`() = runTest(dispatcher) {
        api.onVoyageAnnee = {
            AnneeVoyageDetailResponse(configure = true, statut = "prete", annee = 1941, paragraphes = listOf(paragraphe("D'un autre film.").copy(tmdb_id = 999)))
        }
        val vm = ChroniqueViewModel(api, 1941, 500) {}

        runCurrent()

        assertEquals(EtatBoutonChronique.AJOUTER, vm.ui.value.etat)
        assertNull(vm.ui.value.texte)
    }

    @Test
    fun `en preparation passe par ecrit en cours puis trouve le paragraphe`() = runTest(dispatcher) {
        api.onVoyageChronique = { _, _ -> ChroniqueEcritureResponse(statut = "en_preparation") }
        api.onVoyageAnnee = { AnneeVoyageDetailResponse(configure = true, statut = "prete", annee = 1941) }
        val vm = ChroniqueViewModel(api, 1941, 500) {}

        vm.ajouter()
        runCurrent()
        assertEquals(EtatBoutonChronique.ECRIT_EN_COURS, vm.ui.value.etat)

        api.onVoyageAnnee = {
            AnneeVoyageDetailResponse(configure = true, statut = "prete", annee = 1941, paragraphes = listOf(paragraphe("Trouvé.")))
        }
        testScheduler.advanceUntilIdle()

        assertEquals(EtatBoutonChronique.DANS_LA_CHRONIQUE, vm.ui.value.etat)
        assertEquals("Trouvé.", vm.ui.value.texte)
    }

    // Abandon au plafond : le paragraphe n'arrive jamais, le bouton redevient « Ajouter » plutôt que
    // de rester bloqué sur « écrit… » pour toujours — mutation : ne pas revenir à `AJOUTER` laisserait
    // l'utilisateur sans recours après trois minutes d'attente.
    @Test
    fun `abandon au plafond redevient ajouter`() = runTest(dispatcher) {
        api.onVoyageChronique = { _, _ -> ChroniqueEcritureResponse(statut = "en_preparation") }
        api.onVoyageAnnee = { AnneeVoyageDetailResponse(configure = true, statut = "prete", annee = 1941) }
        val vm = ChroniqueViewModel(api, 1941, 500) {}

        vm.ajouter()
        testScheduler.advanceUntilIdle()

        assertEquals(EtatBoutonChronique.AJOUTER, vm.ui.value.etat)
        assertNull(vm.ui.value.texte)
    }
}
