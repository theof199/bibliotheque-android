package fr.mediatheque.journal.senscritique

import android.util.Log
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val GRAPHQL_URL = "https://apollo.senscritique.com/graphql"
// Le délai de 10 s (brief §6) vit sur `client` (`HttpTimeout`, posé une fois dans `AppContainer`),
// pas ici : un `withTimeout` local, combiné à `runTest` (horloge virtuelle), abandonnait
// immédiatement avant que le `MockEngine` des tests ne réponde (constaté en les écrivant).

// User-Agent de navigateur (brief §6) : l'API GraphQL n'est documentée nulle part, on se présente
// comme le ferait leur propre site.
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

// La forme exacte de `SearchResult` (le champ enveloppant ou une liste directe ?) et celle de
// `DoneResult` ne sont pas connues (brief) : `searchResult` est interrogé comme une liste directe
// d'objets `{ id title originalTitle yearOfProduction medias { picture } }` — la lecture la plus
// probable du brief (« rend des produits avec … ») — et les deux mutations ne demandent que
// `__typename`, seul champ qu'on puisse réclamer sans connaître le schéma. Si l'une de ces
// hypothèses est fausse, l'appel échoue proprement (`Failed`, jamais un plantage) et l'erreur de
// validation GraphQL est journalisée telle quelle par `execute` : le premier essai connecté sert de
// sonde, comme demandé.
private const val SEARCH_QUERY = """
query Rechercher(${'$'}mots: String!) {
  searchResult(keywords: ${'$'}mots, universe: "movie", limit: 10) {
    id
    title
    originalTitle
    yearOfProduction
    medias { picture }
  }
}
"""

private const val RATE_MUTATION = """
mutation Noter(${'$'}id: Int!, ${'$'}note: Int!) {
  productRate(productId: ${'$'}id, rating: ${'$'}note) { __typename }
}
"""

// `productDone` n'accepte peut-être pas d'autre argument que `productId` (brief : « si aucun
// argument de date n'existe, on marque vu sans date ») — c'est ce qui est envoyé ici. Si un
// argument de date existe et est requis, l'erreur de validation GraphQL le nommera, journalisée par
// `execute` (voir le rapport de la tâche : inconnue à lever sur le téléphone du propriétaire).
private const val DONE_MUTATION = """
mutation Vu(${'$'}id: Int!) {
  productDone(productId: ${'$'}id) { __typename }
}
"""

private const val WHOAMI_QUERY = """
query QuiSuisJe(${'$'}pseudo: String!) {
  user(username: ${'$'}pseudo) { __typename }
}
"""

/** Les quatre appels GraphQL du brief : chercher, noter, marquer vu, vérifier la connexion. */
interface SensCritiqueGraphQLClient {
    suspend fun search(idToken: String, keywords: String): ExternalSearchOutcome
    suspend fun rate(idToken: String, productId: Long, rating: Int): ExternalPushOutcome
    suspend fun markDone(idToken: String, productId: Long): ExternalPushOutcome
    suspend fun whoAmI(idToken: String, pseudo: String): Boolean
}

private sealed interface RawOutcome {
    data class Ok(val data: JsonObject) : RawOutcome
    data object Unauthenticated : RawOutcome
    data object Failed : RawOutcome
}

/** Implémentation réelle, par le client Ktor partagé (`ApiClient.okHttpEngine`, sans cookie jar). */
class KtorSensCritiqueGraphQLClient(private val client: HttpClient) : SensCritiqueGraphQLClient {

    override suspend fun search(idToken: String, keywords: String): ExternalSearchOutcome {
        val variables = buildJsonObject { put("mots", keywords) }
        return when (val outcome = execute(idToken, SEARCH_QUERY, variables)) {
            RawOutcome.Unauthenticated -> ExternalSearchOutcome.Unauthenticated
            RawOutcome.Failed -> ExternalSearchOutcome.Failed
            is RawOutcome.Ok -> when (val element = outcome.data["searchResult"]) {
                null, is JsonNull -> ExternalSearchOutcome.Success(emptyList())
                is JsonArray -> ExternalSearchOutcome.Success(element.mapNotNull { (it as? JsonObject)?.let(::candidateFrom) })
                else -> {
                    Log.d(TAG_SENSCRITIQUE, "forme de searchResult inattendue : $element")
                    ExternalSearchOutcome.Failed
                }
            }
        }
    }

    override suspend fun rate(idToken: String, productId: Long, rating: Int): ExternalPushOutcome {
        val variables = buildJsonObject { put("id", productId); put("note", rating) }
        return execute(idToken, RATE_MUTATION, variables).toPushOutcome()
    }

    override suspend fun markDone(idToken: String, productId: Long): ExternalPushOutcome {
        val variables = buildJsonObject { put("id", productId) }
        return execute(idToken, DONE_MUTATION, variables).toPushOutcome()
    }

    override suspend fun whoAmI(idToken: String, pseudo: String): Boolean {
        val variables = buildJsonObject { put("pseudo", pseudo) }
        return execute(idToken, WHOAMI_QUERY, variables) is RawOutcome.Ok
    }

    private fun RawOutcome.toPushOutcome(): ExternalPushOutcome = when (this) {
        RawOutcome.Unauthenticated -> ExternalPushOutcome.Unauthenticated
        RawOutcome.Failed -> ExternalPushOutcome.Failed
        is RawOutcome.Ok -> ExternalPushOutcome.Success
    }

    private suspend fun execute(idToken: String, query: String, variables: JsonObject): RawOutcome {
        data class Reponse(val status: Int, val body: String)
        val reponse = try {
            val r = client.post(GRAPHQL_URL) {
                header(HttpHeaders.Authorization, "Bearer $idToken")
                header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("query", query); put("variables", variables) }.toString())
            }
            Reponse(r.status.value, r.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.d(TAG_SENSCRITIQUE, "GraphQL injoignable : ${e.message}")
            return RawOutcome.Failed
        }

        if (reponse.status == 401 || reponse.status == 403) {
            Log.d(TAG_SENSCRITIQUE, "GraphQL non authentifie (${reponse.status}) : ${reponse.body}")
            return RawOutcome.Unauthenticated
        }

        val root = runCatching { Json.parseToJsonElement(reponse.body) }.getOrNull() as? JsonObject
        if (root == null) {
            Log.d(TAG_SENSCRITIQUE, "reponse GraphQL illisible (${reponse.status}) : ${reponse.body}")
            return RawOutcome.Failed
        }

        val errors = root["errors"] as? JsonArray
        if (errors != null && errors.isNotEmpty()) {
            Log.d(TAG_SENSCRITIQUE, "erreur GraphQL : $errors")
            val texte = errors.joinToString(" ") { erreur ->
                ((erreur as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            return if (texte.contains("unauthenticated", ignoreCase = true) || texte.contains("auth/", ignoreCase = true)) {
                RawOutcome.Unauthenticated
            } else {
                RawOutcome.Failed
            }
        }

        val data = root["data"] as? JsonObject
        if (data == null) {
            Log.d(TAG_SENSCRITIQUE, "reponse GraphQL sans donnees : ${reponse.body}")
            return RawOutcome.Failed
        }
        return RawOutcome.Ok(data)
    }
}

private fun candidateFrom(item: JsonObject): ExternalCandidate? {
    val id = (item["id"] as? JsonPrimitive)?.longOrNull ?: return null
    val title = (item["title"] as? JsonPrimitive)?.contentOrNull ?: return null
    val originalTitle = (item["originalTitle"] as? JsonPrimitive)?.contentOrNull
    val year = (item["yearOfProduction"] as? JsonPrimitive)?.intOrNull
    return ExternalCandidate(id, title, originalTitle, year, pictureUrl = extractPicture(item))
}

/** `medias` peut être une liste ou un objet unique (forme non vérifiée) — on prend la première image trouvée. */
private fun extractPicture(item: JsonObject): String? {
    val medias = item["medias"] ?: return null
    val premier = when (medias) {
        is JsonArray -> medias.firstOrNull() as? JsonObject
        is JsonObject -> medias
        else -> null
    } ?: return null
    return (premier["picture"] as? JsonPrimitive)?.contentOrNull
}
