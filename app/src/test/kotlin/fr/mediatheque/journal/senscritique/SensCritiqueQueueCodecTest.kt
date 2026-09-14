package fr.mediatheque.journal.senscritique

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La file (brief §5) : remplacement par `media_id`, sérialisation aller-retour — le format que
 * `KeystoreSensCritiqueStore` chiffre est celui-ci, mêmes types, même config `Json`
 * (`ignoreUnknownKeys`).
 */
class SensCritiqueQueueCodecTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val queueSerializer = MapSerializer(String.serializer(), QueuedPush.serializer())
    private val decisionsSerializer = MapSerializer(String.serializer(), Long.serializer().nullable)

    // Mutation : passer par une carte tenue à la main (`mutableMapOf` modifié en place puis
    // recopiée) plutôt que `readQueue() + (id to push)` peut oublier l'ancienne entrée en cas
    // d'ordre différent — ce test compare le résultat final, pas la mécanique. Le remplacer par
    // `readQueue() + (autreId to push)` (mauvaise clé) fait échouer les deux assertions de taille
    // et de contenu.
    @Test
    fun `une nouvelle poussee pour le meme media_id remplace l ancienne, jamais ne s ajoute`() {
        val store = InMemorySensCritiqueStore()
        val premiere = QueuedPush("m1", "Chihiro", null, 2001, 6, "2026-09-10", productId = null)
        val seconde = QueuedPush("m1", "Chihiro", null, 2001, 9, "2026-09-11", productId = 42L)

        store.writeQueue(store.readQueue() + (premiere.mediaId to premiere))
        store.writeQueue(store.readQueue() + (seconde.mediaId to seconde))

        assertEquals(1, store.readQueue().size)
        assertEquals(seconde, store.readQueue()["m1"])
    }

    @Test
    fun `une poussee pour un autre media_id s ajoute a cote, sans remplacer`() {
        val store = InMemorySensCritiqueStore()
        val chihiro = QueuedPush("m1", "Chihiro", null, 2001, 6, "2026-09-10")
        val perfectBlue = QueuedPush("m2", "Perfect Blue", null, 1997, 8, "2026-09-11")

        store.writeQueue(store.readQueue() + (chihiro.mediaId to chihiro))
        store.writeQueue(store.readQueue() + (perfectBlue.mediaId to perfectBlue))

        assertEquals(setOf("m1", "m2"), store.readQueue().keys)
    }

    @Test
    fun `une QueuedPush complete survit a un aller-retour JSON, champ par champ`() {
        val original = QueuedPush("m1", "Le Voyage de Chihiro", "Sen to Chihiro no Kamikakushi", 2001, 8, "2026-09-10", productId = 42L)
        val relue = json.decodeFromString(QueuedPush.serializer(), json.encodeToString(QueuedPush.serializer(), original))
        assertEquals(original, relue)
    }

    // `productId` absent (résolution non encore aboutie) et `originalTitle`/`year` absents (mode
    // `Edit`, brief) doivent rester nuls après le retour — pas une valeur par défaut qui masquerait
    // silencieusement une résolution jamais tentée.
    @Test
    fun `une QueuedPush sans productId ni originalTitle ni annee survit a un aller-retour JSON`() {
        val original = QueuedPush("m2", "Un film corrige", originalTitle = null, year = null, rating = 5, watchedOn = "2026-09-11", productId = null)
        val relue = json.decodeFromString(QueuedPush.serializer(), json.encodeToString(QueuedPush.serializer(), original))
        assertNull(relue.productId)
        assertNull(relue.originalTitle)
        assertNull(relue.year)
        assertEquals(original, relue)
    }

    // Les décisions distinguent `productId` (résolu) de `null` (« Aucun de ceux-là », brief §2) :
    // la présence de la clé avec une valeur nulle doit survivre, pas disparaître de la carte.
    @Test
    fun `les decisions — productId ou ignore (null) — survivent a un aller-retour JSON`() {
        val original: Map<String, Long?> = mapOf("m1" to 42L, "m2" to null)
        val relue = json.decodeFromString(decisionsSerializer, json.encodeToString(decisionsSerializer, original))
        assertEquals(original, relue)
        assertTrue("m2" in relue)
        assertNull(relue.getValue("m2"))
    }

    @Test
    fun `une file de plusieurs poussees, apres remplacement, survit a un aller-retour JSON`() {
        val queue = mapOf(
            "m1" to QueuedPush("m1", "Chihiro", null, 2001, 8, "2026-09-10", 42L),
            "m2" to QueuedPush("m2", "Perfect Blue", null, 1997, 6, "2026-09-11"),
        )
        val relue = json.decodeFromString(queueSerializer, json.encodeToString(queueSerializer, queue))
        assertEquals(queue, relue)
    }
}
