package fr.mediatheque.journal.ui.theme

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import fr.mediatheque.journal.R

/**
 * Les icônes Tabler de l'application (brief du 23 septembre 2026, geste 2) : mêmes noms que les
 * lignes de `icones/tabler.txt`, un `R.drawable.tabler_<nom souligné>` par nom, généré par
 * `bin/icones`. Une `Map<String, Int>` plutôt qu'un `enum` — un nom qu'on a *oublié d'ajouter ici*
 * ne doit pas empêcher de compiler (un `enum` avec un `when` exhaustif le ferait), il doit faire
 * rougir un test : c'est le seul moyen d'observer l'oubli distinctement d'une faute de frappe dans
 * l'appel, qui `IconeTabler` fait échouer tout de suite, elle, par son `requireNotNull`.
 */
private val RESSOURCES_TABLER: Map<String, Int> = mapOf(
    "arrow-left" to R.drawable.tabler_arrow_left,
    "check" to R.drawable.tabler_check,
    "x" to R.drawable.tabler_x,
    "plus" to R.drawable.tabler_plus,
    "chevron-right" to R.drawable.tabler_chevron_right,
    "timeline" to R.drawable.tabler_timeline,
    "user" to R.drawable.tabler_user,
    "movie" to R.drawable.tabler_movie,
    "home" to R.drawable.tabler_home,
    "trash" to R.drawable.tabler_trash,
    "ticket" to R.drawable.tabler_ticket,
    "cloud" to R.drawable.tabler_cloud,
    "star-filled" to R.drawable.tabler_star_filled,
    "sparkles" to R.drawable.tabler_sparkles,
    "award" to R.drawable.tabler_award,
)

/** Fonction pure testée (mutation) : `IconeTablerTest.kt`. */
fun ressourceTabler(nom: String): Int? = RESSOURCES_TABLER[nom]

/**
 * Une icône Tabler, à la place d'`Icons.*` (Material) ou d'un `Text("✦")` (geste 2 du brief du
 * 23 septembre 2026). `nom` échoue tout de suite, en développement, s'il ne correspond à rien de
 * `icones/tabler.txt` — plutôt que de poser une icône absente en silence en production.
 */
@Composable
fun IconeTabler(nom: String, description: String?, tint: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    val ressource = requireNotNull(ressourceTabler(nom)) { "Icone Tabler inconnue : $nom (voir icones/tabler.txt)" }
    Icon(painter = painterResource(ressource), contentDescription = description, tint = tint, modifier = modifier)
}
