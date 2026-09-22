package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.TamponVoyage
import fr.mediatheque.journal.api.dto.TicketAMontrerVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private fun page(vararg externalIds: String, next: String? = null) =
        JournalResponse(
            externalIds.map { FakeJournalApi.item("m-$it", "2026-01-10", null, emptyList(), null, externalId = it) },
            next,
        )

    // Jumeau de `FilmsViewModel`/`AuCineViewModel` : `Root.kt` déclenche le premier chargement,
    // pas le constructeur.
    @Test
    fun `la construction ne charge rien, seul refresh declenche les appels`() = runTest(dispatcher) {
        api.onJournal = { page("1") }
        val vm = FriseViewModel(api) { expire++ }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), api.calls)
    }

    // Une panne du Plex (Seerr injoignable, service non configuré) ne doit pas priver la Frise
    // des films déjà vus : elle garde les vus, sans `ErrorBlock` bloquant (brief du 15 septembre
    // 2026). Mutation : propager l'erreur du Plex dans `ui.error` comme celle du journal ferait
    // échouer la troisième assertion.
    @Test
    fun `une panne du plex garde les vus, sans erreur bloquante`() = runTest(dispatcher) {
        api.onJournal = { cursor -> if (cursor == null) page("1", "2") else JournalResponse(emptyList(), null) }
        api.onPlex = { throw FakeJournalApi.network() }

        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.error)
        assertEquals(1, vm.ui.value.annees.size)
        assertEquals(2, vm.ui.value.annees.first().vus.size)
        assertTrue(vm.ui.value.annees.first().aVoir.isEmpty())
        assertEquals(false, vm.ui.value.plexConfigure)
        assertEquals(0, expire)
    }

    // `refresh()` charge le journal en entier avant de construire la frise — pas seulement la
    // première page. Mutation : s'arrêter à la première page laisserait `annees` à une seule
    // entrée (année 2026 pour "1"), au lieu des deux années attendues ici.
    @Test
    fun `refresh charge toutes les pages du journal, pas seulement la premiere`() = runTest(dispatcher) {
        var appelsJournal = 0
        api.onJournal = { cursor ->
            appelsJournal++
            when (cursor) {
                null -> JournalResponse(
                    listOf(FakeJournalApi.item("m1", "2026-01-10", null, emptyList(), null, externalId = "1", year = 2026)),
                    "page-2",
                )
                "page-2" -> JournalResponse(
                    listOf(FakeJournalApi.item("m2", "2020-01-10", null, emptyList(), null, externalId = "2", year = 2020)),
                    null,
                )
                else -> error("curseur inattendu : $cursor")
            }
        }
        api.onPlex = { PlexResponse(configure = true) }

        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(2, appelsJournal) // page 1 (next_cursor = "page-2"), page 2 (next_cursor = null) : arrêt
        assertEquals(listOf(2020, 2026), vm.ui.value.annees.map { it.annee })
    }

    // Un plex configuré et peuplé construit bien la frise, `ensuite` compris — le chemin nominal,
    // par-dessus la fonction pure déjà testée dans `FriseTest`.
    @Test
    fun `refresh construit la frise et ensuite depuis un plex peuple`() = runTest(dispatcher) {
        api.onJournal = { page() }
        api.onPlex = {
            PlexResponse(
                configure = true,
                calcule_le = "2026-09-15T09:00:00.000Z",
                films = listOf(PlexFilm(tmdb_id = 27205, title = "Inception", year = 2010, demande_le = "2026-09-10T00:00:00.000Z")),
            )
        }

        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(2010, vm.ui.value.anneeEnCours)
        assertEquals(27205, vm.ui.value.ensuite?.tmdb_id)
        assertEquals(true, vm.ui.value.plexConfigure)
    }

    // Correctif du 22 septembre 2026 (« la fiche du Voyage se relit après un enregistrement ») :
    // le journal ne se rechargeait qu'à l'entrée sur les écrans de la Frise ou l'accueil, jamais en
    // y revenant depuis le formulaire. `refreshApresEnregistrement()` recharge le journal comme
    // `refresh()` (la fausse API a reçu `journal` une seconde fois, et `ui` porte la nouvelle note)
    // et incrémente `ui.enregistrements`, que `Screen.Annee`/`Screen.FicheVoyage` (`Root.kt`)
    // collectent pour relire leurs salles.
    @Test
    fun `refreshApresEnregistrement recharge le journal et incremente le compteur`() = runTest(dispatcher) {
        api.onJournal = { page("1") }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        val appelsAvant = api.calls.count { it.startsWith("journal") }
        assertEquals(0, vm.ui.value.enregistrements)
        assertNull(vm.ui.value.annees.single().vus.single().entry.rating)

        api.onJournal = {
            JournalResponse(listOf(FakeJournalApi.item("m-1", "2026-01-10", 5, emptyList(), null, externalId = "1")), null)
        }
        // Mutation : retirer l'appel à `refresh()` du corps de `refreshApresEnregistrement()`
        // laisserait le compte d'appels et la note inchangés ci-dessous, seul le compteur bougerait.
        vm.refreshApresEnregistrement()
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.ui.value.enregistrements)
        assertEquals(5, vm.ui.value.annees.single().vus.single().entry.rating)
        assertEquals(appelsAvant + 1, api.calls.count { it.startsWith("journal") })
    }

    @Test
    fun `un 401 sur le journal previent la session`() = runTest(dispatcher) {
        api.onJournal = { throw FakeJournalApi.unauthorized() }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
        assertNull(vm.ui.value.error)
    }

    // Le ticket (décision 2 du brief du 21 septembre 2026, « le ticket »).

    @Test
    fun `relireApresCreation ne relit rien quand le film n'est pas de l'annee en cours`() = runTest(dispatcher) {
        api.onJournal = { page() }
        api.onVoyage = { VoyageResponse(configure = true, annee_en_cours = 1942) }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        val appelsAvant = api.calls.count { it == "voyage" }

        vm.relireApresCreation(1941)
        testScheduler.advanceUntilIdle()

        assertEquals(appelsAvant, api.calls.count { it == "voyage" })
        assertNull(vm.ui.value.voyage.ticketAMontrer)
    }

    // Relit toutes les cinq secondes jusqu'à ce que `ticket_a_montrer` soit là — ici au troisième
    // essai — et s'arrête net : un essai de plus après l'avoir trouvé prouverait qu'elle continue
    // à tort.
    @Test
    fun `relireApresCreation relit jusqu'a trouver le ticket, puis s'arrete`() = runTest(dispatcher) {
        api.onJournal = { page() }
        api.onVoyage = { VoyageResponse(configure = true, annee_en_cours = 1942) }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        // Le compte repart de zéro ici, après le chargement initial : seules les relectures qui
        // suivent `relireApresCreation` comptent, pas l'appel de `refresh()` ci-dessus.
        var relectures = 0
        api.onVoyage = {
            relectures++
            if (relectures < 3) {
                VoyageResponse(configure = true, annee_en_cours = 1942)
            } else {
                VoyageResponse(
                    configure = true,
                    annee_en_cours = 1942,
                    ticket_a_montrer = TicketAMontrerVoyage(1943, "Un motif.", "2026-09-21T10:00:00.000Z"),
                )
            }
        }

        vm.relireApresCreation(1942)
        testScheduler.advanceUntilIdle()

        assertEquals(TicketAMontrerUi(1943, "Un motif."), vm.ui.value.voyage.ticketAMontrer)
        // Deux relectures « rien encore », une troisième qui trouve le ticket, puis plus rien.
        assertEquals(3, relectures)
    }

    @Test
    fun `garderTicket montre le ticket puis le calque se ferme, sans l'encaisser`() = runTest(dispatcher) {
        api.onJournal = { page() }
        api.onVoyage = {
            VoyageResponse(
                configure = true,
                annee_en_cours = 1942,
                ticket_a_montrer = TicketAMontrerVoyage(1943, "Un motif.", "2026-09-21T10:00:00.000Z"),
            )
        }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1943, vm.ui.value.voyage.ticketAMontrer?.annee)

        vm.garderTicket(1943)
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.voyage.ticketAMontrer)
        assertEquals(listOf("montrerTicket 1943"), api.calls.filter { it.startsWith("montrerTicket") })
        assertEquals(emptyList<String>(), api.calls.filter { it.startsWith("utiliserTicket") })
    }

    @Test
    fun `utiliserTicketAMontrer encaisse et montre le ticket, puis rafraichit`() = runTest(dispatcher) {
        api.onJournal = { page() }
        // Comme le ferait le vrai back : `ticket_a_montrer` retombe à `null` une fois `montre`
        // appelé — sans quoi le `refresh()` d'`utiliserTicketAMontrer` le retrouverait aussitôt,
        // masquant une régression qui aurait oublié de fermer le calque après lui.
        var montre = false
        api.onMontrerTicket = { montre = true }
        api.onVoyage = {
            VoyageResponse(
                configure = true,
                annee_en_cours = 1942,
                ticket_a_montrer = if (montre) null else TicketAMontrerVoyage(1943, "Un motif.", "2026-09-21T10:00:00.000Z"),
            )
        }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()
        val appelsVoyageAvant = api.calls.count { it == "voyage" }

        vm.utiliserTicketAMontrer(1943)
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.voyage.ticketAMontrer)
        assertEquals(listOf("utiliserTicket 1943"), api.calls.filter { it.startsWith("utiliserTicket") })
        assertEquals(listOf("montrerTicket 1943"), api.calls.filter { it.startsWith("montrerTicket") })
        // `refresh()` relit tout : un second `voyage` après celui du chargement initial.
        assertEquals(appelsVoyageAvant + 1, api.calls.count { it == "voyage" })
    }

    // Le passeport et son générique automatique (décision 1 du brief du 21 septembre 2026, « les
    // récompenses ») : un tampon qui apparaît entre deux chargements se retrouve sur
    // `nouveauxTampons`, avec ses films — jumeau intégré de `detecterNouveauTampon`
    // (`VoyageCarteTest`), qui prouve déjà que le tout premier chargement n'émet rien.
    @Test
    fun `un tampon qui apparait entre deux chargements se retrouve sur nouveauxTampons`() = runTest(dispatcher) {
        api.onJournal = { page("1") }
        var tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z"))
        api.onVoyage = {
            VoyageResponse(
                configure = true,
                annee_en_cours = 1905,
                annees = listOf(AnneeVoyage(1895, "ouverte", recompense = "palme")),
                tampons = tampons,
            )
        }
        val vm = FriseViewModel(api) { expire++ }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        // La décennie 1900 apparaît : c'est elle, et elle seule, qui doit sortir sur le canal.
        tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z"), TamponVoyage(1900, "2026-09-21T00:00:00.000Z"))
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val nouveau = vm.nouveauxTampons.first()
        assertEquals(1900, nouveau.decennie)
    }
}
