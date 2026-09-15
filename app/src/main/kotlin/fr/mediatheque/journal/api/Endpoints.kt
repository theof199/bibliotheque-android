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
}
