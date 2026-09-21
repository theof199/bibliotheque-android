package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.SeancePriseCourtVoyage
import fr.mediatheque.journal.api.dto.SeancePriseFilmVoyage
import fr.mediatheque.journal.api.dto.SeancePriseVoyage
import fr.mediatheque.journal.api.dto.TamponVoyage
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

    // Le passeport (étape 5 du brief du 21 septembre 2026, « les récompenses ») : `tampons` se lit
    // tel quel. Mutation : oublier de le passer à `VoyageUi` laisserait `ui.tampons` toujours vide.
    @Test
    fun `toVoyageUi lit les tampons du passeport`() {
        val reponse = VoyageResponse(
            configure = true,
            tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z")),
        )
        val ui = reponse.toVoyageUi()

        assertEquals(listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z")), ui.tampons)
    }

    @Test
    fun `toVoyageUi sans tampon reste vide`() {
        val ui = VoyageResponse(configure = true).toVoyageUi()
        assertTrue(ui.tampons.isEmpty())
    }

    // La séance prise (décision 4 du brief du 21 septembre 2026, « la séance ») : se lit quand
    // elle est présente, reste nulle sinon — jumeau des tests du ticket ci-dessus.
    @Test
    fun `toVoyageUi lit la seance prise quand elle est presente`() {
        val reponse = VoyageResponse(
            configure = true,
            seance_prise = SeancePriseVoyage(
                id = "sc-1",
                annee = 1941,
                long = SeancePriseFilmVoyage("Le Faucon maltais", "https://exemple/faucon.jpg"),
                court = SeancePriseCourtVoyage("Un chien andalou"),
            ),
        )
        val ui = reponse.toVoyageUi()

        assertEquals(
            SeancePriseUi(1941, "Le Faucon maltais", "https://exemple/faucon.jpg", "Un chien andalou"),
            ui.seancePrise,
        )
    }

    @Test
    fun `toVoyageUi sans seance prise reste nulle`() {
        val ui = VoyageResponse(configure = true).toVoyageUi()
        assertNull(ui.seancePrise)
    }

    // Le texte de la ligne « Ce soir » (décision 4) : le long seul, ou le long et le court réunis
    // par un « + ». Fonction pure.
    // Mutation : inverser la condition afficherait le court seul, ou « + » sans court.
    @Test
    fun `texteCeSoir donne le long seul, ou le long et le court`() {
        assertEquals("Le Faucon maltais", texteCeSoir(SeancePriseUi(1941, "Le Faucon maltais", null, null)))
        assertEquals(
            "Le Faucon maltais + Un chien andalou",
            texteCeSoir(SeancePriseUi(1941, "Le Faucon maltais", null, "Un chien andalou")),
        )
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

    // La relecture d'un paragraphe (« Ajouter à la chronique », décision 1 du brief du 21 septembre
    // 2026, « la chronique et les salles ») : s'arrête dès qu'il est là, abandon au plafond sinon —
    // jumeau d'`etatFourneeSuivant` et d'`etatRelectureTicketSuivant`.
    @Test
    fun `etatParagrapheSuivant s'arrete des que le paragraphe est trouve, sans compter d'essai de plus`() {
        val (etat, essais) = etatParagrapheSuivant(paragrapheTrouve = true, essaisPrecedents = 4)
        assertEquals(EtatChronique.PRETE, etat)
        assertEquals(4, essais)
    }

    @Test
    fun `etatParagrapheSuivant compte les essais jusqu'a l'abandon au plafond`() {
        var essais = 0
        var etat = EtatChronique.EN_PREPARATION
        repeat(2) {
            val resultat = etatParagrapheSuivant(paragrapheTrouve = false, essaisPrecedents = essais, plafond = 3)
            etat = resultat.first
            essais = resultat.second
            assertEquals("essai $essais", EtatChronique.EN_PREPARATION, etat)
        }
        assertEquals(2, essais)

        // Le troisième essai, et pas avant (mutation : `essais > plafond` au lieu de `>=` ferait
        // attendre un quatrième essai avant l'abandon).
        val troisieme = etatParagrapheSuivant(paragrapheTrouve = false, essaisPrecedents = essais, plafond = 3)
        assertEquals(EtatChronique.ABANDON, troisieme.first)
        assertEquals(3, troisieme.second)
    }

    // L'éligibilité du bouton « Ajouter à la chronique » sur l'écran de correction (décision 1) :
    // seule une année en cours ou ouverte compte — mutation : accepter `VERROUILLEE` ou `null`
    // proposerait la chronique sur une année qui ne l'accepterait pas côté back (`404`).
    @Test
    fun `eligibleChroniqueDepuisEdition n'accepte que ouverte ou en cours`() {
        assertTrue(eligibleChroniqueDepuisEdition(StatutAnneeVoyage.OUVERTE))
        assertTrue(eligibleChroniqueDepuisEdition(StatutAnneeVoyage.EN_COURS))
        assertFalse(eligibleChroniqueDepuisEdition(StatutAnneeVoyage.VERROUILLEE))
        assertFalse(eligibleChroniqueDepuisEdition(null))
    }

    // L'état de la zone « Ouvrir une nouvelle salle » (décision 3) : bouton, étagère fantôme ou
    // motif du refus — jamais deux à la fois pour le même statut.
    @Test
    fun `etatZoneSalleVoyage distingue bouton, fantome et refus`() {
        assertEquals(EtatZoneSalleVoyage.FANTOME, etatZoneSalleVoyage("en_cours"))
        assertEquals(EtatZoneSalleVoyage.REFUS, etatZoneSalleVoyage("refusee"))
        assertEquals(EtatZoneSalleVoyage.BOUTON, etatZoneSalleVoyage(null))
        // Mutation : `creee` n'arrive jamais en pratique (le back nullifie `demande_salle` une fois
        // acceptée) — il doit néanmoins retomber sur le bouton plutôt que de planter ou bloquer.
        assertEquals(EtatZoneSalleVoyage.BOUTON, etatZoneSalleVoyage("creee"))
    }

    // Le bandeau à la fin d'une composition qui n'a rien produit de neuf (décision du propriétaire
    // du 21 septembre 2026, « une composition abandonnée le dit ») : nul dès qu'une séance de plus
    // est apparue, quel que soit l'état — c'est le seul cas de succès.
    // Mutation : ne pas comparer `seancesAvant` à `seancesApres` (par exemple rendre le message
    // « n'a pas pu composer » dès que `etat == PRETE`, sans regarder si le compte a grandi) ferait
    // échouer cette assertion, qui attend `null` alors même que `etat == PRETE`.
    @Test
    fun `messageEchecComposition est nul des qu'une seance de plus est apparue`() {
        assertNull(messageEchecComposition(EtatChronique.PRETE, seancesAvant = 2, seancesApres = 3))
        assertNull(messageEchecComposition(EtatChronique.ABANDON, seancesAvant = 2, seancesApres = 3))
    }

    // Sans séance de plus : un message distinct entre l'abandon au plafond (le back ne répond plus
    // du tout) et une composition qui s'arrête d'elle-même sans rien produire (`seance_en_cours`
    // retombe, mais aucune séance neuve).
    @Test
    fun `messageEchecComposition distingue l'abandon au plafond de l'arret sans rien produire`() {
        assertEquals(
            "Le chroniqueur n’a pas répondu, reviens plus tard.",
            messageEchecComposition(EtatChronique.ABANDON, seancesAvant = 2, seancesApres = 2),
        )
        assertEquals(
            "Le chroniqueur n’a pas pu composer ce soir, réessaie.",
            messageEchecComposition(EtatChronique.PRETE, seancesAvant = 2, seancesApres = 2),
        )
    }

    // Un compte qui a baissé (une séance disparue entre-temps, cas limite) n'est jamais un succès
    // non plus — seule une hausse stricte compte.
    @Test
    fun `messageEchecComposition ne prend pas une baisse du compte pour un succes`() {
        assertEquals(
            "Le chroniqueur n’a pas pu composer ce soir, réessaie.",
            messageEchecComposition(EtatChronique.PRETE, seancesAvant = 3, seancesApres = 2),
        )
    }
}
