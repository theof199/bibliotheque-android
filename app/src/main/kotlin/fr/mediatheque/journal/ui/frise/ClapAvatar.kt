package fr.mediatheque.journal.ui.frise

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import fr.mediatheque.journal.R

/**
 * L'avatar du Voyage (brief du 16 septembre 2026, item 2) : le clap de l'icône, posé à côté du
 * photogramme de l'année en cours. Quand la frontière avance, il se déplace avec elle et
 * **claque**.
 *
 * C'est le drawable de l'icône lui-même (`ic_launcher_animated.xml`, qui anime le groupe « volet »
 * de `ic_launcher_foreground.xml` par `animator/ic_launcher_volet_claque.xml`) : aucune géométrie
 * recopiée, aucun `pathData` en double à garder synchronisé — la même règle que l'écran de
 * démarrage (design §10). D'où l'`AndroidView` plutôt qu'un `Canvas` : Compose ne sait pas, sans
 * dépendance ajoutée, jouer un `AnimatedVectorDrawable`, et les versions du dépôt se montent
 * exprès, jamais au fil de l'eau (README, « Les versions »).
 *
 * `claques` est un compteur, pas un booléen : deux claquements de suite doivent rejouer
 * l'animation, ce qu'un booléen resté `true` ne dirait pas. Sa valeur précédente est gardée dans
 * un porteur ordinaire, hors du système d'instantanés de Compose — l'écrire depuis `update`, qui
 * court à l'application de la composition, relancerait une recomposition à chaque passage.
 *
 * Seul appelant restant depuis le brief des animations Lottie du 23 septembre 2026 (soir) : la
 * célébration d'un film enregistré (`FilmEnregistreCalque.kt`) a son propre clap, `clap-2.json`
 * joué par `Animation` (`ui/theme/Animations.kt`), et ne réutilise plus ce composable.
 */
private class DernierClaque(var valeur: Int)

@Composable
fun ClapAvatar(claques: Int, modifier: Modifier = Modifier) {
    val dernier = remember { DernierClaque(claques) }
    AndroidView(
        modifier = modifier.semantics { contentDescription = "Tu es ici" },
        factory = { contexte -> ImageView(contexte).apply { setImageResource(R.drawable.ic_launcher_animated) } },
        update = { vue ->
            if (dernier.valeur != claques) {
                dernier.valeur = claques
                (vue.drawable as? AnimatedVectorDrawable)?.apply {
                    reset()
                    start()
                }
            }
        },
    )
}
