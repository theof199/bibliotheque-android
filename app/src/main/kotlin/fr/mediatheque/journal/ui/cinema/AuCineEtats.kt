package fr.mediatheque.journal.ui.cinema

import fr.mediatheque.journal.api.dto.SortieCinemaFilm
import fr.mediatheque.journal.api.dto.SortieFilm
import fr.mediatheque.journal.ui.normaliser
import fr.mediatheque.journal.ui.suivis.EtatFilmographie
import fr.mediatheque.journal.ui.suivis.SuivisUi

/**
 * Ce que les tuiles d'« Au ciné » savent des Suivis (« Au ciné · le guichet », décision du
 * 25 septembre 2026) : de quoi poser le sceau or « réalisateur suivi » ou « saga suivie » sur une
 * affiche, **sans aucun appel réseau nouveau** — seulement ce que `SuivisViewModel` a déjà en
 * mémoire, chargé à chaque entrée sur l'accueil.
 *
 * Pas de `realisateursDuFilm` par tuile (`RealisateurResolveur`) : jusqu'à quarante tuiles, donc
 * jusqu'à quarante appels à l'ouverture de l'onglet, refusé. Le réalisateur se rapproche donc par
 * son **nom** : `SortieCinemaFilm.directors` vient d'Allociné, le nom d'un réalisateur suivi de
 * TMDB (`EntiteSuivie.nom`) — les deux s'écrivent parfois différemment (accents, casse), d'où la
 * comparaison par `normaliser`. `realisateurs` porte ces noms déjà normalisés.
 *
 * `filmsDeSagas` : les `tmdb_id` de tous les films des sagas suivies dont la filmographie est
 * arrivée (`EtatFilmographie.Pret`). Une filmographie encore `EnAttente` ou `Indisponible` ne
 * contribue rien : le sceau apparaît à la recomposition, quand elle arrive.
 */
data class ReperesSuivis(
    val realisateurs: Set<String> = emptySet(),
    val filmsDeSagas: Set<Int> = emptySet(),
)

/** Les repères tirés de l'état des Suivis ; fonction pure, testée en JVM. */
fun reperesSuivis(ui: SuivisUi): ReperesSuivis = ReperesSuivis(
    realisateurs = ui.realisateurs.entites.map { normaliser(it.nom) }.toSet(),
    filmsDeSagas = ui.sagas.filmographies.values
        .filterIsInstance<EtatFilmographie.Pret>()
        .flatMap { etat -> etat.films.map { it.tmdb_id } }
        .toSet(),
)

/** Le sceau d'une tuile : `user` pour un réalisateur suivi, `movie` pour une saga suivie. */
enum class MarqueSuivi { REALISATEUR, SAGA }

/**
 * La marque d'une tuile « à l'affiche dans mes cinémas ». Le réalisateur l'emporte quand les deux
 * sont vrais (décision du 25 septembre 2026) : c'est le suivi le plus personnel des deux. Une tuile
 * sans `tmdb_id` n'en porte aucune — ni coche ni sceau sur une tuile qu'on ne peut pas toucher.
 */
fun ReperesSuivis.marque(film: SortieCinemaFilm): MarqueSuivi? {
    val tmdbId = film.tmdb_id ?: return null
    if (film.directors.any { normaliser(it) in realisateurs }) return MarqueSuivi.REALISATEUR
    if (tmdbId in filmsDeSagas) return MarqueSuivi.SAGA
    return null
}

/**
 * La marque d'une tuile « la semaine prochaine » : la saga seule, `SortieFilm` (TMDB) ne portant
 * pas de réalisateur depuis le correctif du 14 septembre 2026 (voir `SortieFilm.toSearchResult`).
 */
fun ReperesSuivis.marque(film: SortieFilm): MarqueSuivi? =
    if (film.tmdb_id in filmsDeSagas) MarqueSuivi.SAGA else null
