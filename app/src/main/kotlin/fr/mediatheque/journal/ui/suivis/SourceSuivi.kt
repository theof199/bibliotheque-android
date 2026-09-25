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
    /** L'en-tête de la section du bas, celles dont tout est vu ou introuvable. */
    val titreComplets: String,
    /** Accordé au nom de la puce : « bouclée le … » (une rétrospective), « bouclé le … » (un cycle). */
    val participeBoucle: String,
) {
    REALISATEURS(
        titre = "Réalisateurs",
        libelleAjouter = "Ajouter un réalisateur",
        placeholderRecherche = "Un nom de réalisateur",
        libelleVide = "Ajoute un réalisateur avec +",
        titreComplets = "Rétrospectives complètes",
        participeBoucle = "bouclée",
    ),
    SAGAS(
        titre = "Sagas",
        libelleAjouter = "Ajouter une saga",
        placeholderRecherche = "Un nom de saga",
        libelleVide = "Ajoute une saga avec +",
        titreComplets = "Cycles complets",
        participeBoucle = "bouclé",
    ),
}
