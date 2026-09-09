package fr.mediatheque.journal.ui.form

import fr.mediatheque.journal.FakeJournalApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * `patchBodyOf` pure et testée séparément (décision 1 de la tâche 6) : le
 * corps d'un `PATCH` ne porte que ce qui a changé depuis `original`.
 */
class PatchBodyOfTest {
    private val original = FakeJournalApi.item("m1", "2026-01-10", 7, listOf("sympa"), "Avant")
    private val identique = FormUi(date = LocalDate.of(2026, 1, 10), rating = 7, reactions = setOf("sympa"), comment = "Avant")

    @Test
    fun `rien ne change — objet vide`() = assertEquals(JsonObject(emptyMap()), patchBodyOf(original, identique))

    @Test
    fun `seule la reaction change — reactions seul, dans l ordre du catalogue`() {
        val form = identique.copy(reactions = setOf("sympa", "adore"))
        val body = patchBodyOf(original, form)
        assertEquals(setOf("reactions"), body.keys)
        assertEquals(listOf("adore", "sympa"), (body.getValue("reactions") as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun `l ordre de stockage du back ne compte pas — comparaison en ensemble, pas en liste`() {
        // Le back a stocké "touche" avant "adore", alors que le catalogue les range dans l'autre
        // sens (`Reactions.all` : adore, puis... touche). Une comparaison par liste, reconstruite
        // dans l'ordre du catalogue, verrait un changement là où l'ensemble choisi est identique.
        val stocke = FakeJournalApi.item("m1", "2026-01-10", 7, listOf("touche", "adore"), "Avant")
        val form = FormUi(date = LocalDate.of(2026, 1, 10), rating = 7, reactions = setOf("adore", "touche"), comment = "Avant")
        assertEquals(JsonObject(emptyMap()), patchBodyOf(stocke, form))
    }

    @Test
    fun `commentaire efface — comment a null seul`() {
        val form = identique.copy(comment = "   ")
        val body = patchBodyOf(original, form)
        assertEquals(setOf("comment"), body.keys)
        assertEquals(JsonNull, body.getValue("comment"))
    }

    @Test
    fun `commentaire change pour une autre valeur — la nouvelle chaine seule`() {
        val form = identique.copy(comment = "Après coup.")
        val body = patchBodyOf(original, form)
        assertEquals(setOf("comment"), body.keys)
        assertEquals(JsonPrimitive("Après coup."), body.getValue("comment"))
    }

    @Test
    fun `note retiree — rating a null seul`() {
        val form = identique.copy(rating = null)
        val body = patchBodyOf(original, form)
        assertEquals(setOf("rating"), body.keys)
        assertEquals(JsonNull, body.getValue("rating"))
    }

    @Test
    fun `note changee — la nouvelle valeur seule`() {
        val form = identique.copy(rating = 9)
        val body = patchBodyOf(original, form)
        assertEquals(setOf("rating"), body.keys)
        assertEquals(JsonPrimitive(9), body.getValue("rating"))
    }

    @Test
    fun `date changee — finished_at seul`() {
        val form = identique.copy(date = LocalDate.of(2026, 1, 11))
        val body = patchBodyOf(original, form)
        assertEquals(setOf("finished_at"), body.keys)
        assertEquals(JsonPrimitive("2026-01-11"), body.getValue("finished_at"))
    }

    @Test
    fun `tout change — les quatre champs`() {
        val form = FormUi(date = LocalDate.of(2026, 2, 1), rating = 9, reactions = setOf("nul"), comment = "Après")
        val body = patchBodyOf(original, form)
        assertEquals(setOf("finished_at", "rating", "reactions", "comment"), body.keys)
    }
}
