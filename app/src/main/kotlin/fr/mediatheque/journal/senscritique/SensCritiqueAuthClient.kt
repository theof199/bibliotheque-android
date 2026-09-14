package fr.mediatheque.journal.senscritique

import android.util.Log
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
    data object Refused : RefreshOutcome
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
 * Trois issues distinctes pour le corps HTTP de la connexion (succès, refusé, autre échec) :
 * aucune ambiguïté sur ce que `body` veut dire une fois sorti du bloc `try`.
 */
private sealed interface SignInHttpOutcome {
    data class Body(val text: String) : SignInHttpOutcome
    data object InvalidCredentials : SignInHttpOutcome
    data object OtherFailure : SignInHttpOutcome
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
) : SensCritiqueAuthClient {

    override suspend fun signIn(email: String, password: String): SignInOutcome {
        val outcome = try {
            val response = client.post(SIGN_IN_URL) {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(SignInBody.serializer(), SignInBody(email, password)))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Log.d(TAG_SENSCRITIQUE, "connexion refusee (${response.status.value}) : $body")
                val code = runCatching { firebaseJson.decodeFromString(FirebaseErrorEnvelope.serializer(), body).error.message }.getOrNull()
                if (code == "INVALID_LOGIN_CREDENTIALS") SignInHttpOutcome.InvalidCredentials else SignInHttpOutcome.OtherFailure
            } else {
                SignInHttpOutcome.Body(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.d(TAG_SENSCRITIQUE, "connexion injoignable : ${e.message}")
            return SignInOutcome.Unreachable
        }

        val text = when (outcome) {
            SignInHttpOutcome.InvalidCredentials -> return SignInOutcome.InvalidCredentials
            SignInHttpOutcome.OtherFailure -> return SignInOutcome.Unreachable
            is SignInHttpOutcome.Body -> outcome.text
        }

        val parsed = runCatching { firebaseJson.decodeFromString(SignInResponse.serializer(), text) }.getOrNull()
        if (parsed == null) {
            Log.d(TAG_SENSCRITIQUE, "reponse de connexion illisible : $text")
            return SignInOutcome.Unreachable
        }
        val expiresIn = parsed.expiresIn.toLongOrNull() ?: 3600L
        val pseudo = parsed.displayName
        if (pseudo == null) {
            Log.d(TAG_SENSCRITIQUE, "connexion reussie mais displayName absent de la reponse Firebase : $text")
        } else {
            // Best-effort : une verification GraphQL qui echoue ne doit jamais faire echouer une
            // connexion Firebase qui, elle, a reussi (brief : jamais un plantage, jamais un blocage
            // sur une hypothese de forme non verifiee).
            val ok = graphql.whoAmI(parsed.idToken, pseudo)
            if (!ok) Log.d(TAG_SENSCRITIQUE, "verification GraphQL du pseudo echouee pour $pseudo (connexion gardee)")
        }
        return SignInOutcome.Success(parsed.idToken, parsed.refreshToken, expiresIn, pseudo)
    }

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        val text = try {
            val response = client.post(REFRESH_URL) {
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                }))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Log.d(TAG_SENSCRITIQUE, "renouvellement refuse (${response.status.value}) : $body")
                null
            } else {
                body
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.d(TAG_SENSCRITIQUE, "renouvellement injoignable : ${e.message}")
            null
        }
        if (text == null) return RefreshOutcome.Refused
        val parsed = runCatching { firebaseJson.decodeFromString(RefreshResponse.serializer(), text) }.getOrNull()
        if (parsed == null) {
            Log.d(TAG_SENSCRITIQUE, "reponse de renouvellement illisible : $text")
            return RefreshOutcome.Refused
        }
        val expiresIn = parsed.expires_in.toLongOrNull() ?: 3600L
        return RefreshOutcome.Success(parsed.id_token, parsed.refresh_token, expiresIn)
    }
}
