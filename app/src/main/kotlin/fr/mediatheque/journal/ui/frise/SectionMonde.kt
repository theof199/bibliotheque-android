package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import fr.mediatheque.journal.R
import fr.mediatheque.journal.ui.theme.animationsReduites

/** La hauteur de la route d'une section, carton exclu — le repère 650 dp de Léon. */
private val HAUTEUR_ROUTE = 650.dp

/**
 * La hauteur de la bande de transition entre deux décennies (retouche du 28 septembre 2026,
 * retour téléphone du propriétaire) : un dégradé du fond du monde précédent vers celui-ci, pour
 * que chaque carton ait de l'air au-dessus plutôt que d'être collé à la fin de la route d'avant.
 */
private val HAUTEUR_BANDE_TRANSITION = 48.dp

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
 *
 * Retouche du 28 septembre 2026 (retour téléphone du propriétaire, livraison 5) : la bande de
 * transition (`fondPrecedent`) donne de l'air à chaque carton, la route se découpe (`clipToBounds`)
 * pour qu'aucune entrée ni ambiance ne déborde sur la section voisine, l'image de fond se fond sur
 * ses quatre bords (`masqueQuatreBords`, plus un voile de `monde.fond`) plutôt que haut/bas
 * seulement, et gagne enfin un crochet d'effet propre (`tremblementEpaule1960`,
 * `grillePixels2000`) — le manque signalé par `rapport-livraison-4.md`.
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
    // Le fond du monde précédent (retouche du 28 septembre 2026, retour téléphone) : nul pour la
    // toute première section — sert la bande de transition entre deux décennies ci-dessous.
    fondPrecedent: Color? = null,
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
    // Le saccadé voulu des quatre mondes du muet (§G, livraison 5) : la progression tenue par pas
    // de 16 images par seconde plutôt que de glisser en continu comme 1930 → 2020 — posée ici,
    // juste avant `etatEntreeCarton`, comme prévu par la livraison 3 (`progressionQuantifiee`).
    val progressionPourEntree = if (monde.decennie in 1890..1920) {
        progressionQuantifiee(progression.value, pasSeizeImagesParSeconde(dureeEntree(monde.decennie)))
    } else {
        progression.value
    }
    val entreeCarton = etatEntreeCarton(dejaEntre, visible, reduit, progressionPourEntree)
    val active = ambianceActive(visible, reduit)
    // Relue par l'image (§G suite, 2000 : la grille de pixels pendant l'entrée) — nulle hors
    // `EnCours`, comme `progressionEntree` de `CartonTitre.kt`.
    val progressionEntreeImage = (entreeCarton as? EtatEntree.EnCours)?.progression

    Column(modifier.fillMaxWidth()) {
        // La bande de transition (retouche du 28 septembre 2026, retour téléphone du propriétaire) :
        // 48 dp de dégradé du fond du monde précédent vers celui-ci, pour que le carton ait de
        // l'air au-dessus plutôt que d'être collé à la fin de la décennie d'avant. Rien pour la
        // toute première section (`fondPrecedent` nul).
        if (fondPrecedent != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HAUTEUR_BANDE_TRANSITION)
                    .background(Brush.verticalGradient(listOf(fondPrecedent, monde.fond))),
            )
        }
        CartonTitre(monde, entreeCarton)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(HAUTEUR_ROUTE)
                // Aucune entrée ni ambiance ne doit déborder de sa propre section (retouche du
                // 28 septembre 2026) — les transformations (rotation, décalage) de `CartonTitre`
                // et `AmbianceDeMonde` restent en dessous de cette taille, mais le découpage
                // garantit qu'un dépassement reste invisible plutôt que de peindre sur la section
                // voisine.
                .clipToBounds()
                .background(monde.fond),
        ) {
            val k = maxWidth / 390.dp

            // Tout en bas de la pile (§H) : derrière la route, jamais sur les pavillons ni le
            // texte. Nulle pour 1890 et 1900, qui n'ont pas d'image.
            monde.image?.let { image -> ImageDeFond(image, monde, active, progressionEntreeImage, k) }

            // L'ombre d'Orlok (§G, 1920, livraison 5) : silhouette statique par-dessus l'image,
            // comme l'affiche de 1910 — toujours visible, pas pilotée par `active` ni par l'entrée.
            if (monde.decennie == 1920) {
                Image(
                    painter = painterResource(R.drawable.voyage_orlok),
                    contentDescription = null,
                    alpha = 0.88f,
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier.offset(x = 140.dp * k, y = 236.dp).size(250.dp * k, 266.dp),
                )
            }

            // Entre l'image et la route (§B, livraison 3) : l'ambiance en boucle du monde, muette
            // hors écran et sans animations réduites (`ambianceActive`).
            AmbianceDeMonde(monde, active, k, Modifier.fillMaxSize())

            // Le chemin de la route n'est analysé qu'une fois par largeur d'écran (revue du
            // 28 septembre 2026, retouche de la livraison 1) — pas à chaque frame dessinée.
            val cheminRoute = rememberCheminRoute(k)
            Canvas(Modifier.fillMaxSize()) { dessinerRoute(cheminRoute, magnetique = monde.decennie == 1980) }

            // L'année en cours de cette section, s'il y en a une (correctif du 28 septembre 2026,
            // « le voyage se sent progresser ») : sert à la fois au cône de lumière (sa case
            // exacte, `rang`, par `centreCase`) et à la marquise (`enCours`, lot 1, « chaque
            // marquise se dit décennie en cours ») — un seul calcul, jamais deux qui pourraient
            // diverger.
            val anneeEnCoursDeLaSection = section.annees.firstOrNull { it.annee == anneeEnCours && it.statut == StatutAnneeVoyage.EN_COURS }
            if (anneeEnCoursDeLaSection != null) {
                // La respiration douce du cône (lot 1 du brief du 28 septembre 2026) : un aller-
                // retour d'alpha lent, coupé (fixe à pleine intensité) si les animations sont
                // réduites — jumelle de la lampe corail de `AmbianceDAujourdhui` (`AmbianceDeMonde.kt`).
                val respirationCone = if (reduit) {
                    1f
                } else {
                    val transitionCone = rememberInfiniteTransition(label = "cone-respiration")
                    val valeur by transitionCone.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
                        label = "respiration",
                    )
                    valeur
                }
                val centre = centreCase(anneeEnCoursDeLaSection.rang, k)
                Canvas(Modifier.fillMaxSize()) { coneDeLumiere(k, corail, centre.x, centre.y, respirationCone) }
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
                // Lot 1 du brief du 28 septembre 2026, « chaque marquise se dit décennie en
                // cours » : seule la vraie décennie en cours (celle qui porte l'année en cours)
                // se le dit — les autres, ni bouclées ni en cours, sont « à venir ».
                enCours = anneeEnCoursDeLaSection != null,
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
 * 1970 (2,4 s), le grésillement néon de 1980 (1,6 s), la claque de 1990 (0,7 s — l'éclat blanc à
 * 55-64 % de l'entrée s'y cale tel quel), le décodage de 2000 (1,1 s), le spinner puis le titre de
 * 2010 (2,4 s de rotation + 0,2 s de disparition + 0,4 s de fondu du titre, superposés en fin de
 * course = 2,8 s de bout en bout) et l'allumage de 2020 (1,2 s). 1890 (1 s de tressaut, le temps
 * que la manivelle tourne en 0,5 s), 1900 (1 s, 16 pas — le clignotement du truc à arrêt), 1910
 * (0,6 s d'iris + 0,7 s de délai + 0,9 s d'intertitre tapé = 1,6 s) et 1920 (≈ 2,2 s : le mot
 * « L'expressionnisme » tracé lettre par lettre à 0,14 s le segment, §G) reprennent les durées du
 * delta plutôt que le défaut — c'est sur elles que `progressionQuantifiee` (16 i/s, §G) est posée
 * ci-dessous, dans `SectionMonde`.
 */
private fun dureeEntree(decennie: Int): Int = when (decennie) {
    1890 -> 1_000
    1900 -> 1_000
    1910 -> 1_600
    1920 -> 2_200
    1930 -> 900
    1940 -> 1_100
    1950 -> 1_000
    1960 -> 1_200
    1970 -> 2_400
    1980 -> 1_600
    1990 -> 700
    2000 -> 1_100
    2010 -> 2_800
    2020 -> 1_200
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
    // Le centre de la case de référence 56 × 44 : un format plus petit ou plus grand s'y centre
    // (§C) plutôt que de partir du même coin — `centreCase` (`VoyageCarte.kt`), jumeau exact du
    // point que vise le cône de lumière de l'année en cours (`coneDeLumiere`).
    val centre = centreCase(annee.rang, k)
    val centreX = centre.x.dp
    val centreY = centre.y.dp

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

    Millesime(annee.annee, positionMillesime(annee.rang), emplacement(annee.rang), k, enCours = annee.statut == StatutAnneeVoyage.EN_COURS)

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
 * masque sur les quatre bords (retouche du 28 septembre 2026, ci-dessous). Décorative, comme la
 * route sous elle : `contentDescription = null`.
 *
 * Retouche du 28 septembre 2026 (retour téléphone) : le crochet manquant signalé par
 * `rapport-livraison-4.md` — une transformation propre à l'image, par monde, vient s'intercaler
 * avant le masque (`tremblementEpaule1960`, `grillePixels2000`), pilotée par les mêmes signaux que
 * le reste (`active`/`reduit` via `ambianceActive`, `progressionEntree` via `etatEntreeCarton`).
 */
@Composable
private fun ImageDeFond(image: ImageDeMonde, monde: Monde, active: Boolean, progressionEntree: Float?, k: Float, modifier: Modifier = Modifier) {
    val cadrage = image.cadrage
    val decennie = monde.decennie
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
            .let { if (decennie == 1960) it.tremblementEpaule1960(active) else it }
            .let { if (decennie == 2000) it.grillePixels2000(progressionEntree) else it }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .masqueQuatreBords(monde.fond),
    )
}

/**
 * Le fondu sur les quatre bords de l'image de fond (retouche du 28 septembre 2026, retour
 * téléphone du propriétaire) : l'ancienne version ne fondait que haut et bas, ce qui se lisait
 * comme un bloc-photo à bords nets sur les côtés — deux masques d'alpha en `BlendMode.DstIn`
 * successifs (vertical puis horizontal, sur une composition hors écran, comme le trou du poinçon
 * de `TicketVoyage.kt`) multiplient leurs fondus, adoucissant les quatre bords à la fois. Un léger
 * voile de `fond` par-dessus l'image (avant le masque, pour qu'il s'estompe avec elle) fait lire
 * l'ensemble comme une lueur plutôt qu'une photo posée.
 */
private fun Modifier.masqueQuatreBords(fond: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(fond.copy(alpha = 0.16f))
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.14f to Color.Black,
            0.82f to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.14f to Color.Black,
            0.86f to Color.Black,
            1f to Color.Transparent,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * 1960 (§G suite) : l'image tremble à l'épaule tant que l'ambiance tourne — rattrapage du crochet
 * manquant (« Déviations du delta » de `rapport-livraison-4.md` : `AmbianceDeMonde` ne pouvait
 * transformer que ce qu'elle dessinait elle-même, jamais `ImageDeFond`, composée indépendamment
 * plus bas dans la pile). Approximé sur six paliers cycliques parmi les quatre positions du delta
 * plutôt que quatre paliers exacts — à une amplitude d'1 dp, la différence ne se voit pas.
 */
private fun Modifier.tremblementEpaule1960(active: Boolean): Modifier = composed {
    if (!active) return@composed this
    val transition = rememberInfiniteTransition(label = "image-tremblement-1960")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "tremblement",
    )
    val position = POSITIONS_TREMBLEMENT_1960[(t * 6).toInt().coerceIn(0, 5) % POSITIONS_TREMBLEMENT_1960.size]
    graphicsLayer {
        translationX = position.x.dp.toPx()
        translationY = position.y.dp.toPx()
    }
}

private val POSITIONS_TREMBLEMENT_1960 = listOf(Offset(0f, 0f), Offset(1f, -1f), Offset(-1f, 1f), Offset(0f, 0f))

/**
 * 2000 (§G suite) : la grille de pixels sur l'image pendant l'entrée — tenue puis effacée
 * (`steps(1)` du delta), calée sur la même `progressionEntree` que le décodage du titre
 * (`modifierEntreeTitre`, `CartonTitre.kt`, 1,1 s) plutôt que sur les 1,6 s propres au delta, pour
 * ne pas ouvrir un second chronomètre pour ce monde — rattrapage du crochet manquant de la
 * livraison 4.
 */
private fun Modifier.grillePixels2000(progressionEntree: Float?): Modifier = drawWithContent {
    drawContent()
    if (progressionEntree == null || progressionEntree > 0.8f) return@drawWithContent
    val couleur = Color(0xFF56C4E0).copy(alpha = 0.12f)
    val pas = 20.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(couleur, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        x += pas
    }
    var y = 0f
    while (y < size.height) {
        drawLine(couleur, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        y += pas
    }
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
