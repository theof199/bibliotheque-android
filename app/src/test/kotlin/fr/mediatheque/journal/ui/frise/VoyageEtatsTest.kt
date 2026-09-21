package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.TicketAMontrerVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // Le ticket (brief du 21 septembre 2026, « le ticket ») : `ticket_a_montrer` se lit, ou reste
    // nul, sans rien perdre du reste de la réponse.
    @Test
    fun `toVoyageUi lit le ticket a montrer quand il est present`() {
        val reponse = VoyageResponse(
            configure = true,
            annee_en_cours = 1942,
            ticket_a_montrer = TicketAMontrerVoyage(1942, "Les essentiels de 1941 sont vus.", "2026-09-21T10:00:00.000Z"),
        )
        val ui = reponse.toVoyageUi()

        assertEquals(TicketAMontrerUi(1942, "Les essentiels de 1941 sont vus."), ui.ticketAMontrer)
    }

    @Test
    fun `toVoyageUi sans ticket a montrer reste nul`() {
        val ui = VoyageResponse(configure = true).toVoyageUi()
        assertNull(ui.ticketAMontrer)
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

    // La relecture après un enregistrement (décision 2 du brief du 21 septembre 2026, « le
    // ticket ») : seul un film de l'année en cours peut avoir fait naître un ticket.
    @Test
    fun `doitRelireApresCreation seulement quand le film est de l'annee en cours`() {
        assertTrue(doitRelireApresCreation(anneeFilm = 1942, anneeEnCours = 1942))
        assertFalse(doitRelireApresCreation(anneeFilm = 1941, anneeEnCours = 1942))
        assertFalse(doitRelireApresCreation(anneeFilm = 1943, anneeEnCours = 1942))
        // Mutation : traiter une année de film inconnue comme « l'année en cours » relirait après
        // chaque film sans année, quelle que soit sa vraie date.
        assertFalse(doitRelireApresCreation(anneeFilm = null, anneeEnCours = 1942))
    }

    @Test
    fun `etatRelectureTicketSuivant s'arrete des que le ticket est trouve, sans compter d'essai de plus`() {
        val (etat, essais) = etatRelectureTicketSuivant(ticketTrouve = true, essaisPrecedents = 4)
        assertEquals(EtatRelectureTicket.TROUVE, etat)
        assertEquals(4, essais)
    }

    @Test
    fun `etatRelectureTicketSuivant compte les essais jusqu'a l'abandon au douzieme`() {
        var essais = 0
        var etat = EtatRelectureTicket.EN_COURS
        repeat(11) {
            val resultat = etatRelectureTicketSuivant(ticketTrouve = false, essaisPrecedents = essais)
            etat = resultat.first
            essais = resultat.second
            assertEquals("essai $essais", EtatRelectureTicket.EN_COURS, etat)
        }
        assertEquals(11, essais)

        // Le douzième essai, et pas avant (mutation : `essais > TICKET_RELECTURE_ESSAIS_MAX` au
        // lieu de `>=` ferait attendre un treizième essai avant l'abandon — plus d'une minute).
        val douzieme = etatRelectureTicketSuivant(ticketTrouve = false, essaisPrecedents = essais)
        assertEquals(EtatRelectureTicket.ABANDON, douzieme.first)
        assertEquals(TICKET_RELECTURE_ESSAIS_MAX, douzieme.second)
    }
}
