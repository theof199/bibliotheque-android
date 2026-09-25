package fr.mediatheque.journal.ui

/**
 * Les petites règles de la feuille de lecture (« la feuille de lecture · le chroniqueur »,
 * planche de Léon du 25 septembre 2026) : couper le texte lu en paragraphes et régler la machine
 * à écrire qui le pose. Fonctions pures, testées en JVM sans Compose ; `FeuilleDeLecture` ne fait
 * que les lire au rythme de ses `delay`.
 */

/**
 * Les premiers caractères d'un texte arrivé, tapés lettre à lettre : à peu près les deux
 * premières lignes de la feuille, assez pour qu'on voie le chroniqueur écrire, pas assez pour
 * faire attendre la lecture — le reste se pose d'un coup.
 */
const val PREMIERS_CARACTERES_TAPES = 90

/** Une lettre de « Le chroniqueur écrit… » toutes les 90 ms : une frappe lente, qui cherche ses mots. */
const val CADENCE_ATTENTE_MS = 90L

/** Une lettre du texte arrivé toutes les 20 ms : la frappe s'est lancée, elle ne cherche plus. */
const val CADENCE_TEXTE_MS = 20L

/** Le temps de laisser lire la phrase d'attente entière avant de l'effacer et de la retaper. */
const val PAUSE_ATTENTE_MS = 1_200L

/** Le curseur s'allume et s'éteint toutes les 500 ms, comme un curseur de texte ordinaire. */
const val CLIGNOTEMENT_MS = 500L

/** Ce que la machine tape tant que le texte n'est pas là — la bobine Lottie qu'elle remplace ne disait rien. */
const val PHRASE_ATTENTE = "Le chroniqueur écrit…"

/**
 * Les paragraphes d'un texte : coupés sur une ou plusieurs lignes vides, débarrassés de leurs
 * espaces de bord, les vides retirés. Un texte sans ligne vide reste un seul paragraphe — les
 * simples retours à la ligne restent dans le paragraphe, le chroniqueur ne s'en sert pas pour
 * séparer ses idées.
 */
fun paragraphes(texte: String): List<String> =
    texte.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Combien de caractères taper lettre à lettre : `limite`, prolongée jusqu'à la fin du mot qu'elle
 * couperait — la frappe s'arrête entre deux mots, jamais au milieu d'un, sinon le reste du mot
 * apparaîtrait d'un coup collé à sa moitié tapée. Jamais plus que le texte.
 */
fun partieTapee(texte: String, limite: Int = PREMIERS_CARACTERES_TAPES): Int {
    if (texte.length <= limite) return texte.length
    return (limite until texte.length).firstOrNull { texte[it].isWhitespace() } ?: texte.length
}

/** Les `lettres` premiers caractères du texte, sans jamais déborder dans un sens ni dans l'autre. */
fun tapes(texte: String, lettres: Int): String = texte.take(lettres.coerceAtLeast(0))

