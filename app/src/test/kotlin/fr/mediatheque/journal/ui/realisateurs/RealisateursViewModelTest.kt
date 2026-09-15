package fr.mediatheque.journal.ui.realisateurs

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.Realisateur
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealisateursViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private val nolan = FakeJournalApi.realisateur(525, "Christopher Nolan")
    private val kubrick = FakeJournalApi.realisateur(240, "Stanley Kubrick")

    private fun vm() = RealisateursViewModel(api) { expire++ }

    // Jumeau de `FriseViewModel`/`FilmsViewModel` : `Root.kt` déclenche le premier chargement,
    // pas le constructeur. Mutation : un `init { refresh() }` fait échouer l'assertion.
    @Test
    fun `la construction ne charge rien, seul refresh declenche les appels`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        vm()
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<String>(), api.calls)
    }

    // La liste d'abord, puis une filmographie par réalisateur, à la suite les unes des autres
    // (brief du 15 septembre 2026). Mutation : lancer les filmographies en parallèle
    // (`liste.map { async { ... } }`) casse l'ordre attendu de `api.calls` dès que la seconde
    // réponse arrive avant la première ; ne pas les charger du tout laisse les deux lignes à
    // « … ».
    @Test
    fun `refresh charge la liste puis chaque filmographie, a la suite`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan, kubrick) }
        api.onFilmographie = { id -> listOf(FakeJournalApi.filmDe(id * 10, "Film de $id", 2000)) }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("realisateurs", "filmographie 525", "filmographie 240"), api.calls)
        assertEquals(listOf(525, 240), vm.ui.value.realisateurs.map { it.tmdb_id })
        assertEquals(
            EtatFilmographie.Pret(listOf(FakeJournalApi.filmDe(5250, "Film de 525", 2000))),
            vm.ui.value.filmographies[525],
        )
    }

    // La règle que le brief pose en toutes lettres : `/films` en erreur, la ligne dit
    // « indisponible » et l'écran continue — ni `ErrorBlock` bloquant, ni chargement interrompu
    // pour les réalisateurs suivants. Mutation : propager l'erreur dans `ui.error` fait échouer
    // la première assertion ; laisser la boucle s'arrêter à la première panne (un `throw` au lieu
    // du `catch`) laisse Kubrick en `EnAttente` et fait échouer la troisième.
    @Test
    fun `une filmographie en erreur dit indisponible sans bloquer l ecran`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan, kubrick) }
        api.onFilmographie = { id ->
            if (id == 525) throw FakeJournalApi.network() else listOf(FakeJournalApi.filmDe(1, "Shining", 1980))
        }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.error)
        assertEquals(EtatFilmographie.Indisponible, vm.ui.value.filmographies[525])
        assertEquals("indisponible", libelleLigne(vm.ui.value.filmographies[525]!!))
        assertTrue(vm.ui.value.filmographies[240] is EtatFilmographie.Pret)
        assertEquals(2, vm.ui.value.realisateurs.size)
    }

    // Ajouter un réalisateur : le `POST`, puis la liste se rafraîchit — sans quoi la personne
    // qu'on vient de suivre n'apparaîtrait qu'à la visite suivante. Mutation : retirer le
    // `refresh()` après le `POST` fait échouer les deux dernières assertions (la liste reste
    // vide, et `realisateurs` n'est appelé qu'une fois).
    @Test
    fun `ajouter suit la personne puis rafraichit la liste`() = runTest(dispatcher) {
        var suivis = emptyList<Realisateur>()
        api.onRealisateurs = { suivis }
        api.onSuivreRealisateur = { id ->
            suivis = listOf(kubrick.copy(tmdb_id = id))
            suivis.first()
        }
        api.onFilmographie = { listOf(FakeJournalApi.filmDe(1, "Shining", 1980)) }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<Int>(), vm.ui.value.realisateurs.map { it.tmdb_id })

        vm.ajouter(240)
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.contains("suivreRealisateur 240"))
        assertEquals(listOf(240), vm.ui.value.realisateurs.map { it.tmdb_id })
        assertEquals(2, api.calls.count { it == "realisateurs" })
    }

    // Le bandeau « Ajouté » de la liste : il passe par le canal du `ViewModel`, pas par celui du
    // `Navigator`, parce que l'écran de recherche s'est déjà refermé quand le `POST` répond.
    // Mutation : ne rien envoyer, ou l'envoyer avant le `POST` (donc aussi quand il échoue),
    // casse l'une des deux assertions.
    @Test
    fun `ajouter annonce Ajoute, un echec annonce le message du back`() = runTest(dispatcher) {
        api.onRealisateurs = { emptyList() }
        val recus = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.messages.collect { recus += it } }

        vm.ajouter(240)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Ajouté"), recus)

        api.onSuivreRealisateur = { throw FakeJournalApi.network() }
        vm.ajouter(525)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("Ajouté", "L’API est injoignable."), recus)
    }

    // Retirer : le `DELETE`, puis la liste se rafraîchit — et la filmographie du partant s'en va
    // avec lui. Mutation : compléter la carte des filmographies au lieu de la reconstruire sur la
    // liste neuve (`etat.filmographies + liste.associate { … }`) garde celle de Nolan, et la
    // dernière assertion tombe.
    @Test
    fun `retirer efface le realisateur et sa filmographie`() = runTest(dispatcher) {
        var suivis = listOf(nolan, kubrick)
        api.onRealisateurs = { suivis }
        api.onFilmographie = { listOf(FakeJournalApi.filmDe(1, "Un film", 1980)) }
        api.onRetirerRealisateur = { id -> suivis = suivis.filterNot { it.tmdb_id == id } }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(525, 240), vm.ui.value.filmographies.keys)

        vm.retirer(525)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(240), vm.ui.value.realisateurs.map { it.tmdb_id })
        assertEquals(setOf(240), vm.ui.value.filmographies.keys)
    }

    // Le journal complet, indexé par `entry_id` : c'est lui qui permet à la fiche d'ouvrir la
    // *correction* d'un film vu plutôt qu'un second visionnage. Toutes les pages, pas seulement
    // la première — jumeau de la Frise. Mutation : s'arrêter au premier `next_cursor` laisse
    // l'entrée « e-2 » introuvable.
    @Test
    fun `chargerEntrees indexe tout le journal par entry id`() = runTest(dispatcher) {
        api.onJournal = { cursor ->
            when (cursor) {
                null -> JournalResponse(listOf(FakeJournalApi.item("m1", "2026-01-10", 8, emptyList(), null, id = "e-1")), "p2")
                "p2" -> JournalResponse(listOf(FakeJournalApi.item("m2", "2026-01-09", 7, emptyList(), null, id = "e-2")), null)
                else -> error("curseur inattendu : $cursor")
            }
        }

        val vm = vm()
        vm.chargerEntrees()
        testScheduler.advanceUntilIdle()

        assertEquals(setOf("e-1", "e-2"), vm.ui.value.entrees.keys)
        assertEquals(8, vm.ui.value.entrees["e-1"]?.entry?.rating)
    }

    // L'accueil ne paie pas le journal complet : `refresh()` ne le touche pas, seule l'entrée sur
    // l'écran Réalisateurs l'appelle. Mutation : charger les entrées depuis `refresh()` fait
    // apparaître « journal null » dans les appels.
    @Test
    fun `refresh ne lit pas le journal`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.none { it.startsWith("journal") })
    }

    // Une filmographie restée « indisponible » se réessaie depuis sa fiche, et repasse par
    // « … » le temps de l'appel. Mutation : ne pas remettre `EnAttente` avant l'appel laisse la
    // ligne à « indisponible » pendant tout le rechargement.
    @Test
    fun `rechargerFilmographie repasse par l attente puis rend la filmographie`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        api.onFilmographie = { throw FakeJournalApi.network() }
        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(EtatFilmographie.Indisponible, vm.ui.value.filmographies[525])

        // Le `delay` rend l'appel observable à mi-course : sans lui, la doublure répondrait dans
        // le même tour de boucle et l'état d'attente serait vrai sans jamais être visible.
        api.onFilmographie = {
            delay(100)
            listOf(FakeJournalApi.filmDe(1, "Memento", 2000))
        }
        vm.rechargerFilmographie(525)
        testScheduler.runCurrent()
        assertEquals(EtatFilmographie.EnAttente, vm.ui.value.filmographies[525])

        testScheduler.advanceUntilIdle()
        assertTrue(vm.ui.value.filmographies[525] is EtatFilmographie.Pret)
    }

    @Test
    fun `un 401 sur la liste previent la session`() = runTest(dispatcher) {
        api.onRealisateurs = { throw FakeJournalApi.unauthorized() }
        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
        assertNull(vm.ui.value.error)
    }

    // L'interrupteur de la fiche (décision du propriétaire du 15 septembre 2026), activé par
    // défaut : une bascule l'éteint, une seconde le rallume. Mutation : ne jamais inverser, ou
    // partir de `false`, casse l'une des trois assertions.
    @Test
    fun `basculerMasquerIntrouvables inverse l etat, actif par defaut`() {
        val vm = vm()
        assertTrue(vm.ui.value.masquerIntrouvables)
        vm.basculerMasquerIntrouvables()
        assertEquals(false, vm.ui.value.masquerIntrouvables)
        vm.basculerMasquerIntrouvables()
        assertTrue(vm.ui.value.masquerIntrouvables)
    }

    // Marquer un film introuvable : le `PUT`, puis sa filmographie se recharge — c'est ce
    // rechargement qui fait apparaître `introuvable: true` sur la ligne. Mutation : ne pas
    // recharger laisse la vieille filmographie affichée, sans la marque qu'on vient de poser.
    @Test
    fun `marquerIntrouvable appelle le back puis recharge la filmographie`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        var marque = false
        api.onFilmographie = {
            listOf(FakeJournalApi.filmDe(1, "Memento", 2000, introuvable = marque))
        }
        api.onMarquerIntrouvable = { marque = true }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(false, (vm.ui.value.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)

        vm.marquerIntrouvable(525, 1)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("marquerIntrouvable 1"), api.calls.filter { it.startsWith("marquerIntrouvable") })
        assertTrue((vm.ui.value.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)
    }

    // Un échec du `PUT` : bandeau « Impossible pour l'instant », et rien d'autre ne bouge — ni
    // rechargement, ni marque locale posée par optimisme. Mutation : rechargerait quand même
    // (`chargerUneFilmographie` hors du `catch`) ferait apparaître un second appel dans
    // `api.calls` alors que ce test n'en attend qu'un.
    @Test
    fun `marquerIntrouvable rate annonce un bandeau, rien ne change`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        api.onFilmographie = { listOf(FakeJournalApi.filmDe(1, "Memento", 2000)) }
        api.onMarquerIntrouvable = { throw FakeJournalApi.network() }

        val recus = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.messages.collect { recus += it } }
        vm.refresh()
        testScheduler.advanceUntilIdle()

        vm.marquerIntrouvable(525, 1)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("Impossible pour l’instant"), recus)
        assertEquals(listOf("realisateurs", "filmographie 525", "marquerIntrouvable 1"), api.calls)
        assertEquals(false, (vm.ui.value.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)
    }

    // Retirer la marque : le `DELETE`, puis la filmographie se recharge — jumeau de
    // `marquerIntrouvable`, l'autre sens.
    @Test
    fun `retirerIntrouvable appelle le back puis recharge la filmographie`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        var marque = true
        api.onFilmographie = {
            listOf(FakeJournalApi.filmDe(1, "Memento", 2000, introuvable = marque))
        }
        api.onRetirerIntrouvable = { marque = false }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertTrue((vm.ui.value.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)

        vm.retirerIntrouvable(525, 1)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("retirerIntrouvable 1"), api.calls.filter { it.startsWith("retirerIntrouvable") })
        assertEquals(false, (vm.ui.value.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)
    }

    @Test
    fun `un 401 sur marquerIntrouvable previent la session`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        api.onFilmographie = { listOf(FakeJournalApi.filmDe(1, "Memento", 2000)) }
        api.onMarquerIntrouvable = { throw FakeJournalApi.unauthorized() }

        val vm = vm()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        vm.marquerIntrouvable(525, 1)
        testScheduler.advanceUntilIdle()

        assertEquals(1, expire)
    }
}
