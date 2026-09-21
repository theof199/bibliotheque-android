package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.SeanceRemplacerBody

/**
 * La séance d'une année (brief du 21 septembre 2026, « la séance ») : les candidats à « Autre long »
 * / « Autre court », le corps envoyé, l'état de la zone entre le podium et les salles, et le tri
 * entre la séance la plus récente et les « Séances passées ». Fonctions pures, testées en JVM,
 * jumelles de `PodiumEtats.kt` à côté.
 */

/** Plex d'abord, puis demandé, puis à demander — puis le reste (décision 3 du brief). */
private fun ordreEtatSeance(etat: String): Int = when (etat) {
    "sur_le_plex" -> 0
    "demande" -> 1
    "a_demander" -> 2
    else -> 3
}

/** Un candidat de remplacement : un film (un long, ou le programme entier pour un court), ou une bobine d'un programme. */
sealed interface CandidatSeance {
    val filmId: String
    val tmdbId: Int
    val title: String
    val coverUrl: String?
    val etat: String

    data class Film(
        override val filmId: String,
        override val tmdbId: Int,
        override val title: String,
        override val coverUrl: String?,
        override val etat: String,
    ) : CandidatSeance

    data class Bobine(
        override val filmId: String,
        override val tmdbId: Int,
        override val title: String,
        override val coverUrl: String?,
        override val etat: String,
    ) : CandidatSeance
}

/** Une salle et ses candidats, dans l'ordre Plex puis demandé puis à demander (décision 3). */
data class GroupeCandidatsSeance(val salle: String, val candidats: List<CandidatSeance>)

/**
 * Les candidats à « Autre long » (décision 3 du brief) : les films sans programme, jamais vus ni
 * introuvables — jumeau du filtre que `POST .../remplacer` applique côté back — groupés par salle,
 * chaque groupe trié Plex d'abord, puis demandé, puis à demander.
 */
fun candidatsSeanceLong(salles: List<SalleUi>): List<GroupeCandidatsSeance> =
    salles.mapNotNull { salle ->
        val candidats = salle.films
            .filter { it.programme == null && it.etat != "vu" && it.etat != "introuvable" }
            .sortedBy { ordreEtatSeance(it.etat) }
            .map { CandidatSeance.Film(it.id, it.tmdbId, it.title, it.coverUrl, it.etat) }
        candidats.takeIf { it.isNotEmpty() }?.let { GroupeCandidatsSeance(salle.nom, it) }
    }

/**
 * Les candidats à « Autre court » (décision 3, corrigée le 21 septembre 2026 : le brief disait
 * à tort de garder les introuvables) : les programmes non vus ni introuvables, chacun suivi de ses
 * bobines non vues ni introuvables — même filtre que le long, jumeau de ce que `POST .../remplacer`
 * refuse côté back dans les deux cas — groupés par salle, chaque groupe trié Plex d'abord, puis
 * demandé, puis à demander sur ses programmes.
 */
fun candidatsSeanceCourt(salles: List<SalleUi>): List<GroupeCandidatsSeance> =
    salles.mapNotNull { salle ->
        val candidats = salle.films
            .filter { it.programme != null }
            .sortedBy { film -> ordreEtatSeance(etatFilmVoyage(film.etat, film.programme!!.bobines)) }
            .flatMap { film ->
                val programme = film.programme!!
                val etatProgramme = etatFilmVoyage(film.etat, programme.bobines)
                val ligneProgramme = if (etatProgramme != "vu" && etatProgramme != "introuvable") {
                    listOf(CandidatSeance.Film(film.id, film.tmdbId, film.title, film.coverUrl, etatProgramme))
                } else {
                    emptyList()
                }
                val lignesBobines = programme.bobines
                    .filter { it.etat != "vu" && it.etat != "introuvable" }
                    .map { CandidatSeance.Bobine(film.id, it.tmdbId, it.title, it.coverUrl, it.etat) }
                ligneProgramme + lignesBobines
            }
        candidats.takeIf { it.isNotEmpty() }?.let { GroupeCandidatsSeance(salle.nom, it) }
    }

/** Le corps de `POST .../remplacer` pour un candidat : `film_id` seul, `+ bobine_tmdb_id` pour une bobine. */
fun corpsRemplacementSeance(morceau: String, candidat: CandidatSeance): SeanceRemplacerBody = when (candidat) {
    is CandidatSeance.Film -> SeanceRemplacerBody(morceau = morceau, film_id = candidat.filmId)
    is CandidatSeance.Bobine -> SeanceRemplacerBody(morceau = morceau, film_id = candidat.filmId, bobine_tmdb_id = candidat.tmdbId)
}

/** La plus récente séance composée (rang le plus élevé) — nulle si aucune n'existe encore. */
fun seanceRecente(seances: List<SeanceUi>): SeanceUi? = seances.maxByOrNull { it.rang }

/**
 * Les séances précédentes, prises ou ignorées, repliées sous « Séances passées » (décision 2) —
 * jamais la plus récente, même si elle est déjà prise ou ignorée. Rang décroissant : la plus
 * récente des passées en tête.
 */
fun seancesPassees(seances: List<SeanceUi>): List<SeanceUi> {
    val recente = seanceRecente(seances)
    return seances
        .filter { it.id != recente?.id && (it.statut == "prise" || it.statut == "ignoree") }
        .sortedByDescending { it.rang }
}

/** L'état de la zone séance, entre le podium et les salles (décision 1-2 du brief du 21 septembre 2026, « la séance »). */
enum class EtatZoneSeance { BOUTON, EN_COURS, CARTE_PROPOSEE, CARTE_PRISE, RIEN }

/**
 * `seanceEnCours` (une composition en vol) prime sur tout le reste ; sinon la séance la plus
 * récente décide : aucune, ou `ignoree` -> le bouton (« ignorer » n'est pas terminal : la spec dit
 * « la prendre, en changer un morceau, ou l'ignorer » — ignorer, c'est en redemander une autre plus
 * tard, comme s'il n'y en avait pas), `proposee` -> sa carte, `prise` -> sa carte étiquetée, tant
 * qu'elle reste prise — seul « Ignorer » libère le bouton, jamais le fait que son long soit vu.
 */
fun etatZoneSeance(seanceEnCours: Boolean, seances: List<SeanceUi>): EtatZoneSeance {
    if (seanceEnCours) return EtatZoneSeance.EN_COURS
    return when (seanceRecente(seances)?.statut) {
        null, "ignoree" -> EtatZoneSeance.BOUTON
        "proposee" -> EtatZoneSeance.CARTE_PROPOSEE
        "prise" -> EtatZoneSeance.CARTE_PRISE
        else -> EtatZoneSeance.RIEN
    }
}

/** « sur ton Plex » · « demandé » · « à demander » · « introuvable », toujours un texte (décision 2 : « sa salle et son état »). */
fun etiquetteEtatSeanceFilm(etat: String): String = when (etat) {
    "sur_le_plex" -> "sur ton Plex"
    "demande" -> "demandé"
    "a_demander" -> "à demander"
    "introuvable" -> "introuvable"
    else -> etat
}
