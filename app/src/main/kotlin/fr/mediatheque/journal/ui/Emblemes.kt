package fr.mediatheque.journal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.frise.Recompense
import fr.mediatheque.journal.ui.frise.glypheRecompense
import fr.mediatheque.journal.ui.theme.Corail
import fr.mediatheque.journal.ui.theme.Limelight

/**
 * La plomberie des images du propriétaire (brief du 23 septembre 2026 soir, geste 3) : cinq
 * emblèmes — les trois récompenses du Voyage (Ours, Lion, Palme) et les deux tampons (passeport,
 * « perdu ») — que le propriétaire dessine lui-même. Tant que le fichier n'existe pas dans
 * `res/drawable-nodpi/`, `Embleme` retombe sur le dessin vectoriel déjà là (`glypheRecompense`
 * pour une récompense, un double cercle sinon) : un seul endroit, ici, change le jour où le WebP
 * arrive. Format attendu, README (« Les emblèmes ») : WebP, fond transparent, 1024 × 1024, nom
 * exact de `Emblemes.nomRessource`.
 */
enum class EmblemeType { OURS, LION, PALME, PASSEPORT, PERDU }

/** Fonctions pures testées (mutation) : `EmblemesTest.kt`. */
object Emblemes {
    fun nomRessource(type: EmblemeType): String = when (type) {
        EmblemeType.OURS -> "embleme_ours"
        EmblemeType.LION -> "embleme_lion"
        EmblemeType.PALME -> "embleme_palme"
        EmblemeType.PASSEPORT -> "embleme_passeport"
        EmblemeType.PERDU -> "embleme_perdu"
    }

    /** La récompense que dessine `glypheRecompense` en substitut — nulle pour les deux tampons, qui ont leur propre dessin. */
    fun recompenseDe(type: EmblemeType): Recompense? = when (type) {
        EmblemeType.OURS -> Recompense.OURS
        EmblemeType.LION -> Recompense.LION
        EmblemeType.PALME -> Recompense.PALME
        EmblemeType.PASSEPORT, EmblemeType.PERDU -> null
    }

    /** L'inverse de `recompenseDe`, pour les appelants qui n'ont qu'un `Recompense` en main (glyphe de la carte, de la boîte, du cartouche). */
    fun typeDe(recompense: Recompense): EmblemeType = when (recompense) {
        Recompense.OURS -> EmblemeType.OURS
        Recompense.LION -> EmblemeType.LION
        Recompense.PALME -> EmblemeType.PALME
    }
}

/** Le sépia des tampons « perdu » — la même teinte que `TeinteSepia` des affiches non vues (`ui/frise/AnneeScreen.kt`). */
private val SepiaDuTampon = Color(0xFF3A2C1E)

/** Le double cercle du tampon passeport — sans le millésime, dessiné par l'appelant (`TamponPasseport`, `TamponQuiTombe`). */
private fun DrawScope.substitutPasseport() {
    drawCircle(Corail, radius = size.minDimension / 2f, style = Stroke(width = size.minDimension * 0.08f))
    drawCircle(Corail.copy(alpha = 0.55f), radius = size.minDimension * 0.38f, style = Stroke(width = size.minDimension * 0.035f))
}

/** Le double cercle du tampon « perdu » — même proportions que le passeport, en sépia. */
private fun DrawScope.substitutPerdu() {
    drawCircle(SepiaDuTampon, radius = size.minDimension / 2f, style = Stroke(width = size.minDimension * 0.08f))
    drawCircle(SepiaDuTampon, radius = size.minDimension * 0.38f, style = Stroke(width = size.minDimension * 0.035f))
}

/**
 * Un emblème, `taille` de côté. `encre`/`fond` ne servent qu'aux trois récompenses (`glypheRecompense`
 * en a besoin pour creuser la face du lion et le museau de l'ours) : les deux tampons dessinent
 * toujours dans leurs propres couleurs fixes, comme avant ce geste.
 *
 * Cherche `R.drawable.<Emblemes.nomRessource(type)>` par son nom plutôt que par une référence
 * `R.drawable.embleme_ours` : cette dernière empêcherait de compiler tant que le fichier n'existe
 * pas, alors que le substitut doit rester le comportement normal en attendant le dessin du
 * propriétaire.
 */
@Composable
fun Embleme(type: EmblemeType, taille: Dp, modifier: Modifier = Modifier, encre: Color = Color.Unspecified, fond: Color = Color.Unspecified) {
    val contexte = LocalContext.current
    val ressource = remember(type) {
        contexte.resources.getIdentifier(Emblemes.nomRessource(type), "drawable", contexte.packageName)
    }
    if (ressource != 0) {
        Image(painterResource(ressource), contentDescription = null, modifier = modifier.size(taille))
        return
    }

    val recompense = Emblemes.recompenseDe(type)
    Box(modifier.size(taille), contentAlignment = Alignment.Center) {
        when {
            recompense != null -> Canvas(Modifier.fillMaxSize()) { glypheRecompense(recompense, encre, fond) }
            type == EmblemeType.PASSEPORT -> Canvas(Modifier.fillMaxSize()) { substitutPasseport() }
            type == EmblemeType.PERDU -> {
                Canvas(Modifier.fillMaxSize()) { substitutPerdu() }
                Text(
                    "PERDU",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Limelight, letterSpacing = 1.sp, fontSize = 9.sp),
                    color = SepiaDuTampon,
                    maxLines = 1,
                )
            }
        }
    }
}
