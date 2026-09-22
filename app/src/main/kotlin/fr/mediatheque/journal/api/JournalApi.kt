package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.CollectionResult
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.PersonneResult
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.RealisateurCredit
import fr.mediatheque.journal.api.dto.RealisateurPageResponse
import fr.mediatheque.journal.api.dto.Saga
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.CarnetFabricationResponse
import fr.mediatheque.journal.api.dto.CartonFilmResponse
import fr.mediatheque.journal.api.dto.ChroniqueBody
import fr.mediatheque.journal.api.dto.ChroniqueEcritureResponse
import fr.mediatheque.journal.api.dto.DemandeSalleEcritureResponse
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.PistesVoyageResponse
import fr.mediatheque.journal.api.dto.PodiumBody
import fr.mediatheque.journal.api.dto.PodiumResponse
import fr.mediatheque.journal.api.dto.SallePlusResponse
import fr.mediatheque.journal.api.dto.SeanceComposerResponse
import fr.mediatheque.journal.api.dto.SeanceEcritureResponse
import fr.mediatheque.journal.api.dto.SeanceRemplacerBody
import fr.mediatheque.journal.api.dto.TicketUtiliseResponse
import fr.mediatheque.journal.api.dto.VoyageCarnetsResponse
import fr.mediatheque.journal.api.dto.VoyageDepensesResponse
import fr.mediatheque.journal.api.dto.VoyageResponse
import fr.mediatheque.journal.api.dto.VoyageTicketsResponse
import kotlinx.serialization.json.JsonObject

/**
 * La seule porte des écrans vers le réseau. Quarante-huit opérations, celles que
 * l'application consomme ; les chemins n'existent que dans `Endpoints`, et ne
 * s'emploient que depuis `ApiClient`. Toute fonction peut lever `ApiError`.
 *
 * `patchViewing` reçoit un `JsonObject` déjà construit par l'appelant (la
 * tâche 6) plutôt qu'un DTO à champs fixes : un `PATCH` ne porte que ce qui a
 * changé, `JsonNull` compris pour effacer un commentaire — un DTO à valeurs
 * par défaut ne saurait pas distinguer « inchangé » de « remis à zéro ».
 *
 * `seances` et `sorties` sont à « Au ciné » (brief du 14 septembre 2026) ce
 * que `journal` et `searchMovies` sont ailleurs : une méthode dédiée plutôt
 * qu'un paramètre optionnel sur `journal`, pour ne rien changer aux appels
 * existants de `FilmsViewModel`.
 */
interface JournalApi {
    suspend fun login(pseudo: String, password: String): User
    suspend fun me(): User
    suspend fun logout()
    suspend fun searchMovies(query: String): List<SearchResult>
    suspend fun addMedia(result: SearchResult): AddMediaResponse
    suspend fun journal(cursor: String?): JournalResponse
    suspend fun addViewing(body: JournalCreateBody): JournalItem
    suspend fun patchViewing(id: String, body: JsonObject): JournalItem
    suspend fun deleteViewing(id: String)

    /**
     * `POST /me/journal/import/letterboxd` (brief du 16 septembre 2026) : le contenu de
     * `diary.csv`, tel quel. La réponse n'arrive qu'une fois le fichier entièrement traité —
     * jusqu'à quelques minutes pour un gros fichier, `ApiClient` lui donne un délai propre.
     */
    suspend fun importLetterboxd(csv: String): ImportLetterboxdResponse
    suspend fun stats(): StatsResponse
    /** `GET /me/journal?reaction=en_salle` : mes séances, du plus récent au plus ancien. */
    suspend fun seances(cursor: String?): JournalResponse
    /** `GET /reference/sorties` : sorties en salle, semaine en cours et semaine prochaine. */
    suspend fun sorties(): SortiesResponse
    /** `GET /reference/plex` : le Plex du propriétaire, demandé sur Seerr — la Frise et « Ensuite ». */
    suspend fun plex(): PlexResponse

    /** `GET /reference/personnes?q=` : dix réalisateurs au plus, pour le « + » de l'écran Réalisateurs. */
    suspend fun chercherPersonnes(query: String): List<PersonneResult>

    /** `GET /me/realisateurs` : ceux que je suis, du plus récemment ajouté au plus ancien. Sans pagination. */
    suspend fun realisateurs(): List<Realisateur>

    /** `POST /me/realisateurs` : idempotent côté back (200 au lieu de 201 si déjà suivi). */
    suspend fun suivreRealisateur(tmdbId: Int): Realisateur

    /** `DELETE /me/realisateurs/{tmdbId}` : `404` si je ne le suis pas. */
    suspend fun retirerRealisateur(tmdbId: Int)

    /** `GET /me/realisateurs/{tmdbId}/films` : sa filmographie, de la plus ancienne sortie à la plus récente. */
    suspend fun filmographie(tmdbId: Int): List<FilmSuivi>

    /**
     * `GET /reference/films/{tmdbId}/realisateurs` (brief du 21 septembre 2026, « la page
     * réalisateur ») : les réalisateurs crédités sur ce film chez TMDB, dédoublonnés — de quoi
     * naviguer d'une fiche vers `pageRealisateur`. Vide si TMDB n'en crédite aucun.
     */
    suspend fun realisateursDuFilm(tmdbId: Int): List<RealisateurCredit>

    /**
     * `GET /me/realisateurs/{tmdbId}/page` : la fiche d'un réalisateur (photo, dates,
     * présentation) et sa filmographie complète, films et séries, qu'il soit suivi ou non.
     */
    suspend fun pageRealisateur(tmdbId: Int): RealisateurPageResponse

    /** `PUT /me/introuvables/{tmdbId}` : marque un film (son propre `tmdb_id`) introuvable. Idempotent, toujours `204`. */
    suspend fun marquerIntrouvable(tmdbId: Int)

    /** `DELETE /me/introuvables/{tmdbId}` : retire la marque. Toujours `204`, même si rien n'était marqué. */
    suspend fun retirerIntrouvable(tmdbId: Int)

    // --- Les sagas (brief du 15 septembre 2026), jumelles des cinq ci-dessus. ---

    /** `GET /reference/sagas?q=` : dix sagas au plus, pour le « + » de l'écran Suivis, segment Sagas. */
    suspend fun chercherSagas(query: String): List<CollectionResult>

    /** `GET /me/sagas` : celles que je suis, de la plus récemment ajoutée à la plus ancienne. Sans pagination. */
    suspend fun sagas(): List<Saga>

    /** `POST /me/sagas` : idempotent côté back (200 au lieu de 201 si déjà suivie). */
    suspend fun suivreSaga(tmdbId: Int): Saga

    /** `DELETE /me/sagas/{tmdbId}` : `404` si je ne la suis pas. */
    suspend fun retirerSaga(tmdbId: Int)

    /** `GET /me/sagas/{tmdbId}/films` : ses films, de la plus ancienne sortie à la plus récente. */
    suspend fun filmsDeSaga(tmdbId: Int): List<FilmSuivi>

    /**
     * `PUT /me/sagas/{tmdbId}/films/{filmId}` : ajoute un film absent de la
     * collection (brief « les films de saga ajoutés à la main », 15 septembre
     * 2026). Idempotent, toujours `204`. `404` si la saga n'est pas suivie, ou
     * si TMDB ne connaît pas ce `filmId`.
     */
    suspend fun ajouterFilmSaga(tmdbId: Int, filmId: Int)

    /** `DELETE /me/sagas/{tmdbId}/films/{filmId}` : le retire. Toujours `204`, même si le film n'avait pas été ajouté. */
    suspend fun retirerFilmSaga(tmdbId: Int, filmId: Int)

    // --- Le Voyage. Brief du 16 septembre 2026 (« le moteur »), réécrit pour celui du 21 septembre
    // 2026 (« l'année en étages », étape 1 côté appli). ---

    /** `GET /me/voyage` : ma progression, la carte — statut, visite et profondeur par année, depuis 1895. Mise en cache 60 s côté back. */
    suspend fun voyage(): VoyageResponse

    /**
     * `GET /me/voyage/annees/{annee}` : l'ouverture, les faits et les salles d'une année, chacune
     * avec ses films et mon état sur chacun. `200` (prête, verrouillée ou non configuré) ou `202`
     * (première visite, l'ouverture vient de s'enfiler).
     */
    suspend fun voyageAnnee(annee: Int): AnneeVoyageDetailResponse

    /** `POST /me/voyage/salles/{salleId}/plus` : « En voir plus » dans une salle — `202` en préparation, `200` épuisée. */
    suspend fun voyageSallePlus(salleId: String): SallePlusResponse

    /** `GET /reference/chroniques/films/{tmdbId}` : le carton « Et pendant ce temps… » d'un film. */
    suspend fun cartonFilm(tmdbId: Int): CartonFilmResponse

    /** `POST /me/voyage/demander/{tmdbId}` : demande le film sur Seerr. `201` à la création, `200` s'il l'était déjà. */
    suspend fun demanderVoyage(tmdbId: Int): DemanderVoyageResponse

    /**
     * `PUT /me/voyage/annees/{annee}/podium/{place}` (brief du 21 septembre 2026, « le podium ») :
     * pose ou déplace un film ou un programme sur une marche, `corps` portant `tmdb_id` **ou**
     * `programme_id`, jamais les deux (`PodiumBody`). Répond le podium complet après l'écriture.
     */
    suspend fun poserPodium(annee: Int, place: Int, corps: PodiumBody): PodiumResponse

    /** `DELETE /me/voyage/annees/{annee}/podium/{place}` : vide la marche. Idempotent, toujours `204`. */
    suspend fun retirerPodium(annee: Int, place: Int)

    // --- Le ticket (brief du 21 septembre 2026, étape 3). ---

    /** `GET /me/voyage/tickets` : mon portefeuille, par année croissante — celle que chaque ticket ouvre. */
    suspend fun voyageTickets(): VoyageTicketsResponse

    /** `POST /me/voyage/tickets/{annee}/montre` : marque le ticket comme montré. Idempotent, toujours `204`. */
    suspend fun montrerTicket(annee: Int)

    /** `POST /me/voyage/tickets/{annee}/utiliser` : encaisse le ticket, `annee` devient mon année en cours. */
    suspend fun utiliserTicket(annee: Int): TicketUtiliseResponse

    // --- La chronique et les salles (brief du 21 septembre 2026, étape 4). ---

    /**
     * `POST /me/voyage/annees/{annee}/chronique` : ajoute un paragraphe sur un film vu, ou un
     * programme entièrement vu — `corps` porte `tmdb_id` **ou** `programme_id`, jamais les deux.
     * `200 { statut: "ecrit", paragraphe }` s'il existait déjà (jamais régénéré), `202 { statut:
     * "en_preparation" }` sinon, qu'il vienne d'être enfilé ou qu'une génération soit déjà en cours.
     */
    suspend fun voyageChronique(annee: Int, corps: ChroniqueBody): ChroniqueEcritureResponse

    /**
     * `POST /me/voyage/annees/{annee}/salles` : « Ouvrir une nouvelle salle » sur une phrase,
     * `piste` (nul par défaut) reprenant le `nom` d'une piste suivie (brief du 22 septembre 2026,
     * « les pistes »). Toujours `202`, la génération s'enfile ; `409` si une demande est déjà en
     * cours pour l'année.
     */
    suspend fun voyageDemanderSalle(annee: Int, demande: String, piste: String? = null): DemandeSalleEcritureResponse

    /** `POST /me/voyage/demandes-salles/{id}/vue` : marque une demande (typiquement un refus) comme vue. Idempotent, toujours `204`. */
    suspend fun voyageDemandeSalleVue(id: String)

    /**
     * `POST /me/voyage/annees/{annee}/pistes` (brief du 22 septembre 2026, « les pistes ») :
     * renouvelle les trois pistes de salles de l'année — appel **synchrone** au chroniqueur, pas
     * d'enfilement ni de relecture, contrairement au reste du Voyage.
     */
    suspend fun voyagePistes(annee: Int): PistesVoyageResponse

    /**
     * `GET /me/voyage/depenses` (décision 2 du brief du 21 septembre 2026, « les dépenses ») : mes
     * appels au chroniqueur, mois par mois, du plus ancien au plus récent.
     */
    suspend fun voyageDepenses(): VoyageDepensesResponse

    // --- La séance (brief du 21 septembre 2026, « la séance »). ---

    /**
     * `POST /me/voyage/annees/{annee}/seances` : « Composer une séance ». Toujours `202`, qu'elle
     * vienne de s'enfiler ou qu'une composition soit déjà en cours pour cette année ; `409` sur une
     * composition déjà en cours (variante distincte selon le back), `404`/`400` selon l'année.
     */
    suspend fun voyageComposerSeance(annee: Int): SeanceComposerResponse

    /** `POST /me/voyage/seances/{id}/remplacer` : remplace le long ou le court, sans appel au chroniqueur. */
    suspend fun voyageRemplacerSeance(id: String, corps: SeanceRemplacerBody): SeanceEcritureResponse

    /** `POST /me/voyage/seances/{id}/prendre` : prend la séance telle quelle, ignore les autres séances prises de l'année. */
    suspend fun voyagePrendreSeance(id: String): SeanceEcritureResponse

    /** `POST /me/voyage/seances/{id}/ignorer` : ignore la séance. */
    suspend fun voyageIgnorerSeance(id: String): SeanceEcritureResponse

    // --- Le carnet (brief du 22 septembre 2026, « le carnet »). ---

    /**
     * `POST /me/voyage/annees/{annee}/carnet` : lance ou relance la fabrication du carnet de cette
     * année. Toujours `202`. `404` sans ouverture pour cette année, `409` si une fabrication est
     * déjà en cours.
     */
    suspend fun voyageFabriquerCarnet(annee: Int): CarnetFabricationResponse

    /** `GET /me/voyage/carnets` : mes carnets déjà fabriqués, et les années dont la fabrication tourne encore. */
    suspend fun voyageCarnets(): VoyageCarnetsResponse

    /**
     * `GET /me/voyage/carnets/{annee}/pdf` : le PDF lui-même, en octets bruts — jamais affiché dans
     * l'appli, seulement écrit dans `cacheDir` puis remis au système. `404` sans carnet pour cette
     * année.
     */
    suspend fun telechargerCarnetPdf(annee: Int): ByteArray
}

/**
 * Toutes les pages de `GET /me/journal`, chargées à la suite. La Frise veut le journal entier
 * pour le ranger par année ; l'écran Réalisateurs le veut pour retrouver une entrée par son
 * `entry_id` — le contrat n'expose aucun `GET /me/journal/{id}`, seulement `PATCH` et `DELETE`.
 * Une seule implémentation, pour que les deux s'arrêtent sur la même condition (`next_cursor`
 * nul) plutôt que chacune sur la sienne.
 */
suspend fun JournalApi.journalComplet(): List<JournalItem> {
    val items = mutableListOf<JournalItem>()
    var cursor: String? = null
    do {
        val page = journal(cursor)
        items += page.items
        cursor = page.next_cursor
    } while (cursor != null)
    return items
}
