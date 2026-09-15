package fr.mediatheque.journal.ui.suivis

/**
 * Ce qui distingue un réalisateur d'une saga pour tout le code partagé de ce
 * paquet (brief « les sagas », 15 septembre 2026, jumelle du brief « les
 * réalisateurs ») : quatre libellés d'écran. Les cinq appels réseau, eux, se
 * dispatchent dans `SuivisViewModel` — un `when (source)` à chaque fois, et
 * un seul.
 *
 * L'onglet « Réalisateurs » devient « Suivis » (décision du propriétaire du
 * 15 septembre 2026) : deux segments dans un `SingleChoiceSegmentedButtonRow`
 * en tête de `SuivisScreen`, mémorisés pour la session dans
 * `SuivisUi.source`.
 */
enum class SourceSuivi(
    val titre: String,
    val libelleAjouter: String,
    val placeholderRecherche: String,
    val libelleVide: String,
) {
    REALISATEURS(
        titre = "Réalisateurs",
        libelleAjouter = "Ajouter un réalisateur",
        placeholderRecherche = "Un nom de réalisateur",
        libelleVide = "Ajoute un réalisateur avec +",
    ),
    SAGAS(
        titre = "Sagas",
        libelleAjouter = "Ajouter une saga",
        placeholderRecherche = "Un nom de saga",
        libelleVide = "Ajoute une saga avec +",
    ),
}
