package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** La hauteur de la route d'une section, carton exclu — le repère 650 dp de Léon. */
private val HAUTEUR_ROUTE = 650.dp

/**
 * Une section du Voyage : le carton-titre d'un monde, sa route et ses dix pavillons, sa marquise
 * (delta de Léon du 25 septembre 2026, « pavillon par pavillon », §B).
 *
 * `k = maxWidth / 390.dp` (`BoxWithConstraints`, posé ici comme le demande le plan) étire les
 * abscisses du repère de Léon sur la largeur réelle de l'écran (360 à 412 dp) ; les ordonnées
 * restent telles quelles, la section gardant toujours la même hauteur.
 *
 * Livraison 1 : ni image de fond, ni ambiance, ni grain (aucun avant la livraison 2 pour l'image,
 * 3 pour l'ambiance) — seulement la route, les dix pavillons dans leurs trois états (avec leurs
 * traitements de couleur statiques, §C), le cône de lumière statique de l'année en cours, le
 * carton (ses cadres par décennie, statiques) et la marquise.
 */
@Composable
fun SectionMonde(
    section: SectionDuVoyage,
    anneeEnCours: Int,
    entreeCarton: EtatEntree,
    claques: Int,
    decennieAllumee: Int,
    onOpenAnnee: (AnneeFrise) -> Unit,
    onOpenDecennie: (DecennieFrise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monde = section.monde
    val corail = MaterialTheme.colorScheme.primary

    Column(modifier.fillMaxWidth()) {
        CartonTitre(monde, entreeCarton)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(HAUTEUR_ROUTE)
                .background(monde.fond),
        ) {
            val k = maxWidth / 390.dp
            // Le chemin de la route n'est analysé qu'une fois par largeur d'écran (revue du
            // 28 septembre 2026, retouche de la livraison 1) — pas à chaque frame dessinée.
            val cheminRoute = rememberCheminRoute(k)
            Canvas(Modifier.fillMaxSize()) { dessinerRoute(cheminRoute, magnetique = monde.decennie == 1980) }

            val anneeEnCoursIci = section.annees.any { it.annee == anneeEnCours && it.statut == StatutAnneeVoyage.EN_COURS }
            if (anneeEnCoursIci) {
                Canvas(Modifier.fillMaxSize()) { coneDeLumiere(k, corail) }
            }

            section.annees.forEach { annee ->
                PavillonAnnee(annee, monde, k, claques, onOpenAnnee)
            }

            Marquise(
                monde = monde,
                bouclee = section.bouclee,
                anime = monde.decennie == decennieAllumee,
                // Le compte d'ampoules allumées (§E) : une par année récompensée de la section,
                // neuf au plus — la décennie bouclée en allume neuf sur neuf d'un coup.
                recompensesCount = section.annees.count { it.recompense != null },
                onClick = { onOpenDecennie(section.rayon) },
                modifier = Modifier.offset(x = 150.dp * k, y = 560.dp),
            )
        }
    }
}

@Composable
private fun PavillonAnnee(
    annee: AnneeDuVoyage,
    monde: Monde,
    k: Float,
    claques: Int,
    onOpenAnnee: (AnneeFrise) -> Unit,
) {
    val format = monde.format
    val place = emplacement(annee.rang)
    // Le centre de la case de référence 56 × 44 : un format plus petit ou plus grand s'y centre
    // (§C) plutôt que de partir du même coin.
    val centreX = (place.x + CASE_REFERENCE_LARGEUR / 2f).dp * k
    val centreY = place.y.dp + (CASE_REFERENCE_HAUTEUR / 2f).dp

    val etat = when (annee.statut) {
        StatutAnneeVoyage.OUVERTE -> EtatPhotogramme.Ouverte(annee.affiche, annee.profondeur, annee.recompense)
        StatutAnneeVoyage.EN_COURS -> EtatPhotogramme.EnCours(annee.affiche)
        StatutAnneeVoyage.VERROUILLEE, null -> EtatPhotogramme.Verrouillee(annee.profondeur)
    }

    Photogramme(
        format = format,
        etat = etat,
        annee = annee.annee,
        monde = monde,
        modifier = Modifier
            .offset(x = centreX - format.largeur / 2, y = centreY - format.hauteur / 2)
            .clickable { onOpenAnnee(annee.groupe) },
    )

    Millesime(annee.annee, positionMillesime(annee.rang), place, k, enCours = annee.statut == StatutAnneeVoyage.EN_COURS)

    if (annee.statut == StatutAnneeVoyage.EN_COURS) {
        ClapAvatar(
            claques = claques,
            modifier = Modifier
                .size(36.dp)
                .offset(x = centreX + format.largeur / 2 + 4.dp, y = centreY - 18.dp),
        )
    }
}

/**
 * Le millésime d'une année, autour de sa case (§C) — sa position dépend du virage de la route à
 * cet endroit ; celui de l'année en cours est `primary` et gras (§C, retouche du 28 septembre
 * 2026).
 */
@Composable
private fun Millesime(annee: Int, position: PositionMillesime, emplacement: EmplacementPhotogramme, k: Float, enCours: Boolean) {
    val left = emplacement.x.dp * k
    val top = emplacement.y.dp
    val style = MaterialTheme.typography.labelMedium.copy(
        fontSize = 11.sp,
        fontWeight = if (enCours) FontWeight.Bold else FontWeight.Normal,
    )
    val couleur = if (enCours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    when (position) {
        PositionMillesime.DESSOUS -> Box(
            Modifier.offset(x = left, y = top + 56.dp).width(CASE_REFERENCE_LARGEUR.dp * k),
            contentAlignment = Alignment.TopCenter,
        ) { Text(annee.toString(), style = style, color = couleur) }
        PositionMillesime.A_GAUCHE -> Box(
            Modifier.offset(x = left - 58.dp * k, y = top + 16.dp).width(54.dp * k),
            contentAlignment = Alignment.TopEnd,
        ) { Text(annee.toString(), style = style, color = couleur, textAlign = TextAlign.End) }
        PositionMillesime.A_DROITE -> Box(
            Modifier.offset(x = left + 62.dp * k, y = top + 16.dp).width(54.dp * k),
            contentAlignment = Alignment.TopStart,
        ) { Text(annee.toString(), style = style, color = couleur) }
        PositionMillesime.DESSUS -> Box(
            Modifier.offset(x = left, y = top - 26.dp).width(CASE_REFERENCE_LARGEUR.dp * k),
            contentAlignment = Alignment.TopCenter,
        ) { Text(annee.toString(), style = style, color = couleur) }
    }
}
