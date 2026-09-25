package fr.mediatheque.journal.ui.fiche

import fr.mediatheque.journal.ui.frise.mondeDe

/**
 * Les petites règles des trois fiches d'un film (« la fiche · trois visages », reprise validée du
 * 25 septembre 2026) : la durée, la ligne « année · durée », l'étiquette de décennie et le léger
 * désordre des puces de réaction. Fonctions pures, testées en JVM sans Compose, pour que la fiche
 * d'une entrée, celle du Voyage et la fiche simple les lisent au même endroit plutôt que d'en
 * garder chacune leur copie.
 */

/**
 * `47` → « 47 min », `107` → « 1 h 47 », `123` → « 2 h 03 » : les minutes toujours sur deux
 * chiffres passé l'heure, comme sur un billet — jamais « 2 h 3 ».
 */
fun formatDuree(min: Int): String {
    if (min < 60) return "$min min"
    val minutes = (min % 60).toString().padStart(2, '0')
    return "${min / 60} h $minutes"
}

/**
 * La ligne qui suit le réalisateur : « 2019 », « 1912 · 2 h 03 », « 1 h 47 » seule quand l'année
 * manque, nulle sans rien — la fiche n'écrit alors pas le « · » qui la précède. La durée n'existe
 * que sur un programme du Voyage (`ProgrammeUi.dureeMin`) : ailleurs, l'année seule.
 */
fun anneeEtDuree(annee: Int?, dureeMin: Int?): String? =
    listOfNotNull(annee?.toString(), dureeMin?.let(::formatDuree)).joinToString(" · ").ifEmpty { null }

/**
 * « Années 1990 · Le blockbuster » : l'étiquette d'une fiche hors Voyage, nouvelle avec la
 * reprise. Le nom vient du monde de la décennie (`mondeDe`, la table §2 du design) ; le nombre,
 * lui, vient de l'année elle-même et pas de `Monde.decennie` — un film de 2031 rejoint le dernier
 * monde (« Aujourd’hui ») mais reste un film des années 2030, et un film de 1888 un film des
 * années 1880. Nulle sans année : pas d'étiquette plutôt qu'une décennie inventée.
 */
fun etiquetteDecennie(annee: Int?): String? {
    if (annee == null) return null
    return "Années ${annee.floorDiv(10) * 10} · ${mondeDe(annee).nom}"
}

/** Les inclinaisons des puces de réaction, en degrés, reprises en boucle. */
private val INCLINAISONS_PUCE = listOf(-3f, 2f, -1.5f)

/** Les décalages verticaux des mêmes puces, en dp, sur le même cycle de trois. */
private val DECALAGES_PUCE = listOf(0, 2, -1)

/**
 * Le seul « chaos » voulu de la reprise (avec le chiffre de la note) : chaque puce de réaction
 * légèrement tournée, sur un cycle fixe plutôt qu'au hasard — la même fiche se redessine à
 * l'identique à chaque recomposition, et deux fiches voisines ne tremblent pas différemment.
 */
fun inclinaisonPuce(index: Int): Float = INCLINAISONS_PUCE[index.mod(INCLINAISONS_PUCE.size)]

/** Jumeau d'[inclinaisonPuce] : le décalage vertical de la puce, quelques dp au plus. */
fun decalagePuce(index: Int): Int = DECALAGES_PUCE[index.mod(DECALAGES_PUCE.size)]
