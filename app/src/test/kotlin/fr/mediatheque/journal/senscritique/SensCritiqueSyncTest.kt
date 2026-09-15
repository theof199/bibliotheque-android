package fr.mediatheque.journal.senscritique

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * `SensCritiqueSync` : résolution (avec mémorisation du choix), poussée, et la file rejouée au
 * lancement — brief du 14 septembre 2026.
 */
class SensCritiqueSyncTest {
    private val film = MatchableFilm("Le Voyage de Chihiro", null, 2001)
    private fun candidat(id: Long) = ExternalCandidate(id, "Le Voyage de Chihiro", null, 2001)

    private fun connecte(store: SensCritiqueStore) {
        store.writeAuth(SensCritiqueAuth("cookie-1", "2099-01-01T00:00:00Z", "TheofB"))
    }

    // --- resolve() via syncAfterSave(), et la mémorisation des choix ---

    // Mutation : lire `store.readDecisions()[mediaId]` sans `containsKey` (donc sans distinguer
    // absent de « présent et nul ») confondrait ce test avec le suivant — les deux doivent se
    // comporter différemment (choisir vs ignorer) pour la même valeur de retour `null`.
    @Test
    fun `une decision deja resolue ne redemande jamais une recherche`() = runTest {
        val store = InMemorySensCritiqueStore().apply { connecte(this); writeDecisions(mapOf("m1" to 42L)) }
        val service = FakeExternalRatingService()
        val sync = SensCritiqueSync(service, store)

        val resultat = sync.syncAfterSave("m1", film, rating = 8, watchedOn = "2026-09-10")
        assertEquals(GestureSyncResult.Pushed, resultat)
        assertTrue("aucune recherche : la decision suffit", service.searchCalls.isEmpty())
        assertEquals(listOf(Triple(42L, 8, LocalDate.parse("2026-09-10"))), service.pushCalls)
    }

    @Test
    fun `une decision deja ignoree ne redemande jamais, et ne pousse rien`() = runTest {
        val store = InMemorySensCritiqueStore().apply { connecte(this); writeDecisions(mapOf("m1" to null)) }
        val service = FakeExternalRatingService()
        val sync = SensCritiqueSync(service, store)

        val resultat = sync.syncAfterSave("m1", film, rating = 8, watchedOn = "2026-09-10")
        assertEquals(GestureSyncResult.Skipped, resultat)
        assertTrue(service.searchCalls.isEmpty())
        assertTrue(service.pushCalls.isEmpty())
    }

    // Mutation : ne jamais rejouer la recherche avec `originalTitle` (retirer l'appel a
    // `fautRepliOriginalTitle`) fait echouer — un seul appel de recherche serait trace, avec le
    // titre francais, jamais l'original.
    @Test
    fun `aucun candidat au premier titre — repli sur l originalTitle, qui trouve`() = runTest {
        val filmAvecOriginal = MatchableFilm("Le Voyage de Chihiro", "Sen to Chihiro no Kamikakushi", 2001)
        val store = InMemorySensCritiqueStore().apply { connecte(this) }
        val service = FakeExternalRatingService(onSearch = { motsCles ->
            if (motsCles == "Sen to Chihiro no Kamikakushi") ExternalSearchOutcome.Success(listOf(candidat(1)))
            else ExternalSearchOutcome.Success(emptyList())
        })
        val sync = SensCritiqueSync(service, store)

        val resultat = sync.syncAfterSave("m1", filmAvecOriginal, rating = 8, watchedOn = "2026-09-10")
        assertEquals(GestureSyncResult.Pushed, resultat)
        assertEquals(listOf("Le Voyage de Chihiro", "Sen to Chihiro no Kamikakushi"), service.searchCalls)
    }

    // Mutation essayee a la main sur `enqueue()` : `store.writeQueue(mapOf(mediaId to push))`
    // (recrire toute la file avec une seule entree) au lieu de `store.readQueue() + (mediaId to
    // push)` (fusionner) passait toute la suite sans qu'aucun autre test ne s'en aperçoive —
    // aucun ne verifiait qu'une poussee en echec pour un nouveau film laisse les autres en place.
    @Test
    fun `une nouvelle poussee en echec s ajoute a la file sans effacer les autres`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(mapOf("m0" to QueuedPush("m0", "Un film deja en file", null, null, 5, "2026-09-01", productId = 1L)))
        }
        val service = FakeExternalRatingService(
            onSearch = { ExternalSearchOutcome.Success(listOf(candidat(42))) },
            onPush = { _, _, _ -> ExternalPushOutcome.Failed },
        )
        val sync = SensCritiqueSync(service, store)

        sync.syncAfterSave("m1", film, rating = 8, watchedOn = "2026-09-10")

        assertEquals(setOf("m0", "m1"), store.readQueue().keys)
    }

    // --- replayQueue() ---

    @Test
    fun `non connecte — la file ne bouge pas, aucun appel`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            writeQueue(mapOf("m1" to QueuedPush("m1", "Chihiro", null, 2001, 8, "2026-09-10", 42L)))
        }
        val service = FakeExternalRatingService()
        SensCritiqueSync(service, store).replayQueue()
        assertEquals(1, store.readQueue().size)
        assertTrue(service.pushCalls.isEmpty())
    }

    // Mutation : oublier `store.writeQueue(store.readQueue() - mediaId)` sur un succes laisse
    // l'entree en file malgre la poussee reussie — cette assertion l'attrape.
    @Test
    fun `une poussee deja resolue (productId connu) est rejouee directement, puis retiree`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(mapOf("m1" to QueuedPush("m1", "Chihiro", null, 2001, 8, "2026-09-10", productId = 42L)))
        }
        val service = FakeExternalRatingService()
        SensCritiqueSync(service, store).replayQueue()

        assertTrue("productId deja connu : pas besoin de rechercher", service.searchCalls.isEmpty())
        assertEquals(listOf(Triple(42L, 8, LocalDate.parse("2026-09-10"))), service.pushCalls)
        assertTrue(store.readQueue().isEmpty())
    }

    // Mutation : ne pas relancer `resolve()` pour une entree sans `productId` (par exemple la
    // traiter comme un echec direct) fait echouer la premiere assertion (aucune recherche tentee).
    @Test
    fun `une poussee jamais resolue (recherche non tentee avant) est resolue puis poussee au rejeu`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(mapOf("m1" to QueuedPush("m1", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = null)))
        }
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Success(listOf(candidat(42))) })
        SensCritiqueSync(service, store).replayQueue()

        assertEquals(listOf("Le Voyage de Chihiro"), service.searchCalls)
        assertEquals(listOf(Triple(42L, 8, LocalDate.parse("2026-09-10"))), service.pushCalls)
        assertTrue(store.readQueue().isEmpty())
    }

    // Le coeur du brief §5 : « en sautant ce qui demande un choix ». Mutation : rendre le rejeu
    // silencieux en poussant quand meme le premier candidat trouve fait echouer la premiere
    // assertion (une poussee aurait eu lieu) ; retirer l'entree de la file par erreur fait echouer
    // la seconde.
    @Test
    fun `un rejeu ambigu est saute — reste en file, silencieusement, sans pousser`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(mapOf("m1" to QueuedPush("m1", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = null)))
        }
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Success(listOf(candidat(1), candidat(2))) })
        SensCritiqueSync(service, store).replayQueue()

        assertTrue(service.pushCalls.isEmpty())
        assertEquals(1, store.readQueue().size)
    }

    @Test
    fun `un rejeu dont la recherche echoue (reseau) reste en file`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(mapOf("m1" to QueuedPush("m1", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = null)))
        }
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Failed })
        SensCritiqueSync(service, store).replayQueue()

        assertTrue(service.pushCalls.isEmpty())
        assertEquals(1, store.readQueue().size)
    }

    // Meme garde que le test suivant, mais sur le chemin de `resolve()` (recherche, pas poussee
    // directe) : une entree jamais resolue dont la recherche echoue par jeton refuse doit, elle
    // aussi, arreter le rejeu. Mutation : remplacer le `return` par `Unit` dans la branche
    // `SyncResolution.Unauthenticated` de `replayQueue` laisse cette assertion passer (aucun test
    // ne couvrait ce chemin avant que cette mutation, essayee a la main, ne le revele) — "m2" ne
    // doit jamais etre recherche.
    @Test
    fun `un rejeu non resolu qui rencontre un jeton refuse a la recherche s arrete aussi`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(
                linkedMapOf(
                    "m1" to QueuedPush("m1", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = null),
                    "m2" to QueuedPush("m2", "Perfect Blue", null, 1997, 6, "2026-09-11", productId = null),
                ),
            )
        }
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Unauthenticated })
        SensCritiqueSync(service, store).replayQueue()

        assertEquals(listOf("Le Voyage de Chihiro"), service.searchCalls)
        assertNull(store.readAuth())
    }

    // Mutation : continuer la boucle apres un `Unauthenticated` (au lieu de `return`) tenterait la
    // recherche pour "m2" — cette assertion l'attrape en verifiant qu'elle n'a jamais eu lieu.
    @Test
    fun `un rejeu qui rencontre un jeton refuse s arrete, et deconnecte`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(
                linkedMapOf(
                    "m1" to QueuedPush("m1", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = 42L),
                    "m2" to QueuedPush("m2", "Perfect Blue", null, 1997, 6, "2026-09-11", productId = 7L),
                ),
            )
        }
        val service = FakeExternalRatingService(onPush = { _, _, _ -> ExternalPushOutcome.Unauthenticated })
        SensCritiqueSync(service, store).replayQueue()

        assertEquals(listOf(Triple(42L, 8, LocalDate.parse("2026-09-10"))), service.pushCalls)
        assertNull("la deconnexion doit avoir efface l'auth", store.readAuth())
    }

    // --- abandon() : la feuille quittee sans reponse (critique 2 de la revue du 14 septembre 2026) ---

    // Mutation : appeler `recordChoice(mediaId, null)` au lieu d'`enqueueUnresolved` dans
    // `abandon` confondrait ce test avec « Aucun de ceux-la » — cette assertion verifie
    // precisement qu'aucune decision n'est ecrite, a la difference d'un vrai choix.
    @Test
    fun `abandon met la poussee en file sans productId, sans memoriser de decision`() = runTest {
        val store = InMemorySensCritiqueStore().apply { connecte(this) }
        val service = FakeExternalRatingService()
        val sync = SensCritiqueSync(service, store)

        val resultat = sync.abandon("m1", film, rating = 8, watchedOn = "2026-09-10")

        assertEquals(GestureSyncResult.QueuedForRetry, resultat)
        assertTrue(service.pushCalls.isEmpty())
        assertTrue(service.searchCalls.isEmpty())
        val queued = store.readQueue()["m1"]
        assertNull(queued?.productId)
        assertEquals(8, queued?.rating)
        assertTrue("aucune decision memorisee : la prochaine correction redemande", store.readDecisions().isEmpty())
    }

    // Un `abandon` pour un `mediaId` deja en file (une correction precedente avait deja echoue)
    // remplace l'entree plutot que de s'y ajouter — jumeau du comportement de `enqueue`.
    @Test
    fun `abandon remplace une entree deja en file pour le meme media_id`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(mapOf("m1" to QueuedPush("m1", "Ancien titre", null, 1999, 3, "2026-01-01", productId = 99L)))
        }
        val sync = SensCritiqueSync(FakeExternalRatingService(), store)

        sync.abandon("m1", film, rating = 8, watchedOn = "2026-09-10")

        assertEquals(1, store.readQueue().size)
        val queued = store.readQueue().getValue("m1")
        assertEquals("Le Voyage de Chihiro", queued.title)
        assertEquals(8, queued.rating)
        assertNull(queued.productId)
    }

    // --- important 5 de la revue du 14 septembre 2026 : aucune exception ne doit fuir de SensCritiqueSync ---

    // Mutation : retirer le `try`/`catch` autour du corps de `syncAfterSave` fait remonter la
    // `RuntimeException` jusqu'au test (JUnit la rapporte comme un echec de test, pas comme
    // l'assertion ci-dessous) — cette assertion ne s'execute alors jamais.
    @Test
    fun `une exception inattendue pendant syncAfterSave devient QueuedForRetry, mise en file`() = runTest {
        val store = InMemorySensCritiqueStore().apply { connecte(this) }
        val service = FakeExternalRatingService(onSearch = { throw RuntimeException("panne inattendue") })
        val sync = SensCritiqueSync(service, store)

        val resultat = sync.syncAfterSave("m1", film, rating = 8, watchedOn = "2026-09-10")

        assertEquals(GestureSyncResult.QueuedForRetry, resultat)
        assertEquals(8, store.readQueue()["m1"]?.rating)
    }

    @Test
    fun `une exception inattendue pendant choose devient QueuedForRetry`() = runTest {
        val store = InMemorySensCritiqueStore().apply { connecte(this) }
        val service = FakeExternalRatingService(onPush = { _, _, _ -> throw RuntimeException("panne inattendue") })
        val sync = SensCritiqueSync(service, store)

        val resultat = sync.choose("m1", film, rating = 8, watchedOn = "2026-09-10", productId = 42L)

        assertEquals(GestureSyncResult.QueuedForRetry, resultat)
    }

    // Mutation : retirer `runCatching { store.writeQueue(store.readQueue() - mediaId) }` de la
    // branche « date illisible » de `pushAndRecord` (garder le `return Failed` seul) fait echouer
    // la premiere assertion — l'entree resterait en file indefiniment, jamais retiree. Retirer tout
    // le bloc `runCatching { LocalDate.parse(...) }` (revenir a un appel nu) est aussi attrape, mais
    // par le filet du dessus (`replayQueue`) plutot que celui-ci : les deux se recouvrent, ce test
    // prouve le comportement observable, pas laquelle des deux lignes agit.
    @Test
    fun `une entree de file avec une date illisible est retiree, sans bloquer la suivante`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(
                linkedMapOf(
                    "m1" to QueuedPush("m1", "Corrompu", null, null, 5, "pas-une-date", productId = 42L),
                    "m2" to QueuedPush("m2", "Perfect Blue", null, 1997, 6, "2026-09-11", productId = 7L),
                ),
            )
        }
        SensCritiqueSync(FakeExternalRatingService(), store).replayQueue()

        assertTrue("m1" !in store.readQueue())
        assertTrue("m2 pousse avec succes doit aussi avoir ete retiree" , "m2" !in store.readQueue())
    }

    // Une exception qui ne vient pas de la date (ici la recherche elle-meme) ne doit ni planter le
    // rejeu ni bloquer les entrees suivantes.
    @Test
    fun `une exception inattendue pendant le rejeu d une entree n empeche pas les suivantes`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(
                linkedMapOf(
                    "m1" to QueuedPush("m1", "Provoque une exception", null, null, 5, "2026-09-10", productId = null),
                    "m2" to QueuedPush("m2", "Perfect Blue", null, 1997, 6, "2026-09-11", productId = 7L),
                ),
            )
        }
        val service = FakeExternalRatingService(onSearch = { motsCles ->
            if (motsCles == "Provoque une exception") throw RuntimeException("panne inattendue") else ExternalSearchOutcome.Success(emptyList())
        })
        SensCritiqueSync(service, store).replayQueue()

        assertTrue("m1" !in store.readQueue())
        assertEquals(listOf(Triple(7L, 6, LocalDate.parse("2026-09-11"))), service.pushCalls)
    }

    // --- journalisation (brief du 15 septembre 2026, point 3) ---

    // Mutation : ne pas appeler `logResultatPoussee` (ou logger un texte qui ne mentionne pas
    // "Pushed") fait echouer cette assertion.
    @Test
    fun `syncAfterSave journalise le resultat de la poussee, jamais un secret`() = runTest {
        val store = InMemorySensCritiqueStore().apply { connecte(this); writeDecisions(mapOf("m1" to 42L)) }
        val service = FakeExternalRatingService()
        val logger = FakeSensCritiqueLogger()
        val sync = SensCritiqueSync(service, store, logger)

        sync.syncAfterSave("m1", film, rating = 8, watchedOn = "2026-09-10")

        assertTrue(logger.lines.any { it.contains("m1") && it.contains("Pushed") })
        assertTrue("jamais le cookieRef dans le journal", logger.lines.none { it.contains("cookie-1") })
    }

    // Le rejeu resume ses trois compteurs en une ligne (brief §3) : ici une entree deja resolue
    // (poussee reussie) et une entree ambigue (sautee, "m2" partage le titre de deux candidats).
    // Mutation : oublier d'incrementer `reussies` ou `sautees` fait echouer cette assertion.
    @Test
    fun `replayQueue journalise le nombre d entrees tentees, reussies et sautees`() = runTest {
        val store = InMemorySensCritiqueStore().apply {
            connecte(this)
            writeQueue(
                linkedMapOf(
                    "m1" to QueuedPush("m1", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = 42L),
                    "m2" to QueuedPush("m2", "Le Voyage de Chihiro", null, 2001, 8, "2026-09-10", productId = null),
                ),
            )
        }
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Success(listOf(candidat(1), candidat(2))) })
        val logger = FakeSensCritiqueLogger()

        SensCritiqueSync(service, store, logger).replayQueue()

        assertEquals(
            listOf("rejeu SensCritique : 2 tentee(s), 1 reussie(s), 1 sautee(s)"),
            logger.lines.filter { it.startsWith("rejeu SensCritique") },
        )
    }
}
