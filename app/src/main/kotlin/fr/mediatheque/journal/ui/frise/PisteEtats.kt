package fr.mediatheque.journal.ui.frise

/**
 * Les pistes de salles (brief du 22 septembre 2026, « les pistes ») : des salles que le
 * chroniqueur propose sans jamais les ouvrir, en pastilles au-dessus du champ de la feuille
 * « Nouvelle salle » (`NouvelleSalleSheet`, `AnneeScreen.kt`). Fonctions pures, testées en JVM,
 * comme `PodiumEtats.kt`/`SeanceEtats.kt`/`CarnetEtats.kt` à côté.
 */

/** Une piste de salle, telle qu'`AnneeViewModel` la range depuis `AnneeVoyageDetailResponse.pistes`. */
data class PisteUi(val nom: String, val raison: String)

/**
 * Les pistes encore visibles après qu'une salle a été ouverte sur l'une d'elles (décision 2 du
 * brief) : celle-là seule disparaît — retirée localement dès le `202`, avant même que le
 * chroniqueur ait répondu, la fiche se relisant ensuite comme d'habitude (le back l'a retirée
 * aussi). `pisteUtilisee` nulle (le champ a été rempli à la main, sans toucher de pastille) laisse
 * la liste intacte.
 */
fun pistesApresUsage(pistes: List<PisteUi>, pisteUtilisee: String?): List<PisteUi> =
    if (pisteUtilisee == null) pistes else pistes.filterNot { it.nom == pisteUtilisee }

/**
 * La visibilité du bouton « D'autres pistes » (décision 3 du brief) : seulement quand il ne reste
 * aucune piste — dont 1895 et 1896, ouvertes sans aucune piste dès le départ.
 */
fun autresPistesVisible(pistes: List<PisteUi>): Boolean = pistes.isEmpty()

/**
 * L'état local de la feuille « Nouvelle salle » (décision 1 du brief) : le texte du champ, et le
 * nom de la dernière pastille touchée — `null` tant qu'aucune ne l'a été.
 */
data class NouvelleSalleEtat(val texte: String = "", val pisteTouchee: String? = null)

/** Un événement de la feuille « Nouvelle salle » : toucher une pastille, ou écrire dans le champ. */
sealed interface NouvelleSalleEvenement {
    data class ToucherPiste(val piste: PisteUi) : NouvelleSalleEvenement
    data class Ecrire(val texte: String) : NouvelleSalleEvenement
}

/**
 * L'état suivant de la feuille « Nouvelle salle » (décision 1 du brief) : toucher une pastille
 * remplit le champ avec son `nom` et la retient comme dernière touchée ; écrire dans le champ,
 * même après avoir touché une pastille, change le texte affiché mais **ne défait jamais**
 * `pisteTouchee` — c'est elle que le bouton d'envoi reprend comme `piste`, quel que soit le texte
 * au moment de l'envoi.
 */
fun nouvelleSalleSuivant(etat: NouvelleSalleEtat, evenement: NouvelleSalleEvenement): NouvelleSalleEtat = when (evenement) {
    is NouvelleSalleEvenement.ToucherPiste -> etat.copy(texte = evenement.piste.nom, pisteTouchee = evenement.piste.nom)
    is NouvelleSalleEvenement.Ecrire -> etat.copy(texte = evenement.texte)
}
