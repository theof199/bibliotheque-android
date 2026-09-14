package fr.mediatheque.journal.senscritique

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * La mutation de connexion, vue dans les scripts du site SensCritique le 14 septembre 2026 — après
 * qu'un premier essai réel a montré que l'API GraphQL refuse un `idToken` Firebase
 * (`auth/unauthenticated-user`) : SensCritique n'utilise pas Firebase pour authentifier ses propres
 * appels, seulement pour son propre site web. La connexion est une mutation GraphQL comme les
 * autres, sur le même point d'entrée, avant d'avoir de jeton.
 */
private const val SIGN_IN_MUTATION = """
mutation Connexion(${'$'}email: String!, ${'$'}password: String!) {
  signInWithEmailAndPassword(email: ${'$'}email, password: ${'$'}password) {
    me { username }
    userCookie { cookieRef dateExpiration }
  }
}
"""

sealed interface SignInOutcome {
    data class Success(val cookieRef: String, val dateExpiration: String, val pseudo: String?) : SignInOutcome

    /**
     * Toute erreur GraphQL rendue par la mutation de connexion — identifiants faux ou autre, le
     * code n'est pas catalogué (brief du 14 septembre 2026 : contrairement à Firebase, cette API ne
     * rend pas de code stable et documenté ici). `code` sert uniquement au journal
     * (`SensCritiqueLogger`) ; l'écran affiche toujours « Identifiants refusés. », quel qu'il soit.
     */
    data class Refused(val code: String?) : SignInOutcome
    data object Unreachable : SignInOutcome
}

/** La connexion SensCritique : une mutation GraphQL, jamais Firebase (brief du 14 septembre 2026). */
interface SensCritiqueAuthClient {
    suspend fun signIn(email: String, password: String): SignInOutcome
}

/**
 * Implémentation réelle : `POST` sur le même point d'entrée GraphQL que le reste
 * (`SensCritiqueGraphQLClient`), sans en-tête `Authorization` — on n'a pas encore de `cookieRef`,
 * c'est justement ce que cette mutation rend. Le mot de passe ne survit jamais au-delà de cet
 * appel : jamais journalisé, jamais dans le corps d'une ligne de log, y compris sur les branches
 * d'échec.
 */
class GraphQlSensCritiqueAuthClient(
    private val client: HttpClient,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
) : SensCritiqueAuthClient {

    override suspend fun signIn(email: String, password: String): SignInOutcome {
        val body = try {
            val response = client.post(GRAPHQL_URL) {
                header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("query", SIGN_IN_MUTATION)
                        put("variables", buildJsonObject { put("email", email); put("password", password) })
                    }.toString(),
                )
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d("connexion injoignable : ${e.message}")
            return SignInOutcome.Unreachable
        }

        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        if (root == null) {
            logger.d("reponse de connexion illisible")
            return SignInOutcome.Unreachable
        }

        val errors = root["errors"] as? JsonArray
        if (errors != null && errors.isNotEmpty()) {
            val code = codeDeLaPremiereErreur(errors)
            // Le code n'est pas un secret (une constante fixe du fournisseur, brief §1) : il se
            // journalise en clair, à la difference du mot de passe qui n'atteint jamais ce point.
            // Jamais le tableau `errors` entier : une erreur de validation GraphQL peut y recopier
            // une variable envoyee (le mot de passe, par exemple), dans `message`.
            logger.d("connexion refusee : ${code ?: "${errors.size} erreur(s) sans code"}")
            return SignInOutcome.Refused(code)
        }

        val resultat = (root["data"] as? JsonObject)?.get("signInWithEmailAndPassword") as? JsonObject
        val cookie = resultat?.get("userCookie") as? JsonObject
        val cookieRef = (cookie?.get("cookieRef") as? JsonPrimitive)?.contentOrNull
        val dateExpiration = (cookie?.get("dateExpiration") as? JsonPrimitive)?.contentOrNull
        if (cookieRef == null || dateExpiration == null) {
            // Jamais le corps entier (il porterait `cookieRef` en clair) : seule la forme compte ici.
            logger.d("reponse de connexion sans cookieRef ou dateExpiration exploitables")
            return SignInOutcome.Unreachable
        }
        val pseudo = ((resultat["me"] as? JsonObject)?.get("username") as? JsonPrimitive)?.contentOrNull
        return SignInOutcome.Success(cookieRef, dateExpiration, pseudo)
    }
}

private fun codeDeLaPremiereErreur(errors: JsonArray): String? = errors.firstNotNullOfOrNull { erreur ->
    val objet = erreur as? JsonObject ?: return@firstNotNullOfOrNull null
    (objet["code"] as? JsonPrimitive)?.contentOrNull
        ?: ((objet["extensions"] as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
}
