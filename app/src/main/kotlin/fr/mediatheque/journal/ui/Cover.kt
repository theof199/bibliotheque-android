package fr.mediatheque.journal.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
import coil3.compose.SubcomposeAsyncImage

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
        SubcomposeAsyncImage(
            model = url,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            // Rien au chargement d'une affiche (design §7) : le repli à l'initiale reste le geste
            // de l'absence de jaquette, pas celui d'une attente (revue de la vague finale, mineur
            // 9) — sinon chaque ligne d'une liste montre l'initiale une frame avant l'affiche.
            loading = {},
            error = { initiale() },
            modifier = modifier.size(width, height).clip(shape),
        )
    }
}
