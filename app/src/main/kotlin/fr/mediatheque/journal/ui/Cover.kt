package fr.mediatheque.journal.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : le trio que `Modifier.voler`
 * (ci-dessous) a besoin pour faire voler une affiche d'une grille vers une fiche — la portée posée
 * par `SharedTransitionLayout` dans `Root.kt`, celle de la transition en cours (`AnimatedContent`,
 * un `AnimatedVisibilityScope`), et la clé partagée entre les deux affiches d'une même paire
 * (identifiant stable du film + rôle). Nul partout où la paire grille → fiche n'est pas raisonnable
 * (`Portrait`, `ui/suivis/`, n'en porte pas : la fiche d'un suivi n'a pas d'image à son en-tête) ou
 * pas demandée.
 */
@ExperimentalSharedTransitionApi
class AfficheVolante(val scope: SharedTransitionScope, val visibilite: AnimatedVisibilityScope, val cle: Any)

/**
 * Construit l'`AfficheVolante` d'un écran qui reçoit sa portée en paramètres optionnels — nulle si
 * l'un des deux manque (l'écran composé seul, hors de `Root.kt`, par exemple un aperçu) : évite de
 * répéter le `if` aux quatre paires du geste 8.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun afficheVolante(scope: SharedTransitionScope?, visibilite: AnimatedVisibilityScope?, cle: Any): AfficheVolante? =
    if (scope != null && visibilite != null) AfficheVolante(scope, visibilite, cle) else null

/**
 * Pose le mouvement partagé sur un modificateur ordinaire plutôt que sur `Cover` lui-même (geste 8) :
 * `Cover` sert des dizaines d'affiches sans rapport avec les quatre paires du geste, et lui donner un
 * paramètre de type expérimental aurait forcé un `@OptIn` sur chacun de ses appelants. `null` rend
 * le modificateur tel quel.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.voler(volante: AfficheVolante?): Modifier {
    if (volante == null) return this
    return with(volante.scope) { this@voler.sharedElement(rememberSharedContentState(volante.cle), volante.visibilite) }
}

/**
 * Décision 2 de la tâche 5 : un `contentDescription` toujours posé, jaquette
 * ou non — le brief le laissait vide sans jaquette, corrigé ici avec le titre
 * (design §8 : « une affiche dit “Affiche de {titre}” »), pour que l'initiale
 * de remplacement se lise aussi au lecteur d'écran.
 *
 * `colorFilter` (brief du 21 septembre 2026, l'étagère d'une salle du Voyage) : nul partout
 * ailleurs, il teinte l'affiche d'un film pas encore vu en sépia sans dupliquer ce composant.
 */
@Composable
fun Cover(url: String?, title: String, width: Dp, height: Dp, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val shape = MaterialTheme.shapes.small
    val description = "Affiche de $title"
    val initiale: @Composable () -> Unit = {
        Box(
            Modifier.size(width, height).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (url == null) {
        Box(modifier.clearAndSetSemantics { contentDescription = description }) { initiale() }
    } else {
        val contexte = LocalContext.current
        SubcomposeAsyncImage(
            // `crossfade(200)` (peaufinage du 23 septembre 2026, geste 9) : l'affiche apparaît en
            // fondu plutôt que d'un coup une fois chargée.
            model = ImageRequest.Builder(contexte).data(url).crossfade(200).build(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            // Rien *pendant* le chargement (design §7, geste 9 du peaufinage du 23 septembre 2026
            // corrige ce commentaire qui disait le contraire de ce que fait `crossfade` ci-dessus) :
            // le repli à l'initiale reste le geste de l'absence de jaquette, pas celui d'une attente
            // (revue de la vague finale, mineur 9) — aucun indicateur, aucun repli n'apparaît tant
            // que la requête est en vol, seule l'affiche qui arrive se fond dedans.
            loading = {},
            error = { initiale() },
            modifier = modifier.size(width, height).clip(shape),
        )
    }
}

/**
 * Le badge « sur le Plex » (revue du 24 septembre 2026, point 2) : une icône Tabler dans un petit
 * cercle sombre, coin haut droit d'une affiche, jamais posée sur du texte. Avant cette revue,
 * seule `RealisateurScreen` en posait un (une icône « cloud » nue, sans fond) ; ce composant le
 * remplace et devient le seul badge Plex de l'application, pour que « le même partout » (le brief)
 * tienne vraiment. `Modifier.align(Alignment.TopEnd)` reste à la charge de l'appelant : ce composant
 * ne connaît pas le `Box` qui le pose.
 *
 * La petite caméra rouge qui flottait sur certaines captures de l'accueil et de la Frise (points
 * de repère de la revue) n'est pas de ce badge, ni d'aucun élément dessiné par l'application : elle
 * n'apparaît nulle part dans le code (aucune icône, aucun `Canvas`, aucune image à cette échelle et
 * cette teinte), à des positions qui ne correspondent à aucune affiche ni aucun texte communs aux
 * deux captures — seulement à une même position d'écran d'une capture à l'autre. Tout indique un
 * élément externe à l'appli (bulle flottante d'un outil de capture/mirroring), pas un badge Plex ;
 * rien à retirer côté application.
 */
@Composable
fun PlexBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .size(22.dp)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconeTabler(
            "cloud",
            "Sur le Plex",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(13.dp),
        )
    }
}
