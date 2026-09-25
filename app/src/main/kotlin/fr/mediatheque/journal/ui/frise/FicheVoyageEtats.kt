package fr.mediatheque.journal.ui.frise

/**
 * La fiche d'un film du Voyage (brief du 21 septembre 2026, « l'année en étages », spec §3-§4) :
 * l'état affiché d'un film ou d'un programme, ses boutons selon cet état, le lien Plex à ouvrir, et
 * l'étiquette de la tuile en bout d'étagère. Fonctions pures, testées en JVM sans réseau ni
 * `ViewModel`, comme `VoyageEtats.kt` et `VoyageCarte.kt` à côté.
 */

/**
 * L'état affiché d'un film : le sien, sauf s'il porte des bobines (un programme), auquel cas il
 * n'est « vu » que quand **toutes** ses bobines le sont — jamais une lecture partielle. Le back
 * calcule déjà ce résultat (`GET /me/voyage/annees/{annee}`) ; l'appli le redérive pour rester
 * juste après une correction locale d'une bobine, sans attendre un rechargement.
 */
fun etatFilmVoyage(etatPropre: String, bobines: List<BobineUi>): String =
    if (bobines.isEmpty()) etatPropre else if (bobines.all { it.etat == "vu" }) "vu" else etatPropre

/**
 * Les sept boutons possibles de la fiche d'un film (spec du 19 septembre 2026, §3 ; « Mettre sur le
 * podium » ajouté le 21 septembre 2026 ; « Corriger » avec « la fiche · trois visages », 25
 * septembre 2026).
 */
enum class BoutonFicheVoyage { CORRIGER, VOIR_SUR_LE_PLEX, JE_L_AI_VU, DEMANDER, MARQUER_INTROUVABLE, RETIRER_INTROUVABLE, METTRE_SUR_LE_PODIUM }

/**
 * Les boutons à montrer, selon l'état du film et la présence d'un lien Plex :
 * - « Voir sur le Plex » dépend seulement du lien, jamais de l'état — un film déjà vu peut se
 *   revoir ;
 * - « Je l'ai vu » disparaît une fois le film vu, seul cas où il n'a plus lieu d'être ;
 * - « Demander sur Sir » n'apparaît que sur `a_demander`, jamais sur `demande` (déjà fait) ;
 * - « Introuvable » et son inverse sont mutuellement exclusifs, et aucun des deux ne sort sur un
 *   film déjà vu ;
 * - « Mettre sur le podium » (décision 3 du brief du 21 septembre 2026) n'apparaît que sur un film
 *   (ou un programme) vu — `etat` porte déjà le calcul programme-aware d'`etatFilmVoyage` ;
 * - « Corriger » (« la fiche · trois visages », 25 septembre 2026) prend la tête de la pile sur un
 *   film vu dont l'entrée du journal est connue (`entreeConnue`, sur un programme : l'entrée du
 *   long) — la place qu'occupe « Je l'ai vu » tant que le film n'est pas vu, jamais les deux
 *   ensemble. Sans entrée connue, rien à corriger : le bouton ne sort pas.
 */
fun boutonsFicheVoyage(etat: String, plexUrl: String?, entreeConnue: Boolean): List<BoutonFicheVoyage> = buildList {
    if (etat == "vu" && entreeConnue) add(BoutonFicheVoyage.CORRIGER)
    if (plexUrl != null) add(BoutonFicheVoyage.VOIR_SUR_LE_PLEX)
    if (etat != "vu") add(BoutonFicheVoyage.JE_L_AI_VU)
    if (etat == "a_demander") add(BoutonFicheVoyage.DEMANDER)
    when {
        etat == "introuvable" -> add(BoutonFicheVoyage.RETIRER_INTROUVABLE)
        etat != "vu" -> add(BoutonFicheVoyage.MARQUER_INTROUVABLE)
    }
    if (etat == "vu") add(BoutonFicheVoyage.METTRE_SUR_LE_PODIUM)
}

/** Le lien à ouvrir pour « Voir sur le Plex » : soit l'appli (`plex://`), soit le web — jamais les deux à la fois. */
sealed interface LienPlex {
    val uri: String
    data class App(override val uri: String) : LienPlex
    data class Web(override val uri: String) : LienPlex
}

/**
 * `plex_url` (d'un film ou d'une bobine) porte déjà l'un ou l'autre format, selon ce que le back a
 * trouvé — on essaie `plex://` en premier s'il commence ainsi (l'appli Plex), sinon le web tel
 * quel ; nul si le film n'est pas sur le Plex.
 */
fun lienPlexVoyage(plexUrl: String?): LienPlex? = when {
    plexUrl.isNullOrBlank() -> null
    plexUrl.startsWith("plex://") -> LienPlex.App(plexUrl)
    else -> LienPlex.Web(plexUrl)
}

/**
 * La petite étiquette d'état sous une affiche de l'étagère (spec du 19 septembre 2026, §3) — rien
 * pour « vu » (la pastille de note suffit) ni pour « à demander » (rien à dire de plus qu'une
 * affiche en sépia).
 */
fun etiquetteEtatFilm(etat: String): String? = when (etat) {
    "sur_le_plex" -> "sur ton Plex"
    "demande" -> "demandé"
    "introuvable" -> "introuvable"
    else -> null
}

/**
 * L'étiquette de la tuile en bout d'étagère : une salle qui se remplit prime sur « épuisée », qui
 * prime sur « en voir plus » — jamais les trois à la fois.
 */
fun etiquetteEtagere(epuisee: Boolean, fourneeEnCours: Boolean): String = when {
    fourneeEnCours -> "La salle se remplit…"
    epuisee -> "Salle épuisée"
    else -> "En voir plus"
}

/**
 * Un film compte pour la complétion d'une salle (habillage du 23 septembre 2026, geste 10) : vu ou
 * introuvable, les deux états terminaux — jamais « sur le Plex », « demandé » ou « à demander »,
 * qui restent tous les trois à acquérir. Même dérivation programme-aware qu'`etatFilmVoyage`.
 */
private fun FilmSalleUi.acquisPourLaSalle(): Boolean {
    val etat = etatFilmVoyage(etat, programme?.bobines ?: emptyList())
    return etat == "vu" || etat == "introuvable"
}

/**
 * Une salle vient de se boucler (geste 10, célébration « Salle bouclée ») : tous ses films acquis
 * après le geste, alors qu'elle ne l'était pas avant — jamais sur une salle déjà bouclée (pas de
 * nouvelle célébration à chaque relecture), jamais sur une salle sans film (`avant` nul, tout juste
 * apparue par une fournée, ou `apres` vide).
 */
fun salleVientDeSeBoucler(avant: SalleUi?, apres: SalleUi): Boolean {
    if (avant == null || avant.films.isEmpty() || apres.films.isEmpty()) return false
    val etaitBouclee = avant.films.all { it.acquisPourLaSalle() }
    val estBouclee = apres.films.all { it.acquisPourLaSalle() }
    return estBouclee && !etaitBouclee
}
