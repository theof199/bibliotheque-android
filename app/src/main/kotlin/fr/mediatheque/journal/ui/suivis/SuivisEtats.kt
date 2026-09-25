package fr.mediatheque.journal.ui.suivis

import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.formatRelatif
import fr.mediatheque.journal.ui.profile.filmographieTerminee
import java.time.LocalDate

/**
 * Les règles pures de la liste des Suivis (rétrospectives et cycles, 25 septembre 2026) : le
 * compte de l'en-tête, l'ordre des cartes, la section « complets » du bas, la ligne sous un nom.
 * Aucune ne touche au `ViewModel` : les listes qu'il tient restent dans l'ordre du back, l'écran
 * en tire cet ordre-ci à l'affichage (`repartirSuivis`). Testées en JVM, `SuivisEtatsTest.kt`.
 *
 * Les dates se comparent en chaînes ISO sur leurs **dix premiers caractères** : `finished_at` est
 * une date (`2026-09-22`), `ajoute_le` un instant (`2026-09-15T18:22:41.000Z`) ; tronqués au jour,
 * l'ordre lexicographique est l'ordre chronologique.
 */

/** « 5 rétrospectives · 2 cycles », accordé : « 1 rétrospective · 0 cycle ». */
fun compteSuivis(nRetrospectives: Int, nCycles: Int): String {
    val r = if (nRetrospectives > 1) "rétrospectives" else "rétrospective"
    val c = if (nCycles > 1) "cycles" else "cycle"
    return "$nRetrospectives $r · $nCycles $c"
}

/** Le jour du visionnage le plus récent de ces films (`2026-09-22`), nul si aucun n'est vu. */
fun dernierVisionnage(films: List<FilmSuivi>): String? =
    films.mapNotNull { it.vu?.finished_at?.take(10) }.maxOrNull()

/**
 * Le jour de la dernière activité sur une entité : son film vu le plus récemment, sinon le jour où
 * je l'ai ajoutée. `films` nul (filmographie pas encore là, ou indisponible) : `ajouteLe` seul.
 */
fun derniereActivite(ajouteLe: String, films: List<FilmSuivi>?): String =
    films?.let(::dernierVisionnage) ?: ajouteLe.take(10)

/**
 * Les entités de la plus récemment active à la plus ancienne (`derniereActivite`). Une entité dont
 * la filmographie n'est pas `Pret` se classe sur son seul `ajouteLe`, et remonte à sa place quand
 * ses films arrivent — `animateItem` absorbe le déplacement. Tri stable : à égalité, l'ordre du
 * back (du plus récemment ajouté au plus ancien) tient.
 */
fun trierParActivite(entites: List<EntiteSuivie>, filmographies: Map<Int, EtatFilmographie>): List<EntiteSuivie> =
    entites.sortedByDescending { derniereActivite(it.ajouteLe, filmsPrets(filmographies[it.tmdbId])) }

/**
 * Une rétrospective ou un cycle bouclé : au moins un film, et chacun vu ou marqué introuvable —
 * la règle de `filmographieTerminee` (Bilan) et de `retrospectiveComplete` (page d'un réalisateur),
 * réutilisée telle quelle, **sauf** qu'une liste vide n'est pas bouclée ici : une carte sans aucun
 * film n'a rien à célébrer dans la section du bas.
 */
fun entiteBouclee(films: List<FilmSuivi>): Boolean = films.isNotEmpty() && filmographieTerminee(films)

/**
 * La liste de l'écran, en deux : en cours d'abord, bouclées ensuite (la section « complets »),
 * chacune triée par activité. Une filmographie pas encore `Pret` reste en cours.
 */
fun repartirSuivis(
    entites: List<EntiteSuivie>,
    filmographies: Map<Int, EtatFilmographie>,
): Pair<List<EntiteSuivie>, List<EntiteSuivie>> =
    trierParActivite(entites, filmographies).partition { entite ->
        filmsPrets(filmographies[entite.tmdbId])?.let(::entiteBouclee) != true
    }

/**
 * La ligne sous le nom d'une carte :
 * - « 4 sur 12 · vu il y a 3 jours » quand un film est vu (`activite`, le jour du dernier) ;
 * - « 0 sur 12 · ajouté le 12 septembre 2026 » sinon ;
 * - « 6 sur 6 · bouclée le 2 août 2026 » (ou « bouclé », `source.participeBoucle`) pour une entité
 *   bouclée, datée de son dernier visionnage, sinon de son ajout.
 * Sans aucune date (`ajouteLe` vide), le compte seul.
 */
fun sousLigneCarte(
    source: SourceSuivi,
    vus: Int,
    total: Int,
    activite: String?,
    ajouteLe: String,
    aujourdHui: LocalDate,
    bouclee: Boolean,
): String {
    val compte = "$vus sur $total"
    val ajout = ajouteLe.take(10).takeIf { it.isNotEmpty() }
    val suite = when {
        bouclee -> (activite ?: ajout)?.let { "${source.participeBoucle} le ${formatDate(it)}" }
        activite != null -> "vu ${formatRelatif(activite, aujourdHui)}"
        else -> ajout?.let { "ajouté le ${formatDate(it)}" }
    }
    return if (suite == null) compte else "$compte · $suite"
}

private fun filmsPrets(etat: EtatFilmographie?): List<FilmSuivi>? = (etat as? EtatFilmographie.Pret)?.films
