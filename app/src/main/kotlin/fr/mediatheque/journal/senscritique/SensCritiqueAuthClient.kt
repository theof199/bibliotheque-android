package fr.mediatheque.journal.senscritique

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * La clé de l'application web de SensCritique, lue sur leur site le 14 septembre 2026 — elle est à
 * eux, publique par nature (toute page web qui parle à Firebase Auth l'expose), et peut changer.
 */
const val SENSCRITIQUE_FIREBASE_API_KEY = "AIzaSyDW8Pil_nhRW4Toww5JvUOO5XGgAxHNVMY"

const val TAG_SENSCRITIQUE = "SensCritique"

private const val SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$SENSCRITIQUE_FIREBASE_API_KEY"
private const val REFRESH_URL = "https://securetoken.googleapis.com/v1/token?key=$SENSCRITIQUE_FIREBASE_API_KEY"

sealed interface SignInOutcome {
    data class Success(val idToken: String, val refreshToken: String, val expiresInSeconds: Long, val pseudo: String?) : SignInOutcome
    data object InvalidCredentials : SignInOutcome
    data object Unreachable : SignInOutcome
}

sealed interface RefreshOutcome {
    data class Success(val idToken: String, val refreshToken: String, val expiresInSeconds: Long) : RefreshOutcome
    /** 400/401/403 avec un corps d'erreur Firebase : le `refreshToken` n'est plus valable, déconnexion méritée. */
    data object Refused : RefreshOutcome
    /** Réseau, 5xx, corps illisible : transitoire, ne doit jamais déconnecter (revue du 14 septembre 2026, important 3). */
    data object Unreachable : RefreshOutcome
}

/** La connexion Firebase Auth de SensCritique — REST, jamais un SDK (brief du 14 septembre 2026). */
interface SensCritiqueAuthClient {
    suspend fun signIn(email: String, password: String): SignInOutcome
    suspend fun refresh(refreshToken: String): RefreshOutcome
}

@Serializable
private data class SignInBody(val email: String, val password: String, val returnSecureToken: Boolean = true)

@Serializable
private data class SignInResponse(
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String,
    val displayName: String? = null,
)

@Serializable
private data class FirebaseErrorEnvelope(val error: FirebaseError)

@Serializable
private data class FirebaseError(val code: Int? = null, val message: String? = null)

@Serializable
private data class RefreshResponse(
    val id_token: String,
    val refresh_token: String,
    val expires_in: String,
)

// `ignoreUnknownKeys` : les deux réponses Firebase sont plus riches que ce qu'on en lit.
private val firebaseJson = Json { ignoreUnknownKeys = true }

/**
 * Les noms des clefs JSON présentes au premier niveau, jamais les valeurs. Un corps de réponse
 * Firebase porte `idToken` et `refreshToken` en clair — y compris sur la branche attendue
 * (`displayName` absent) — et le `release` n'est pas minifié : le journaliser tel quel les ferait
 * sortir sur le téléphone du propriétaire, que le README invite justement à lire (revue du
 * 14 septembre 2026, critique 1). Un corps illisible n'a rien à cacher, mais on ne le répète pas
 * non plus : il peut être partiellement valide.
 */
private fun clefsSeulement(corps: String): String {
    val objet = runCatching { firebaseJson.parseToJsonElement(corps) }.getOrNull() as? JsonObject
    return objet?.keys?.sorted()?.joinToString(", ") ?: "illisible"
}

/**
 * Trois issues distinctes pour le corps HTTP de la connexion (succès, refusé, autre échec) :
 * aucune ambiguïté sur ce que `body` veut dire une fois sorti du bloc `try`.
 */
private sealed interface SignInHttpOutcome {
    data class Body(val text: String) : SignInHttpOutcome
    data object InvalidCredentials : SignInHttpOutcome
    data object OtherFailure : SignInHttpOutcome
}

/** Même idée pour le renouvellement — `Refused` seulement sur les statuts d'un vrai refus Firebase. */
private val REFUS_STATUS_CODES = setOf(400, 401, 403)

private sealed interface RefreshHttpOutcome {
    data class Body(val text: String) : RefreshHttpOutcome
    data object Refused : RefreshHttpOutcome
    data object Unreachable : RefreshHttpOutcome
}

/**
 * Implémentation réelle : Firebase Auth REST, puis une vérification GraphQL du pseudo (brief §
 * « ce qui est vérifié », `query { user(username:) }`) — jamais bloquante pour la connexion
 * elle-même, seulement pour confirmer que le jeton marche aussi côté GraphQL. `displayName` peut
 * être absent (jamais vérifié sur le vrai compte) : la connexion réussit quand même, sans pseudo,
 * et le fait se journalise pour la découverte réelle.
 *
 * Le délai de 10 s (brief §6) est posé une fois sur `client` lui-même (`HttpTimeout`, dans
 * `AppContainer`), pas ici par `withTimeout` : combiné à `runTest` (horloge virtuelle), un
 * `withTimeout` posé autour de l'appel déclenchait un abandon immédiat en test, avant même que le
 * `MockEngine` ne réponde — constaté en écrivant les tests de ce fichier.
 */
class FirebaseSensCritiqueAuthClient(
    private val client: HttpClient,
    private val graphql: SensCritiqueGraphQLClient,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
) : SensCritiqueAuthClient {

    override suspend fun signIn(email: String, password: String): SignInOutcome {
        val outcome = try {
            val response = client.post(SIGN_IN_URL) {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(SignInBody.serializer(), SignInBody(email, password)))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.d("connexion refusee (${response.status.value}), clefs : ${clefsSeulement(body)}")
                val code = runCatching { firebaseJson.decodeFromString(FirebaseErrorEnvelope.serializer(), body).error.message }.getOrNull()
                if (code == "INVALID_LOGIN_CREDENTIALS") SignInHttpOutcome.InvalidCredentials else SignInHttpOutcome.OtherFailure
            } else {
                SignInHttpOutcome.Body(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d("connexion injoignable : ${e.message}")
            return SignInOutcome.Unreachable
        }

        val text = when (outcome) {
            SignInHttpOutcome.InvalidCredentials -> return SignInOutcome.InvalidCredentials
            SignInHttpOutcome.OtherFailure -> return SignInOutcome.Unreachable
            is SignInHttpOutcome.Body -> outcome.text
        }

        val parsed = runCatching { firebaseJson.decodeFromString(SignInResponse.serializer(), text) }.getOrNull()
        if (parsed == null) {
            logger.d("reponse de connexion illisible, clefs : ${clefsSeulement(text)}")
            return SignInOutcome.Unreachable
        }
        val expiresIn = parsed.expiresIn.toLongOrNull() ?: 3600L
        val pseudo = parsed.displayName
        if (pseudo == null) {
            logger.d("connexion reussie mais displayName absent, clefs : ${clefsSeulement(text)}")
        } else {
            // Best-effort : une verification GraphQL qui echoue ne doit jamais faire echouer une
            // connexion Firebase qui, elle, a reussi (brief : jamais un plantage, jamais un blocage
            // sur une hypothese de forme non verifiee).
            val ok = graphql.whoAmI(parsed.idToken, pseudo)
            if (!ok) logger.d("verification GraphQL du pseudo echouee pour $pseudo (connexion gardee)")
        }
        return SignInOutcome.Success(parsed.idToken, parsed.refreshToken, expiresIn, pseudo)
    }

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        // Seuls 400/401/403 avec un corps d'erreur Firebase valent un vrai refus (le refreshToken
        // n'est plus bon) : tout le reste (reseau, 5xx, corps illisible) est transitoire et ne doit
        // jamais deconnecter le compte (revue du 14 septembre 2026, important 3).
        val outcome = try {
            val response = client.post(REFRESH_URL) {
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                }))
            }
            val body = response.bodyAsText()
            when {
                response.status.isSuccess() -> RefreshHttpOutcome.Body(body)
                response.status.value in REFUS_STATUS_CODES -> {
                    logger.d("renouvellement refuse (${response.status.value}), clefs : ${clefsSeulement(body)}")
                    RefreshHttpOutcome.Refused
                }
                else -> {
                    logger.d("renouvellement injoignable (${response.status.value}), clefs : ${clefsSeulement(body)}")
                    RefreshHttpOutcome.Unreachable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d("renouvellement injoignable : ${e.message}")
            RefreshHttpOutcome.Unreachable
        }

        val text = when (outcome) {
            RefreshHttpOutcome.Refused -> return RefreshOutcome.Refused
            RefreshHttpOutcome.Unreachable -> return RefreshOutcome.Unreachable
            is RefreshHttpOutcome.Body -> outcome.text
        }

        val parsed = runCatching { firebaseJson.decodeFromString(RefreshResponse.serializer(), text) }.getOrNull()
        if (parsed == null) {
            logger.d("reponse de renouvellement illisible, clefs : ${clefsSeulement(text)}")
            return RefreshOutcome.Unreachable
        }
        val expiresIn = parsed.expires_in.toLongOrNull() ?: 3600L
        return RefreshOutcome.Success(parsed.id_token, parsed.refresh_token, expiresIn)
    }
}
