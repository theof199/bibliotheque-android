package fr.mediatheque.journal.ui.fiche

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.theme.Fraunces
import fr.mediatheque.journal.ui.theme.IconeTabler

/** De combien le titre remonte sur le bas de l'affiche : il « mord » dessus plutôt que de la suivre. */
private val CHEVAUCHEMENT_TITRE = 56.dp

/** La hauteur d'une ligne du titre (Fraunces 30 / 34) : le chiffre de la note s'aligne sur son bas. */
private val LIGNE_TITRE = 34.sp

/**
 * L'en-tête commun aux trois fiches d'un film (« la fiche · trois visages », reprise validée du
 * 25 septembre 2026) : l'affiche en héros, le retour dans son disque sombre, le titre qui mord sur
 * le bas de l'affiche, la note penchée, la ligne du réalisateur, puis le filet or et l'étiquette.
 * La fiche d'une entrée (`FicheEntreeScreen`) le pose aujourd'hui ; la fiche du Voyage et la fiche
 * simple le reprendront — d'où des paramètres déjà calculés (`anneeEtDuree`, `etiquette`,
 * `couleurEtiquette`) plutôt qu'un objet d'un seul des trois écrans : chacun dit ce qu'il sait, le
 * Voyage « Salle · nom » dans l'accent de son monde, les deux autres la décennie du film.
 *
 * Ne défile pas lui-même : c'est le haut de la colonne défilante de l'écran, qui ajoute sa suite
 * dessous (réactions, bobines, salle, boutons).
 *
 * `note` nulle (film pas vu, ou vu sans note) : pas de « TA NOTE » du tout. `realisateur` nul : ni
 * nom ni « · » devant l'année. `titreOriginal` : déjà filtré par l'appelant
 * (`titreOriginalAffiche`), affiché tel quel s'il n'est pas nul.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EnTeteFiche(
    affiche: String?,
    titre: String,
    titreOriginal: String?,
    note: Int?,
    realisateur: (@Composable () -> Unit)?,
    anneeEtDuree: String?,
    etiquette: String?,
    couleurEtiquette: Color,
    fond: Color,
    onBack: () -> Unit,
    volante: AfficheVolante?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        AfficheHero(affiche, titre, fond, volante)
        // 8 dp et pas 12 : `IconButton` garde 48 dp de zone touchable autour de son disque de 40,
        // le disque visible tombe donc à 12 dp des bords.
        BoutonRetourSurAffiche(onBack, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp))
        Column(
            Modifier.fillMaxWidth().padding(top = HAUTEUR_AFFICHE_HERO - CHEVAUCHEMENT_TITRE),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LigneTitreEtNote(titre, note)
            titreOriginal?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LigneRealisateur(realisateur, anneeEtDuree)
            etiquette?.let { EtiquetteFiche(it, couleurEtiquette, Modifier.padding(top = 6.dp)) }
        }
    }
}

/**
 * Le retour par-dessus l'affiche : un disque noir à 55 % derrière la flèche blanche, lisible sur
 * n'importe quelle image — la flèche nue du reste de l'appli disparaissait sur une affiche claire.
 */
@Composable
private fun BoutonRetourSurAffiche(onBack: () -> Unit, modifier: Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
    ) {
        IconeTabler("arrow-left", "Retour", tint = Color.White)
    }
}

@Composable
private fun LigneTitreEtNote(titre: String, note: Int?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            titre,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, lineHeight = LIGNE_TITRE),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        note?.let { NoteFiche(it, Modifier.debordeVersLeHaut()) }
    }
}

/**
 * « TA NOTE » au-dessus du grand chiffre penché — l'autre désordre voulu de la reprise, avec les
 * puces. Une seule annonce au lecteur d'écran, « Ta note, 8 », plutôt que le libellé puis le
 * chiffre séparément.
 */
@Composable
private fun NoteFiche(note: Int, modifier: Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Ta note, $note" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "TA NOTE",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.16.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            note.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Fraunces, fontSize = 76.sp, lineHeight = 76.sp),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.graphicsLayer { rotationZ = -9f },
        )
    }
}

/**
 * Le bloc de la note ne prend dans la rangée que la hauteur d'une ligne du titre et déborde vers le
 * haut, sur le bas de l'affiche : sans cela, ses quelque 100 dp pousseraient la ligne du
 * réalisateur loin sous un titre d'une ligne — le vide que la reprise est venue retirer. Le bas du
 * chiffre tombe ainsi sur le bas de la première ligne du titre.
 */
private fun Modifier.debordeVersLeHaut(): Modifier = layout { mesurable, contraintes ->
    val place = mesurable.measure(contraintes)
    val hauteurVisible = LIGNE_TITRE.roundToPx()
    layout(place.width, hauteurVisible) { place.place(0, hauteurVisible - place.height) }
}

@Composable
private fun LigneRealisateur(realisateur: (@Composable () -> Unit)?, anneeEtDuree: String?) {
    if (realisateur == null && anneeEtDuree == null) return
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        realisateur?.invoke()
        anneeEtDuree?.let {
            Text(
                if (realisateur != null) " · $it" else it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le court filet or qui part du bord de l'écran, puis l'étiquette penchée : « Années 1990 · Le
 * blockbuster » hors Voyage, « Salle · nom » sur le Voyage. Le filet ignore la marge de 16 dp du
 * reste de l'en-tête — il vient du bord, comme un repère qu'on aurait tiré jusqu'au mot.
 */
@Composable
private fun EtiquetteFiche(etiquette: String, couleur: Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp).height(1.dp).background(MaterialTheme.colorScheme.secondary))
        Text(
            etiquette.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.16.em),
            color = couleur,
            modifier = Modifier.graphicsLayer { rotationZ = -1.5f },
        )
    }
}
