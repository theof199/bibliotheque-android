package fr.mediatheque.journal.ui.form

import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.reactions.Reactions
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Le corps d'un `PATCH /me/journal/:id`, réduit à ce qui a changé depuis
 * `original` (décision 1 de la tâche 6). Le back ne remonte la note au suivi
 * que si le corps porte la clé `rating`, et n'efface le commentaire que sur
 * un `null` explicite : un champ inchangé doit donc rester **absent**, jamais
 * réémis avec sa valeur d'avant — c'est pour ça qu'on construit un
 * `JsonObject` à la main plutôt qu'un DTO à valeurs par défaut, qui ne
 * saurait pas distinguer « inchangé » de « remis à zéro ».
 *
 * `reactions` se compare comme un ensemble, pas comme une liste : `FormUi`
 * porte un `Set` (sans ordre garanti côté sélection), et la liste qui part
 * vers le back est de toute façon reconstruite dans l'ordre du catalogue
 * (`Reactions.ordered`) — rejouer la même sélection dans un ordre différent
 * n'est donc pas un changement à signaler.
 */
fun patchBodyOf(original: JournalItem, form: FormUi): JsonObject = buildJsonObject {
    val finishedAt = form.date.toString()
    if (finishedAt != original.entry.finished_at) put("finished_at", JsonPrimitive(finishedAt))

    if (form.rating != original.entry.rating) {
        put("rating", form.rating?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    val originalReactions = original.carnet.reactions.toSet()
    if (form.reactions != originalReactions) {
        putJsonArray("reactions") { Reactions.ordered(form.reactions).forEach { add(JsonPrimitive(it)) } }
    }

    val comment = form.comment.trim().ifEmpty { null }
    if (comment != original.carnet.comment) {
        put("comment", comment?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}
