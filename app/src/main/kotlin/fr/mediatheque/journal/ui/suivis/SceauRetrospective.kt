package fr.mediatheque.journal.ui.suivis

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import fr.mediatheque.journal.ui.SceauOr

/**
 * Le sceau or « rétrospective complète » (geste 20 du complément du 23 septembre 2026 à
 * l'habillage) : `tabler:award` (geste 2 du brief du 23 septembre 2026 soir, qui remplace le `✦`
 * en Fraunces d'origine), posé sur le portrait — 30 dp sur la page d'un réalisateur, 22 dp sur une
 * carte de la liste des Suivis.
 *
 * Depuis « Au ciné · le guichet » (25 septembre 2026), un simple appel de `SceauOr` (`ui/SceauOr.kt`),
 * qui porte la forme, le liseré, l'animation et leurs raisons : rien ne change pour les Suivis.
 */
@Composable
fun SceauRetrospective(
    taille: Dp,
    anime: Boolean,
    modifier: Modifier = Modifier,
    fond: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    SceauOr(taille, anime, "award", "Rétrospective complète", modifier, fond)
}
