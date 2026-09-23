package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PodiumBody

/**
 * Le podium d'une année (brief du 21 septembre 2026, « le podium ») : les candidats à une marche,
 * le corps à envoyer selon le candidat choisi, et les lignes des deux feuilles qui le posent —
 * celle qui choisit un film pour une marche (`AnneeScreen`), celle qui choisit une marche pour un
 * film (`FicheVoyageScreen`). Fonctions pures, testées en JVM, comme `FicheVoyageEtats.kt` à côté.
 */

/** Un candidat à une marche : un film vu du journal (`tmdb_id`), ou un programme entièrement vu d'une salle (`programme_id`). */
sealed interface CandidatPodium {
    val title: String
    val coverUrl: String?

    data class Film(val tmdbId: Int, override val title: String, override val coverUrl: String?, val note: Int?) : CandidatPodium
    data class Programme(val programmeId: String, override val title: String, override val coverUrl: String?) : CandidatPodium
}

/**
 * Les candidats à une marche (décision 2 du brief) : mes films vus de cette année-là, depuis le
 * journal déjà chargé (jamais un film d'une autre année — c'est `PUT .../podium/{place}` qui
 * l'exigerait sinon en `400`), puis mes programmes entièrement vus, depuis les salles déjà chargées
 * (jamais un programme partiel — un film de salle ordinaire n'est pas un candidat ici, il l'est déjà
 * comme film du journal s'il est vu).
 */
fun candidatsPodium(journal: List<JournalItem>, annee: Int, salles: List<SalleUi>): List<CandidatPodium> {
    val films = journal
        .filter { it.media.year == annee }
        .mapNotNull { item ->
            item.media.external_id.toIntOrNull()?.let { tmdbId ->
                CandidatPodium.Film(tmdbId, item.media.title, item.media.cover_url, item.entry.rating)
            }
        }
    val programmes = salles
        .flatMap { it.films }
        .filter { film -> film.programme != null && etatFilmVoyage(film.etat, film.programme.bobines) == "vu" }
        .map { film -> CandidatPodium.Programme(film.id, film.title, film.coverUrl) }
    return films + programmes
}

/** Le corps de `PUT .../podium/{place}` pour un candidat : `tmdb_id` ou `programme_id`, jamais les deux. */
fun corpsPodium(candidat: CandidatPodium): PodiumBody = when (candidat) {
    is CandidatPodium.Film -> PodiumBody(tmdb_id = candidat.tmdbId)
    is CandidatPodium.Programme -> PodiumBody(programme_id = candidat.programmeId)
}

/** L'entrant du podium (geste 18 du complément du 23 septembre 2026 à l'habillage) : la marche dont l'occupant vient de changer, et son nouvel occupant. */
data class EntreePodium(val place: Int, val marche: PodiumMarcheUi)

/**
 * Le trio de tête qui bouge (geste 18) : compare l'ancien podium au nouveau, place par place (1,
 * 2, 3, l'ordre des marches, jamais l'ordre visuel 2-1-3 du `Row`), et rend la première marche dont
 * l'occupant a changé — jamais une marche qui vient de se vider (`apres` nul n'entre pas en jeu,
 * seul un nouvel occupant est un « entrant »). L'identité compare `tmdbId`/`programmeId`, pas le
 * titre : un même film republié avec un titre corrigé ne rejoue pas l'entrée. Fonction pure, testée
 * en JVM.
 */
fun entreePodium(ancien: List<PodiumMarcheUi?>, nouveau: List<PodiumMarcheUi?>): EntreePodium? {
    for (place in 1..3) {
        val avant = ancien.getOrNull(place - 1)
        val apres = nouveau.getOrNull(place - 1)
        if (apres != null && identitePodium(avant) != identitePodium(apres)) {
            return EntreePodium(place, apres)
        }
    }
    return null
}

private fun identitePodium(marche: PodiumMarcheUi?): Any? = marche?.let { it.tmdbId ?: it.programmeId }

/** Une ligne de la feuille « Marche *N* » (`AnneeScreen`, appui sur une marche) : retirer, ou choisir un candidat. */
sealed interface LignePodiumFeuille {
    data class Retirer(val place: Int) : LignePodiumFeuille
    data class Candidat(val candidat: CandidatPodium, val estOccupant: Boolean) : LignePodiumFeuille
}

/**
 * Les lignes de la feuille « Marche *N* » : « Retirer du podium » en tête **seulement si la marche
 * est occupée** (décision 2 du brief), puis un candidat par ligne, l'occupant actuel coché.
 */
fun lignesFeuillePodium(place: Int, marcheActuelle: PodiumMarcheUi?, candidats: List<CandidatPodium>): List<LignePodiumFeuille> = buildList {
    if (marcheActuelle != null) add(LignePodiumFeuille.Retirer(place))
    candidats.forEach { candidat ->
        val estOccupant = when (candidat) {
            is CandidatPodium.Film -> marcheActuelle?.tmdbId == candidat.tmdbId
            is CandidatPodium.Programme -> marcheActuelle?.programmeId == candidat.programmeId
        }
        add(LignePodiumFeuille.Candidat(candidat, estOccupant))
    }
}

/** Une ligne de la feuille « Mettre sur le podium » (`FicheVoyageScreen`) : une marche, son occupant actuel si elle en a un. */
data class LigneChoixMarche(val place: Int, val occupantActuel: String?, val estCeFilm: Boolean)

/**
 * Les trois lignes de la feuille « Mettre sur le podium » (décision 3 du brief) : une par marche,
 * « libre » ou le titre de son occupant, la marche qui porte déjà ce film ou ce programme cochée.
 * `tmdbId` et `programmeId` : exactement l'un des deux, jamais les deux — jumeau de `corpsPodium`.
 */
fun lignesChoixMarche(podium: List<PodiumMarcheUi?>, tmdbId: Int?, programmeId: String?): List<LigneChoixMarche> =
    (1..3).map { place ->
        val marche = podium.getOrNull(place - 1)
        LigneChoixMarche(
            place = place,
            occupantActuel = marche?.title,
            estCeFilm = marche != null &&
                ((tmdbId != null && marche.tmdbId == tmdbId) || (programmeId != null && marche.programmeId == programmeId)),
        )
    }
