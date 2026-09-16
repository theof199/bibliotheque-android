package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le Voyage (brief du 16 septembre 2026, phase 1) : les états d'une année et d'une génération,
 * en fonctions pures — sans réseau ni `ViewModel`.
 */
class VoyageEtatsTest {

    @Test
    fun `statutAnneeVoyage reconnait les trois statuts, et rien d'autre`() {
        assertEquals(StatutAnneeVoyage.FAITE, statutAnneeVoyage("faite"))
        assertEquals(StatutAnneeVoyage.OUVERTE, statutAnneeVoyage("ouverte"))
        assertEquals(StatutAnneeVoyage.VERROUILLEE, statutAnneeVoyage("verrouillee"))
        // Mutation : retourner `StatutAnneeVoyage.VERROUILLEE` par défaut au lieu de `null` ferait
        // passer une année absente de `/me/voyage` pour verrouillée plutôt qu'inconnue.
        assertNull(statutAnneeVoyage(null))
        assertNull(statutAnneeVoyage("autre chose"))
    }

    @Test
    fun `toVoyageUi indexe les annees et lit la frontiere`() {
        val reponse = VoyageResponse(
            configure = true,
            depart = 1895,
            frontiere = 1941,
            frontiere_statut = "ouverte",
            annees = listOf(AnneeVoyage(1895, "faite", vus = 2), AnneeVoyage(1941, "ouverte", vus = 1)),
        )
        val ui = reponse.toVoyageUi()

        assertEquals(true, ui.configure)
        assertEquals(1941, ui.frontiere)
        assertEquals(true, ui.frontiereOuverte)
        assertEquals(StatutAnneeVoyage.FAITE, statutVoyage(1895, ui))
        assertEquals(StatutAnneeVoyage.OUVERTE, statutVoyage(1941, ui))
        // Une année absente de la réponse : ni faite, ni ouverte, ni verrouillée — inconnue.
        assertNull(statutVoyage(1942, ui))
    }

    @Test
    fun `phraseFrontiere dit ouverte ou en preparation, et rien sans frontiere`() {
        val ouverte = VoyageUi(frontiere = 1941, frontiereOuverte = true)
        val enPreparation = VoyageUi(frontiere = 1941, frontiereOuverte = false)

        assertEquals("Tu en es à 1941", phraseFrontiere(ouverte))
        // Mutation : intervertir les deux branches du `if` de `phraseFrontiere` ferait échouer
        // cette assertion-ci comme la précédente — aucune des deux ne passerait par accident.
        assertEquals("1941 se prépare…", phraseFrontiere(enPreparation))
        assertNull(phraseFrontiere(VoyageUi(frontiere = null)))
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
}
