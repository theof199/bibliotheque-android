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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
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

/**
 * Vérifiée (revue du 14 septembre 2026, troisième essai réel — la recherche a été lue dans les
 * documents GraphQL du site SensCritique lui-même, puis confirmée en lecture seule) :
 * `searchProductExplorer(query:, limit:, filters:)` rend `{ items { ... } }`, pas une liste directe.
 * `filters` porte `[{identifier:"universe", termValues:["movie"]}]` sur leur site ; si le serveur le
 * refuse (`BAD_USER_INPUT`), `search` rejoue sans filtre — et filtre `universe == 1` côté appli dans
 * tous les cas, ceinture et bretelles (« Voyage dans la Lune » a un homonyme livre en universe 2).
 */
private const val SEARCH_QUERY = """
query Rechercher(${'$'}q: String, ${'$'}f: [SearchFilter]) {
  searchProductExplorer(query: ${'$'}q, limit: 10, filters: ${'$'}f) {
    items {
      id
      title
      originalTitle
      yearOfProduction
      universe
      directors { name }
      medias { picture }
    }
  }
}
"""

/** Le champ `id` non nul vaut succès (document du site SensCritique, revue du 14 septembre 2026). */
private const val RATE_MUTATION = """
mutation Noter(${'$'}id: Int!, ${'$'}note: Int!) {
  productRate(productId: ${'$'}id, rating: ${'$'}note) { id }
}
"""

/** `success` vrai vaut succès ; pas d'argument de date (document du site, revue du 14 septembre 2026). */
private const val DONE_MUTATION = """
mutation Vu(${'$'}id: Int!) {
  productDone(productId: ${'$'}id) { success }
}
"""

/**
 * La mutation de date manquante (document du site, revue du 14 septembre 2026) — appelée après
 * `productDone`, jamais à sa place : `productDone` marque « vu », `setProductDateDone` pose la date.
 * Un échec ici seul ne défait pas la poussée (`SensCritiqueRatingService.push`).
 */
private const val DATE_MUTATION = """
mutation Date(${'$'}id: Int!, ${'$'}d: String!) {
  setProductDateDone(productId: ${'$'}id, date: ${'$'}d) { id }
}
"""

private const val WHOAMI_QUERY = """
query QuiSuisJe(${'$'}pseudo: String!) {
  user(username: ${'$'}pseudo) { __typename }
}
"""

/** Les cinq appels GraphQL du brief : chercher, noter, marquer vu, poser la date, vérifier la connexion. */
interface SensCritiqueGraphQLClient {
    suspend fun search(idToken: String, keywords: String): ExternalSearchOutcome
    suspend fun rate(idToken: String, productId: Long, rating: Int): ExternalPushOutcome
    suspend fun markDone(idToken: String, productId: Long): ExternalPushOutcome
    suspend fun setDate(idToken: String, productId: Long, date: String): ExternalPushOutcome
    suspend fun whoAmI(idToken: String, pseudo: String): Boolean
}

private sealed interface RawOutcome {
    data class Ok(val data: JsonObject) : RawOutcome
    data object Unauthenticated : RawOutcome
    /** `errors` porte le tableau GraphQL brut quand il existe — `search` s'en sert pour détecter `BAD_USER_INPUT`. */
    data class Failed(val errors: JsonArray? = null) : RawOutcome
}

/** Implémentation réelle, par le client Ktor partagé (`ApiClient.okHttpEngine`, sans cookie jar). */
class KtorSensCritiqueGraphQLClient(
    private val client: HttpClient,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
) : SensCritiqueGraphQLClient {

    override suspend fun search(idToken: String, keywords: String): ExternalSearchOutcome {
        val avecFiltre = buildJsonObject {
            put("q", keywords)
            put(
                "f",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("identifier", "universe")
                            put("termValues", buildJsonArray { add(JsonPrimitive("movie")) })
                        },
                    )
                },
            )
        }
        return when (val premier = execute(idToken, SEARCH_QUERY, avecFiltre)) {
            is RawOutcome.Ok -> itemsFrom(premier.data)
            RawOutcome.Unauthenticated -> ExternalSearchOutcome.Unauthenticated
            is RawOutcome.Failed -> {
                if (premier.errors?.estBadUserInput() == true) {
                    logger.d("recherche avec filtre univers refusee (BAD_USER_INPUT), repli sans filtre")
                    val sansFiltre = buildJsonObject { put("q", keywords); put("f", JsonNull) }
                    when (val second = execute(idToken, SEARCH_QUERY, sansFiltre)) {
                        is RawOutcome.Ok -> itemsFrom(second.data)
                        RawOutcome.Unauthenticated -> ExternalSearchOutcome.Unauthenticated
                        is RawOutcome.Failed -> ExternalSearchOutcome.Failed
                    }
                } else {
                    ExternalSearchOutcome.Failed
                }
            }
        }
    }

    override suspend fun rate(idToken: String, productId: Long, rating: Int): ExternalPushOutcome {
        val variables = buildJsonObject { put("id", productId); put("note", rating) }
        return execute(idToken, RATE_MUTATION, variables).toPushOutcome("productRate", "id")
    }

    override suspend fun markDone(idToken: String, productId: Long): ExternalPushOutcome {
        val variables = buildJsonObject { put("id", productId) }
        return execute(idToken, DONE_MUTATION, variables).toDoneOutcome()
    }

    override suspend fun setDate(idToken: String, productId: Long, date: String): ExternalPushOutcome {
        val variables = buildJsonObject { put("id", productId); put("d", date) }
        return execute(idToken, DATE_MUTATION, variables).toPushOutcome("setProductDateDone", "id")
    }

    override suspend fun whoAmI(idToken: String, pseudo: String): Boolean {
        val variables = buildJsonObject { put("pseudo", pseudo) }
        return execute(idToken, WHOAMI_QUERY, variables) is RawOutcome.Ok
    }

    /**
     * `data` présent ne suffit pas : un `data.<champAttendu>` absent ou nul (GraphQL peut répondre
     * `200` avec `{"data":{"productRate":null}}` sans lever d'erreur, un serveur qui a refusé la
     * mutation sans le dire par une `errors[]`) valait `Success` — corrigé (revue du 14 septembre
     * 2026, mineur b). Depuis le troisième essai réel, le champ attendu est précisément celui que
     * le document du site nomme (`productRate.id`, `setProductDateDone.id`) — plus un `__typename`
     * de circonstance. Le corps journalisé ne porte jamais le jeton (transmis en en-tête, jamais
     * dans `data`).
     */
    private fun RawOutcome.toPushOutcome(champAttendu: String, sousChamp: String): ExternalPushOutcome = when (this) {
        RawOutcome.Unauthenticated -> ExternalPushOutcome.Unauthenticated
        is RawOutcome.Failed -> ExternalPushOutcome.Failed
        is RawOutcome.Ok -> {
            val valeur = (data[champAttendu] as? JsonObject)?.get(sousChamp)
            if (valeur != null && valeur !is JsonNull) {
                ExternalPushOutcome.Success
            } else {
                logger.d("mutation sans resultat pour $champAttendu.$sousChamp : $data")
                ExternalPushOutcome.Failed
            }
        }
    }

    /** `productDone` : succès seulement si `success` vaut vrai — pas juste présent (document du site). */
    private fun RawOutcome.toDoneOutcome(): ExternalPushOutcome = when (this) {
        RawOutcome.Unauthenticated -> ExternalPushOutcome.Unauthenticated
        is RawOutcome.Failed -> ExternalPushOutcome.Failed
        is RawOutcome.Ok -> {
            val succes = ((data["productDone"] as? JsonObject)?.get("success") as? JsonPrimitive)?.booleanOrNull
            if (succes == true) {
                ExternalPushOutcome.Success
            } else {
                logger.d("productDone sans succes : $data")
                ExternalPushOutcome.Failed
            }
        }
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
            logger.d("GraphQL injoignable : ${e.message}")
            return RawOutcome.Failed()
        }

        if (reponse.status == 401 || reponse.status == 403) {
            logger.d("GraphQL non authentifie (${reponse.status}) : ${reponse.body}")
            return RawOutcome.Unauthenticated
        }

        val root = runCatching { Json.parseToJsonElement(reponse.body) }.getOrNull() as? JsonObject
        if (root == null) {
            logger.d("reponse GraphQL illisible (${reponse.status}) : ${reponse.body}")
            return RawOutcome.Failed()
        }

        val errors = root["errors"] as? JsonArray
        if (errors != null && errors.isNotEmpty()) {
            logger.d("erreur GraphQL : $errors")
            val texte = errors.joinToString(" ") { erreur ->
                ((erreur as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            return if (texte.contains("unauthenticated", ignoreCase = true) || texte.contains("auth/", ignoreCase = true)) {
                RawOutcome.Unauthenticated
            } else {
                RawOutcome.Failed(errors)
            }
        }

        val data = root["data"] as? JsonObject
        if (data == null) {
            logger.d("reponse GraphQL sans donnees : ${reponse.body}")
            return RawOutcome.Failed()
        }
        return RawOutcome.Ok(data)
    }
}

/** `code == "BAD_USER_INPUT"` (convention `extensions.code`), ou le mot dans le message à défaut. */
private fun JsonArray.estBadUserInput(): Boolean = any { erreur ->
    val objet = erreur as? JsonObject ?: return@any false
    val code = ((objet["extensions"] as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
    val message = (objet["message"] as? JsonPrimitive)?.contentOrNull
    code == "BAD_USER_INPUT" || message?.contains("BAD_USER_INPUT") == true
}

private fun itemsFrom(data: JsonObject): ExternalSearchOutcome {
    val items = (data["searchProductExplorer"] as? JsonObject)?.get("items") as? JsonArray
    if (items == null) {
        return ExternalSearchOutcome.Failed
    }
    val candidats = items
        .mapNotNull { it as? JsonObject }
        .filter { estUnFilm(it) }
        .mapNotNull(::candidateFrom)
    return ExternalSearchOutcome.Success(candidats)
}

/**
 * `universe == 1` pour un film (revue du 14 septembre 2026, point 1) : ceinture et bretelles, même
 * quand le filtre côté serveur a été accepté — « Le Voyage dans la Lune » partage son titre avec un
 * recueil de lettres en `universe 2` (livre), que le filtre serveur seul ne suffit pas à écarter à
 * coup sûr.
 */
private fun estUnFilm(item: JsonObject): Boolean = (item["universe"] as? JsonPrimitive)?.intOrNull == 1

private fun candidateFrom(item: JsonObject): ExternalCandidate? {
    val id = (item["id"] as? JsonPrimitive)?.longOrNull ?: return null
    val title = (item["title"] as? JsonPrimitive)?.contentOrNull ?: return null
    val originalTitle = (item["originalTitle"] as? JsonPrimitive)?.contentOrNull
    val year = (item["yearOfProduction"] as? JsonPrimitive)?.intOrNull
    val director = ((item["directors"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("name")
        ?.let { it as? JsonPrimitive }?.contentOrNull
    return ExternalCandidate(id, title, originalTitle, year, pictureUrl = extractPicture(item), director = director)
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
