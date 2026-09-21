package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le Voyage (brief du 21 septembre 2026, « l'année en étages ») : les états d'une année et d'une
 * génération, en fonctions pures — sans réseau ni `ViewModel`.
 */
class VoyageEtatsTest {

    @Test
    fun `statutAnneeVoyage reconnait les trois statuts, et rien d'autre`() {
        assertEquals(StatutAnneeVoyage.OUVERTE, statutAnneeVoyage("ouverte"))
        assertEquals(StatutAnneeVoyage.EN_COURS, statutAnneeVoyage("en_cours"))
        assertEquals(StatutAnneeVoyage.VERROUILLEE, statutAnneeVoyage("verrouillee"))
        // Mutation : retourner `StatutAnneeVoyage.VERROUILLEE` par défaut au lieu de `null` ferait
        // passer une année absente de `/me/voyage` pour verrouillée plutôt qu'inconnue.
        assertNull(statutAnneeVoyage(null))
        assertNull(statutAnneeVoyage("faite"))
        assertNull(statutAnneeVoyage("autre chose"))
    }

    @Test
    fun `toVoyageUi indexe les annees et lit l'annee en cours`() {
        val reponse = VoyageResponse(
            configure = true,
            depart = 1895,
            annee_en_cours = 1941,
            annees = listOf(
                AnneeVoyage(1895, "ouverte", visitee = true, profondeur = 2),
                AnneeVoyage(1941, "en_cours", visitee = true, profondeur = 1),
            ),
        )
        val ui = reponse.toVoyageUi()

        assertEquals(true, ui.configure)
        assertEquals(1941, ui.anneeEnCours)
        assertEquals(StatutAnneeVoyage.OUVERTE, statutVoyage(1895, ui))
        assertEquals(StatutAnneeVoyage.EN_COURS, statutVoyage(1941, ui))
        // Une année absente de la réponse : ni ouverte, ni en cours, ni verrouillée — inconnue.
        assertNull(statutVoyage(1942, ui))
    }

    @Test
    fun `etatChroniqueSuivant rend PRETE des que le statut l'est, sans compter d'essai de plus`() {
        val (etat, essais) = etatChroniqueSuivant(configure = true, statut = "prete", essaisPrecedents = 3)
        assertEquals(EtatChronique.PRETE, etat)
        assertEquals(3, essais)
    }

    @Test
    fun `etatChroniqueSuivant rend NON_CONFIGURE sans jamais compter d'essai`() {
        val (etat, essais) = etatChroniqueSuivant(configure = false, statut = null, essaisPrecedents = 5)
        assertEquals(EtatChronique.NON_CONFIGURE, etat)
        assertEquals(5, essais)
    }

    @Test
    fun `etatChroniqueSuivant compte les essais en preparation jusqu'a l'abandon au dixieme`() {
        var essais = 0
        var etat = EtatChronique.EN_PREPARATION
        repeat(9) {
            val resultat = etatChroniqueSuivant(configure = true, statut = "en_preparation", essaisPrecedents = essais)
            etat = resultat.first
            essais = resultat.second
            assertEquals("essai $essais", EtatChronique.EN_PREPARATION, etat)
        }
        assertEquals(9, essais)

        // Le dixième essai, et pas avant (mutation : `essais > CHRONIQUE_ESSAIS_MAX` au lieu de
        // `>=` ferait attendre un onzième essai avant l'abandon).
        val dixieme = etatChroniqueSuivant(configure = true, statut = "en_preparation", essaisPrecedents = essais)
        assertEquals(EtatChronique.ABANDON, dixieme.first)
        assertEquals(CHRONIQUE_ESSAIS_MAX, dixieme.second)
    }

    // La relecture d'une fournée (« En voir plus » sur une salle, brief du 21 septembre 2026) :
    // s'arrête dès que `fourneeEnCours` retombe, abandon au plafond sinon.
    @Test
    fun `etatFourneeSuivant s'arrete des que fourneeEnCours retombe, sans compter d'essai de plus`() {
        val (etat, essais) = etatFourneeSuivant(fourneeEnCours = false, essaisPrecedents = 4)
        assertEquals(EtatFournee.TERMINEE, etat)
        assertEquals(4, essais)
    }

    @Test
    fun `etatFourneeSuivant compte les essais en cours jusqu'a l'abandon au plafond`() {
        var essais = 0
        var etat = EtatFournee.EN_COURS
        repeat(9) {
            val resultat = etatFourneeSuivant(fourneeEnCours = true, essaisPrecedents = essais, plafond = 10)
            etat = resultat.first
            essais = resultat.second
            assertEquals("essai $essais", EtatFournee.EN_COURS, etat)
        }
        assertEquals(9, essais)

        // Mutation : `essais > plafond` au lieu de `>=` ferait attendre un onzième essai.
        val dixieme = etatFourneeSuivant(fourneeEnCours = true, essaisPrecedents = essais, plafond = 10)
        assertEquals(EtatFournee.ABANDON, dixieme.first)
        assertEquals(10, dixieme.second)
    }
}
