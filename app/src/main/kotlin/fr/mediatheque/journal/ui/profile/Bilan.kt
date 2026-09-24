package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.frise.MONDES
import fr.mediatheque.journal.ui.suivis.EntiteSuivie
import fr.mediatheque.journal.ui.suivis.EtatFilmographie
import java.time.YearMonth
import kotlin.math.round

/**
 * Le Bilan du profil (brief du 15 septembre 2026) : la cinéphilie du
 * propriétaire, résumée en une carte, **calculée dans l'appli** — depuis le
 * journal complet, les réactions et les listes suivies —, à la différence
 * des deux chiffres au-dessus d'elle (`ProfileViewModel`), qui relisent
 * `GET /stats`. Fonctions pures, sans réseau ni `ViewModel`, testées en JVM.
 *
 * Trois sources indépendantes, trois moments d'apparition (« Chargement non
 * bloquant », brief du même jour) : le journal alimente `bilanJournal`, les
 * deux listes suivies alimentent `bilanSuivi`, une fois chacune (jumelle de
 * la généralisation des réalisateurs et des sagas — la même fonction sert
 * les deux).
 */

/** « 1920 → 2020, 9 décennies sur 11 ». */
data class DecenniesCouvertes(val premiere: Int, val derniere: Int, val couvertes: Int, val total: Int)

/** Le film le plus ancien vu, par année de sortie. */
data class FilmAncien(val titre: String, val annee: Int)

data class BilanJournal(
    val filmsVus: Int,
    val filmsVusCetteAnnee: Int,
    val seancesEnSalle: Int,
    val seancesEnSalleCetteAnnee: Int,
    /** Une décimale, films notés seulement — nulle si aucun film n'a de note. */
    val noteMoyenne: Double?,
    /** Nulles si aucun film vu n'a d'année de sortie connue. */
    val decennies: DecenniesCouvertes?,
    val plusAncien: FilmAncien?,
)

/** L'année d'une date ISO (« 2026-07-12 » → 2026) — le journal n'en rend jamais d'autre forme. */
private fun anneeDe(dateIso: String): Int = dateIso.take(4).toInt()

/** La décennie d'une année (1962 → 1960). */
private fun decennieDe(annee: Int): Int = (annee / 10) * 10

/**
 * Les décennies couvertes par une liste d'années de sortie, non vide. « 1920
 * → 2020, 9 décennies sur 11 » : `premiere`/`derniere` sont les décennies
 * extrêmes, `total` est leur span inclusif (11 décennies entre 1920 et 2020),
 * `couvertes` ne compte que celles qui ont au moins un film vu — deux
 * décennies de la fourchette peuvent rester vides.
 */
fun decenniesCouvertes(annees: List<Int>): DecenniesCouvertes {
    require(annees.isNotEmpty()) { "decenniesCouvertes attend au moins une année." }
    val decenniesVues = annees.map(::decennieDe).toSet()
    val premiere = decenniesVues.min()
    val derniere = decenniesVues.max()
    return DecenniesCouvertes(
        premiere = premiere,
        derniere = derniere,
        couvertes = decenniesVues.size,
        total = (derniere - premiere) / 10 + 1,
    )
}

/** Arrondi à une décimale — `round` plutôt qu'un simple troncage, pour que 8,25 devienne 8,3 et non 8,2. */
private fun uneDecimale(valeur: Double): Double = round(valeur * 10) / 10.0

/**
 * Le Bilan tiré du journal complet : le compte des films vus (distincts,
 * comme les deux chiffres du haut du profil — une même œuvre revue deux fois
 * ne compte qu'une fois), des séances en salle (réaction `en_salle`), la note
 * moyenne, les décennies couvertes et le plus ancien film vu.
 */
fun bilanJournal(journal: List<JournalItem>, anneeCourante: Int): BilanJournal {
    val filmsDistincts = journal.map { it.entry.media_id }.distinct()
    val decesCetteAnnee = { item: JournalItem -> anneeDe(item.entry.finished_at) == anneeCourante }

    val filmsVus = filmsDistincts.size
    val filmsVusCetteAnnee = journal.filter(decesCetteAnnee).map { it.entry.media_id }.distinct().size

    val seances = journal.filter { Reactions.EN_SALLE in it.carnet.reactions }
    val seancesEnSalle = seances.size
    val seancesEnSalleCetteAnnee = seances.count(decesCetteAnnee)

    val notes = journal.mapNotNull { it.entry.rating }
    val noteMoyenne = if (notes.isEmpty()) null else uneDecimale(notes.average())

    val annees = journal.mapNotNull { it.media.year }
    val decennies = if (annees.isEmpty()) null else decenniesCouvertes(annees)
    val plusAncien = journal.filter { it.media.year != null }
        .minByOrNull { it.media.year!! }
        ?.let { FilmAncien(it.media.title, it.media.year!!) }

    return BilanJournal(
        filmsVus = filmsVus,
        filmsVusCetteAnnee = filmsVusCetteAnnee,
        seancesEnSalle = seancesEnSalle,
        seancesEnSalleCetteAnnee = seancesEnSalleCetteAnnee,
        noteMoyenne = noteMoyenne,
        decennies = decennies,
        plusAncien = plusAncien,
    )
}

/** Combien d'entités suivies (réalisateurs ou sagas), et combien « terminées ». */
data class BilanSuivi(val suivis: Int, val termines: Int)

/**
 * Une filmographie (ou les films d'une saga) est terminée quand tous ses
 * films retrouvables sont vus — les introuvables ne comptent pas contre elle
 * (décision du propriétaire du 15 septembre 2026, cohérente avec
 * `prochainAVoir` : un réalisateur avec un introuvable non vu et tout le
 * reste vu compte terminé). Vide, elle est terminée aussi : rien n'y reste à
 * voir.
 */
fun filmographieTerminee(films: List<FilmSuivi>): Boolean =
    films.all { it.introuvable || it.vu != null }

/**
 * Le Bilan d'une des deux sources suivies : combien d'entités, et combien
 * terminées parmi celles dont la filmographie a répondu. Fonction générique
 * — la même sert les réalisateurs et les sagas (brief du 15 septembre 2026) :
 * elle ne connaît que `EntiteSuivie` et `EtatFilmographie`, jamais
 * `Realisateur` ni `Saga`.
 */
fun bilanSuivi(entites: List<EntiteSuivie>, filmographies: Map<Int, EtatFilmographie>): BilanSuivi {
    val termines = entites.count { entite ->
        val etat = filmographies[entite.tmdbId] as? EtatFilmographie.Pret ?: return@count false
        filmographieTerminee(etat.films)
    }
    return BilanSuivi(suivis = entites.size, termines = termines)
}

// --- Les trois graphiques du profil (point 14 de la revue du 24 septembre 2026) -------------------
//
// Trois séries, chacune une fonction pure sur le journal complet, testées en JVM comme le reste de
// ce fichier — `ProfileScreen.kt` ne fait que les dessiner (`Canvas`), jamais les calculer.

/**
 * Les films vus par mois, sur les douze derniers mois glissants jusqu'à [moisCourant] inclus, dans
 * l'ordre chronologique (le plus ancien d'abord) — chaque entrée de journal compte, revoyure
 * comprise : un graphique d'activité, pas un compte de films distincts comme `bilanJournal`.
 */
fun filmsParMois(journal: List<JournalItem>, moisCourant: YearMonth): List<Int> {
    val comptes = journal.groupingBy { YearMonth.parse(it.entry.finished_at.take(7)) }.eachCount()
    return (11 downTo 0).map { reculDeMois -> comptes[moisCourant.minusMonths(reculDeMois.toLong())] ?: 0 }
}

/**
 * Une case par décennie du Voyage, vraie si un film vu a une année de sortie dans cette décennie —
 * les quatorze décennies de `MONDES` (`ui/frise/Mondes.kt`, 1890 à 2020), dans le même ordre :
 * cette grille et la carte du Voyage comptent donc toujours le même nombre de cases.
 */
fun decenniesCouvertesGrille(journal: List<JournalItem>): List<Boolean> {
    val decenniesVues = journal.mapNotNull { it.media.year }.map { (it / 10) * 10 }.toSet()
    return MONDES.map { it.decennie in decenniesVues }
}

/**
 * Un compte par note, de 1 à 10, dans cet ordre — dix entiers ; un film vu sans note n'entre dans
 * aucune case, il n'existe pas de case « sans note » dans ce graphique.
 */
fun repartitionNotes(journal: List<JournalItem>): List<Int> {
    val comptes = journal.mapNotNull { it.entry.rating }.groupingBy { it }.eachCount()
    return (1..10).map { note -> comptes[note] ?: 0 }
}
