package fr.mediatheque.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * Le fond héros d'une fiche (brief du 23 septembre 2026 soir, geste 4 ; dégradé resserré à la
 * revue du 24 septembre 2026, point 3) : l'affiche déjà reçue, étirée en fond derrière l'en-tête,
 * floutée et assombrie sous un dégradé vers le fond de page — la fiche d'un film (journal,
 * `FormScreen`, et Voyage, `FicheVoyageScreen`) et la fiche d'année (`AnneeScreen`, l'affiche du
 * n°1 du podium). Le contenu posé par-dessus ne change pas.
 *
 * Le dégradé atteint le fond plein dès [FIN_FONDU] de la hauteur, pas seulement à la toute
 * dernière ligne de pixels : avant la revue du 24 septembre 2026, il courait sur la hauteur
 * entière, si bien que le titre de l'année, sa profondeur et son monde — posés bien avant le bas
 * de la boîte — se lisaient encore sur l'image (constat de la revue, point 3 : « aucun texte de
 * liste ne se lit sur l'image »). Resserré, le dégradé fond l'image dans le fond uni juste sous
 * l'en-tête (le bouton retour et le tout début du titre), et tout le texte qui suit repose sur du
 * fond plein — sans réduire `hauteur`, pour garder l'affiche visible en arrière-plan du haut de
 * l'écran.
 *
 * API 26 : `Modifier.blur` exige l'API 31, comme ailleurs dans l'appli (`AnneeDansLaBoiteCalque`).
 * Le flou vient donc d'un sous-échantillonnage plutôt que d'un flou natif : une seconde requête
 * Coil de la même URL, réduite à 24 px (`size(24)`), puis étirée avec le filtrage bilinéaire par
 * défaut d'`AsyncImage` — un vrai flou, tiré des quelques pixels qui restent, pas un simple voile
 * posé sur l'affiche nette.
 *
 * `null` (pas encore d'affiche) ne pose rien : ni fond, ni espace réservé, le reste de l'écran
 * n'en sait rien.
 */
private const val FIN_FONDU = 0.4f

@Composable
fun FondHeros(url: String?, hauteur: Dp, modifier: Modifier = Modifier, fond: Color = MaterialTheme.colorScheme.background) {
    if (url == null) return
    val contexte = LocalContext.current
    Box(modifier.fillMaxWidth().height(hauteur)) {
        AsyncImage(
            model = ImageRequest.Builder(contexte).data(url).size(24).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // L'assombrissement, uniforme, puis le dégradé qui fond dans le reste de la page — les
        // deux ensemble évitent qu'un bord net trahisse le montage (design §2 : les erreurs de
        // contraste se voient d'abord sur un fond qui bouge).
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, FIN_FONDU to fond, 1f to fond),
            ),
        )
    }
}
