package fr.mediatheque.journal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Le tampon « PERDU » d'un film introuvable (complément du 23 septembre 2026 à l'habillage
 * « papier et pellicule », geste 17) : sépia, cercle double, rotation −12°, opacité 0,85 — posé sur
 * toute affiche d'un film marqué introuvable, à la place du simple grisage qui servait jusqu'ici.
 * Depuis le geste 3 du brief du 23 septembre 2026 (soir), le dessin lui-même vit dans `Embleme`
 * (`Emblemes.kt`, `EmblemeType.PERDU`) : ce composant ne porte plus que l'animation (rotation,
 * échelle, opacité), substitut vectoriel ou WebP du propriétaire une fois qu'il existe — un seul
 * endroit à changer ce jour-là.
 *
 * `echelle` (1 par défaut, statique) porte la chute à l'instant de la marque : 3 → 1 en 300 ms,
 * c'est l'appelant qui l'anime (`Animatable`) et déclenche l'haptique `Confirm` à l'arrivée — ce
 * composant ne fait que dessiner l'état qu'on lui donne, comme `Perforations`/`CadreOrne`
 * (`ui/theme/Ornements.kt`) pour le reste de l'habillage.
 *
 * `taille` : ce tampon n'a pas de taille propre, il se pose sur l'affiche qu'on lui indique, quelle
 * que soit sa taille dans l'appli — la fiche d'un suivi (40 × 60 dp) comme la filmographie d'un
 * réalisateur, en plus grand. `modifier` ne sert plus qu'au positionnement (`Modifier.align(...)`).
 */
@Composable
fun TamponPerdu(taille: Dp, modifier: Modifier = Modifier, echelle: Float = 1f) {
    Box(
        modifier.graphicsLayer {
            scaleX = echelle
            scaleY = echelle
            rotationZ = -12f
            alpha = 0.85f
        },
    ) {
        Embleme(EmblemeType.PERDU, taille)
    }
}
