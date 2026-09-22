package fr.mediatheque.journal.reactions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Source unique des réactions (décision du 22 septembre 2026) : le catalogue
 * vit désormais dans le back (`packages/shared/src/reactions.ts`), servi par
 * `GET /reference/reactions`. `Reactions.kt` n'en garde qu'une copie compilée,
 * pour l'écran hors ligne — ce test la compare à l'exemple du contrat, le
 * catalogue entier, dans l'ordre, et rougit à la moindre divergence : une
 * réaction ajoutée, retirée, renommée ou réordonnée côté back doit casser
 * ici, avant le téléphone.
 */
class ReactionsContratTest {
    private val contrat: JsonElement by lazy {
        Json.parseToJsonElement(File("../contract/openapi.json").readText())
    }

    @Test fun `le catalogue compile est identique, dans l'ordre, a l'exemple du contrat`() {
        val exemple = contrat.jsonObject["paths"]!!.jsonObject["/reference/reactions"]!!.jsonObject["get"]!!
            .jsonObject["responses"]!!.jsonObject["200"]!!.jsonObject["content"]!!.jsonObject["application/json"]!!
            .jsonObject["examples"]!!.jsonObject["Réponse type"]!!.jsonObject["value"]!!
            .jsonObject["reactions"]!!.jsonArray

        val duContrat = exemple.map {
            val reaction = it.jsonObject
            Triple(
                reaction["cle"]!!.jsonPrimitive.content,
                reaction["emoji"]!!.jsonPrimitive.content,
                reaction["phrase"]!!.jsonPrimitive.content,
            )
        }
        val duCatalogue = Reactions.all.map { Triple(it.key, it.emoji, it.phrase) }

        assertEquals(duContrat, duCatalogue)
    }
}
