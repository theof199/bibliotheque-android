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
    fun viewing(id: String): String = "me/journal/$id"
}
