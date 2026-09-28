package fr.mediatheque.journal.ui.frise

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * L'ambiance en boucle d'un monde (delta de Léon du 25 septembre 2026, « pavillon par pavillon »,
 * §G ; machinerie de la livraison 3) : une touche animée par-dessus l'image de fond, entre elle et
 * la route (§B, ordre de z de `SectionMonde.kt`) — jamais sur les pavillons, jamais sur le carton,
 * qui joue sa propre entrée (`CartonTitre.kt`).
 *
 * `active` (`ambianceActive`, `VoyageCarte.kt`, pure) dit si la section est visible et si les
 * animations ne sont pas réduites : chaque branche ajoutée ici ne doit lancer sa
 * `rememberInfiniteTransition` que si `active` est vrai — jumeau du `if (defilement)` de
 * `Perforations` (`theme/Ornements.kt`) — et ne rien dessiner sinon : contrairement au carton, ces
 * boucles n'ont pas d'« état de repos » à montrer — une bande qui glisse, arrêtée, n'ajoute rien à
 * l'image qu'elle décore.
 *
 * Encore vide à cette machinerie : aucun monde n'a d'ambiance dessinée pour l'instant — 1930, 1940
 * et 1950 rejoignent le `when` ci-dessous dans le commit suivant (§G) ; 1960 → 2020 (livraison 4)
 * et 1890 → 1920 (livraison 5) n'auront eux aussi qu'à ajouter leur propre branche ici, `k` et
 * `active` déjà en place — rien d'autre à reprendre dans `SectionMonde.kt`.
 */
@Composable
fun AmbianceDeMonde(monde: Monde, active: Boolean, k: Float, modifier: Modifier = Modifier) {
    when (monde.decennie) {
        else -> Unit
    }
}
