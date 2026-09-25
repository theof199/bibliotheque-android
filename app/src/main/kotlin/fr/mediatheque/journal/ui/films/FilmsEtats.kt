package fr.mediatheque.journal.ui.films

import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.reactions.Reactions
import java.text.Normalizer

/**
 * « Mes films · le hall » (décisions du propriétaire du 24 septembre 2026) : la recherche, le tri
 * et les filtres de l'écran, en fonctions pures testées en JVM sans réseau ni `ViewModel`, comme
 * `RealisateurEtats.kt` (`ui/realisateur/`). L'état lui-même vit dans `FiltresFilmsViewModel`.
 */

/** L'ordre de la liste : par date (récents ou anciens d'abord), ou par note. */
enum class TriFilms { DATE_DESC, DATE_ASC, NOTE_DESC }

/**
 * Ce que le propriétaire a choisi sur l'écran. `noteMin` nul = aucune note minimale ;
 * `reactions` vide = aucun filtre de réaction.
 */
data class FiltresFilms(
    val texte: String = "",
    val tri: TriFilms = TriFilms.DATE_DESC,
    val noteMin: Int? = null,
    val reactions: Set<String> = emptySet(),
) {
    /**
     * Vrai dès que la liste affichée peut différer de la pagination brute : l'écran doit alors
     * charger tout le journal (`FilmsViewModel.chargerTout()`), sinon « Rien trouvé » mentirait
     * sur les pages pas encore chargées. Un texte fait seulement d'espaces ne filtre rien
     * (`appliquerFiltres`) : il ne compte pas, pour ne pas charger tout le journal pour rien.
     */
    val actifs: Boolean
        get() = texte.isNotBlank() || tri != TriFilms.DATE_DESC || noteMin != null || reactions.isNotEmpty()
}

/**
 * Le texte tel qu'on le compare : décomposé (NFD), marques diacritiques retirées, en minuscules,
 * sans espaces aux bords. « Léon » et « leon », « MIYAZAKI » et « miyazaki » deviennent la même
 * chaîne : on tape sur un téléphone, sans chercher l'accent ni la majuscule.
 */
private fun normaliser(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITIQUES, "").lowercase().trim()

private val DIACRITIQUES = Regex("\\p{M}+")

/**
 * La liste affichée : d'abord les filtres, puis le tri.
 *
 * - **Texte** : cherché dans le titre **et** le réalisateur (« miya » trouve les films de Hayao
 *   Miyazaki), insensible à la casse et aux accents (voir `normaliser`). Vide ou blanc : aucun
 *   filtre.
 * - **Note minimale** : garde les visionnages notés au moins `noteMin` ; un visionnage sans note
 *   est écarté dès qu'une note minimale est posée.
 * - **Réactions** : garde les visionnages qui portent **toutes** les réactions cochées (et, pas
 *   ou) — cocher de plus en plus resserre la liste, jamais l'inverse.
 * - **Tri**, toujours stable (une égalité garde l'ordre d'arrivée du back) : par `finished_at`
 *   (date ISO seule, `2026-09-03`, qui se compare donc comme une chaîne), récents ou anciens
 *   d'abord ; ou par note décroissante, les visionnages sans note en dernier, une égalité de note
 *   départagée par la date, récents d'abord.
 */
fun appliquerFiltres(items: List<JournalItem>, filtres: FiltresFilms): List<JournalItem> {
    val texte = normaliser(filtres.texte)
    val noteMin = filtres.noteMin
    val gardes = items.filter { item ->
        val rating = item.entry.rating
        val texteOk = texte.isEmpty() ||
            normaliser(item.media.title).contains(texte) ||
            normaliser(item.media.director.orEmpty()).contains(texte)
        val noteOk = noteMin == null || (rating != null && rating >= noteMin)
        texteOk && noteOk && item.carnet.reactions.containsAll(filtres.reactions)
    }
    return when (filtres.tri) {
        TriFilms.DATE_DESC -> gardes.sortedByDescending { it.entry.finished_at }
        TriFilms.DATE_ASC -> gardes.sortedBy { it.entry.finished_at }
        TriFilms.NOTE_DESC -> gardes.sortedWith(
            compareBy<JournalItem> { it.entry.rating == null }
                .thenByDescending { it.entry.rating }
                .thenByDescending { it.entry.finished_at },
        )
    }
}

/**
 * Les réactions d'un visionnage **en mots**, dans l'ordre du catalogue (`Reactions.ordered`),
 * séparées par « · » : « J’ai adoré · À revoir ». Sans `en_salle` : l'icône ticket posée à côté de
 * la date le dit déjà (`auCinema`), l'écrire aussi en toutes lettres le dirait deux fois. Vide
 * quand il ne reste rien.
 */
fun motsReactions(item: JournalItem): String =
    Reactions.ordered(item.carnet.reactions.toSet())
        .filter { it != Reactions.EN_SALLE }
        .joinToString(" · ") { Reactions.phrase(it) }

/** Vu en salle : la réaction `en_salle` est posée — la ligne montre alors l'icône ticket. */
fun auCinema(item: JournalItem): Boolean = Reactions.EN_SALLE in item.carnet.reactions

/** La puce Date : « Date, récents d’abord », ou « Date, anciens d’abord » après un tap. */
fun libellePuceDate(filtres: FiltresFilms): String =
    if (filtres.tri == TriFilms.DATE_ASC) "Date, anciens d’abord" else "Date, récents d’abord"

/**
 * La puce Note, qui porte à la fois le tri par note et la note minimale (décision du propriétaire
 * du 24 septembre 2026) : « Note », « Note, tri », « Note · ≥ 8 » ou « Note, tri · ≥ 8 ».
 */
fun libellePuceNote(filtres: FiltresFilms): String {
    val tri = if (filtres.tri == TriFilms.NOTE_DESC) "Note, tri" else "Note"
    return filtres.noteMin?.let { "$tri · ≥ $it" } ?: tri
}

/** La puce Réaction : « Réaction », ou « Réaction · 2 » avec deux réactions cochées. */
fun libellePuceReaction(filtres: FiltresFilms): String =
    if (filtres.reactions.isEmpty()) "Réaction" else "Réaction · ${filtres.reactions.size}"

/**
 * Le compte à droite de l'en-tête, lu dans `GET /stats` (`ProfileUi`) : « 87 films · 12 cette
 * année », « 87 films » sans le chiffre de l'année, `null` tant que le total n'est pas là — rien
 * ne s'affiche alors, jamais un zéro ni « … » (même règle que le profil). « 1 film », « 0 film » :
 * le français ne met le pluriel qu'à partir de deux.
 */
fun compteEnTete(total: Int?, cetteAnnee: Int?): String? {
    if (total == null) return null
    val films = if (total >= 2) "$total films" else "$total film"
    return if (cetteAnnee == null) films else "$films · $cetteAnnee cette année"
}
