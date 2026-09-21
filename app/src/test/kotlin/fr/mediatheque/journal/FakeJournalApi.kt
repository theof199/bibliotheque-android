package fr.mediatheque.journal

import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.AddedMedia
import fr.mediatheque.journal.api.dto.Carnet
import fr.mediatheque.journal.api.dto.CollectionResult
import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalMedia
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.LogEntry
import fr.mediatheque.journal.api.dto.PersonneResult
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.RealisateurCredit
import fr.mediatheque.journal.api.dto.RealisateurPageResponse
import fr.mediatheque.journal.api.dto.Saga
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortiesEnCours
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.SortieSemaine
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.api.dto.VuDuFilm
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.CartonFilmResponse
import fr.mediatheque.journal.api.dto.ChroniqueBody
import fr.mediatheque.journal.api.dto.ChroniqueEcritureResponse
import fr.mediatheque.journal.api.dto.DemandeSalleEcritureResponse
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.PodiumBody
import fr.mediatheque.journal.api.dto.PodiumResponse
import fr.mediatheque.journal.api.dto.SallePlusResponse
import fr.mediatheque.journal.api.dto.SeanceComposerResponse
import fr.mediatheque.journal.api.dto.SeanceEcritureResponse
import fr.mediatheque.journal.api.dto.SeanceFilmVoyage
import fr.mediatheque.journal.api.dto.SeanceRemplacerBody
import fr.mediatheque.journal.api.dto.SeanceVoyage
import fr.mediatheque.journal.api.dto.TicketUtiliseResponse
import fr.mediatheque.journal.api.dto.VoyageDeFilmographie
import fr.mediatheque.journal.api.dto.VoyageDepensesResponse
import fr.mediatheque.journal.api.dto.VoyageResponse
import fr.mediatheque.journal.api.dto.VoyageTicketsResponse
import kotlinx.serialization.json.JsonObject

class FakeJournalApi : JournalApi {
    val calls = mutableListOf<String>()

    var onLogin: suspend (String, String) -> User = { _, _ -> ALICE }
    var onMe: suspend () -> User = { ALICE }
    var onLogout: suspend () -> Unit = {}
    var onSearch: suspend (String) -> List<SearchResult> = { emptyList() }
    var onAddMedia: suspend (SearchResult) -> AddMediaResponse = { r -> AddMediaResponse(true, AddedMedia("m-${r.external_id}", r.title)) }
    var onJournal: suspend (String?) -> JournalResponse = { JournalResponse(emptyList(), null) }
    var onAddViewing: suspend (JournalCreateBody) -> JournalItem = { b -> item(b.media_id, b.finished_at, b.rating, b.reactions ?: emptyList(), b.comment) }
    // `patchViewing` reçoit un `JsonObject` (tâche 3, décision 1) : le corps ne porte que ce qui a
    // changé, la valeur par défaut ne le lit donc pas.
    var onPatchViewing: suspend (String, JsonObject) -> JournalItem = { id, _ -> item("m", "2026-01-01", null, emptyList(), null, id) }
    var onDeleteViewing: suspend (String) -> Unit = {}
    var onStats: suspend () -> StatsResponse = { error("onStats non configuré") }
    var onImportLetterboxd: suspend (String) -> ImportLetterboxdResponse = { error("onImportLetterboxd non configuré") }
    var onSeances: suspend (String?) -> JournalResponse = { JournalResponse(emptyList(), null) }
    var onSorties: suspend () -> SortiesResponse = {
        SortiesResponse(
            SortiesEnCours(du = "2026-09-15", au = "2026-09-15", cinemas_configures = true),
            SortieSemaine("2026-09-21", "2026-09-27"),
        )
    }
    var onPlex: suspend () -> PlexResponse = { PlexResponse(configure = true) }
    var onChercherPersonnes: suspend (String) -> List<PersonneResult> = { emptyList() }
    var onRealisateurs: suspend () -> List<Realisateur> = { emptyList() }
    var onSuivreRealisateur: suspend (Int) -> Realisateur = { id -> realisateur(id, "Personne $id") }
    var onRetirerRealisateur: suspend (Int) -> Unit = {}
    var onFilmographie: suspend (Int) -> List<FilmSuivi> = { emptyList() }
    var onRealisateursDuFilm: suspend (Int) -> List<RealisateurCredit> = { emptyList() }
    var onPageRealisateur: suspend (Int) -> RealisateurPageResponse = { id -> pageRealisateur(id, "Personne $id") }
    var onMarquerIntrouvable: suspend (Int) -> Unit = {}
    var onRetirerIntrouvable: suspend (Int) -> Unit = {}
    var onChercherSagas: suspend (String) -> List<CollectionResult> = { emptyList() }
    var onSagas: suspend () -> List<Saga> = { emptyList() }
    var onSuivreSaga: suspend (Int) -> Saga = { id -> saga(id, "Saga $id") }
    var onRetirerSaga: suspend (Int) -> Unit = {}
    var onFilmsDeSaga: suspend (Int) -> List<FilmSuivi> = { emptyList() }
    var onAjouterFilmSaga: suspend (Int, Int) -> Unit = { _, _ -> }
    var onRetirerFilmSaga: suspend (Int, Int) -> Unit = { _, _ -> }
    var onVoyage: suspend () -> VoyageResponse = { VoyageResponse(configure = true) }
    var onVoyageAnnee: suspend (Int) -> AnneeVoyageDetailResponse = { AnneeVoyageDetailResponse(configure = true) }
    var onVoyageSallePlus: suspend (String) -> SallePlusResponse = { SallePlusResponse(statut = "epuisee") }
    var onCartonFilm: suspend (Int) -> CartonFilmResponse = { CartonFilmResponse(configure = true) }
    var onDemanderVoyage: suspend (Int) -> DemanderVoyageResponse = { DemanderVoyageResponse(demande = true) }
    var onPoserPodium: suspend (Int, Int, PodiumBody) -> PodiumResponse = { _, _, _ -> PodiumResponse() }
    var onRetirerPodium: suspend (Int, Int) -> Unit = { _, _ -> }
    var onVoyageTickets: suspend () -> VoyageTicketsResponse = { VoyageTicketsResponse() }
    var onMontrerTicket: suspend (Int) -> Unit = { _ -> }
    var onUtiliserTicket: suspend (Int) -> TicketUtiliseResponse = { TicketUtiliseResponse() }
    var onVoyageChronique: suspend (Int, ChroniqueBody) -> ChroniqueEcritureResponse = { _, _ -> ChroniqueEcritureResponse(statut = "en_preparation") }
    var onVoyageDemanderSalle: suspend (Int, String) -> DemandeSalleEcritureResponse = { _, _ -> DemandeSalleEcritureResponse(demande_id = "d-1") }
    var onVoyageDemandeSalleVue: suspend (String) -> Unit = { _ -> }
    var onVoyageDepenses: suspend () -> VoyageDepensesResponse = { VoyageDepensesResponse() }
    var onVoyageComposerSeance: suspend (Int) -> SeanceComposerResponse = { SeanceComposerResponse() }
    var onVoyageRemplacerSeance: suspend (String, SeanceRemplacerBody) -> SeanceEcritureResponse =
        { id, _ -> SeanceEcritureResponse(seance(id)) }
    var onVoyagePrendreSeance: suspend (String) -> SeanceEcritureResponse = { id -> SeanceEcritureResponse(seance(id)) }
    var onVoyageIgnorerSeance: suspend (String) -> SeanceEcritureResponse = { id -> SeanceEcritureResponse(seance(id)) }

    override suspend fun login(pseudo: String, password: String) = track("login $pseudo") { onLogin(pseudo, password) }
    override suspend fun me() = track("me") { onMe() }
    override suspend fun logout() = track("logout") { onLogout() }
    override suspend fun searchMovies(query: String) = track("search $query") { onSearch(query) }
    override suspend fun addMedia(result: SearchResult) = track("addMedia ${result.external_id}") { onAddMedia(result) }
    override suspend fun journal(cursor: String?) = track("journal $cursor") { onJournal(cursor) }
    override suspend fun addViewing(body: JournalCreateBody) = track("addViewing ${body.media_id}") { onAddViewing(body) }
    override suspend fun patchViewing(id: String, body: JsonObject) = track("patchViewing $id") { onPatchViewing(id, body) }
    override suspend fun deleteViewing(id: String) = track("deleteViewing $id") { onDeleteViewing(id) }
    override suspend fun stats() = track("stats") { onStats() }
    override suspend fun importLetterboxd(csv: String) = track("importLetterboxd") { onImportLetterboxd(csv) }
    override suspend fun seances(cursor: String?) = track("seances $cursor") { onSeances(cursor) }
    override suspend fun sorties() = track("sorties") { onSorties() }
    override suspend fun plex() = track("plex") { onPlex() }
    override suspend fun chercherPersonnes(query: String) = track("chercherPersonnes $query") { onChercherPersonnes(query) }
    override suspend fun realisateurs() = track("realisateurs") { onRealisateurs() }
    override suspend fun suivreRealisateur(tmdbId: Int) = track("suivreRealisateur $tmdbId") { onSuivreRealisateur(tmdbId) }
    override suspend fun retirerRealisateur(tmdbId: Int) = track("retirerRealisateur $tmdbId") { onRetirerRealisateur(tmdbId) }
    override suspend fun filmographie(tmdbId: Int) = track("filmographie $tmdbId") { onFilmographie(tmdbId) }
    override suspend fun realisateursDuFilm(tmdbId: Int) = track("realisateursDuFilm $tmdbId") { onRealisateursDuFilm(tmdbId) }
    override suspend fun pageRealisateur(tmdbId: Int) = track("pageRealisateur $tmdbId") { onPageRealisateur(tmdbId) }
    override suspend fun marquerIntrouvable(tmdbId: Int) = track("marquerIntrouvable $tmdbId") { onMarquerIntrouvable(tmdbId) }
    override suspend fun retirerIntrouvable(tmdbId: Int) = track("retirerIntrouvable $tmdbId") { onRetirerIntrouvable(tmdbId) }
    override suspend fun chercherSagas(query: String) = track("chercherSagas $query") { onChercherSagas(query) }
    override suspend fun sagas() = track("sagas") { onSagas() }
    override suspend fun suivreSaga(tmdbId: Int) = track("suivreSaga $tmdbId") { onSuivreSaga(tmdbId) }
    override suspend fun retirerSaga(tmdbId: Int) = track("retirerSaga $tmdbId") { onRetirerSaga(tmdbId) }
    override suspend fun filmsDeSaga(tmdbId: Int) = track("filmsDeSaga $tmdbId") { onFilmsDeSaga(tmdbId) }
    override suspend fun ajouterFilmSaga(tmdbId: Int, filmId: Int) =
        track("ajouterFilmSaga $tmdbId $filmId") { onAjouterFilmSaga(tmdbId, filmId) }
    override suspend fun retirerFilmSaga(tmdbId: Int, filmId: Int) =
        track("retirerFilmSaga $tmdbId $filmId") { onRetirerFilmSaga(tmdbId, filmId) }
    override suspend fun voyage() = track("voyage") { onVoyage() }
    override suspend fun voyageAnnee(annee: Int) = track("voyageAnnee $annee") { onVoyageAnnee(annee) }
    override suspend fun voyageSallePlus(salleId: String) = track("voyageSallePlus $salleId") { onVoyageSallePlus(salleId) }
    override suspend fun cartonFilm(tmdbId: Int) = track("cartonFilm $tmdbId") { onCartonFilm(tmdbId) }
    override suspend fun demanderVoyage(tmdbId: Int) = track("demanderVoyage $tmdbId") { onDemanderVoyage(tmdbId) }
    override suspend fun poserPodium(annee: Int, place: Int, corps: PodiumBody) =
        track("poserPodium $annee $place ${corps.tmdb_id ?: corps.programme_id}") { onPoserPodium(annee, place, corps) }
    override suspend fun retirerPodium(annee: Int, place: Int) = track("retirerPodium $annee $place") { onRetirerPodium(annee, place) }
    override suspend fun voyageTickets() = track("voyageTickets") { onVoyageTickets() }
    override suspend fun montrerTicket(annee: Int) = track("montrerTicket $annee") { onMontrerTicket(annee) }
    override suspend fun utiliserTicket(annee: Int) = track("utiliserTicket $annee") { onUtiliserTicket(annee) }
    override suspend fun voyageChronique(annee: Int, corps: ChroniqueBody) =
        track("voyageChronique $annee ${corps.tmdb_id ?: corps.programme_id}") { onVoyageChronique(annee, corps) }
    override suspend fun voyageDemanderSalle(annee: Int, demande: String) =
        track("voyageDemanderSalle $annee") { onVoyageDemanderSalle(annee, demande) }
    override suspend fun voyageDemandeSalleVue(id: String) = track("voyageDemandeSalleVue $id") { onVoyageDemandeSalleVue(id) }
    override suspend fun voyageDepenses() = track("voyageDepenses") { onVoyageDepenses() }
    override suspend fun voyageComposerSeance(annee: Int) = track("voyageComposerSeance $annee") { onVoyageComposerSeance(annee) }
    override suspend fun voyageRemplacerSeance(id: String, corps: SeanceRemplacerBody) =
        track("voyageRemplacerSeance $id ${corps.morceau} ${corps.film_id} ${corps.bobine_tmdb_id}") { onVoyageRemplacerSeance(id, corps) }
    override suspend fun voyagePrendreSeance(id: String) = track("voyagePrendreSeance $id") { onVoyagePrendreSeance(id) }
    override suspend fun voyageIgnorerSeance(id: String) = track("voyageIgnorerSeance $id") { onVoyageIgnorerSeance(id) }

    private suspend fun <T> track(name: String, block: suspend () -> T): T {
        calls += name
        return block()
    }

    companion object {
        val ALICE = User("u-alice", "alice", "#E4572E")

        // `finishedAt` est une date seule ("2026-01-10"), pas un instant ISO : le contrat du back
        // ne rend que ça, et `formatDate` (`LocalDate.parse`) lèverait sur un instant complet.
        // `title`, `coverUrl`, `year`, `director` couvrent la fiche affichée par `FormScreen` en
        // correction (décision 2 de la tâche 6) ; leurs défauts gardent les appels existants
        // inchangés.
        fun item(
            mediaId: String,
            finishedAt: String,
            rating: Int?,
            reactions: List<String>,
            comment: String?,
            id: String = "e-$finishedAt",
            title: String = "Un film",
            coverUrl: String? = null,
            year: Int? = null,
            director: String? = null,
            externalId: String = "",
        ) = JournalItem(
            LogEntry(id, mediaId, finishedAt, rating),
            JournalMedia(mediaId, title, coverUrl, year, director, externalId),
            Carnet(reactions, comment),
        )

        /** Un réalisateur suivi, tel que `GET /me/realisateurs` le rend. */
        fun realisateur(tmdbId: Int, name: String, profileUrl: String? = null, ajouteLe: String = "2026-09-15T18:22:41.000Z") =
            Realisateur(tmdbId, name, profileUrl, ajouteLe)

        /** Une saga suivie, tel que `GET /me/sagas` le rend — jumelle de `realisateur` ci-dessus. */
        fun saga(tmdbId: Int, name: String, coverUrl: String? = null, ajouteLe: String = "2026-09-15T18:30:12.000Z") =
            Saga(tmdbId, name, coverUrl, ajouteLe)

        /**
         * Un film d'une filmographie ou d'une saga. `vu` non nul le rend « déjà journalisé » :
         * `entryId` est l'entrée de journal que la fiche ouvrira en correction.
         */
        fun filmDe(
            tmdbId: Int,
            title: String,
            year: Int? = null,
            entryId: String? = null,
            rating: Int? = null,
            finishedAt: String = "2026-07-12",
            introuvable: Boolean = false,
            ajoute: Boolean = false,
        ) = FilmSuivi(
            tmdb_id = tmdbId,
            title = title,
            year = year,
            release_date = year?.let { "$it-01-01" },
            vu = entryId?.let { VuDuFilm(it, rating, finishedAt) },
            introuvable = introuvable,
            ajoute = ajoute,
        )

        /** Un réalisateur crédité sur un film (`GET /reference/films/{tmdbId}/realisateurs`). */
        fun realisateurCredit(tmdbId: Int, name: String) = RealisateurCredit(tmdbId, name)

        /** Un film de la filmographie d'une page réalisateur (brief du 21 septembre 2026, « la page réalisateur »). */
        fun filmDeFilmographie(
            tmdbId: Int,
            title: String,
            year: Int? = null,
            type: String = "movie",
            entryId: String? = null,
            rating: Int? = null,
            finishedAt: String = "2026-07-12",
            introuvable: Boolean = false,
            surLePlex: Boolean = false,
            demande: Boolean = false,
            plexUrl: String? = null,
            anneeOuverte: Boolean = false,
            voyage: VoyageDeFilmographie? = null,
        ) = FilmDeFilmographie(
            tmdb_id = tmdbId,
            title = title,
            year = year,
            release_date = year?.let { "$it-01-01" } ?: "2000-01-01",
            type = type,
            vu = entryId?.let { VuDuFilm(it, rating, finishedAt) },
            introuvable = introuvable,
            sur_le_plex = surLePlex,
            demande = demande,
            plex_url = plexUrl,
            annee_ouverte = anneeOuverte,
            voyage = voyage,
        )

        /** `GET /me/realisateurs/{tmdbId}/page` par défaut : une fiche minimale, sans film. */
        fun pageRealisateur(
            tmdbId: Int,
            name: String,
            naissance: String? = null,
            deces: String? = null,
            presentation: String = "",
            suivi: Boolean = false,
            films: List<FilmDeFilmographie> = emptyList(),
        ) = RealisateurPageResponse(
            tmdb_id = tmdbId,
            name = name,
            naissance = naissance,
            deces = deces,
            presentation = presentation,
            suivi = suivi,
            films = films,
        )

        /** Une séance par défaut (brief du 21 septembre 2026, « la séance »), pour les réponses de `prendre`/`ignorer`/`remplacer`. */
        fun seance(id: String, statut: String = "proposee", rang: Int = 1) = SeanceVoyage(
            id = id,
            rang = rang,
            statut = statut,
            composee_le = "2026-09-21T22:00:00.000Z",
            anecdote = "Une anecdote de générique.",
            long = SeanceFilmVoyage(film_id = "f-long", tmdb_id = 1, title = "Un long", salle = "Les essentiels", etat = "a_demander"),
        )

        fun unauthorized() = ApiError("UNAUTHENTICATED", "Connecte-toi d’abord.", retryable = false, status = 401)
        fun rateLimited(seconds: Int) = ApiError("RATE_LIMITED", "Trop de tentatives.", retryable = true, status = 429, retryAfterSeconds = seconds)
        fun network() = ApiError.network(java.io.IOException("hors ligne"))
        fun refused() = ApiError("UNAUTHENTICATED", "Pseudo ou mot de passe incorrect.", retryable = false, status = 401)
    }
}
