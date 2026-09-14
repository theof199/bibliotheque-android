package fr.mediatheque.journal.senscritique

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ce que le mot de passe SensCritique ne devient jamais : `refreshToken` (1 h à renouveler) et le pseudo. */
@Serializable
data class SensCritiqueAuth(val refreshToken: String, val pseudo: String?)

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
 * Où le jeton SensCritique (le `refreshToken`, jamais le mot de passe), le pseudo, les choix de la
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

@Serializable
private data class Payload(
    val auth: SensCritiqueAuth? = null,
    val decisions: Map<String, Long?> = emptyMap(),
    val queue: Map<String, QueuedPush> = emptyMap(),
)

/**
 * Le magasin réel : un unique blob JSON chiffré AES-GCM par une clé du `AndroidKeyStore` (jamais
 * `EncryptedSharedPreferences`, dépréciée — brief §1), posé dans ses propres `SharedPreferences`.
 * Une seule clé matérielle, un seul blob : pas de nonce à faire coïncider entre plusieurs champs
 * chiffrés séparément. Non exercé par les tests JVM (pas d'`AndroidKeyStore` hors du téléphone),
 * comme `PreferencesSessionStore`.
 */
class KeystoreSensCritiqueStore(context: Context) : SensCritiqueStore {
    private val prefs = context.getSharedPreferences("senscritique", Context.MODE_PRIVATE)

    // `ignoreUnknownKeys` : un champ ajouté à `Payload` dans une version future ne doit pas faire
    // échouer la lecture d'un blob écrit par une version plus ancienne — jumeau de `ApiClient.ApiJson`.
    private val json = Json { ignoreUnknownKeys = true }

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
        val raw = prefs.getString(KEY_DATA, null) ?: return Payload()
        return runCatching {
            val separateur = raw.indexOf(':')
            val iv = Base64.decode(raw.substring(0, separateur), Base64.NO_WRAP)
            val chiffre = Base64.decode(raw.substring(separateur + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            json.decodeFromString(Payload.serializer(), String(cipher.doFinal(chiffre), Charsets.UTF_8))
        }.getOrDefault(Payload())
    }

    private fun writePayload(payload: Payload) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val chiffre = cipher.doFinal(json.encodeToString(Payload.serializer(), payload).toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val donnees = Base64.encodeToString(chiffre, Base64.NO_WRAP)
        prefs.edit().putString(KEY_DATA, "$iv:$donnees").apply()
    }

    override fun readAuth(): SensCritiqueAuth? = readPayload().auth
    override fun writeAuth(auth: SensCritiqueAuth?) { writePayload(readPayload().copy(auth = auth)) }
    override fun readDecisions(): Map<String, Long?> = readPayload().decisions
    override fun writeDecisions(decisions: Map<String, Long?>) { writePayload(readPayload().copy(decisions = decisions)) }
    override fun readQueue(): Map<String, QueuedPush> = readPayload().queue
    override fun writeQueue(queue: Map<String, QueuedPush>) { writePayload(readPayload().copy(queue = queue)) }
    override fun clear() { prefs.edit().remove(KEY_DATA).apply() }

    private companion object {
        const val KEY_ALIAS = "senscritique_store_key"
        const val KEY_DATA = "payload"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
