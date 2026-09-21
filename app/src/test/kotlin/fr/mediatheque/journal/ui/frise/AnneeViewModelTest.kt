package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.ChroniqueEcritureResponse
import fr.mediatheque.journal.api.dto.DemandeSalleEcritureResponse
import fr.mediatheque.journal.api.dto.DemandeSalleVoyage
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.FilmSalleVoyage
import fr.mediatheque.journal.api.dto.MaturiteVoyage
import fr.mediatheque.journal.api.dto.ParagrapheFilmVoyage
import fr.mediatheque.journal.api.dto.ParagrapheVoyage
import fr.mediatheque.journal.api.dto.PodiumMarcheVoyage
import fr.mediatheque.journal.api.dto.PodiumResponse
import fr.mediatheque.journal.api.dto.ProgressionVoyage
import fr.mediatheque.journal.api.dto.SalleVoyage
import fr.mediatheque.journal.api.dto.SallePlusResponse
import fr.mediatheque.journal.api.dto.TicketAnneeVoyage
import fr.mediatheque.journal.api.dto.TicketUtiliseResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * La page d'année du Voyage (brief du 21 septembre 2026, « l'année en étages », « le podium »,
 * puis « le ticket ») : l'état initial repris du fragment déjà chargé par `FriseViewModel`, la
 * relecture de la chronique (première visite, abandon, verrouillée), « En voir plus » sur une
 * salle, la demande sur Seerr, le marquage « introuvable », le podium, et `utiliserTicket` — qui a
 * remplacé le bouton provisoire « Année suivante ».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnneeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val api = FakeJournalApi()

    private fun film(id: String, tmdbId: Int, etat: String, rang: Int = 1) = FilmSalleVoyage(
        id = id,
        rang = rang,
        tmdb_id = tmdbId,
        title = "Film $tmdbId",
        realisateur = "Un réalisateur",
        raison = "Une raison.",
        etat = etat,
    )

    private fun salle(id: String, vararg films: FilmSalleVoyage, epuisee: Boolean = false, fourneeEnCours: Boolean = false) = SalleVoyage(
        id = id,
        rang = 1,
        nom = "Les essentiels",
        raison_d_etre = "Ce qu'il ne fallait pas manquer",
        cle = "essentiels",
        epuisee = epuisee,
        fournee_en_cours = fourneeEnCours,
        films = films.toList(),
    )

    private fun prete(vararg salles: SalleVoyage) = AnneeVoyageDetailResponse(
        configure = true,
        statut = "prete",
        annee = 1941,
        profondeur = 1,
        ouverture = "Une année de cinéma.",
        faits = listOf("Un fait."),
        salles = salles.toList(),
    )

    @Test
    fun `anneeUiInitiale reprend le fragment deja charge par FriseViewModel`() {
        val snapshot = AnneeVoyage(annee = 1941, statut = "ouverte", visitee = true, profondeur = 3)

        val ui = anneeUiInitiale(1941, snapshot)

        assertEquals(StatutAnneeVoyage.OUVERTE, ui.statutVoyage)
        assertEquals(3, ui.profondeur)
        assertTrue(ui.salles.isEmpty())
    }

    // Mutation : une année sans fragment (snapshot nul) doit rester sans statut connu — retourner
    // `StatutAnneeVoyage.VERROUILLEE` par défaut ferait échouer cette assertion.
    @Test
    fun `anneeUiInitiale sans fragment reste sans statut connu`() {
        val ui = anneeUiInitiale(1950, null)
        assertNull(ui.statutVoyage)
        assertEquals(0, ui.profondeur)
    }

    @Test
    fun `relire charge l'annee et passe a PRETE avec ses salles dans l'ordre`() = runTest(dispatcher) {
        api.onVoyageAnnee = {
            prete(
                salle("s2", film("f2", 2, "vu")).copy(rang = 2),
                salle("s1", film("f1", 1, "a_demander")).copy(rang = 1),
            )
        }
        val vm = AnneeViewModel(api, 1941, null) {}

        vm.relire()
        runCurrent()

        assertEquals(EtatAnnee.PRETE, vm.ui.value.etat)
        assertEquals(listOf("s1", "s2"), vm.ui.value.salles.map { it.id })
        assertEquals("Une année de cinéma.", vm.ui.value.ouverture)
        assertEquals(1, vm.ui.value.profondeur)
    }

    @Test
    fun `relire sur la premiere visite reste en preparation puis abandonne au plafond`() = runTest(dispatcher) {
        api.onVoyageAnnee = { AnneeVoyageDetailResponse(configure = true, statut = "en_preparation", annee = 1942) }
        val vm = AnneeViewModel(api, 1942, null) {}

        vm.relire()
        testScheduler.advanceUntilIdle()

        assertEquals(EtatAnnee.ABANDON, vm.ui.value.etat)
        assertEquals(CHRONIQUE_ANNEE_ESSAIS_MAX, vm.ui.value.essais)
    }

    // La forme « verrouillée » est terminale : elle ne doit jamais être confondue avec « en
    // préparation » (mutation : la faire passer par `etatChroniqueSuivant` sans ce garde-fou
    // ferait sonder l'année 36 fois pour rien, une année verrouillée ne devenant jamais « prête »).
    @Test
    fun `relire sur une annee verrouillee ne compte pas d'essai, et se relit a l'entree suivante`() = runTest(dispatcher) {
        var appels = 0
        var statut = "verrouillee"
        api.onVoyageAnnee = { appels++; AnneeVoyageDetailResponse(configure = true, statut = statut, annee = 1999, profondeur = 2) }
        val vm = AnneeViewModel(api, 1999, null) {}

        vm.relire()
        runCurrent()

        assertEquals(EtatAnnee.VERROUILLEE, vm.ui.value.etat)
        assertEquals(StatutAnneeVoyage.VERROUILLEE, vm.ui.value.statutVoyage)
        assertEquals(2, vm.ui.value.profondeur)
        assertEquals(0, vm.ui.value.essais)
        assertEquals(1, appels)

        // Une deuxième entrée relit : un ticket a pu ouvrir l'année entre-temps.
        statut = "en_preparation"
        vm.relire()
        runCurrent()
        assertEquals(2, appels)
        assertEquals(EtatAnnee.EN_PREPARATION, vm.ui.value.etat)
    }

    @Test
    fun `demander reussit et pose l'etat demande, seulement sur le bon film`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "a_demander"), film("f2", 600, "a_demander"))) }
        api.onDemanderVoyage = { DemanderVoyageResponse(demande = true) }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()

        vm.demander(500)
        runCurrent()

        val films = vm.ui.value.salles.flatMap { it.films }.associateBy { it.tmdbId }
        assertEquals("demande", films.getValue(500).etat)
        assertEquals("a_demander", films.getValue(600).etat)
        assertEquals(listOf("demanderVoyage 500"), api.calls.filter { it.startsWith("demanderVoyage") })
    }

    @Test
    fun `demander en echec envoie le message du back au bandeau, sans changer l'etat`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "a_demander"))) }
        api.onDemanderVoyage = { throw ApiError("UPSTREAM_UNAVAILABLE", "Seerr ne répond pas. Réessaie plus tard.", retryable = true, status = 503) }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()

        val messages = mutableListOf<String>()
        val job = launch { vm.messages.collect { messages += it } }

        vm.demander(500)
        runCurrent()

        assertEquals(listOf("Seerr ne répond pas. Réessaie plus tard."), messages)
        assertEquals("a_demander", vm.ui.value.salles.first().films.first().etat)
        job.cancel()
    }

    // Le back recalcule `etat` à chaque lecture (contrairement aux essentiels du 16 septembre
    // 2026) : marquer introuvable relit les salles plutôt que de deviner un état localement.
    @Test
    fun `marquer introuvable relit les salles depuis le back`() = runTest(dispatcher) {
        var introuvable = false
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, if (introuvable) "introuvable" else "sur_le_plex"))) }
        api.onMarquerIntrouvable = { introuvable = true }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        assertEquals("sur_le_plex", vm.ui.value.salles.first().films.first().etat)

        vm.marquerIntrouvable(500)
        runCurrent()

        assertEquals("introuvable", vm.ui.value.salles.first().films.first().etat)
        assertEquals(listOf("marquerIntrouvable 500"), api.calls.filter { it.startsWith("marquerIntrouvable") })
    }

    @Test
    fun `voirPlus epuisee tout de suite ne relit rien de plus`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1")) }
        api.onVoyageSallePlus = { SallePlusResponse(statut = "epuisee") }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        val appelsAvant = api.calls.count { it.startsWith("voyageAnnee") }

        vm.voirPlus("s1")
        runCurrent()

        assertTrue(vm.ui.value.salles.first().epuisee)
        assertFalse(vm.ui.value.salles.first().fourneeEnCours)
        assertEquals(appelsAvant, api.calls.count { it.startsWith("voyageAnnee") })
    }

    // La relecture d'une fournée s'arrête dès que `fournee_en_cours` retombe — avec le nouveau
    // film déjà dans la salle à ce moment-là (spec du 19 septembre 2026, §3 : « nouveaux films »).
    @Test
    fun `voirPlus enfile une fournee et relit jusqu'a ce que fournee_en_cours retombe`() = runTest(dispatcher) {
        var relectures = 0
        api.onVoyageAnnee = {
            relectures++
            when (relectures) {
                1 -> prete(salle("s1", film("f1", 500, "vu"), fourneeEnCours = false))
                2 -> prete(salle("s1", film("f1", 500, "vu"), fourneeEnCours = true))
                else -> prete(salle("s1", film("f1", 500, "vu"), film("f2", 501, "a_demander"), fourneeEnCours = false))
            }
        }
        api.onVoyageSallePlus = { SallePlusResponse(statut = "en_preparation") }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        assertFalse(vm.ui.value.salles.first().fourneeEnCours)

        vm.voirPlus("s1")
        // La tuile se remplit tout de suite, avant même la première relecture.
        runCurrent()
        assertTrue(vm.ui.value.salles.first().fourneeEnCours)

        testScheduler.advanceUntilIdle()

        assertFalse(vm.ui.value.salles.first().fourneeEnCours)
        assertEquals(listOf(500, 501), vm.ui.value.salles.first().films.map { it.tmdbId })
    }

    // Le ticket (décision 4 du brief du 21 septembre 2026) a remplacé « Année suivante » : encaisse
    // le ticket que `ui.ticket` porte déjà (pas `annee + 1` recalculé), pas celui d'une autre
    // année passée en paramètre — `utiliserTicket` ne prend d'ailleurs plus aucun paramètre.
    @Test
    fun `utiliserTicket reussit, passe l'annee ouverte, marque le ticket utilise et appelle le rappel`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1")).copy(ticket = TicketAnneeVoyage(1942, "2026-09-21T10:00:00.000Z")) }
        api.onUtiliserTicket = { TicketUtiliseResponse(annee_en_cours = 1942) }
        val vm = AnneeViewModel(api, 1941, AnneeVoyage(1941, "en_cours", visitee = true)) {}
        vm.relire()
        runCurrent()

        var rappelee = false
        vm.utiliserTicket { rappelee = true }
        runCurrent()

        assertEquals(StatutAnneeVoyage.OUVERTE, vm.ui.value.statutVoyage)
        assertEquals(true, vm.ui.value.ticket?.utilise)
        assertTrue(rappelee)
        assertEquals(listOf("utiliserTicket 1942"), api.calls.filter { it.startsWith("utiliserTicket") })
    }

    @Test
    fun `utiliserTicket en echec n'appelle pas le rappel, ni ne marque le ticket utilise`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1")).copy(ticket = TicketAnneeVoyage(1942, "2026-09-21T10:00:00.000Z")) }
        api.onUtiliserTicket = { throw ApiError("HTTP_500", "Erreur imprevue.", retryable = true, status = 500) }
        val vm = AnneeViewModel(api, 1941, AnneeVoyage(1941, "en_cours", visitee = true)) {}
        vm.relire()
        runCurrent()

        var rappelee = false
        vm.utiliserTicket { rappelee = true }
        runCurrent()

        assertFalse(rappelee)
        assertEquals(StatutAnneeVoyage.EN_COURS, vm.ui.value.statutVoyage)
        assertEquals(false, vm.ui.value.ticket?.utilise)
    }

    // Mutation : ne pas garder ce garde-fou ferait appeler `POST .../utiliser` une seconde fois
    // sur un ticket déjà encaissé, si la ligne du bas restait visible une frame de trop.
    @Test
    fun `utiliserTicket sans ticket en attente ne fait rien`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1")) }
        val vm = AnneeViewModel(api, 1941, AnneeVoyage(1941, "en_cours", visitee = true)) {}
        vm.relire()
        runCurrent()

        var rappelee = false
        vm.utiliserTicket { rappelee = true }
        runCurrent()

        assertFalse(rappelee)
        assertEquals(emptyList<String>(), api.calls.filter { it.startsWith("utiliserTicket") })
    }

    // Le `PUT` répond déjà le podium complet, mais `poserPodium` relit l'année entière plutôt que
    // de ne recopier que cette réponse (commentaire de la méthode) : c'est cette relecture, et non
    // le retour du `PUT`, qui met `vm.ui.value.podium` à jour ci-dessous.
    @Test
    fun `poserPodium ecrit puis relit le podium et appelle le rappel`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))) }
        var appelsPut = 0
        api.onPoserPodium = { _, _, _ -> appelsPut++; PodiumResponse(listOf(PodiumMarcheVoyage(1, 500, null, "Film 500", null), null, null)) }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        assertEquals(listOf(null, null, null), vm.ui.value.podium)

        var rappelee = false
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))).copy(podium = listOf(PodiumMarcheVoyage(1, 500, null, "Film 500", null), null, null)) }
        vm.poserPodium(1, CandidatPodium.Film(500, "Film 500", null, null)) { rappelee = true }
        runCurrent()

        assertEquals(1, appelsPut)
        assertEquals(500, vm.ui.value.podium[0]?.tmdbId)
        assertTrue(rappelee)
        assertEquals(listOf("poserPodium 1941 1 500"), api.calls.filter { it.startsWith("poserPodium") })
    }

    @Test
    fun `poserPodium en echec envoie le message du back au bandeau, sans appeler le rappel`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))) }
        api.onPoserPodium = { _, _, _ -> throw ApiError("VALIDATION", "Ce film n’est pas de cette annee-la.", retryable = false, status = 400) }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()

        val messages = mutableListOf<String>()
        val job = launch { vm.messages.collect { messages += it } }

        var rappelee = false
        vm.poserPodium(1, CandidatPodium.Film(500, "Film 500", null, null)) { rappelee = true }
        runCurrent()

        assertEquals(listOf("Ce film n’est pas de cette annee-la."), messages)
        assertFalse(rappelee)
        assertEquals(listOf(null, null, null), vm.ui.value.podium)
        job.cancel()
    }

    @Test
    fun `retirerPodium vide la marche, relit le podium et appelle le rappel`() = runTest(dispatcher) {
        api.onVoyageAnnee = {
            prete(salle("s1", film("f1", 500, "vu"))).copy(podium = listOf(PodiumMarcheVoyage(1, 500, null, "Film 500", null), null, null))
        }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        assertEquals(500, vm.ui.value.podium[0]?.tmdbId)

        var appelsDelete = 0
        api.onRetirerPodium = { _, _ -> appelsDelete++ }
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))) }
        var rappelee = false
        vm.retirerPodium(1) { rappelee = true }
        runCurrent()

        assertEquals(1, appelsDelete)
        assertNull(vm.ui.value.podium[0])
        assertTrue(rappelee)
        assertEquals(listOf("retirerPodium 1941 1"), api.calls.filter { it.startsWith("retirerPodium") })
    }

    // « Ajouter à la chronique » (décision 1 du brief du 21 septembre 2026, « la chronique et les
    // salles ») : un paragraphe déjà écrit (`200 ecrit`) s'affiche directement, sans passer par la
    // relecture.
    @Test
    fun `ajouterChronique deja ecrit affiche le paragraphe tout de suite, sans relire`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))) }
        val paragraphe = ParagrapheVoyage(
            id = "p1",
            tmdb_id = 500,
            titre = "Un titre",
            texte = "Le texte du paragraphe.",
            ecrit_le = "2026-09-21T21:30:00.000Z",
            film = ParagrapheFilmVoyage("Film 500", null),
        )
        api.onVoyageChronique = { _, _ -> ChroniqueEcritureResponse(statut = "ecrit", paragraphe = paragraphe) }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        val appelsAvant = api.calls.count { it.startsWith("voyageAnnee") }
        val cle: Pair<Int?, String?> = 500 to null

        vm.ajouterChronique(500, null)
        runCurrent()

        assertEquals(listOf("Le texte du paragraphe."), vm.ui.value.paragraphes.map { it.texte })
        assertFalse(cle in vm.ui.value.paragraphesEnCours)
        // Mutation : appeler `voyageAnnee` ici régénérerait un paragraphe déjà écrit — le contrat
        // dit `200 ecrit` sans jamais rappeler le chroniqueur.
        assertEquals(appelsAvant, api.calls.count { it.startsWith("voyageAnnee") })
    }

    // `202 en_preparation` marque tout de suite le bouton « en cours » (optimiste), puis la
    // relecture s'arrête dès que `paragraphes` porte le film.
    @Test
    fun `ajouterChronique en preparation marque en cours puis relit jusqu'au paragraphe`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))) }
        api.onVoyageChronique = { _, _ -> ChroniqueEcritureResponse(statut = "en_preparation") }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        val cle: Pair<Int?, String?> = 500 to null

        vm.ajouterChronique(500, null)
        runCurrent()
        assertTrue(cle in vm.ui.value.paragraphesEnCours)
        assertTrue(vm.ui.value.paragraphes.isEmpty())

        val paragraphe = ParagrapheVoyage(
            id = "p1",
            tmdb_id = 500,
            titre = "Un titre",
            texte = "Le texte.",
            ecrit_le = "2026-09-21T21:30:00.000Z",
            film = ParagrapheFilmVoyage("Film 500", null),
        )
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))).copy(paragraphes = listOf(paragraphe)) }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("Le texte."), vm.ui.value.paragraphes.map { it.texte })
        assertFalse(cle in vm.ui.value.paragraphesEnCours)
    }

    // Abandon au plafond : le paragraphe n'arrive jamais, la relecture cesse et « en cours » retombe
    // — mutation : ne jamais retirer la clé laisserait le bouton bloqué sur « écrit… » pour toujours.
    @Test
    fun `ajouterChronique abandonne au plafond et retire l'etat en cours`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1", film("f1", 500, "vu"))) }
        api.onVoyageChronique = { _, _ -> ChroniqueEcritureResponse(statut = "en_preparation") }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()
        val cle: Pair<Int?, String?> = 500 to null

        vm.ajouterChronique(500, null)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.ui.value.paragraphes.isEmpty())
        assertFalse(cle in vm.ui.value.paragraphesEnCours)
    }

    // « Ouvrir une nouvelle salle » (décision 3) : enfile la demande, marque l'étagère fantôme tout
    // de suite, puis relit jusqu'à ce que la salle apparaisse (`creee`, `demande_salle` retombé nul).
    @Test
    fun `ouvrirNouvelleSalle marque en cours puis relit jusqu'a la creation`() = runTest(dispatcher) {
        api.onVoyageAnnee = { prete(salle("s1")) }
        api.onVoyageDemanderSalle = { _, _ -> DemandeSalleEcritureResponse(demande_id = "d1") }
        val vm = AnneeViewModel(api, 1941, null) {}
        vm.relire()
        runCurrent()

        vm.ouvrirNouvelleSalle("la comédie italienne")
        runCurrent()
        assertEquals("en_cours", vm.ui.value.demandeSalle?.statut)

        api.onVoyageAnnee = { prete(salle("s1"), salle("s2")) } // demande_salle absente : acceptée
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.demandeSalle)
        assertEquals(listOf("s1", "s2"), vm.ui.value.salles.map { it.id })
    }

    // Le motif d'un refus marque la demande vue une seule fois, dès son premier affichage (décision 3).
    @Test
    fun `une demande refusee marque vue une seule fois`() = runTest(dispatcher) {
        var vueAppels = 0
        api.onVoyageAnnee = {
            prete(salle("s1")).copy(demande_salle = DemandeSalleVoyage("d1", "la comédie italienne", "refusee", "Rien qui mérite une salle à part."))
        }
        api.onVoyageDemandeSalleVue = { vueAppels++ }
        val vm = AnneeViewModel(api, 1941, null) {}

        vm.relire()
        runCurrent()

        assertEquals("refusee", vm.ui.value.demandeSalle?.statut)
        assertEquals("Rien qui mérite une salle à part.", vm.ui.value.demandeSalle?.motif)
        assertEquals(1, vueAppels)
        assertEquals(listOf("voyageDemandeSalleVue d1"), api.calls.filter { it.startsWith("voyageDemandeSalleVue") })

        // La même demande revue plus tard (une nouvelle salle demandée pendant que le back n'a pas
        // encore digéré la vue) ne la fait pas marquer vue deux fois — mutation : retirer la garde
        // `demandeSalleVueEnvoyeePour` ferait remonter ce compte à 2.
        api.onVoyageDemanderSalle = { _, _ -> DemandeSalleEcritureResponse(demande_id = "d2") }
        vm.ouvrirNouvelleSalle("une autre salle")
        testScheduler.advanceUntilIdle()

        assertEquals(1, vueAppels)
    }

    // La ligne du bas de la fiche d'année (décision 4 du brief du 21 septembre 2026, « le ticket »),
    // fonction pure : le ticket non utilisé prime sur le verdict de maturité.
    @Test
    fun `ligneBasAnnee montre le ticket qui attend quand il n'est pas encore utilise`() {
        val ligne = ligneBasAnnee(TicketAnneeUi(1942, utilise = false), MaturiteUi(mure = true, motif = "Peu importe"))
        assertEquals(LigneBasAnnee.TicketEnAttente(1942), ligne)
    }

    // Mutation : ne pas exclure un ticket déjà utilisé ferait réafficher « t'attend » pour
    // toujours, même après que la ligne aurait dû retomber à rien.
    @Test
    fun `ligneBasAnnee ne montre rien quand le ticket est deja utilise`() {
        val ligne = ligneBasAnnee(TicketAnneeUi(1942, utilise = true), null)
        assertEquals(LigneBasAnnee.Rien, ligne)
    }

    @Test
    fun `ligneBasAnnee montre le verdict negatif sans ticket`() {
        val ligne = ligneBasAnnee(null, MaturiteUi(mure = false, motif = "Il reste des essentiels a voir"))
        assertEquals(LigneBasAnnee.PasEncoreMure("Il reste des essentiels a voir"), ligne)
    }

    @Test
    fun `ligneBasAnnee ne montre rien pour un verdict positif sans ticket encore emis`() {
        val ligne = ligneBasAnnee(null, MaturiteUi(mure = true, motif = "Peu importe"))
        assertEquals(LigneBasAnnee.Rien, ligne)
    }

    @Test
    fun `ligneBasAnnee ne montre rien sans ticket ni maturite`() {
        assertEquals(LigneBasAnnee.Rien, ligneBasAnnee(null, null))
    }

    // La ligne de progression, sous la profondeur (décision 2 du brief du 21 septembre 2026,
    // « les récompenses »), fonction pure : « Aucun essentiel encore » avant `essentiels_total`,
    // « *N* sur *M* » ensuite, le pluriel sur chaque compte. Mutation : accorder « essentiel(s) »
    // ou « salle(s) complète(s) » sur l'autre nombre du couple ferait échouer les deux dernières
    // assertions ; ne pas court-circuiter sur `essentielsTotal == 0` ferait apparaître « 0 essentiel
    // sur 0 » au lieu du message dédié.
    @Test
    fun `ligneProgression donne le compte au pluriel, ou Aucun essentiel encore`() {
        assertEquals("Aucun essentiel encore", ligneProgression(ProgressionUi(0, 0, 0, 0)))
        assertEquals(
            "1 essentiel sur 5 · 1 salle complète sur 4",
            ligneProgression(ProgressionUi(essentielsVus = 1, essentielsTotal = 5, sallesCompletes = 1, sallesAutres = 4)),
        )
        assertEquals(
            "3 essentiels sur 5 · 2 salles complètes sur 4",
            ligneProgression(ProgressionUi(essentielsVus = 3, essentielsTotal = 5, sallesCompletes = 2, sallesAutres = 4)),
        )
    }

    // Nulle (la ligne ne s'affiche pas) tant que la progression n'est pas encore chargée — jamais
    // confondue avec « Aucun essentiel encore », qui dit que le back a répondu sans essentiel.
    @Test
    fun `ligneProgression est nulle sans progression chargee`() {
        assertNull(ligneProgression(null))
    }

    // La récompense et la progression se lisent sur une année prête (étape 5, « les récompenses »)
    // — jumeau du test `relire charge l'annee...` plus haut, qui vérifie déjà `ouverture` et
    // `profondeur`. Mutation : lire `reponse.recompense`/`reponse.progression` sans passer par
    // `recompenseDe`/`versUi`, ou les ignorer, laisserait `ui.recompense`/`ui.progression` à `null`.
    @Test
    fun `relire charge la recompense et la progression sur une annee prete`() = runTest(dispatcher) {
        api.onVoyageAnnee = {
            prete(salle("s1")).copy(
                recompense = "lion",
                progression = ProgressionVoyage(essentiels_vus = 3, essentiels_total = 5, salles_completes = 2, salles_autres = 4),
            )
        }
        val vm = AnneeViewModel(api, 1941, null) {}

        vm.relire()
        runCurrent()

        assertEquals(Recompense.LION, vm.ui.value.recompense)
        assertEquals(ProgressionUi(3, 5, 2, 4), vm.ui.value.progression)
    }
}
