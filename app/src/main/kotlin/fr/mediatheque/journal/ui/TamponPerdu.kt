package fr.mediatheque.journal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.Limelight

/**
 * Le tampon « PERDU » d'un film introuvable (complément du 23 septembre 2026 à l'habillage
 * « papier et pellicule », geste 17) : sépia, cercle double, rotation −12°, opacité 0,85 — posé sur
 * toute affiche d'un film marqué introuvable, à la place du simple grisage qui servait jusqu'ici.
 *
 * `echelle` (1 par défaut, statique) porte la chute à l'instant de la marque : 3 → 1 en 300 ms,
 * c'est l'appelant qui l'anime (`Animatable`) et déclenche l'haptique `Confirm` à l'arrivée — ce
 * composant ne fait que dessiner l'état qu'on lui donne, comme `Perforations`/`CadreOrne`
 * (`ui/theme/Ornements.kt`) pour le reste de l'habillage.
 *
 * `modifier` doit porter une taille (`Modifier.size(...)`) : ce tampon n'a pas de taille propre, il
 * se pose sur l'affiche qu'on lui indique, quelle que soit sa taille dans l'appli — la fiche d'un
 * suivi (30 × 45 dp) comme la filmographie d'un réalisateur, en plus grand.
 */
@Composable
fun TamponPerdu(modifier: Modifier = Modifier, echelle: Float = 1f) {
    Box(
        modifier
            .graphicsLayer {
                scaleX = echelle
                scaleY = echelle
                rotationZ = -12f
                alpha = 0.85f
            }
            .drawBehind {
                drawCircle(SepiaDuTampon, radius = size.minDimension / 2f, style = Stroke(width = size.minDimension * 0.08f))
                drawCircle(SepiaDuTampon, radius = size.minDimension * 0.38f, style = Stroke(width = size.minDimension * 0.035f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "PERDU",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Limelight, letterSpacing = 1.sp, fontSize = 9.sp),
            color = SepiaDuTampon,
            maxLines = 1,
        )
    }
}

/**
 * Le sépia du tampon — la même teinte que `TeinteSepia` des affiches non vues
 * (`ui/frise/AnneeScreen.kt`), redéfinie ici plutôt qu'importée : `ui` (racine) ne dépend d'aucun
 * écran précis, l'inverse de ce que ferait cet import.
 */
private val SepiaDuTampon = Color(0xFF3A2C1E)
