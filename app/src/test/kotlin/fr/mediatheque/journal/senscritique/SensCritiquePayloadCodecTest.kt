package fr.mediatheque.journal.senscritique

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `decodePayload` — pure, testable sans `AndroidKeyStore` (à la différence de
 * `KeystoreSensCritiqueStore` qui l'appelle, brief du 14 septembre 2026, étape 2) : une charge
 * utile version 1 (Firebase, `SensCritiqueAuth(refreshToken, pseudo)`) ne se lit plus connectée
 * dans la forme actuelle (`cookieRef`, `dateExpiration`), mais `decisions` et `queue` —
 * inchangées par cette migration — survivent.
 */
class SensCritiquePayloadCodecTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `une charge utile version 2 se decode normalement, auth comprise`() {
        val texte = """
            {"version":2,"auth":{"cookieRef":"c1","dateExpiration":"2026-10-14T10:00:00Z","pseudo":"TheofB"},
             "decisions":{"m1":42},"queue":{}}
        """.trimIndent()
        val payload = decodePayload(json, texte)
        assertEquals(SensCritiqueAuth("c1", "2026-10-14T10:00:00Z", "TheofB"), payload.auth)
        assertEquals(mapOf("m1" to 42L), payload.decisions)
    }

    // Le coeur de la migration (brief §2). Mutation : ne pas router la version 1 vers la branche de
    // secours (donc tenter `Payload.serializer()` directement) ferait cette assertion echouer par
    // une exception (champs requis `cookieRef`/`dateExpiration` absents), pas par une assertion
    // ratee — la preuve est que ce test passe *sans* lever.
    @Test
    fun `une charge utile version 1 (Firebase, refreshToken) se lit deconnectee, decisions et queue conservees`() {
        val texte = """
            {"version":1,"auth":{"refreshToken":"refresh-1","pseudo":"TheofB"},
             "decisions":{"m1":42,"m2":null},
             "queue":{"m3":{"mediaId":"m3","title":"Chihiro","rating":8,"watchedOn":"2026-09-10","productId":42}}}
        """.trimIndent()
        val payload = decodePayload(json, texte)

        assertNull("version 1 : deconnecte, refreshToken ne se decode plus en cookieRef", payload.auth)
        assertEquals(PAYLOAD_VERSION, payload.version)
        assertEquals(mapOf("m1" to 42L, "m2" to null), payload.decisions)
        assertTrue("m3" in payload.queue)
        assertEquals("Chihiro", payload.queue.getValue("m3").title)
    }

    // Une charge utile sans `version` du tout (jamais ecrite par ce code, mais un ancien format
    // theorique) se traite comme la version 1 : deconnectee, plutot que de tenter un decodage qui
    // echouerait de la meme facon.
    @Test
    fun `une charge utile sans champ version se traite comme la version 1`() {
        val texte = """{"auth":{"refreshToken":"refresh-1","pseudo":"TheofB"},"decisions":{},"queue":{}}"""
        val payload = decodePayload(json, texte)
        assertNull(payload.auth)
        assertEquals(PAYLOAD_VERSION, payload.version)
    }

    @Test
    fun `une charge utile version 2 sans auth (jamais connecte) se decode normalement`() {
        val texte = """{"version":2,"decisions":{},"queue":{}}"""
        val payload = decodePayload(json, texte)
        assertNull(payload.auth)
        assertTrue(payload.decisions.isEmpty())
        assertTrue(payload.queue.isEmpty())
    }
}
