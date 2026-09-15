package fr.mediatheque.journal.ui.suivis

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.Saga
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

/**
 * `SuivisViewModel` (brief du 15 septembre 2026, généralisation de
 * `RealisateursViewModelTest.kt` du même jour). La plupart des règles sont
 * éprouvées sur la source des réalisateurs, comme avant la généralisation ;
 * un petit bloc à la fin (« dispatch … ») éprouve, une fois chacun, que le
 * `when (source)` des quatre appels réseau atteint bien la bonne route pour
 * les sagas — la seule chose que la réutilisation des tests réalisateurs ne
 * peut pas prouver.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuivisViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private val nolan = FakeJournalApi.realisateur(525, "Christopher Nolan")
    private val kubrick = FakeJournalApi.realisateur(240, "Stanley Kubrick")

    private fun vm() = SuivisViewModel(api) { expire++ }

    // Jumeau de `FriseViewModel`/`FilmsViewModel` : `Root.kt` déclenche le premier chargement,
    // pas le constructeur. Mutation : un `init { refresh(...) }` fait échouer l'assertion.
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("realisateurs", "filmographie 525", "filmographie 240"), api.calls)
        assertEquals(listOf(525, 240), vm.ui.value.realisateurs.entites.map { it.tmdbId })
        assertEquals(
            EtatFilmographie.Pret(listOf(FakeJournalApi.filmDe(5250, "Film de 525", 2000))),
            vm.ui.value.realisateurs.filmographies[525],
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()

        assertNull(vm.ui.value.realisateurs.error)
        assertEquals(EtatFilmographie.Indisponible, vm.ui.value.realisateurs.filmographies[525])
        assertEquals("indisponible", libelleLigne(vm.ui.value.realisateurs.filmographies[525]!!))
        assertTrue(vm.ui.value.realisateurs.filmographies[240] is EtatFilmographie.Pret)
        assertEquals(2, vm.ui.value.realisateurs.entites.size)
    }

    // Ajouter un réalisateur : le `POST`, puis la liste se rafraîchit — sans quoi la personne
    // qu'on vient de suivre n'apparaîtrait qu'à la visite suivante. Mutation : retirer le
    // `refresh(...)` après le `POST` fait échouer les deux dernières assertions (la liste reste
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<Int>(), vm.ui.value.realisateurs.entites.map { it.tmdbId })

        vm.ajouter(SourceSuivi.REALISATEURS, 240)
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.contains("suivreRealisateur 240"))
        assertEquals(listOf(240), vm.ui.value.realisateurs.entites.map { it.tmdbId })
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

        vm.ajouter(SourceSuivi.REALISATEURS, 240)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Ajouté"), recus)

        api.onSuivreRealisateur = { throw FakeJournalApi.network() }
        vm.ajouter(SourceSuivi.REALISATEURS, 525)
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(525, 240), vm.ui.value.realisateurs.filmographies.keys)

        vm.retirer(SourceSuivi.REALISATEURS, 525)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(240), vm.ui.value.realisateurs.entites.map { it.tmdbId })
        assertEquals(setOf(240), vm.ui.value.realisateurs.filmographies.keys)
    }

    // Le journal complet, indexé par `entry_id` : c'est lui qui permet à une fiche d'ouvrir la
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

    // L'accueil ne paie pas le journal complet : `refresh(...)` ne le touche pas, seule l'entrée
    // sur l'écran Suivis l'appelle. Mutation : charger les entrées depuis `refresh(...)` fait
    // apparaître « journal null » dans les appels.
    @Test
    fun `refresh ne lit pas le journal`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        val vm = vm()
        vm.refresh(SourceSuivi.REALISATEURS)
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()
        assertEquals(EtatFilmographie.Indisponible, vm.ui.value.realisateurs.filmographies[525])

        // Le `delay` rend l'appel observable à mi-course : sans lui, la doublure répondrait dans
        // le même tour de boucle et l'état d'attente serait vrai sans jamais être visible.
        api.onFilmographie = {
            delay(100)
            listOf(FakeJournalApi.filmDe(1, "Memento", 2000))
        }
        vm.rechargerFilmographie(SourceSuivi.REALISATEURS, 525)
        testScheduler.runCurrent()
        assertEquals(EtatFilmographie.EnAttente, vm.ui.value.realisateurs.filmographies[525])

        testScheduler.advanceUntilIdle()
        assertTrue(vm.ui.value.realisateurs.filmographies[525] is EtatFilmographie.Pret)
    }

    @Test
    fun `un 401 sur la liste previent la session`() = runTest(dispatcher) {
        api.onRealisateurs = { throw FakeJournalApi.unauthorized() }
        val vm = vm()
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()
        assertEquals(1, expire)
        assertNull(vm.ui.value.realisateurs.error)
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()
        assertEquals(false, (vm.ui.value.realisateurs.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)

        vm.marquerIntrouvable(SourceSuivi.REALISATEURS, 525, 1)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("marquerIntrouvable 1"), api.calls.filter { it.startsWith("marquerIntrouvable") })
        assertTrue((vm.ui.value.realisateurs.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()

        vm.marquerIntrouvable(SourceSuivi.REALISATEURS, 525, 1)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("Impossible pour l’instant"), recus)
        assertEquals(listOf("realisateurs", "filmographie 525", "marquerIntrouvable 1"), api.calls)
        assertEquals(false, (vm.ui.value.realisateurs.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)
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
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()
        assertTrue((vm.ui.value.realisateurs.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)

        vm.retirerIntrouvable(SourceSuivi.REALISATEURS, 525, 1)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("retirerIntrouvable 1"), api.calls.filter { it.startsWith("retirerIntrouvable") })
        assertEquals(false, (vm.ui.value.realisateurs.filmographies[525] as EtatFilmographie.Pret).films.first().introuvable)
    }

    @Test
    fun `un 401 sur marquerIntrouvable previent la session`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan) }
        api.onFilmographie = { listOf(FakeJournalApi.filmDe(1, "Memento", 2000)) }
        api.onMarquerIntrouvable = { throw FakeJournalApi.unauthorized() }

        val vm = vm()
        vm.refresh(SourceSuivi.REALISATEURS)
        testScheduler.advanceUntilIdle()

        vm.marquerIntrouvable(SourceSuivi.REALISATEURS, 525, 1)
        testScheduler.advanceUntilIdle()

        assertEquals(1, expire)
    }

    // Les deux sources sont tenues à part (brief du 15 septembre 2026) : ajouter, retirer ou
    // recharger sur l'une ne doit jamais toucher l'état de l'autre — y compris quand les deux
    // partagent le même entier TMDB, un réalisateur et une collection pouvant parfaitement
    // partager un identifiant. Mutation : fusionner les deux cartes de filmographies
    // (`SuiviState` unique au lieu de deux) fait tomber la troisième assertion.
    @Test
    fun `les deux sources restent independantes, meme sur le meme entier`() = runTest(dispatcher) {
        api.onRealisateurs = { listOf(nolan.copy(tmdb_id = 8091)) }
        api.onSagas = { listOf(FakeJournalApi.saga(8091, "Alien (Saga)")) }
        api.onFilmographie = { listOf(FakeJournalApi.filmDe(1, "Film de realisateur", 2000)) }
        api.onFilmsDeSaga = { listOf(FakeJournalApi.filmDe(2, "Film de saga", 1980), FakeJournalApi.filmDe(3, "Autre", 1990)) }

        val vm = vm()
        vm.refresh(SourceSuivi.REALISATEURS)
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(8091), vm.ui.value.realisateurs.entites.map { it.tmdbId })
        assertEquals(listOf(8091), vm.ui.value.sagas.entites.map { it.tmdbId })
        assertEquals(1, vm.ui.value.realisateurs.filmographies.size)
        assertEquals(1, vm.ui.value.sagas.filmographies.size)
        assertEquals(
            listOf("Film de realisateur"),
            (vm.ui.value.realisateurs.filmographies[8091] as EtatFilmographie.Pret).films.map { it.title },
        )
        assertEquals(
            listOf("Film de saga", "Autre"),
            (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films.map { it.title },
        )
    }

    // -------------------------------------------------------------------------
    // Dispatch : les quatre `when (source)` de `SuivisViewModel`, du côté sagas
    // (le côté réalisateurs est déjà éprouvé par tout ce qui précède).
    // -------------------------------------------------------------------------

    @Test
    fun `dispatch refresh appelle sagas et non realisateurs`() = runTest(dispatcher) {
        api.onSagas = { listOf(FakeJournalApi.saga(8091, "Alien (Saga)")) }
        val vm = vm()
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.contains("sagas"))
        assertTrue(api.calls.none { it == "realisateurs" })
        assertEquals(listOf(8091), vm.ui.value.sagas.entites.map { it.tmdbId })
    }

    @Test
    fun `dispatch ajouter appelle suivreSaga`() = runTest(dispatcher) {
        var suivies = emptyList<Saga>()
        api.onSagas = { suivies }
        api.onSuivreSaga = { id -> suivies = listOf(FakeJournalApi.saga(id, "Saga $id")); suivies.first() }

        val vm = vm()
        vm.ajouter(SourceSuivi.SAGAS, 8091)
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.contains("suivreSaga 8091"))
        assertEquals(listOf(8091), vm.ui.value.sagas.entites.map { it.tmdbId })
    }

    @Test
    fun `dispatch retirer appelle retirerSaga`() = runTest(dispatcher) {
        var suivies = listOf(FakeJournalApi.saga(8091, "Alien (Saga)"))
        api.onSagas = { suivies }
        api.onRetirerSaga = { id -> suivies = suivies.filterNot { it.tmdb_id == id } }

        val vm = vm()
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()

        vm.retirer(SourceSuivi.SAGAS, 8091)
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.contains("retirerSaga 8091"))
        assertEquals(emptyList<Int>(), vm.ui.value.sagas.entites.map { it.tmdbId })
    }

    @Test
    fun `dispatch filmographie appelle filmsDeSaga`() = runTest(dispatcher) {
        api.onSagas = { listOf(FakeJournalApi.saga(8091, "Alien (Saga)")) }
        api.onFilmsDeSaga = { listOf(FakeJournalApi.filmDe(348, "Alien", 1979)) }

        val vm = vm()
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()

        assertTrue(api.calls.contains("filmsDeSaga 8091"))
        assertEquals(
            listOf(348),
            (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films.map { it.tmdb_id },
        )
    }

    // -------------------------------------------------------------------------
    // ajouterFilm / retirerFilm : les films de saga ajoutés à la main (brief du
    // 15 septembre 2026 — une collection TMDB n'est pas toujours complète).
    // Sans `source`, à la différence de marquerIntrouvable/retirerIntrouvable :
    // la route n'existe que pour les sagas.
    // -------------------------------------------------------------------------

    // Ajouter un film : le `PUT`, puis la filmographie de la saga se recharge — c'est ce
    // rechargement qui fait apparaître le nouveau film, à sa place chronologique. Mutation : ne
    // pas recharger laisse l'ancienne filmographie affichée, sans Prometheus.
    @Test
    fun `ajouterFilm appelle le back puis recharge la filmographie, a sa place`() = runTest(dispatcher) {
        api.onSagas = { listOf(FakeJournalApi.saga(8091, "Alien (Saga)")) }
        var ajoute = false
        api.onFilmsDeSaga = {
            val films = mutableListOf(FakeJournalApi.filmDe(348, "Alien", 1979))
            if (ajoute) films += FakeJournalApi.filmDe(70981, "Prometheus", 2012, ajoute = true)
            films
        }
        api.onAjouterFilmSaga = { _, _ -> ajoute = true }

        val vm = vm()
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()
        assertEquals(
            listOf(348),
            (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films.map { it.tmdb_id },
        )

        vm.ajouterFilm(8091, 70981)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("ajouterFilmSaga 8091 70981"), api.calls.filter { it.startsWith("ajouterFilmSaga") })
        val filmographie = (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films
        assertEquals(listOf(348, 70981), filmographie.map { it.tmdb_id })
        assertEquals(true, filmographie.last().ajoute)
    }

    // Le bandeau « Ajouté à la saga », et « Impossible pour l'instant » sur un échec — jumeau de
    // `marquerIntrouvable`, pas de `ajouter` : ce message-ci est fixe, il ne reprend pas celui du
    // back. Mutation : ne rien envoyer, ou l'envoyer avant le `PUT` (donc aussi quand il échoue),
    // casse l'une des deux assertions.
    @Test
    fun `ajouterFilm annonce Ajoute a la saga, un echec annonce le bandeau`() = runTest(dispatcher) {
        api.onSagas = { emptyList() }
        val recus = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.messages.collect { recus += it } }

        vm.ajouterFilm(8091, 70981)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("Ajouté à la saga"), recus)

        api.onAjouterFilmSaga = { _, _ -> throw FakeJournalApi.network() }
        vm.ajouterFilm(8091, 70981)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("Ajouté à la saga", "Impossible pour l’instant"), recus)
    }

    // Un échec du `PUT` : bandeau « Impossible pour l'instant », rien d'autre ne bouge — jumeau
    // de `marquerIntrouvable rate annonce un bandeau, rien ne change`. Mutation : recharger quand
    // même (`chargerUneFilmographie` hors du `catch`) ferait apparaître un second appel à
    // `filmsDeSaga` alors que ce test n'en attend qu'un.
    @Test
    fun `ajouterFilm rate annonce un bandeau, rien ne change`() = runTest(dispatcher) {
        api.onSagas = { listOf(FakeJournalApi.saga(8091, "Alien (Saga)")) }
        api.onFilmsDeSaga = { listOf(FakeJournalApi.filmDe(348, "Alien", 1979)) }
        api.onAjouterFilmSaga = { _, _ -> throw FakeJournalApi.network() }

        val recus = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.messages.collect { recus += it } }
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()

        vm.ajouterFilm(8091, 70981)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("Impossible pour l’instant"), recus)
        assertEquals(listOf("sagas", "filmsDeSaga 8091", "ajouterFilmSaga 8091 70981"), api.calls)
        assertEquals(
            listOf(348),
            (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films.map { it.tmdb_id },
        )
    }

    // Retirer un film ajouté : le `DELETE`, puis la filmographie se recharge — jumeau de
    // `ajouterFilm`, l'autre sens.
    @Test
    fun `retirerFilm appelle le back puis recharge la filmographie`() = runTest(dispatcher) {
        api.onSagas = { listOf(FakeJournalApi.saga(8091, "Alien (Saga)")) }
        var retire = false
        api.onFilmsDeSaga = {
            val films = mutableListOf(FakeJournalApi.filmDe(348, "Alien", 1979))
            if (!retire) films += FakeJournalApi.filmDe(70981, "Prometheus", 2012, ajoute = true)
            films
        }
        api.onRetirerFilmSaga = { _, _ -> retire = true }

        val vm = vm()
        vm.refresh(SourceSuivi.SAGAS)
        testScheduler.advanceUntilIdle()
        assertEquals(
            listOf(348, 70981),
            (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films.map { it.tmdb_id },
        )

        vm.retirerFilm(8091, 70981)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("retirerFilmSaga 8091 70981"), api.calls.filter { it.startsWith("retirerFilmSaga") })
        assertEquals(
            listOf(348),
            (vm.ui.value.sagas.filmographies[8091] as EtatFilmographie.Pret).films.map { it.tmdb_id },
        )
    }

    @Test
    fun `retirerFilm annonce Retire de la saga`() = runTest(dispatcher) {
        api.onSagas = { emptyList() }
        val recus = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.messages.collect { recus += it } }

        vm.retirerFilm(8091, 70981)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("Retiré de la saga"), recus)
    }
}
