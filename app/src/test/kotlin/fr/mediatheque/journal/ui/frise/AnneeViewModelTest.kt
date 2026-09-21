package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.FilmSalleVoyage
import fr.mediatheque.journal.api.dto.MaturiteVoyage
import fr.mediatheque.journal.api.dto.PodiumMarcheVoyage
import fr.mediatheque.journal.api.dto.PodiumResponse
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
    fun `relire sur une annee verrouillee s'arrete tout de suite, sans compter d'essai`() = runTest(dispatcher) {
        var appels = 0
        api.onVoyageAnnee = { appels++; AnneeVoyageDetailResponse(configure = true, statut = "verrouillee", annee = 1999, profondeur = 2) }
        val vm = AnneeViewModel(api, 1999, null) {}

        vm.relire()
        runCurrent()

        assertEquals(EtatAnnee.VERROUILLEE, vm.ui.value.etat)
        assertEquals(StatutAnneeVoyage.VERROUILLEE, vm.ui.value.statutVoyage)
        assertEquals(2, vm.ui.value.profondeur)
        assertEquals(0, vm.ui.value.essais)
        assertEquals(1, appels)

        // Une deuxième entrée sur l'écran ne relance rien de plus.
        vm.relire()
        runCurrent()
        assertEquals(1, appels)
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
}
