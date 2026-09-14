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
        store.writeAuth(SensCritiqueAuth("refresh", "TheofB"))
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
}
