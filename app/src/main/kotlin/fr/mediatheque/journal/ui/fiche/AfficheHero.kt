package fr.mediatheque.journal.ui.fiche

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.voler

/**
 * La hauteur de l'affiche en héros : `EnTeteFiche` pose son titre à partir d'elle, pour qu'il morde
 * sur le bas de l'image.
 */
val HAUTEUR_AFFICHE_HERO = 500.dp

/** Là où l'image commence à s'éteindre dans le fond de la fiche, en fraction de sa hauteur. */
private const val DEBUT_FONDU = 0.55f

/**
 * L'affiche en héros (« la fiche · trois visages », reprise validée du 25 septembre 2026, après le
 * retour du propriétaire : « trop de vide, une plus grande affiche ») : pleine largeur, 500 dp,
 * coupée par le bas et jamais par le haut (`TopCenter`) — le haut d'une affiche porte le plus
 * souvent le visage ou le titre. Elle s'éteint dans `fond` sur son dernier tiers, là où le titre de
 * la fiche vient se poser. Elle remplace, sur les trois fiches d'un film, le fond héros flouté et
 * l'affiche 96 × 144 d'avant ; `FondHeros` reste au formulaire.
 *
 * `volante` porte le vol de l'affiche (geste 8 du peaufinage du 23 septembre 2026) : posé sur
 * l'image seule.
 *
 * Correctif du 28 septembre 2026 (le fondu apparaissait en retard, une fois le vol terminé,
 * laissant un bord net couper la page en deux pendant le vol) : `SharedTransitionLayout` sort le
 * nœud volant de la mise en page pendant la transition pour le dessiner dans une calque au-dessus
 * de l'écran — un `Box` frère posé à côté ne le rejoint donc qu'à l'atterrissage. Le fondu se
 * dessine maintenant sur le nœud volant lui-même (`fonduDeBas`, juste au-dessous de `voler` dans
 * la même chaîne de modificateurs), sur les deux branches (l'image et son repli sans URL) : il vole
 * avec l'affiche du premier au dernier instant.
 *
 * Sans URL, le bloc `surfaceContainerHigh` à l'initiale du titre — le repli de `Cover`, à la taille
 * du héros.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AfficheHero(url: String?, titre: String, fond: Color, volante: AfficheVolante?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(HAUTEUR_AFFICHE_HERO)) {
        if (url == null) {
            InitialeHero(titre, Modifier.voler(volante).fonduDeBas(fond).matchParentSize())
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(200).build(),
                contentDescription = "Affiche de $titre",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.voler(volante).fonduDeBas(fond).matchParentSize(),
            )
        }
    }
}

/**
 * Le fondu vers `fond` du dernier tiers de l'affiche (`DEBUT_FONDU`), posé directement sur le nœud
 * volant (correctif du 28 septembre 2026, ci-dessus) plutôt que sur un `Box` frère — dessiné après
 * le contenu (`drawContent()` d'abord), donc toujours par-dessus l'image ou l'initiale de repli.
 */
private fun Modifier.fonduDeBas(fond: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0f to Color.Transparent, DEBUT_FONDU to Color.Transparent, 1f to fond))
}

/**
 * Le repli sans affiche, jumeau de l'initiale privée de `Cover` à une autre échelle : `Cover` en
 * fixe la taille en `Dp`, le héros prend toute la largeur. La description reste « Affiche de
 * {titre} », pour que le lecteur d'écran lise la même chose avec ou sans image.
 */
@Composable
private fun InitialeHero(titre: String, modifier: Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = "Affiche de $titre" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            titre.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
