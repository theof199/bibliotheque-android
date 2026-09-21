package fr.mediatheque.journal.api

/**
 * Les chemins de l'API, et rien d'autre : `ApiClient` est seul à les
 * employer, jamais un chemin écrit en dur ailleurs. Relatifs, sans `/` en
 * tête — la base de `ApiClient` finit par `/`, et c'est ce qui garde le
 * préfixe `/api` de l'instance en ligne.
 */
object Endpoints {
    const val login = "auth/login"
    const val me = "auth/me"
    const val logout = "auth/logout"
    const val search = "search"
    const val media = "media"
    const val journal = "me/journal"
    /** `POST /me/journal/import/letterboxd` (brief du 16 septembre 2026) : corps JSON `{ csv }`, jamais `text/csv`. */
    const val importLetterboxd = "me/journal/import/letterboxd"
    const val stats = "stats"
    const val sorties = "reference/sorties"
    const val plex = "reference/plex"
    const val personnes = "reference/personnes"
    const val realisateurs = "me/realisateurs"
    const val introuvables = "me/introuvables"
    fun viewing(id: String): String = "me/journal/$id"

    /** `GET`/`DELETE /me/realisateurs/{tmdbId}` — l'identifiant de la **personne**, pas celui de la ligne. */
    fun realisateur(tmdbId: Int): String = "$realisateurs/$tmdbId"
    fun filmographie(tmdbId: Int): String = "${realisateur(tmdbId)}/films"

    /** `PUT`/`DELETE /me/introuvables/{tmdbId}` — l'identifiant d'un **film**, pas d'une personne. */
    fun introuvable(tmdbId: Int): String = "$introuvables/$tmdbId"

    // --- Les sagas (brief du 15 septembre 2026), jumelles des réalisateurs ci-dessus. ---

    const val sagas = "me/sagas"
    const val collections = "reference/sagas"

    /** `GET`/`DELETE /me/sagas/{tmdbId}` — l'identifiant de la **collection**, pas celui de la ligne. */
    fun saga(tmdbId: Int): String = "$sagas/$tmdbId"
    fun filmsDeSaga(tmdbId: Int): String = "${saga(tmdbId)}/films"

    /**
     * `PUT`/`DELETE /me/sagas/{tmdbId}/films/{filmId}` — le premier identifiant est celui de la
     * **collection**, le second celui du **film** (brief « les films de saga ajoutés à la main »,
     * 15 septembre 2026).
     */
    fun filmDeSaga(tmdbId: Int, filmId: Int): String = "${filmsDeSaga(tmdbId)}/$filmId"

    // --- Le Voyage. Brief du 16 septembre 2026 (« le moteur »), réécrit pour celui du 21 septembre
    // 2026 (« l'année en étages ») : `chroniqueAnnee` a disparu, remplacée par `voyageAnnee`.
    // `voyageAnneeSuivante` a disparu le même jour, « le ticket » (étape 3) le remplaçant. ---

    const val voyage = "me/voyage"
    fun voyageAnnee(annee: Int): String = "me/voyage/annees/$annee"
    fun voyageSallePlus(salleId: String): String = "me/voyage/salles/$salleId/plus"
    fun chroniqueFilm(tmdbId: Int): String = "reference/chroniques/films/$tmdbId"
    fun demanderVoyage(tmdbId: Int): String = "me/voyage/demander/$tmdbId"

    /** `PUT`/`DELETE /me/voyage/annees/{annee}/podium/{place}` (brief du 21 septembre 2026, « le podium »). */
    fun voyagePodium(annee: Int, place: Int): String = "${voyageAnnee(annee)}/podium/$place"

    // --- Le ticket (brief du 21 septembre 2026, étape 3). ---

    const val voyageTickets = "me/voyage/tickets"
    fun voyageTicketMontre(annee: Int): String = "$voyageTickets/$annee/montre"
    fun voyageTicketUtiliser(annee: Int): String = "$voyageTickets/$annee/utiliser"

    // --- La chronique et les salles (brief du 21 septembre 2026, étape 4). ---

    fun voyageChronique(annee: Int): String = "${voyageAnnee(annee)}/chronique"
    fun voyageSalles(annee: Int): String = "${voyageAnnee(annee)}/salles"
    fun voyageDemandeSalleVue(id: String): String = "me/voyage/demandes-salles/$id/vue"

    /** `GET /me/voyage/depenses` (décision 2 du brief du 21 septembre 2026, « les dépenses »). */
    const val voyageDepenses = "me/voyage/depenses"

    // --- La séance (brief du 21 septembre 2026, « la séance »). ---

    fun voyageSeances(annee: Int): String = "${voyageAnnee(annee)}/seances"
    fun voyageSeanceRemplacer(id: String): String = "me/voyage/seances/$id/remplacer"
    fun voyageSeancePrendre(id: String): String = "me/voyage/seances/$id/prendre"
    fun voyageSeanceIgnorer(id: String): String = "me/voyage/seances/$id/ignorer"
}
