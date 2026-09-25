package fr.mediatheque.journal.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Une affiche « sous vitre » (« Accueil · la porte d'entrée », planche de Léon validée le
 * 25 septembre 2026) : la `Cover` d'une jaquette, son liseré or, une ombre douce qui la décolle du
 * fond sombre, un reflet oblique par-dessus comme la vitre d'un cadre, et la pastille de note en
 * bas à droite. Un seul composable, dans `ui/` plutôt que `ui/home/`, parce que la grille de
 * l'accueil et sa vitrine vide (`VitrineVide`) doivent porter exactement le même verre : deux
 * copies du reflet divergeraient à la première retouche.
 *
 * Le reflet et l'ombre sont posés sur le modificateur de la `Cover`, après `voler` : ils volent
 * avec l'affiche vers la fiche au lieu de rester peints dans la grille qui s'efface. La pastille,
 * elle, reste à sa place, comme avant la vitre.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AfficheVitrine(
    url: String?,
    titre: String,
    largeur: Dp,
    hauteur: Dp,
    note: Int?,
    volante: AfficheVolante?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Cover(
            url,
            titre,
            largeur,
            hauteur,
            // Ces modificateurs passent avant la taille et la découpe que `Cover` pose lui-même :
            // le reflet suit donc la forme de l'affiche par son propre contour (`reflet`), pas par
            // la découpe.
            modifier = Modifier.voler(volante).ombreVitrine().lisereOr().reflet(),
        )
        note?.let { PastilleNote(it, Modifier.align(Alignment.BottomEnd)) }
    }
}

/**
 * La pastille de note, reprise de la grille d'avant la vitre (22 → 24 dp, 25 septembre 2026).
 * Design §8 : elle dit « Note {n} sur 10 », pas le chiffre nu que `Text` donnerait seul à TalkBack.
 */
@Composable
private fun PastilleNote(note: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(4.dp)
            .size(24.dp)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape)
            .clearAndSetSemantics { contentDescription = "Note $note sur 10" },
        contentAlignment = Alignment.Center,
    ) {
        Text("$note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

/**
 * L'ombre douce d'une affiche sous vitre : 6 dp d'élévation, sans découpe — la découpe reste celle
 * de `Cover`, et une ombre découpée ne se verrait plus.
 */
@Composable
fun Modifier.ombreVitrine(): Modifier = this.shadow(6.dp, MaterialTheme.shapes.small, clip = false)

/**
 * Le reflet oblique de la vitre, peint par-dessus le contenu : un dégradé orienté à 115° (comme un
 * `linear-gradient(115deg, …)` de la planche), blanc à 10 % dans le coin haut gauche, 3 % au
 * 28e centième, rien à partir de 45 %, puis une lueur de 4 % qui remonte jusqu'au bord droit. Des
 * alphas aussi bas pour que le reflet reste un éclat sur le verre, jamais un voile blanc.
 * Peint dans le contour de `shape`, pas dans un rectangle : posé avant la découpe de `Cover`, il
 * blanchirait sinon les coins arrondis.
 */
@Composable
fun Modifier.reflet(shape: Shape = MaterialTheme.shapes.small): Modifier = this.drawWithCache {
    val (debut, fin) = extremitesDegrade(size, ANGLE_REFLET_DEGRES)
    val degrade = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.10f),
        0.28f to Color.White.copy(alpha = 0.03f),
        0.45f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.04f),
        start = debut,
        end = fin,
    )
    val contour = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        drawContent()
        drawOutline(contour, degrade)
    }
}

private const val ANGLE_REFLET_DEGRES = 115.0

/**
 * Les deux bouts d'un dégradé orienté comme en CSS : 0° vers le haut, 90° vers la droite, dans le
 * sens des aiguilles d'une montre, la ligne passant par le centre et assez longue pour que ses
 * extrémités touchent les coins — sans quoi les 10 % du coin haut gauche tomberaient hors cadre.
 */
private fun extremitesDegrade(taille: Size, angleDegres: Double): Pair<Offset, Offset> {
    val angle = Math.toRadians(angleDegres)
    val dx = sin(angle).toFloat()
    val dy = -cos(angle).toFloat()
    val demiLongueur = (abs(taille.width * dx) + abs(taille.height * dy)) / 2f
    val centre = Offset(taille.width / 2f, taille.height / 2f)
    val demi = Offset(dx * demiLongueur, dy * demiLongueur)
    return (centre - demi) to (centre + demi)
}
