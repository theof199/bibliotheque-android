package fr.mediatheque.journal.senscritique

import java.text.Normalizer
import kotlin.math.abs

/**
 * L'appariement des titres, porté de `apps/api/src/dev/importer-senscritique.ts`
 * (`biblio-back`, brief du 14 septembre 2026) : mêmes règles, mêmes noms de fonction, adaptées à
 * nos types (`MatchableFilm` — notre film, un seul titre connu — et `ExternalCandidate` — un
 * résultat de recherche chez le service externe, qui porte lui `title` et `originalTitle`).
 *
 * Direction inverse de l'importateur du back : là-bas on cherchait un produit SensCritique (qui
 * porte `title` + `originalTitle`) parmi des résultats TMDB (qui portent aussi les deux). Ici on
 * cherche notre propre film (un seul titre, celui que l'API nous a donné) parmi des résultats
 * SensCritique (`title` + `originalTitle`) — la fonction `titresCorrespondent` reste la même,
 * elle compare simplement des listes de titres non vides des deux côtés.
 */
private val ARTICLES = setOf("le", "la", "les", "l", "un", "une", "des", "du", "the", "a", "an")

/** Minuscules, accents retirés, ponctuation ignorée, articles ignorés. */
fun normaliserTitre(titre: String): String {
    val sansAccents = Normalizer.normalize(titre, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val nettoye = sansAccents.lowercase().replace(Regex("[^a-z0-9]+"), " ")
    return nettoye.split(' ').filter { it.isNotEmpty() && it !in ARTICLES }.joinToString(" ")
}

/**
 * Même année (±1), ou n'importe laquelle si notre côté n'en connaît pas. Sans année côté candidat
 * alors que notre côté en connaît une, le rapprochement échoue : rien ne permet de la confirmer.
 */
fun anneesCompatibles(anneeConnue: Int?, anneeCandidat: Int?): Boolean {
    if (anneeConnue == null) return true
    if (anneeCandidat == null) return false
    return abs(anneeCandidat - anneeConnue) <= 1
}

/** Un titre égal, après normalisation, au `title` ou à l'`originalTitle` du candidat. */
fun titresCorrespondent(film: MatchableFilm, candidat: ExternalCandidate): Boolean {
    val titresFilm = listOfNotNull(film.title, film.originalTitle).map(::normaliserTitre)
    val titresCandidat = listOfNotNull(candidat.title, candidat.originalTitle).map(::normaliserTitre)
    return titresCandidat.any { it in titresFilm }
}

sealed interface Appariement {
    data class Apparie(val candidat: ExternalCandidate) : Appariement
    data class Ambigu(val candidats: List<ExternalCandidate>) : Appariement
    data object Aucun : Appariement
}

/**
 * Le cœur de la règle : un candidat n'est retenu que seul. Deux candidats plausibles, ou aucun, et
 * le film part vers la feuille de choix plutôt que d'être deviné.
 */
fun apparierCandidat(film: MatchableFilm, candidats: List<ExternalCandidate>): Appariement {
    val correspondances = candidats.filter { anneesCompatibles(film.year, it.year) && titresCorrespondent(film, it) }
    return when (correspondances.size) {
        1 -> Appariement.Apparie(correspondances[0])
        0 -> Appariement.Aucun
        else -> Appariement.Ambigu(correspondances)
    }
}

/**
 * Faut-il rejouer la recherche avec l'`originalTitle` ? Sur le résultat de l'appariement **après
 * filtrage** (année et titre), pas sur le nombre brut de résultats — inutile aussi quand c'est déjà
 * `ambigu` : reprendre avec un autre titre ne désambiguïserait rien.
 */
fun fautRepliOriginalTitle(film: MatchableFilm, appariement: Appariement): Boolean =
    appariement is Appariement.Aucun &&
        film.originalTitle != null &&
        normaliserTitre(film.originalTitle) != normaliserTitre(film.title)
