package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaBody
import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.ApiErrorBody
import fr.mediatheque.journal.api.dto.CollectionResult
import fr.mediatheque.journal.api.dto.CollectionsResponse
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.FilmsResponse
import fr.mediatheque.journal.api.dto.ImportLetterboxdBody
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.LoginBody
import fr.mediatheque.journal.api.dto.PersonneResult
import fr.mediatheque.journal.api.dto.PersonnesResponse
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.Saga
import fr.mediatheque.journal.api.dto.SearchResponse
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SessionResponse
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.SuivreRealisateurBody
import fr.mediatheque.journal.api.dto.SuivreSagaBody
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.CartonFilmResponse
import fr.mediatheque.journal.api.dto.ChroniqueBody
import fr.mediatheque.journal.api.dto.ChroniqueEcritureResponse
import fr.mediatheque.journal.api.dto.DemandeSalleBody
import fr.mediatheque.journal.api.dto.DemandeSalleEcritureResponse
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.PodiumBody
import fr.mediatheque.journal.api.dto.PodiumResponse
import fr.mediatheque.journal.api.dto.SallePlusResponse
import fr.mediatheque.journal.api.dto.TicketUtiliseResponse
import fr.mediatheque.journal.api.dto.VoyageResponse
import fr.mediatheque.journal.api.dto.VoyageTicketsResponse
import fr.mediatheque.journal.reactions.Reactions
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.CookieJar
import java.io.IOException

/**
 * Le client, et les chemins de `Endpoints` — jamais un chemin écrit en dur
 * ici ni ailleurs.
 *
 * `expectSuccess = false` : c'est nous qui lisons le statut, pour transformer
 * l'enveloppe `{ code, message, retryable }` en `ApiError` avec `Retry-After`.
 * Une panne réseau devient une `ApiError` aussi : les écrans n'ont qu'un type
 * d'erreur à connaître.
 */
class ApiClient(baseUrl: String, engine: HttpClientEngine) : JournalApi {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiJson) }
        // Sans valeur par défaut ici : les appels ordinaires gardent les délais bruts du moteur
        // OkHttp (10 s). Seul `importLetterboxd` pose un délai propre, par requête — voir plus bas.
        install(HttpTimeout)
        defaultRequest {
            // La base finit par `/` et les chemins ne commencent pas par `/` :
            // c'est ce qui garde le préfixe `/api` de l'instance en ligne.
            url(baseUrl.trimEnd('/') + "/")
            header("X-Mediatheque-Client", "android")
        }
    }

    override suspend fun login(pseudo: String, password: String): User =
        call<SessionResponse> {
            client.post(Endpoints.login) {
                contentType(ContentType.Application.Json)
                setBody(LoginBody(pseudo, password))
            }
        }.user

    override suspend fun me(): User = call<SessionResponse> { client.get(Endpoints.me) }.user

    override suspend fun logout() {
        call<Unit> { client.post(Endpoints.logout) }
    }

    override suspend fun searchMovies(query: String): List<SearchResult> =
        call<SearchResponse> {
            client.get(Endpoints.search) {
                parameter("type", "movie")
                parameter("q", query)
            }
        }.items

    override suspend fun addMedia(result: SearchResult): AddMediaResponse =
        call {
            client.post(Endpoints.media) {
                contentType(ContentType.Application.Json)
                setBody(AddMediaBody(result.source, result.external_id, result.type))
            }
        }

    override suspend fun journal(cursor: String?): JournalResponse =
        call {
            client.get(Endpoints.journal) {
                parameter("limit", 40)
                if (cursor != null) parameter("cursor", cursor)
            }
        }

    override suspend fun addViewing(body: JournalCreateBody): JournalItem =
        call {
            client.post(Endpoints.journal) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    override suspend fun patchViewing(id: String, body: JsonObject): JournalItem =
        call {
            client.patch(Endpoints.viewing(id)) {
                // Le corps est déjà un arbre JSON construit par l'appelant
                // (`JsonNull` explicite compris) : on l'encode nous-mêmes et on
                // le pose comme `TextContent`, pour ne rien laisser à la
                // négociation de contenu réinterpréter.
                setBody(TextContent(ApiJson.encodeToString(JsonObject.serializer(), body), ContentType.Application.Json))
            }
        }

    override suspend fun deleteViewing(id: String) {
        call<Unit> { client.delete(Endpoints.viewing(id)) }
    }

    override suspend fun importLetterboxd(csv: String): ImportLetterboxdResponse =
        call {
            client.post(Endpoints.importLetterboxd) {
                contentType(ContentType.Application.Json)
                setBody(ImportLetterboxdBody(csv))
                // 5 minutes (brief du 16 septembre 2026) : la réponse n'arrive qu'une fois le
                // fichier entièrement traité, jusqu'à ~2 minutes pour 500 lignes côté back — le
                // délai par défaut du moteur (10 s) couperait bien avant.
                timeout { requestTimeoutMillis = IMPORT_LETTERBOXD_TIMEOUT_MS }
            }
        }

    override suspend fun stats(): StatsResponse = call { client.get(Endpoints.stats) }

    override suspend fun seances(cursor: String?): JournalResponse =
        call {
            client.get(Endpoints.journal) {
                parameter("limit", 40)
                if (cursor != null) parameter("cursor", cursor)
                parameter("reaction", Reactions.EN_SALLE)
            }
        }

    override suspend fun sorties(): SortiesResponse = call { client.get(Endpoints.sorties) }

    override suspend fun plex(): PlexResponse = call { client.get(Endpoints.plex) }

    override suspend fun chercherPersonnes(query: String): List<PersonneResult> =
        call<PersonnesResponse> { client.get(Endpoints.personnes) { parameter("q", query) } }.results

    override suspend fun realisateurs(): List<Realisateur> = call { client.get(Endpoints.realisateurs) }

    override suspend fun suivreRealisateur(tmdbId: Int): Realisateur =
        call {
            client.post(Endpoints.realisateurs) {
                contentType(ContentType.Application.Json)
                setBody(SuivreRealisateurBody(tmdbId))
            }
        }

    override suspend fun retirerRealisateur(tmdbId: Int) {
        call<Unit> { client.delete(Endpoints.realisateur(tmdbId)) }
    }

    override suspend fun filmographie(tmdbId: Int): List<FilmSuivi> =
        call<FilmsResponse> { client.get(Endpoints.filmographie(tmdbId)) }.films

    override suspend fun marquerIntrouvable(tmdbId: Int) {
        call<Unit> { client.put(Endpoints.introuvable(tmdbId)) }
    }

    override suspend fun retirerIntrouvable(tmdbId: Int) {
        call<Unit> { client.delete(Endpoints.introuvable(tmdbId)) }
    }

    override suspend fun chercherSagas(query: String): List<CollectionResult> =
        call<CollectionsResponse> { client.get(Endpoints.collections) { parameter("q", query) } }.results

    override suspend fun sagas(): List<Saga> = call { client.get(Endpoints.sagas) }

    override suspend fun suivreSaga(tmdbId: Int): Saga =
        call {
            client.post(Endpoints.sagas) {
                contentType(ContentType.Application.Json)
                setBody(SuivreSagaBody(tmdbId))
            }
        }

    override suspend fun retirerSaga(tmdbId: Int) {
        call<Unit> { client.delete(Endpoints.saga(tmdbId)) }
    }

    override suspend fun filmsDeSaga(tmdbId: Int): List<FilmSuivi> =
        call<FilmsResponse> { client.get(Endpoints.filmsDeSaga(tmdbId)) }.films

    override suspend fun ajouterFilmSaga(tmdbId: Int, filmId: Int) {
        call<Unit> { client.put(Endpoints.filmDeSaga(tmdbId, filmId)) }
    }

    override suspend fun retirerFilmSaga(tmdbId: Int, filmId: Int) {
        call<Unit> { client.delete(Endpoints.filmDeSaga(tmdbId, filmId)) }
    }

    override suspend fun voyage(): VoyageResponse = call { client.get(Endpoints.voyage) }

    override suspend fun voyageAnnee(annee: Int): AnneeVoyageDetailResponse =
        call { client.get(Endpoints.voyageAnnee(annee)) }

    override suspend fun voyageSallePlus(salleId: String): SallePlusResponse =
        call { client.post(Endpoints.voyageSallePlus(salleId)) }

    override suspend fun cartonFilm(tmdbId: Int): CartonFilmResponse =
        call { client.get(Endpoints.chroniqueFilm(tmdbId)) }

    override suspend fun demanderVoyage(tmdbId: Int): DemanderVoyageResponse =
        call { client.post(Endpoints.demanderVoyage(tmdbId)) }

    override suspend fun poserPodium(annee: Int, place: Int, corps: PodiumBody): PodiumResponse =
        call {
            client.put(Endpoints.voyagePodium(annee, place)) {
                contentType(ContentType.Application.Json)
                setBody(corps)
            }
        }

    override suspend fun retirerPodium(annee: Int, place: Int) {
        call<Unit> { client.delete(Endpoints.voyagePodium(annee, place)) }
    }

    override suspend fun voyageTickets(): VoyageTicketsResponse = call { client.get(Endpoints.voyageTickets) }

    override suspend fun montrerTicket(annee: Int) {
        call<Unit> { client.post(Endpoints.voyageTicketMontre(annee)) }
    }

    override suspend fun utiliserTicket(annee: Int): TicketUtiliseResponse =
        call { client.post(Endpoints.voyageTicketUtiliser(annee)) }

    override suspend fun voyageChronique(annee: Int, corps: ChroniqueBody): ChroniqueEcritureResponse =
        call {
            client.post(Endpoints.voyageChronique(annee)) {
                contentType(ContentType.Application.Json)
                setBody(corps)
            }
        }

    override suspend fun voyageDemanderSalle(annee: Int, demande: String): DemandeSalleEcritureResponse =
        call {
            client.post(Endpoints.voyageSalles(annee)) {
                contentType(ContentType.Application.Json)
                setBody(DemandeSalleBody(demande))
            }
        }

    override suspend fun voyageDemandeSalleVue(id: String) {
        call<Unit> { client.post(Endpoints.voyageDemandeSalleVue(id)) }
    }

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T {
        val response = try {
            block()
        } catch (e: IOException) {
            throw ApiError.network(e)
        }
        if (!response.status.isSuccess()) throw errorOf(response)
        // Un succès au corps non-JSON (portail captif Wi-Fi, proxy en travers) fait lever
        // `response.body()` une `JsonConvertException`/`NoTransformationFoundException` que
        // personne n'attrapait : l'application plantait plutôt que d'afficher une erreur (revue de
        // la vague finale, Important 2). `CancellationException` doit ressortir telle quelle, sans
        // quoi l'annulation d'une coroutine par un `ViewModel` (un écran qui part, un `retry` qui
        // relance) se transformerait en erreur affichée au lieu de s'éteindre en silence.
        return try {
            @Suppress("UNCHECKED_CAST")
            if (T::class == Unit::class) Unit as T else response.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ApiError(
                code = "REPONSE_ILLISIBLE",
                message = "L’API a répondu quelque chose d’inattendu.",
                retryable = true,
                status = response.status.value,
                cause = e,
            )
        }
    }

    private suspend fun errorOf(response: HttpResponse): ApiError {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val body = runCatching { ApiJson.decodeFromString<ApiErrorBody>(text) }.getOrNull()
        return ApiError(
            code = body?.code ?: "HTTP_${response.status.value}",
            message = body?.message ?: "L’API a répondu ${response.status.value}.",
            retryable = body?.retryable ?: false,
            status = response.status.value,
            retryAfterSeconds = response.headers["Retry-After"]?.trim()?.toIntOrNull(),
        )
    }

    companion object {
        /** Cinq minutes, pour `importLetterboxd` seul (brief du 16 septembre 2026). */
        const val IMPORT_LETTERBOXD_TIMEOUT_MS = 5 * 60_000L

        /**
         * `ignoreUnknownKeys` : les réponses sont riches, on n'en modèle qu'une
         * partie, et le contrat a le droit de s'enrichir. `encodeDefaults` :
         * un corps envoie ses valeurs par défaut non nulles (`reactions: []`).
         * `explicitNulls = false` : un champ nul de valeur par défaut (comme
         * `rating` ou `comment` de `JournalCreateBody`) est omis du corps
         * plutôt qu'envoyé à `null` — c'est ce qui garde la garde du back sur
         * `rating` (`if (body.rating !== undefined)`) intacte depuis l'appli.
         * Au décodage, un champ nullable absent devient `null`, ce qui est
         * voulu.
         */
        val ApiJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        /** Le moteur de l'application : OkHttp, avec notre cookie. Les tests passent `MockEngine`. */
        fun okHttpEngine(cookieJar: CookieJar): HttpClientEngine = OkHttp.create {
            config { cookieJar(cookieJar) }
        }
    }
}
