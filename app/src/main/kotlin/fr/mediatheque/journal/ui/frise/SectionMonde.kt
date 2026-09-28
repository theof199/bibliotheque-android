package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.animationsReduites

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
 *
 * Livraison 2 (28 septembre 2026, §H, §F) : `ImageDeFond` (nulle pour 1890 et 1900) rejoint tout
 * en bas de la pile, derrière la route — jamais posée sur les pavillons ni le texte, comme le
 * demande §H — et le grain (`monde.grain`) tout en haut, sous forme d'une tuile de bruit tuilée
 * plutôt qu'une image commitée de plus. Toujours pas d'ambiance ni d'entrée jouée (livraisons 3
 * à 5) : le carton reste statique, à l'état `Jouee`.
 *
 * Livraison 3 (28 septembre 2026, §D, §G) : l'entrée du carton se branche enfin sur le magasin des
 * mondes visités — `visible` (la section est dans le viewport, posé par `VoyageScreen.kt` depuis
 * `LazyListState.layoutInfo`) et `dejaEntre` (sa décennie est déjà dans le magasin) pilotent
 * `etatEntreeCarton` (`VoyageCarte.kt`, pure) : première visite, l'entrée joue une fois
 * (`Animatable` 0 → 1, `dureeEntree` par monde) puis `onEntreeJouee` écrit le magasin ; déjà
 * visitée, le carton part directement à l'état final ; animations réduites, pareil, sans jouer
 * l'entrée. `AmbianceDeMonde` (nouveau) rejoint la pile entre l'image et la route (§B) — sa boucle
 * ne tourne que tant que la section reste visible (`ambianceActive`, pure elle aussi), jamais hors
 * écran ni animations réduites. Seuls 1930, 1940 et 1950 ont une entrée et une ambiance pour
 * l'instant (§G) ; les autres mondes traversent la même machine sans qu'elle ne dessine rien de
 * plus qu'avant — 1960 → 2020 (livraison 4) et 1890 → 1920 en 16 i/s (livraison 5, via
 * `progressionQuantifiee` posée sur `progression` avant `etatEntreeCarton`) n'auront qu'à ajouter
 * leurs propres branches à `CartonTitre` et `AmbianceDeMonde`.
 */
@Composable
fun SectionMonde(
    section: SectionDuVoyage,
    anneeEnCours: Int,
    visible: Boolean,
    dejaEntre: Boolean,
    onEntreeJouee: () -> Unit,
    claques: Int,
    decennieAllumee: Int,
    onOpenAnnee: (AnneeFrise) -> Unit,
    onOpenDecennie: (DecennieFrise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monde = section.monde
    val corail = MaterialTheme.colorScheme.primary

    // Le réglage système ne change pas pendant qu'on regarde l'écran (`Mouvement.kt`) : un seul
    // `remember`, relu à la composition suivante — jumeau de `Cover.kt`/`FeuilleDeLecture.kt`.
    val reduit = remember { animationsReduites() }
    val progression = remember { Animatable(0f) }
    LaunchedEffect(monde.decennie, visible, dejaEntre, reduit) {
        if (dejaEntre || !visible) return@LaunchedEffect
        if (reduit) {
            onEntreeJouee()
            return@LaunchedEffect
        }
        progression.snapTo(0f)
        progression.animateTo(1f, tween(dureeEntree(monde.decennie), easing = LinearEasing))
        onEntreeJouee()
    }
    val entreeCarton = etatEntreeCarton(dejaEntre, visible, reduit, progression.value)
    val active = ambianceActive(visible, reduit)

    Column(modifier.fillMaxWidth()) {
        CartonTitre(monde, entreeCarton)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(HAUTEUR_ROUTE)
                .background(monde.fond),
        ) {
            val k = maxWidth / 390.dp

            // Tout en bas de la pile (§H) : derrière la route, jamais sur les pavillons ni le
            // texte. Nulle pour 1890 et 1900, qui n'ont pas d'image.
            monde.image?.let { image -> ImageDeFond(image, k) }

            // Entre l'image et la route (§B, livraison 3) : l'ambiance en boucle du monde, muette
            // hors écran et sans animations réduites (`ambianceActive`).
            AmbianceDeMonde(monde, active, k, Modifier.fillMaxSize())

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

            // Tout en haut de la pile (§F) : le grain, sur toute la section — discret, jamais sur
            // le carton (posé hors de cette `BoxWithConstraints`, `Column` plus haut).
            if (monde.grain > 0f) {
                Box(Modifier.fillMaxSize().grain(monde.grain))
            }
        }
    }
}

/**
 * La durée de l'entrée d'un carton, par monde (§G, livraisons 3 et 4) : reprend la durée totale
 * donnée par chaque description — l'onde sonore de 1930 (0,9 s), le store qui balaie 1940 (1,1 s),
 * les trois plaques qui se recalent en 1950 (1 s), les trois sauts de 1960 (1,2 s), le zoom lent de
 * 1970 (2,4 s), le grésillement néon de 1980 (1,6 s). 900 ms par défaut pour les mondes sans entrée
 * dessinée encore (1890 → 1920, 1990 → 2020) : la valeur ne se voit pas tant que `CartonTitre` ne
 * dessine rien de plus pour `EnCours` — seule la date à laquelle le magasin se marque en dépend un
 * peu.
 */
private fun dureeEntree(decennie: Int): Int = when (decennie) {
    1930 -> 900
    1940 -> 1_100
    1950 -> 1_000
    1960 -> 1_200
    1970 -> 2_400
    1980 -> 1_600
    else -> 900
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

/**
 * L'image de fond d'un monde (§H, livraison 2) : le WebP de `bin/images`, recadré « cover » à son
 * cadrage (`ImageDeMonde.cadrage`, déjà recadré au bon ratio par le script — l'ancrage ici ne fait
 * plus que documenter l'intention), ses filtres colorimétriques (`ImageDeMonde.matrice()`) et son
 * masque vertical (transparent aux deux bords, opaque de 14 % à 82 % — §H, pour qu'elle se fonde
 * dans `monde.fond` plutôt que de finir en bandeau net). Décorative, comme la route sous elle :
 * `contentDescription = null`.
 */
@Composable
private fun ImageDeFond(image: ImageDeMonde, k: Float, modifier: Modifier = Modifier) {
    val cadrage = image.cadrage
    Image(
        painter = painterResource(image.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(cadrage.ancrageX * 2f - 1f, cadrage.ancrageY * 2f - 1f),
        alpha = image.alpha,
        colorFilter = ColorFilter.colorMatrix(image.matrice()),
        modifier = modifier
            .offset(x = cadrage.x * k, y = cadrage.y)
            .size(cadrage.largeur * k, cadrage.hauteur)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .masqueVertical(),
    )
}

/**
 * Le fondu haut et bas de l'image de fond (§H) : un vrai masque d'alpha (`BlendMode.DstIn` sur une
 * composition hors écran, comme le trou du poinçon de `TicketVoyage.kt`), pas un dégradé vers
 * `monde.fond` — l'image se fond dans ce qu'il y a derrière elle quel que soit son alpha propre.
 */
private fun Modifier.masqueVertical(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.14f to Color.Black,
            0.82f to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * La tuile de grain (§F, livraison 2) : un bruit gris uniforme, tuilé sur toute la section par un
 * `BitmapShader` en mode répété plutôt que redessiné à chaque frame — la fractale à deux octaves
 * du delta de Léon reste une approximation assumée, comme les sépias de `Mondes.kt`. `remember`
 * la recalcule à chaque recomposition de la section (elle en sort et y rentre en défilant), mais
 * une tuile de 48 px ne coûte qu'un tableau de 2304 entiers : sans commande dessinée par frame,
 * le coût reste négligeable à côté d'une image commitée de plus.
 */
private const val TAILLE_TUILE_GRAIN = 48

private fun tuileDeGrain(): android.graphics.Bitmap {
    val graine = kotlin.random.Random(0)
    val pixels = IntArray(TAILLE_TUILE_GRAIN * TAILLE_TUILE_GRAIN) {
        val v = graine.nextInt(256)
        android.graphics.Color.argb(255, v, v, v)
    }
    return android.graphics.Bitmap.createBitmap(pixels, TAILLE_TUILE_GRAIN, TAILLE_TUILE_GRAIN, android.graphics.Bitmap.Config.ARGB_8888)
}

private fun Modifier.grain(alpha: Float): Modifier = composed {
    val brush = remember {
        val shader = android.graphics.BitmapShader(tuileDeGrain(), android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }
    drawWithContent {
        drawContent()
        drawRect(brush = brush, alpha = alpha, blendMode = BlendMode.Screen)
    }
}
