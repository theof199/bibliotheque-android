package fr.mediatheque.journal.ui.form

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FormViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeJournalApi()
    private val chihiro = SearchResult("tmdb", "129", "movie", "Le Voyage de Chihiro", 2001)
    private var done: String? = null
    private var expire = 0

    private fun create() = FormViewModel(api, FormMode.Create(chihiro), { expire++ }) { done = it }
    private fun edit() = FormViewModel(api, FormMode.Edit(FakeJournalApi.item("m", "2026-01-10", 7, listOf("sympa"), "Avant")), { expire++ }) { done = it }

    @Test
    fun `un film nouveau — deux appels dans l ordre, tous les champs envoyes`() {
        var corps: JournalCreateBody? = null
        api.onAddViewing = { b -> corps = b; FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions, b.comment) }
        val vm = create()
        vm.setDate(LocalDate.of(2026, 9, 3))
        vm.toggleRating(8)
        vm.toggleReaction("adore"); vm.toggleReaction("touche")
        vm.setComment("Revu avec Léa.")
        vm.save()

        assertEquals(listOf("addMedia 129", "addViewing m-129"), api.calls)
        assertEquals(JournalCreateBody("m-129", "2026-09-03", 8, listOf("adore", "touche"), "Revu avec Léa."), corps)
        assertEquals("Enregistré", done)
    }

    @Test
    fun `sans note ni commentaire, envoie null et une liste vide`() {
        var corps: JournalCreateBody? = null
        api.onAddViewing = { b -> corps = b; FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions, b.comment) }
        create().save()
        assertNull(corps!!.rating)
        assertNull(corps!!.comment)
        assertTrue(corps!!.reactions.isEmpty())
    }

    @Test
    fun `la date par defaut est aujourd hui`() {
        var corps: JournalCreateBody? = null
        api.onAddViewing = { b -> corps = b; FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions, b.comment) }
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
        assertNull(done)
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

        api.onAddViewing = { b -> FakeJournalApi.item(b.media_id, b.finished_at, b.rating, b.reactions, b.comment) }
        vm.retry()
        assertEquals(listOf("addMedia 129", "addViewing m-129", "addViewing m-129"), api.calls)
        assertEquals("Enregistré", done)
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
        assertEquals("Corrigé", done)
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
        assertEquals("Supprimé", done)
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
        assertNull(done)
    }
}
