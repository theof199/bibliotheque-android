package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.JournalItem

/**
 * La carte du Voyage : les règles qui décident ce que l'écran montre — la récompense d'une année,
 * l'avancée de la frontière, les tampons du passeport. Fonctions pures, testées en JVM sans réseau
 * ni `ViewModel`, comme `VoyageEtats.kt` à côté.
 *
 * Brief du 21 septembre 2026 (« l'année en étages ») : `GET /me/voyage` ne sert plus ni essentiels
 * ni récompense par année (spec du 19 septembre 2026, §7 : « pour la carte, la liste des années
 * avec statut, profondeur, récompense » — la récompense n'arrive qu'à l'étape 5, §8). `Recompense`,
 * `recompense` et `phraseRecompenses` restent, pures et testées, pour ce jour-là : personne ne les
 * appelle encore avec autre chose qu'une liste vide. Jumeau pour le passeport : une décennie ne se
 * boucle qu'avec un Ours par année et le ticket suivant utilisé (spec §6, étape 3) — `tamponsPasseport`
 * ne peut donc encore rien tamponner.
 */

/**
 * La récompense d'une année faite (étape 5, à venir) — les trois festivals du brief du
 * 16 septembre 2026 : tout vu → la Palme, un ou deux introuvables → le Lion, au-delà → l'Ours.
 */
enum class Recompense(val singulier: String, val pluriel: String) {
    PALME("Palme", "Palmes"),
    LION("Lion", "Lions"),
    OURS("Ours", "Ours"),
}

fun recompense(essentielsTotal: Int, essentielsFaits: Int): Recompense {
    val manquants = (essentielsTotal - essentielsFaits).coerceAtLeast(0)
    return when {
        manquants == 0 -> Recompense.PALME
        manquants <= 2 -> Recompense.LION
        else -> Recompense.OURS
    }
}

/** « 3 Palmes · 1 Lion » : le compte du HUD, dans l'ordre Palme, Lion, Ours, sans les zéros — vide tant que rien n'est décerné. */
fun phraseRecompenses(recompenses: List<Recompense>): String {
    val comptes = recompenses.groupingBy { it }.eachCount()
    return Recompense.entries
        .mapNotNull { r -> comptes[r]?.takeIf { it > 0 }?.let { n -> "$n ${if (n > 1) r.pluriel else r.singulier}" } }
        .joinToString(" · ")
}

/**
 * Ce qu'une frontière qui avance vient de boucler : l'année quittée, et la décennie quittée si la
 * frontière a changé de monde.
 *
 * `avant` est l'année en cours mémorisée, `apres` celle que `GET /me/voyage` vient de rendre.
 * C'est `avant` qui est bouclée, pas `apres` : l'année en cours est celle qu'on quitte, pas celle
 * qu'on rejoint. Une décennie se boucle quand l'année quittée et la nouvelle ne sont plus dans le
 * même monde (1899 → 1900 boucle les années 1890).
 */
data class FrontiereAvancee(val anneeBouclee: Int, val decennieBouclee: Int?)

fun detecterFrontiereAvancee(avant: Int?, apres: Int?): FrontiereAvancee? {
    if (avant == null || apres == null || apres <= avant) return null
    val decennieAvant = mondeDe(avant).decennie
    val decennieApres = mondeDe(apres).decennie
    return FrontiereAvancee(
        anneeBouclee = avant,
        decennieBouclee = decennieAvant.takeIf { it != decennieApres },
    )
}

/** Un film du générique de fin : son titre et son année de sortie. */
data class FilmGenerique(val titre: String, val annee: Int)

/**
 * Un tampon du passeport, qui porte aussi tout ce que son générique affiche : l'écran
 * `Screen.Generique` ne recharge donc rien, il relit ce tampon.
 */
data class TamponDecennie(
    val decennie: Int,
    val titreVoyageur: String,
    val premiereEntree: String?,
    val derniereEntree: String?,
    val films: List<FilmGenerique>,
)

/**
 * Les tampons du passeport : vide à cette étape (brief du 21 septembre 2026). Une décennie ne se
 * boucle qu'avec un Ours par année et le ticket de la décennie suivante utilisé (spec du
 * 19 septembre 2026, §6) — ni l'un ni l'autre n'existe encore côté back (étapes 3 et 5). La
 * signature reste celle de l'étape à venir : `voyage` et `journal` ne sont pas encore lus.
 */
@Suppress("UNUSED_PARAMETER")
fun tamponsPasseport(voyage: VoyageUi, journal: List<JournalItem>): List<TamponDecennie> = emptyList()
