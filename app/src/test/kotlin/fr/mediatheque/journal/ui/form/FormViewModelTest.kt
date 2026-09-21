package fr.mediatheque.journal.ui.form

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.senscritique.ExternalCandidate
import fr.mediatheque.journal.senscritique.ExternalPushOutcome
import fr.mediatheque.journal.senscritique.ExternalSearchOutcome
import fr.mediatheque.journal.senscritique.FakeExternalRatingService
import fr.mediatheque.journal.senscritique.InMemorySensCritiqueStore
import fr.mediatheque.journal.senscritique.SensCritiqueAuth
import fr.mediatheque.journal.senscritique.SensCritiqueStore
import fr.mediatheque.journal.senscritique.SensCritiqueSync
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FormViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeJournalApi()
    private val chihiro = SearchResult("tmdb", "129", "movie", "Le Voyage de Chihiro", 2001)
    private var expire = 0

    // Magasin SensCritique vide (non connecté) par défaut : `SensCritiqueSync.syncAfterSave` rend
    // `Skipped` tout de suite (`isConnected()` faux), donc aucun des tests existants ci-dessous —
    // qui ne parlent pas de SensCritique — n'a besoin de programmer ce double. Les tests
    // SensCritique, en bas de fichier, passent leur propre `sync` connecté.
    private fun create(sync: SensCritiqueSync = disconnectedSync()) = FormViewModel(api, FormMode.Create(chihiro), sync) { expire++ }
    private fun edit(sync: SensCritiqueSync = disconnectedSync()) =
        FormViewModel(api, FormMode.Edit(FakeJournalApi.item("m", "2026-01-10", 7, listOf("sympa"), "Avant")), sync) { expire++ }

    private fun disconnectedSync() = SensCritiqueSync(FakeExternalRatingService(), InMemorySensCritiqueStore())

    private fun connectedSync(store: SensCritiqueStore, service: FakeExternalRatingService) =
        SensCritiqueSync(service, store.apply { writeAuth(SensCritiqueAuth("cookie-1", "2099-01-01T00:00:00Z", "TheofB")) })

    @Test
    fun `un film nouveau — deux appels dans l ordre, tous les champs envoyes`() {
        var corps: JournalCreateBody? = null
        api.onAddViewing = { b -> corps = b; FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
        val vm = create()
        vm.setDate(LocalDate.of(2026, 9, 3))
        vm.toggleRating(8)
        vm.toggleReaction("adore"); vm.toggleReaction("touche")
        vm.setComment("Revu avec Léa.")
        vm.save()

        assertEquals(listOf("addMedia 129", "addViewing m-129"), api.calls)
        assertEquals(JournalCreateBody("m-129", "2026-09-03", 8, listOf("adore", "touche"), "Revu avec Léa."), corps)
        assertEquals("Enregistré", vm.ui.value.done)
    }

    // Le ticket (brief du 21 septembre 2026, « le ticket ») : l'année de sortie du film créé passe
    // par `doneFilmAnnee`, jumeau de `doneCartonTmdbId` — c'est `Navigator.home` qui la relaie
    // ensuite à `FriseViewModel.relireApresCreation`.
    @Test
    fun `une creation porte l'annee du film dans doneFilmAnnee`() {
        val vm = create()
        vm.save()
        assertEquals(2001, vm.ui.value.doneFilmAnnee)
    }

    // Mutation : passer `cartonTmdbId` (ou toute autre valeur) à la place de `null` ici ferait
    // relire un ticket après chaque correction, alors qu'un visionnage déjà journalisé ne peut
    // pas en faire naître un nouveau.
    @Test
    fun `une correction ne porte jamais d'annee de film`() {
        val vm = edit()
        vm.save()
        assertEquals(null, vm.ui.value.doneFilmAnnee)
    }

    // Correctif du 16 septembre 2026 : `reactions` part désormais nulle, comme
    // `rating` et `comment`, plutôt qu'en liste vide — le POST omet alors la
    // clé au lieu d'écraser un carnet déjà écrit ce jour-là.
    @Test
    fun `sans note, reaction ni commentaire, tout est nul`() {
        var corps: JournalCreateBody? = null
        api.onAddViewing = { b -> corps = b; FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
        create().save()
        assertNull(corps!!.rating)
        assertNull(corps!!.comment)
        assertNull(corps!!.reactions)
    }

    @Test
    fun `la date par defaut est aujourd hui`() {
        var corps: JournalCreateBody? = null
        api.onAddViewing = { b -> corps = b; FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
        create().save()
        assertEquals(LocalDate.now().toString(), corps!!.finished_at)
    }

    @Test
    fun `toucher deux fois la meme note la retire`() {
        val vm = create()
        vm.toggleRating(8); vm.toggleRating(8)
        assertNull(vm.ui.value.rating)
    }

    @Test
    fun `douze reactions au plus`() {
        val vm = create()
        (1..13).forEach { vm.toggleReaction("r$it") }
        assertEquals(12, vm.ui.value.reactions.size)
    }

    @Test
    fun `si l ajout echoue, rien n est retenu`() {
        api.onAddMedia = { throw FakeJournalApi.network() }
        val vm = create()
        vm.save()
        assertEquals(listOf("addMedia 129"), api.calls)
        assertEquals(ApiError.NETWORK_MESSAGE, vm.ui.value.error?.message)
        assertNull(vm.ui.value.errorContext)
        assertNull(vm.ui.value.done)
    }

    // Décision 3 de la tâche 6 : le brief remplaçait ici le message par une phrase fixe et posait
    // `retryable = true` sans regarder l'erreur. Un `400` (commentaire trop long, date incohérente)
    // aurait alors affiché une phrase fausse et un « Réessayer » qui échoue toujours. Le message du
    // back reste donc le sien, `retryable` vient de lui, et la phrase du brief devient une ligne de
    // contexte séparée (`errorContext`).
    @Test
    fun `si le visionnage echoue par panne reseau, le contexte s ajoute et Reessayer ne relance que lui`() {
        api.onAddViewing = { throw FakeJournalApi.network() }
        val vm = create()
        vm.save()
        assertEquals(ApiError.NETWORK_MESSAGE, vm.ui.value.error?.message)
        assertTrue(vm.ui.value.error!!.retryable)
        assertEquals("Le film est ajouté, mais pas ton visionnage.", vm.ui.value.errorContext)

        api.onAddViewing = { b -> FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
        vm.retry()
        assertEquals(listOf("addMedia 129", "addViewing m-129", "addViewing m-129"), api.calls)
        assertEquals("Enregistré", vm.ui.value.done)
    }

    @Test
    fun `si le visionnage echoue par un 400, montre le message du back et n offre pas de reessai`() {
        api.onAddViewing = { throw ApiError("VALIDATION", "Le commentaire est trop long.", retryable = false, status = 400) }
        val vm = create()
        vm.save()
        assertEquals("Le commentaire est trop long.", vm.ui.value.error?.message)
        assertFalse(vm.ui.value.error!!.retryable)
        assertEquals("Le film est ajouté, mais pas ton visionnage.", vm.ui.value.errorContext)
    }

    @Test
    fun `en correction, le formulaire est pre-rempli et le patch ne porte que les champs changes`() {
        val vm = edit()
        assertEquals(LocalDate.of(2026, 1, 10), vm.ui.value.date)
        assertEquals(7, vm.ui.value.rating)
        assertEquals(setOf("sympa"), vm.ui.value.reactions)
        assertEquals("Avant", vm.ui.value.comment)

        var corps: JsonObject? = null
        api.onPatchViewing = { id, b -> corps = b; FakeJournalApi.item("m", "2026-01-10", 7, emptyList(), null, id) }
        vm.setComment("")
        vm.toggleReaction("sympa")
        vm.save()
        assertEquals(listOf("patchViewing e-2026-01-10"), api.calls)
        assertEquals(setOf("comment", "reactions"), corps!!.keys)
        assertEquals("Corrigé", vm.ui.value.done)
    }

    @Test
    fun `si rien n a change, le patch part avec un corps vide`() {
        var corps: JsonObject? = null
        api.onPatchViewing = { id, b -> corps = b; FakeJournalApi.item("m", "2026-01-10", 7, listOf("sympa"), "Avant", id) }
        edit().save()
        assertEquals(emptySet<String>(), corps!!.keys)
    }

    @Test
    fun `un commentaire vide part en null`() {
        var comment: String? = "x"
        var vu = false
        api.onPatchViewing = { id, b ->
            vu = true
            comment = (b["comment"] as? JsonPrimitive)?.contentOrNull
            FakeJournalApi.item("m", "2026-01-10", 7, listOf("sympa"), comment, id)
        }
        val vm = edit()
        vm.setComment("   ")
        vm.save()
        assertTrue(vu)
        assertNull(comment)
    }

    @Test
    fun `supprimer`() {
        val vm = edit()
        vm.delete()
        assertEquals(listOf("deleteViewing e-2026-01-10"), api.calls)
        assertEquals("Supprimé", vm.ui.value.done)
    }

    @Test
    fun `un 401 previent la session`() {
        api.onAddMedia = { throw FakeJournalApi.unauthorized() }
        create().save()
        assertEquals(1, expire)
    }

    // Le film est bien ajouté quand `POST /me/journal` répond 401 (session expirée entre les deux
    // appels) : ce cas ne doit pas se lire comme le back qui refuse le visionnage (décision 3), il
    // doit se lire comme n'importe quel 401 — la session, sans phrase de contexte ni « Réessayer ».
    @Test
    fun `un 401 sur le visionnage previent la session, sans contexte`() {
        api.onAddViewing = { throw FakeJournalApi.unauthorized() }
        val vm = create()
        vm.save()
        assertEquals(1, expire)
        assertNull(vm.ui.value.error)
        assertNull(vm.ui.value.errorContext)
        assertNull(vm.ui.value.done)
    }

    // Correction 1 de la tâche 6, décision 2 : ce `FormViewModel` reste en vie (indexé sur le
    // film, décision 5), donc une réouverture après un succès ressortirait sinon la note, les
    // réactions et le commentaire de l'action qui vient de réussir.
    @Test
    fun `apres un enregistrement reussi, le brouillon repart a zero`() {
        api.onAddViewing = { b -> FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
        val vm = create()
        vm.toggleRating(8)
        vm.toggleReaction("adore")
        vm.setComment("Un avis.")
        vm.save()

        assertEquals("Enregistré", vm.ui.value.done)
        assertNull(vm.ui.value.rating)
        assertTrue(vm.ui.value.reactions.isEmpty())
        assertEquals("", vm.ui.value.comment)
    }

    @Test
    fun `apres un enregistrement reussi, un second enregistrement rappelle addMedia`() {
        api.onAddViewing = { b -> FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
        val vm = create()
        vm.save()
        vm.save()
        assertEquals(listOf("addMedia 129", "addViewing m-129", "addMedia 129", "addViewing m-129"), api.calls)
    }

    // C'est `FormScreen` qui appelle `doneConsumed()` une fois `nav.home(...)` fait (correction 1) ;
    // ce test verifie seulement que l'appel efface bien le signal, sans le rejouer.
    @Test
    fun `doneConsumed efface le signal`() {
        val vm = create()
        vm.save()
        assertEquals("Enregistré", vm.ui.value.done)
        vm.doneConsumed()
        assertNull(vm.ui.value.done)
    }

    // ---------------------------------------------------------------------------
    // SensCritique (brief du 14 septembre 2026), avec un faux `ExternalRatingService`.
    // ---------------------------------------------------------------------------

    private fun candidat(id: Long, title: String = "Le Voyage de Chihiro", year: Int? = 2001) =
        ExternalCandidate(id, title, originalTitle = null, year = year)

    @Test
    fun `note nulle, connecte — aucun appel SensCritique, message sans suffixe`() {
        val service = FakeExternalRatingService()
        val vm = create(connectedSync(InMemorySensCritiqueStore(), service))
        vm.save() // brouillon par defaut : pas de note
        assertEquals("Enregistré", vm.ui.value.done)
        assertTrue(service.searchCalls.isEmpty())
        assertTrue(service.pushCalls.isEmpty())
    }

    @Test
    fun `note posee, non connecte — aucun appel SensCritique, message sans suffixe`() {
        val service = FakeExternalRatingService()
        val vm = create(disconnectedSync())
        vm.toggleRating(8)
        vm.save()
        assertEquals("Enregistré", vm.ui.value.done)
        assertTrue(service.searchCalls.isEmpty())
        assertTrue(service.pushCalls.isEmpty())
    }

    @Test
    fun `candidat net — pousse et le message porte la coche`() {
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Success(listOf(candidat(42))) })
        val vm = create(connectedSync(InMemorySensCritiqueStore(), service))
        vm.toggleRating(8)
        vm.save()
        assertEquals("Enregistré · SensCritique ✓", vm.ui.value.done)
        assertEquals(listOf(Triple(42L, 8, LocalDate.now())), service.pushCalls)
    }

    // Mutation : rendre `ExternalPushOutcome.Failed` sans jamais appeler `enqueue` dans
    // `SensCritiqueSync.pushAndRecord` fait echouer la seconde assertion (file vide) ; renvoyer le
    // message avec la coche malgre l'echec fait echouer la premiere.
    @Test
    fun `poussee echouee — message de reessai et poussee mise en file`() {
        val store = InMemorySensCritiqueStore()
        val service = FakeExternalRatingService(
            onSearch = { ExternalSearchOutcome.Success(listOf(candidat(42))) },
            onPush = { _, _, _ -> ExternalPushOutcome.Failed },
        )
        val vm = create(connectedSync(store, service))
        vm.toggleRating(8)
        vm.save()
        assertEquals("Enregistré · SensCritique : réessai au prochain lancement", vm.ui.value.done)
        val queued = store.readQueue()["m-129"]
        assertEquals(42L, queued?.productId)
        assertEquals(8, queued?.rating)
    }

    // Revue du 14 septembre 2026, important 2 : le message « reconnecte-toi » ne doit jamais
    // s'afficher pendant que le magasin croit encore etre connecte, sinon le profil dirait
    // toujours « Connecté : … » alors que la prochaine poussee echouera pareil.
    // `SensCritiqueSync.pushAndRecord` deconnecte deja le magasin (`store.writeAuth(null)`) sur
    // `ExternalPushOutcome.Unauthenticated` : ce test l'interdit d'un cote (le store) comme de
    // l'autre (le message). Mutation : dans `pushAndRecord`, ne plus appeler `store.writeAuth(null)`
    // sur cette branche fait echouer la seconde assertion (le store resterait connecte).
    @Test
    fun `poussee non authentifiee — message de reconnexion et magasin deconnecte`() {
        val store = InMemorySensCritiqueStore()
        val service = FakeExternalRatingService(
            onSearch = { ExternalSearchOutcome.Success(listOf(candidat(42))) },
            onPush = { _, _, _ -> ExternalPushOutcome.Unauthenticated },
        )
        val vm = create(connectedSync(store, service))
        vm.toggleRating(8)
        vm.save()

        assertEquals("Enregistré · SensCritique : reconnecte-toi", vm.ui.value.done)
        assertNull("la poussee non authentifiee doit deconnecter le magasin", store.readAuth())
    }

    // Mutation : rendre `Appariement.Ambigu` en `Apparie` (premier candidat) dans `apparierCandidat`
    // fait echouer la premiere assertion (`pendingSensCritiqueChoice` resterait nul, `done` serait
    // deja pose). Ne jamais poser `pendingSensCritiqueChoice` fait echouer la meme assertion.
    @Test
    fun `candidats ambigus — l etat choix demandé precede done, puis le choix pousse`() {
        val store = InMemorySensCritiqueStore()
        val service = FakeExternalRatingService(
            onSearch = { ExternalSearchOutcome.Success(listOf(candidat(1), candidat(2))) },
        )
        val vm = create(connectedSync(store, service))
        vm.toggleRating(8)
        vm.save()

        assertNull("le geste ne doit pas etre termine tant que le choix n'est pas fait", vm.ui.value.done)
        val pending = vm.ui.value.pendingSensCritiqueChoice
        assertEquals(listOf(1L, 2L), pending?.candidates?.map { it.productId })

        vm.chooseSensCritiqueCandidate(2L)
        assertNull(vm.ui.value.pendingSensCritiqueChoice)
        assertEquals("Enregistré · SensCritique ✓", vm.ui.value.done)
        assertEquals(listOf(Triple(2L, 8, LocalDate.now())), service.pushCalls)
    }

    // Le choix « Aucun de ceux-là » (productId nul) est memorise (brief §2) : une seconde
    // correction du meme film ne redemande plus, et ne pousse rien.
    @Test
    fun `Aucun de ceux-la memorise la decision et ne pousse rien`() {
        val store = InMemorySensCritiqueStore()
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Success(listOf(candidat(1), candidat(2))) })
        val vm = create(connectedSync(store, service))
        vm.toggleRating(8)
        vm.save()
        vm.chooseSensCritiqueCandidate(null)

        assertEquals("Enregistré", vm.ui.value.done)
        assertTrue(service.pushCalls.isEmpty())
        assertTrue("m-129" in store.readDecisions())
        assertNull(store.readDecisions()["m-129"])
    }

    // Critique 2 de la revue du 14 septembre 2026 : le retour systeme referme la feuille quoi qu'il
    // arrive (Material 3 ne peut pas l'en empecher) — sans abandonSensCritiqueChoice(),
    // pendingSensCritiqueChoice restait non nul et done n'etait jamais pose, alors que l'entree
    // etait deja ecrite au back : le geste restait bloque, invisible, la feuille disparue.
    // Mutation : ne rien faire dans `abandonSensCritiqueChoice` (juste effacer
    // `pendingSensCritiqueChoice`, sans appeler `sensCritique.abandon`) fait echouer les deux
    // dernieres assertions (file vide, aucun message).
    @Test
    fun `abandonner la feuille (retour systeme) met la poussee en file et termine le geste`() {
        val store = InMemorySensCritiqueStore()
        val service = FakeExternalRatingService(onSearch = { ExternalSearchOutcome.Success(listOf(candidat(1), candidat(2))) })
        val vm = create(connectedSync(store, service))
        vm.toggleRating(8)
        vm.save()
        assertNotNull("la feuille doit etre affichee avant l'abandon", vm.ui.value.pendingSensCritiqueChoice)

        vm.abandonSensCritiqueChoice()

        assertNull(vm.ui.value.pendingSensCritiqueChoice)
        assertEquals("Enregistré · SensCritique : réessai au prochain lancement", vm.ui.value.done)
        assertTrue(service.pushCalls.isEmpty())
        val queued = store.readQueue()["m-129"]
        assertEquals(8, queued?.rating)
        assertNull("pas de productId : la resolution redemandera a la prochaine correction", queued?.productId)
        // Aucune decision memorisee — a la difference de « Aucun de ceux-la » : la prochaine
        // correction du film redemande.
        assertFalse("m-129" in store.readDecisions())
    }

    // abandonSensCritiqueChoice() sans feuille en attente (deja consommee, ou jamais posee) ne doit
    // rien faire : ni exception, ni double poussee en file.
    @Test
    fun `abandonner sans feuille en attente ne fait rien`() {
        val vm = create(disconnectedSync())
        vm.abandonSensCritiqueChoice()
        assertNull(vm.ui.value.done)
        assertNull(vm.ui.value.pendingSensCritiqueChoice)
    }
}
