package fr.mediatheque.journal.ui.frise

import androidx.compose.ui.graphics.Color

/**
 * Les mondes du Voyage (brief du 16 septembre 2026, phase 2 « la carte ») : une décennie, un
 * monde — un fond, un nom, un sous-titre, une couleur d'accent, un motif et un titre de voyageur.
 *
 * **Liste fixe, décision de design, kitsch assumé** : elle ne se dérive de rien et ne se charge
 * de nulle part. Les quatorze entrées couvrent 1890 → 2020 ; au-delà, `mondeDe` retombe sur la
 * dernière, et avant 1890 sur « Les origines » — le Voyage part de 1895, mais un film du journal
 * peut porter une année plus ancienne.
 *
 * Trois couleurs de fond viennent du brief mot pour mot (1890 sépia `#2A2118`, 1900 `#1C1C22`,
 * 1960 `#0E1A2B`) ; les onze autres sont choisies dans le même registre — sombres, l'appli
 * restant noire (design §1).
 *
 * L'accent d'un monde ne change **pas la police** : Manrope est la seule du dépôt (design §3).
 * Un monde se signe par sa couleur d'accent, la casse et l'espacement de son titre, et une
 * lettrine — jamais par une famille nouvelle.
 */

/**
 * Le motif de fond d'une bande de monde. **Simplification assumée** (permise par le brief) :
 * six motifs génériques réutilisés d'un monde à l'autre plutôt que quatorze motifs sur mesure —
 * quelques lignes, points ou étoiles au `Canvas`, jamais une image bitmap.
 */
enum class MotifMonde { CERCLE, ETOILES, DIAGONALES, RAYURES, GRAIN, BANDES }

data class Monde(
    val decennie: Int,
    val nom: String,
    val sousTitre: String,
    val fond: Color,
    val accent: Color,
    val motif: MotifMonde,
    /** Le titre gagné en bouclant la décennie, en lettres lumineuses sur la marquise (brief, item 4). */
    val titreVoyageur: String,
)

val MONDES: List<Monde> = listOf(
    Monde(1890, "Les origines", "la manivelle", Color(0xFF2A2118), Color(0xFFD9B382), MotifMonde.CERCLE, "Spectateur des origines"),
    Monde(1900, "La féerie", "Méliès et les forains", Color(0xFF1C1C22), Color(0xFFE7D8A8), MotifMonde.ETOILES, "Compagnon de Méliès"),
    Monde(1910, "Le muet", "les grands récits", Color(0xFF201A14), Color(0xFFD8C49A), MotifMonde.BANDES, "Témoin du muet"),
    Monde(1920, "L’expressionnisme", "ombres obliques", Color(0xFF111114), Color(0xFFBFC7D1), MotifMonde.DIAGONALES, "Enfant de l’expressionnisme"),
    Monde(1930, "Le parlant", "Hollywood se met à causer", Color(0xFF16171C), Color(0xFFE3C77B), MotifMonde.BANDES, "Témoin du parlant"),
    Monde(1940, "Le noir", "argentique et imperméables", Color(0xFF0F1012), Color(0xFFA9B0B8), MotifMonde.GRAIN, "Détective du noir"),
    Monde(1950, "Le Technicolor", "large et saturé", Color(0xFF1B1026), Color(0xFFF2A03D), MotifMonde.BANDES, "Enfant du Technicolor"),
    Monde(1960, "Les nouvelles vagues", "caméra à l’épaule", Color(0xFF0E1A2B), Color(0xFF7FB3D5), MotifMonde.DIAGONALES, "Vaguiste"),
    Monde(1970, "Le Nouvel Hollywood", "grain et pellicule rayée", Color(0xFF1A1208), Color(0xFFD98E36), MotifMonde.RAYURES, "Nouvel Hollywoodien"),
    Monde(1980, "Le néon", "VHS et synthés", Color(0xFF120A22), Color(0xFFFF4FD8), MotifMonde.RAYURES, "Néon kid"),
    Monde(1990, "Le blockbuster", "générique en lettres d’acier", Color(0xFF14181C), Color(0xFFB8C4CE), MotifMonde.BANDES, "Blockbusteur"),
    Monde(2000, "Le numérique", "propre et net", Color(0xFF0B1218), Color(0xFF56C4E0), MotifMonde.GRAIN, "Numérique"),
    Monde(2010, "Le streaming", "tout, tout de suite", Color(0xFF101014), Color(0xFFE5534B), MotifMonde.RAYURES, "Streameur"),
    Monde(2020, "Aujourd’hui", "le voyage continue", Color(0xFF0A0A0A), Color(0xFFFF6B57), MotifMonde.CERCLE, "Contemporain"),
)

/**
 * Le monde d'une année. 1899 appartient encore aux origines, 1900 ouvre la féerie : la bascule se
 * fait au millésime rond, jamais à `annee - 1`. Une année antérieure à 1890 (un film du journal
 * daté de 1888) rejoint les origines plutôt que de sortir de la liste, et une année postérieure
 * au dernier monde rejoint le dernier.
 */
fun mondeDe(annee: Int): Monde {
    val index = (annee - MONDES.first().decennie).floorDiv(10)
    return MONDES[index.coerceIn(0, MONDES.lastIndex)]
}

/** Le monde d'une décennie déjà arrondie — `mondeDe` sait le faire, ce nom dit juste l'intention. */
fun mondeDeLaDecennie(decennie: Int): Monde = mondeDe(decennie)

/**
 * Le numéro de chapitre du HUD, en chiffres romains — « Chapitre I · Les origines ». `index` est
 * la place du monde dans `MONDES` (0 pour 1890), le chapitre commençant à I et non à zéro.
 *
 * L'algorithme est le général, pas une table des quatorze valeurs : une quinzième décennie
 * (2030) donnera « XV » sans qu'on y retouche.
 */
fun chapitreRomain(index: Int): String {
    val valeurs = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
    var reste = index + 1
    if (reste <= 0) return ""
    return buildString {
        valeurs.forEach { (valeur, lettres) ->
            while (reste >= valeur) {
                append(lettres)
                reste -= valeur
            }
        }
    }
}

/** Le chapitre d'une année : « Chapitre I · Les origines ». */
fun chapitreDe(annee: Int): String {
    val monde = mondeDe(annee)
    return "Chapitre ${chapitreRomain(MONDES.indexOf(monde))} · ${monde.nom}"
}
