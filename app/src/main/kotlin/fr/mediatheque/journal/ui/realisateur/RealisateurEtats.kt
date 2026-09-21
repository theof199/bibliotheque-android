package fr.mediatheque.journal.ui.realisateur

import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.api.dto.RealisateurCredit
import java.time.LocalDate

/**
 * La page réalisateur (brief du 21 septembre 2026, « la page réalisateur ») : fonctions pures,
 * testées en JVM sans réseau ni `ViewModel`, comme `FicheVoyageEtats.kt` (`ui/frise/`) à côté.
 */

/** « 1861 – 1938 », « née en 1961 », ou vide selon ce que TMDB donne (décision 1 du brief). */
fun ligneDates(naissance: String?, deces: String?): String = when {
    naissance != null && deces != null -> "${LocalDate.parse(naissance).year} – ${LocalDate.parse(deces).year}"
    naissance != null -> "née en ${LocalDate.parse(naissance).year}"
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
