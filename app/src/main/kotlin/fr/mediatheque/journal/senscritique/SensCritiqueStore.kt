package fr.mediatheque.journal.senscritique

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ce que le mot de passe SensCritique ne devient jamais : `cookieRef` (rendu par la mutation de
 * connexion, brief du 14 septembre 2026 après un premier essai réel — jamais un jeton Firebase) et
 * `dateExpiration`, tel que SensCritique le rend (une chaîne ISO, jamais reformatée), plus le
 * pseudo.
 */
@Serializable
data class SensCritiqueAuth(val cookieRef: String, val dateExpiration: String, val pseudo: String?)

/** Une poussée qui a échoué, en attente d'un rejeu — brief §5. */
@Serializable
data class QueuedPush(
    val mediaId: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val rating: Int,
    val watchedOn: String,
    /** Nul quand la résolution elle-même a échoué (pas encore de produit trouvé). */
    val productId: Long? = null,
)

/**
 * Où le jeton SensCritique (le `cookieRef`, jamais le mot de passe), le pseudo, les choix de la
 * feuille et la file des poussées échouées dorment entre deux lancements — jumeau de `SessionStore`
 * (brief du 14 septembre 2026).
 *
 * `decisions` : `media_id -> productId` (résolu par la feuille) ou `media_id -> null` (« Aucun de
 * ceux-là », mémorisé aussi) — absence de la clé veut dire « jamais demandé ». C'est la raison pour
 * laquelle ce n'est **pas** un simple `get()` côté lecteur : `Map<String, Long?>.get()` ne distingue
 * pas une clé absente d'une clé présente à `null`, `containsKey` est nécessaire (revue interne).
 */
interface SensCritiqueStore {
    fun readAuth(): SensCritiqueAuth?
    fun writeAuth(auth: SensCritiqueAuth?)

    fun readDecisions(): Map<String, Long?>
    fun writeDecisions(decisions: Map<String, Long?>)

    fun readQueue(): Map<String, QueuedPush>
    fun writeQueue(queue: Map<String, QueuedPush>)

    /** « Déconnecter » : efface tout, file comprise (brief §1). */
    fun clear()
}

/** Le magasin des tests — jumeau d'`InMemorySessionStore`. */
class InMemorySensCritiqueStore : SensCritiqueStore {
    private var auth: SensCritiqueAuth? = null
    private var decisions: Map<String, Long?> = emptyMap()
    private var queue: Map<String, QueuedPush> = emptyMap()

    override fun readAuth(): SensCritiqueAuth? = auth
    override fun writeAuth(auth: SensCritiqueAuth?) { this.auth = auth }
    override fun readDecisions(): Map<String, Long?> = decisions
    override fun writeDecisions(decisions: Map<String, Long?>) { this.decisions = decisions }
    override fun readQueue(): Map<String, QueuedPush> = queue
    override fun writeQueue(queue: Map<String, QueuedPush>) { this.queue = queue }
    override fun clear() { auth = null; decisions = emptyMap(); queue = emptyMap() }
}

/**
 * Version 2 (brief du 14 septembre 2026, étape 2) : `SensCritiqueAuth` porte `cookieRef` et
 * `dateExpiration`, plus le pseudo — la version 1 (Firebase, `refreshToken`) ne s'y décode plus,
 * voir `decodePayload`.
 */
const val PAYLOAD_VERSION = 2

/**
 * Le `Json` du magasin — un seul, partagé par `KeystoreSensCritiqueStore` et par les tests qui
 * l'exercent en aller-retour réel (`SensCritiquePayloadCodecTest`), pour ne jamais dériver de la
 * configuration réellement écrite sur le téléphone. `encodeDefaults = true` est indispensable
 * (correctif du 15 septembre 2026, cause racine de « je dois me reconnecter tout le temps ») :
 * `Payload.version` porte une valeur par défaut (`= PAYLOAD_VERSION`), et sans `encodeDefaults`,
 * kotlinx.serialization n'écrit jamais un champ égal à son défaut — le blob chiffré ne portait donc
 * aucun `version`. `decodePayload` le lisait alors absent (`?: 1`), prenait un blob pourtant en
 * version 2 pour l'ancien format Firebase, et rendait `auth = null` : déconnecté. Le cache mémoire
 * de `KeystoreSensCritiqueStore` masquait le défaut tant que l'application restait en vie ; au
 * relancement, le blob relu retombait dans le même piège. `ignoreUnknownKeys` : jumeau
 * d'`ApiClient.ApiJson`, un champ ajouté plus tard ne casse pas la lecture d'un blob plus ancien.
 */
val SENSCRITIQUE_STORE_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class Payload(
    val version: Int = PAYLOAD_VERSION,
    val auth: SensCritiqueAuth? = null,
    val decisions: Map<String, Long?> = emptyMap(),
    val queue: Map<String, QueuedPush> = emptyMap(),
)

private val DECISIONS_SERIALIZER = MapSerializer(String.serializer(), Long.serializer().nullable)
private val QUEUE_SERIALIZER = MapSerializer(String.serializer(), QueuedPush.serializer())

/**
 * Une charge utile en version 1 (Firebase — `SensCritiqueAuth(refreshToken, pseudo)`) ne se décode
 * plus dans la forme actuelle de `SensCritiqueAuth` (`cookieRef`, `dateExpiration`) : un champ requis
 * manquerait, et `Payload.serializer()` entier échouerait, y compris `decisions` et `queue` —
 * inchangées par cette migration, alors qu'elles n'ont aucune raison d'être perdues. Lue
 * déconnectée (`auth = null`), en gardant tout le reste, plutôt que rejetée en bloc.
 */
fun decodePayload(json: Json, texte: String): Payload {
    val racine = json.parseToJsonElement(texte) as JsonObject
    val version = (racine["version"] as? JsonPrimitive)?.intOrNull ?: 1
    if (version >= PAYLOAD_VERSION) {
        return json.decodeFromJsonElement(Payload.serializer(), racine)
    }
    val decisions = (racine["decisions"] as? JsonObject)?.let {
        runCatching { json.decodeFromJsonElement(DECISIONS_SERIALIZER, it) }.getOrNull()
    } ?: emptyMap()
    val queue = (racine["queue"] as? JsonObject)?.let {
        runCatching { json.decodeFromJsonElement(QUEUE_SERIALIZER, it) }.getOrNull()
    } ?: emptyMap()
    return Payload(version = PAYLOAD_VERSION, auth = null, decisions = decisions, queue = queue)
}

/**
 * Le magasin réel : un unique blob JSON chiffré AES-GCM par une clé du `AndroidKeyStore` (jamais
 * `EncryptedSharedPreferences`, dépréciée — brief §1), posé dans ses propres `SharedPreferences`.
 * Une seule clé matérielle, un seul blob : pas de nonce à faire coïncider entre plusieurs champs
 * chiffrés séparément. Non exercé par les tests JVM (pas d'`AndroidKeyStore` hors du téléphone),
 * comme `PreferencesSessionStore`.
 *
 * `cache` (important 5 de la revue du 14 septembre 2026) : une fois lu, l'état vit en mémoire pour
 * le reste de la session — une écriture chiffrée qui échoue (Keystore verrouillé, `SharedPreferences`
 * en panne) reste alors visible aux lectures suivantes de *cette* session, même si le disque, lui,
 * est resté en retard ; seule une nouvelle session repartirait du dernier blob écrit avec succès.
 */
class KeystoreSensCritiqueStore(
    context: Context,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
) : SensCritiqueStore {
    private val prefs = context.getSharedPreferences("senscritique", Context.MODE_PRIVATE)

    // Le meme Json que celui documente plus haut (`SENSCRITIQUE_STORE_JSON`) : `encodeDefaults`
    // y est indispensable, voir sa doc.
    private val json = SENSCRITIQUE_STORE_JSON

    private var cache: Payload? = null

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun readPayload(): Payload {
        cache?.let { return it }
        val raw = prefs.getString(KEY_DATA, null) ?: return Payload().also { cache = it }
        val payload = runCatching {
            val separateur = raw.indexOf(':')
            val iv = Base64.decode(raw.substring(0, separateur), Base64.NO_WRAP)
            val chiffre = Base64.decode(raw.substring(separateur + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            decodePayload(json, String(cipher.doFinal(chiffre), Charsets.UTF_8))
        }.getOrElse {
            // Jamais le contenu (mineur e) : seule la lecture illisible compte, pas ce qu'elle cachait.
            logger.d("magasin SensCritique illisible, repart de zero pour cette session")
            Payload()
        }
        cache = payload
        return payload
    }

    private fun writePayload(payload: Payload) {
        // Optimiste : la session voit l'etat a jour meme si le chiffrement echoue plus bas
        // (important 5) — seul le disque resterait en retard, jamais cette session.
        cache = payload
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val chiffre = cipher.doFinal(json.encodeToString(Payload.serializer(), payload).toByteArray(Charsets.UTF_8))
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val donnees = Base64.encodeToString(chiffre, Base64.NO_WRAP)
            prefs.edit().putString(KEY_DATA, "$iv:$donnees").apply()
        }.onFailure {
            logger.d("ecriture du magasin SensCritique echouee, gardee en memoire pour cette session")
        }
    }

    override fun readAuth(): SensCritiqueAuth? = readPayload().auth
    override fun writeAuth(auth: SensCritiqueAuth?) { writePayload(readPayload().copy(auth = auth)) }
    override fun readDecisions(): Map<String, Long?> = readPayload().decisions
    override fun writeDecisions(decisions: Map<String, Long?>) { writePayload(readPayload().copy(decisions = decisions)) }
    override fun readQueue(): Map<String, QueuedPush> = readPayload().queue
    override fun writeQueue(queue: Map<String, QueuedPush>) { writePayload(readPayload().copy(queue = queue)) }
    override fun clear() {
        cache = Payload()
        runCatching { prefs.edit().remove(KEY_DATA).apply() }
    }

    private companion object {
        const val KEY_ALIAS = "senscritique_store_key"
        const val KEY_DATA = "payload"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
