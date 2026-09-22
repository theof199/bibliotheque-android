package fr.mediatheque.journal.ui.realisateur

import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.api.dto.RealisateurCredit
import fr.mediatheque.journal.ui.frise.Monde
import fr.mediatheque.journal.ui.frise.mondeDe
import java.time.LocalDate

/**
 * La page réalisateur (brief du 21 septembre 2026, « la page réalisateur » ; reprise du même jour,
 * « la page réalisateur, reprise ») : fonctions pures, testées en JVM sans réseau ni `ViewModel`,
 * comme `FicheVoyageEtats.kt` (`ui/frise/`) à côté.
 */

/**
 * « 1861 – 1938 » quand les deux dates sont connues (inchangé) ; sinon « né en 1958 »/« née en
 * 1958 »/« naissance en 1958 » selon `genre` (décision 5 de la reprise) ; vide sans aucune date.
 */
fun ligneDates(naissance: String?, deces: String?, genre: String?): String = when {
    naissance != null && deces != null -> "${LocalDate.parse(naissance).year} – ${LocalDate.parse(deces).year}"
    naissance != null -> {
        val annee = LocalDate.parse(naissance).year
        when (genre) {
            "homme" -> "né en $annee"
            "femme" -> "née en $annee"
            else -> "naissance en $annee"
        }
    }
    else -> ""
}

/**
 * La fiche que le tap sur une affiche de la filmographie ouvre (décision 2 du brief) : la fiche
 * du Voyage si le film y a une ligne (`voyage` non nul), la fiche simple sinon.
 */
sealed interface DestinationFilm {
    data class Voyage(val annee: Int, val salleId: String, val filmId: String) : DestinationFilm
    data class Simple(val film: FilmDeFilmographie) : DestinationFilm
}

fun destinationFilm(film: FilmDeFilmographie): DestinationFilm {
    val voyage = film.voyage
    return if (voyage != null) {
        DestinationFilm.Voyage(voyage.annee, voyage.salle_id, voyage.film_id)
    } else {
        DestinationFilm.Simple(film)
    }
}

/**
 * L'état affiché d'un film de filmographie, jumeau de `etatFilmVoyage` (`ui/frise/`) mais sans
 * bobines à combiner : vu prime sur tout, puis introuvable, puis Plex, puis déjà demandé, puis « à
 * demander » par défaut.
 */
fun etatFilmographie(film: FilmDeFilmographie): String = when {
    film.vu != null -> "vu"
    film.introuvable -> "introuvable"
    film.sur_le_plex -> "sur_le_plex"
    film.demande -> "demande"
    else -> "a_demander"
}

/** Les cinq boutons possibles de la fiche simple d'un film (décision 2 du brief). */
enum class BoutonFicheFilm { VOIR_SUR_LE_PLEX, JE_L_AI_VU, DEMANDER, MARQUER_INTROUVABLE, RETIRER_INTROUVABLE }

/**
 * Les boutons à montrer, selon l'état du film, la présence d'un lien Plex, et son type (décision
 * 2 du brief : une série n'a ni « Je l'ai vu » ni formulaire, et garde seulement Plex et Sir — pas
 * de marque « introuvable » sur cette fiche-ci pour elle non plus).
 */
fun boutonsFicheFilm(type: String, etat: String, plexUrl: String?): List<BoutonFicheFilm> = buildList {
    if (plexUrl != null) add(BoutonFicheFilm.VOIR_SUR_LE_PLEX)
    if (type != "tv") {
        if (etat != "vu") add(BoutonFicheFilm.JE_L_AI_VU)
        when {
            etat == "introuvable" -> add(BoutonFicheFilm.RETIRER_INTROUVABLE)
            etat != "vu" -> add(BoutonFicheFilm.MARQUER_INTROUVABLE)
        }
    }
    if (etat == "a_demander") add(BoutonFicheFilm.DEMANDER)
}

/**
 * Ce que fait le tap sur un nom de réalisateur touchable (décision 3 du brief), une fois
 * `RealisateurResolveur.resoudre` revenu : un seul → sa page directement ; plusieurs → une feuille
 * à choisir ; aucun → un bandeau, TMDB ne connaît pas de réalisateur pour ce film.
 */
sealed interface ResultatRealisateur {
    data class Un(val tmdbId: Int) : ResultatRealisateur
    data class Plusieurs(val realisateurs: List<RealisateurCredit>) : ResultatRealisateur
    data object Aucun : ResultatRealisateur
}

fun resultatTapRealisateur(realisateurs: List<RealisateurCredit>): ResultatRealisateur = when {
    realisateurs.isEmpty() -> ResultatRealisateur.Aucun
    realisateurs.size == 1 -> ResultatRealisateur.Un(realisateurs[0].tmdb_id)
    else -> ResultatRealisateur.Plusieurs(realisateurs)
}

/**
 * Le `tmdb_id` du film pour un nom de réalisateur touchable (retouche du 21 septembre 2026, sur
 * `Screen.Form` comme sur `Screen.Edit`) : `SearchResult.external_id` n'est le `tmdb_id` du film
 * que si `source` vaut `"tmdb"` — une autre source (SensCritique, Letterboxd, …) ne connaît pas cet
 * identifiant-là, `GET /reference/films/{tmdbId}/realisateurs` ne saurait pas quoi en faire. Nul
 * dans ce cas : le nom reste affiché, mais inerte.
 */
fun filmTmdbIdTouchable(source: String, externalId: String): Int? =
    if (source == "tmdb") externalId.toIntOrNull() else null

// --- La grille verticale par décennie (décision 1 de la reprise du 21 septembre 2026) -----------

/**
 * Une décennie de la filmographie groupée pour la grille (décision 1) : les longs
 * (`court == false && type == "movie"`), puis les courts et les séries — une décennie sans long a
 * `longs` vide, ce que l'écran lit pour la montrer dépliée d'emblée, sans ligne à taper.
 */
data class DecennieFilmographie(
    val decennie: Int?,
    val longs: List<FilmDeFilmographie>,
    val courtsEtSeries: List<FilmDeFilmographie>,
)

/** « Années 1980 » : 1895 rejoint « Années 1890 », l'en-tête arrondissant au millésime de décennie. */
fun libelleDecennie(decennie: Int?): String = if (decennie != null) "Années $decennie" else "Année inconnue"

private fun decennieDe(annee: Int?): Int? = annee?.let { (it / 10) * 10 }

/**
 * La filmographie groupée par décennie. L'ordre des groupes est celui de leur première rencontre
 * dans `films` (`groupBy` construit une `LinkedHashMap`, jamais un tri par décennie) : l'ordre
 * chronologique du back se retrouve donc tel quel dans l'ordre des groupes.
 */
fun regrouperParDecennie(films: List<FilmDeFilmographie>): List<DecennieFilmographie> =
    films.groupBy { decennieDe(it.year) }.map { (decennie, filmsDeLaDecennie) ->
        DecennieFilmographie(
            decennie = decennie,
            longs = filmsDeLaDecennie.filter { !it.court && it.type == "movie" },
            courtsEtSeries = filmsDeLaDecennie.filter { it.court || it.type != "movie" },
        )
    }

/**
 * Les films que la page montre (retouche du 22 septembre 2026) : sans les films que j'ai marqués
 * introuvables tant que l'interrupteur « Masquer les introuvables » est activé, tous sinon. Se
 * calcule **avant** `regrouperParDecennie` : une décennie dont tous les films sont introuvables
 * disparaît avec eux, plutôt que de laisser un en-tête sans affiche.
 */
fun filmsAffiches(films: List<FilmDeFilmographie>, masquerIntrouvables: Boolean): List<FilmDeFilmographie> =
    if (masquerIntrouvables) films.filterNot { it.introuvable } else films

/**
 * Le libellé de la ligne de repli d'une décennie (décision 3) : « 6 courts · 1 série », accordé au
 * pluriel au-delà de un, la partie absente (aucun court, ou aucune série) omise.
 */
fun libelleCourtsEtSeries(courtsEtSeries: List<FilmDeFilmographie>): String {
    val courts = courtsEtSeries.count { it.court }
    val series = courtsEtSeries.count { it.type != "movie" }
    return listOfNotNull(
        if (courts > 0) "$courts court${if (courts > 1) "s" else ""}" else null,
        if (series > 0) "$series série${if (series > 1) "s" else ""}" else null,
    ).joinToString(" · ")
}

/**
 * La ligne de résumé sous le bouton Suivre (décision 4) : « 42 films · 9 vus · 3 sur le Plex »,
 * comptée sur toutes les lignes (films, courts et séries confondus), accordée au singulier — « 0 vu »
 * et « 0 sur le Plex » s'écrivent quand même, jamais omis.
 */
fun ligneResume(films: List<FilmDeFilmographie>): String {
    val total = films.size
    val vus = films.count { it.vu != null }
    val surLePlex = films.count { it.sur_le_plex }
    val filmMot = if (total <= 1) "film" else "films"
    val vuMot = if (vus <= 1) "vu" else "vus"
    return "$total $filmMot · $vus $vuMot · $surLePlex sur le Plex"
}

/**
 * Le monde du Voyage de la page (décision 6) : celui de l'année du premier film daté de la
 * filmographie, sinon 1895 — jamais celui d'un film sans année au milieu de la liste.
 */
fun mondeDeLaPage(films: List<FilmDeFilmographie>): Monde =
    mondeDe(films.firstOrNull { it.year != null }?.year ?: 1895)
