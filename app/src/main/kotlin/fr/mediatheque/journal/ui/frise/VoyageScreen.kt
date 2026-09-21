package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.showBriefly
import kotlin.math.PI
import kotlin.math.sin
import java.time.LocalDate

/**
 * Le Voyage, la carte — l'écran de l'onglet « Frise ».
 *
 * Une pellicule serpente du haut vers le bas, un photogramme par année, regroupés en **mondes**
 * (une décennie, `Mondes.kt`) que ferme une marquise de cinéma. En tête, le HUD : le chapitre, les
 * années visitées, les récompenses de festival. Le clap de l'icône marque l'année en cours et
 * claque quand elle avance.
 *
 * Réécrit pour le brief du 21 septembre 2026 (« l'année en étages ») : `GET /me/voyage` ne sert
 * plus ni frontière ni essentiels — une année **ouverte** (avant l'année en cours) reste creusable
 * pour toujours, elle n'est jamais « faite ». Le photogramme d'une année ouverte montre donc sa
 * profondeur et l'affiche de son dernier film vu (le podium, qui la remplacera, viendra à l'étape
 * 2), et la carte « Prochaine étape » a disparu avec les essentiels qui la nourrissaient. Les
 * glyphes de récompense, le compte du HUD, la marquise allumée et le passeport restent dans le
 * code (`Recompense`, `phraseRecompenses`, `tamponsPasseport`) mais ne s'affichent que si le back
 * en donne — il n'en donne aucun à cette étape.
 *
 * L'écran ne charge rien lui-même : `FriseViewModel` tient déjà le journal, le Plex et
 * `GET /me/voyage` pour l'accueil comme pour ici (`Root.kt`, clé « frise »).
 */
@Composable
fun VoyageScreen(
    vm: FriseViewModel,
    onOpenAnnee: (AnneeFrise) -> Unit,
    onOpenDecennie: (DecennieFrise) -> Unit,
    onOpenGenerique: (TamponDecennie) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val anneeActuelle = remember { LocalDate.now().year }
    val liste = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var claques by remember { mutableIntStateOf(0) }
    var decennieAllumee by remember { mutableIntStateOf(0) }

    val cellules = remember(ui.voyage, ui.annees, ui.decennies, ui.passeport, anneeActuelle) {
        construireCarte(ui, anneeActuelle)
    }
    val anneeEnCours = ui.voyage.anneeEnCours

    // À l'ouverture, la liste défile jusqu'à l'année en cours. `anneeEnCours` en clé plutôt que
    // `Unit` : si elle avance pendant qu'on est sur l'écran (le bouton provisoire d'`AnneeScreen`),
    // la carte suit le clap au prochain chargement.
    LaunchedEffect(anneeEnCours, cellules.size) {
        val index = cellules.indexOfFirst { it is Cellule.Annee && it.annee == anneeEnCours }
        if (index >= 0) liste.scrollToItem(index)
    }

    // L'année en cours qui avance : le clap claque, la snackbar dit l'année dans la boîte, et une
    // décennie bouclée allume sa marquise puis ouvre son générique — silencieux à cette étape, le
    // passeport restant vide tant que le back ne boucle aucune décennie (§6 de la spec).
    LaunchedEffect(Unit) {
        vm.avancees.collect { avancee ->
            claques += 1
            snackbar.showBriefly("${avancee.anneeBouclee} dans la boîte !")
            avancee.decennieBouclee?.let { decennie ->
                decennieAllumee = decennie
                vm.ui.value.passeport.firstOrNull { it.decennie == decennie }?.let(onOpenGenerique)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Hud(ui.voyage)
            LazyColumn(state = liste, modifier = Modifier.weight(1f)) {
                items(cellules.size) { index ->
                    when (val cellule = cellules[index]) {
                        is Cellule.Titre -> TitreDeMonde(cellule.monde)
                        is Cellule.Annee -> CelluleAnnee(
                            cellule = cellule,
                            claques = claques,
                            onClick = { onOpenAnnee(cellule.groupe) },
                        )
                        is Cellule.Marquise -> Marquise(
                            monde = cellule.monde,
                            bouclee = cellule.bouclee,
                            anime = cellule.monde.decennie == decennieAllumee,
                            onClick = { onOpenDecennie(cellule.rayon) },
                        )
                    }
                }
            }
        }
    }
}

// --- La carte, mise à plat en cellules ---------------------------------------------------------

/**
 * Une ligne de la carte. Mise à plat plutôt qu'imbriquée : la `LazyColumn` ne compose que ce qui
 * est à l'écran, et le défilement jusqu'à l'année en cours a besoin d'un index, pas d'un arbre.
 */
private sealed interface Cellule {
    data class Titre(val monde: Monde) : Cellule
    data class Annee(
        val annee: Int,
        val monde: Monde,
        val statut: StatutAnneeVoyage?,
        val affiche: String?,
        /** Nulle à cette étape : le back ne sert encore aucune récompense (`VoyageCarte.kt`). */
        val recompense: Recompense?,
        val profondeur: Int,
        val groupe: AnneeFrise,
        /** Les trois abscisses du segment de pellicule, en fraction de largeur : entrée, ancre, sortie. */
        val xEntree: Float,
        val xAncre: Float,
        val xSortie: Float,
    ) : Cellule
    data class Marquise(val monde: Monde, val bouclee: Boolean, val rayon: DecennieFrise) : Cellule
}

/** L'abscisse d'une année, en fraction de largeur : un serpentin de période quatre, centré. */
private fun ancreDe(rang: Int): Float = 0.5f + 0.30f * sin(rang * PI / 2).toFloat()

/**
 * Met la carte à plat, du départ du Voyage à l'année en cours, monde par monde.
 *
 * Le Voyage commence à `depart` (1895) quoi que le journal contienne de plus ancien : un film de
 * 1888 se range dans les origines sans ouvrir d'année avant le départ. Les années postérieures à
 * aujourd'hui n'existent pas non plus — rien à tourner dans le futur.
 */
private fun construireCarte(ui: FriseUi, anneeActuelle: Int): List<Cellule> {
    val depart = ui.voyage.depart
    if (anneeActuelle < depart) return emptyList()

    val cellules = mutableListOf<Cellule>()
    var rang = 0
    val premiereDecennie = mondeDe(depart).decennie
    val derniereDecennie = mondeDe(anneeActuelle).decennie

    (premiereDecennie..derniereDecennie step 10).forEach { decennie ->
        val monde = mondeDeLaDecennie(decennie)
        cellules += Cellule.Titre(monde)
        val annees = (maxOf(decennie, depart)..minOf(decennie + 9, anneeActuelle)).toList()
        annees.forEach { annee ->
            val fragment = ui.voyage.parAnnee[annee]
            val groupe = ui.annees.firstOrNull { it.annee == annee } ?: AnneeFrise(annee, emptyList(), emptyList())
            cellules += Cellule.Annee(
                annee = annee,
                monde = monde,
                statut = statutAnneeVoyage(fragment?.statut),
                affiche = groupe.vus.firstNotNullOfOrNull { it.media.cover_url },
                recompense = null,
                profondeur = fragment?.profondeur ?: groupe.vus.size,
                groupe = groupe,
                xEntree = ancreDe(rang - 1) / 2f + ancreDe(rang) / 2f,
                xAncre = ancreDe(rang),
                xSortie = ancreDe(rang) / 2f + ancreDe(rang + 1) / 2f,
            )
            rang += 1
        }
        cellules += Cellule.Marquise(
            monde = monde,
            // Toujours éteinte à cette étape : une décennie ne se boucle qu'à l'étape 3 (le
            // ticket) et l'étape 5 (l'Ours par année) — `ui.passeport` reste vide jusque-là.
            bouclee = ui.passeport.any { it.decennie == decennie },
            rayon = ui.decennies.firstOrNull { it.decennie == decennie }
                ?: DecennieFrise(decennie, 0, 0, emptyList(), (decennie until decennie + 10).map { AnneeDecennie(it, 0, 0) }),
        )
    }
    return cellules
}

// --- Les morceaux de l'écran -------------------------------------------------------------------

/** Le HUD : « Chapitre I · Les origines », « 3 années visitées · 1898 en cours », les récompenses. */
@Composable
private fun Hud(voyage: VoyageUi) {
    val visitees = voyage.parAnnee.values.count { it.visitee }
    // Toujours vide à cette étape (`VoyageCarte.kt`) : la ligne ne s'affiche donc jamais, sans
    // qu'il faille un `if` de plus ici — c'est `phraseRecompenses` elle-même qui rend "".
    val recompenses = phraseRecompenses(emptyList())
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            chapitreDe(voyage.anneeEnCours),
            style = MaterialTheme.typography.titleLarge,
            color = mondeDe(voyage.anneeEnCours).accent,
        )
        Text(
            "$visitees ${if (visitees <= 1) "année visitée" else "années visitées"} · ${voyage.anneeEnCours} en cours",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recompenses.isNotEmpty()) {
            Text(recompenses, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Le titre d'un monde, en tête de sa section : la lettrine, le nom espacé, le sous-titre.
 *
 * Manrope reste la seule police (design §3) : l'accent d'un monde se fait par la graisse, la
 * casse, l'espacement des lettres et cette lettrine, jamais par une famille de plus.
 */
@Composable
private fun TitreDeMonde(monde: Monde) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { motifDeMonde(monde.motif, monde.accent, monde.decennie) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            monde.nom.first { it.isLetter() }.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = monde.accent,
        )
        Column {
            Text(
                "${monde.decennie} · ${monde.nom.uppercase()}",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = monde.accent,
            )
            Text(monde.sousTitre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La hauteur d'une cellule d'année : la pellicule y fait une demi-ondulation. */
private val HAUTEUR_CELLULE = 104.dp

/** Le photogramme : 54 × 40 dp, posé sur la bande. */
private val LARGEUR_PHOTOGRAMME = 54.dp
private val HAUTEUR_PHOTOGRAMME = 40.dp

@Composable
private fun CelluleAnnee(cellule: Cellule.Annee, claques: Int, onClick: () -> Unit) {
    val monde = cellule.monde
    val enCours = cellule.statut == StatutAnneeVoyage.EN_COURS
    val bandeFond = monde.accent.copy(alpha = 0.16f)
    val perforation = monde.fond
    // Les couleurs se lisent ici, dans la composition : un `DrawScope` n'est pas composable et ne
    // sait pas atteindre `MaterialTheme` (le même piège que `eteinte` dans `Marquise`).
    val corail = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(HAUTEUR_CELLULE)
            .background(monde.fond)
            .drawBehind { motifDeMonde(monde.motif, monde.accent, monde.decennie * 100 + cellule.annee) },
    ) {
        val largeur = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            segmentDePellicule(
                xEntree = size.width * cellule.xEntree,
                xAncre = size.width * cellule.xAncre,
                xSortie = size.width * cellule.xSortie,
                largeur = LARGEUR_PELLICULE.toPx(),
                couleurBande = bandeFond,
                couleurPerforation = perforation,
            )
        }

        // Le cône de lumière corail de l'année en cours : un dégradé radial posé sous le
        // photogramme, jamais une couleur de plus dans le thème.
        if (enCours) {
            Canvas(Modifier.fillMaxSize()) {
                val centre = Offset(size.width * cellule.xAncre, size.height / 2f)
                val rayon = size.height * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(corail.copy(alpha = 0.35f), Color.Transparent),
                        center = centre,
                        radius = rayon,
                    ),
                    radius = rayon,
                    center = centre,
                )
            }
        }

        Photogramme(
            cellule = cellule,
            modifier = Modifier
                .offset(
                    x = largeur * cellule.xAncre - LARGEUR_PHOTOGRAMME / 2,
                    y = HAUTEUR_CELLULE / 2 - HAUTEUR_PHOTOGRAMME / 2 - 6.dp,
                )
                .clickable(onClick = onClick),
        )

        if (enCours) {
            ClapAvatar(
                claques = claques,
                modifier = Modifier
                    .size(44.dp)
                    .offset(
                        x = (largeur * cellule.xAncre + LARGEUR_PHOTOGRAMME / 2 + 6.dp)
                            .coerceAtMost(largeur - 46.dp),
                        y = HAUTEUR_CELLULE / 2 - 30.dp,
                    ),
            )
        }
    }
}

@Composable
private fun Photogramme(cellule: Cellule.Annee, modifier: Modifier = Modifier) {
    val monde = cellule.monde
    val shape = RoundedCornerShape(3.dp)
    // Une année ouverte reste creusable pour toujours (brief du 21 septembre 2026) : elle n'est
    // jamais « faite », mais porte la même mise en avant (liseré doré, affiche) que l'ancienne
    // année faite — c'est elle, avant l'année en cours, qu'on peut déjà visiter.
    val ouverte = cellule.statut == StatutAnneeVoyage.OUVERTE
    val enCours = cellule.statut == StatutAnneeVoyage.EN_COURS
    val recompense = cellule.recompense
    val description = when {
        ouverte -> "${cellule.annee}, ${etiquetteProfondeur(cellule.profondeur)}" + (recompense?.let { ", ${it.singulier}" } ?: "")
        enCours -> "${cellule.annee}, tu es ici"
        else -> "${cellule.annee}, à tourner"
    }

    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(LARGEUR_PHOTOGRAMME, HAUTEUR_PHOTOGRAMME)
                .clip(shape)
                .background(Color.Black, shape)
                .let {
                    when {
                        ouverte -> it.border(1.5.dp, LiserreDore, shape)
                        enCours -> it.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                        else -> it.dashedBorder(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), cornerRadius = 3.dp, strokeWidth = 1.dp)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (cellule.affiche != null && (ouverte || enCours)) {
                Cover(cellule.affiche, "${cellule.annee}", LARGEUR_PHOTOGRAMME, HAUTEUR_PHOTOGRAMME)
            } else {
                Text(
                    cellule.annee.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ouverte || enCours) monde.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val sous = when {
            enCours -> "Tu es ici"
            ouverte -> etiquetteProfondeur(cellule.profondeur)
            cellule.profondeur > 0 -> "${cellule.profondeur} vu${if (cellule.profondeur > 1) "s" else ""} en avance"
            else -> "à tourner"
        }
        Row(
            Modifier.padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (ouverte && recompense != null) {
                Box(
                    Modifier
                        .size(12.dp)
                        .drawBehind { glypheRecompense(recompense, LiserreDore, monde.fond) },
                )
            }
            Text(
                sous,
                style = MaterialTheme.typography.labelSmall,
                color = if (enCours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** « 12 films », « 1 film », « 0 film » : la profondeur d'une année ouverte, sous son photogramme. */
private fun etiquetteProfondeur(profondeur: Int): String = "$profondeur ${if (profondeur == 1) "film" else "films"}"

/**
 * La porte d'un monde : une marquise de cinéma. Ses ampoules sont éteintes tant que la décennie
 * n'est pas bouclée, et s'allument une à une (une seconde et demie) quand elle vient de l'être —
 * d'un coup, sans animation, sur une décennie bouclée depuis longtemps qu'on ne fait que dérouler.
 */
@Composable
private fun Marquise(monde: Monde, bouclee: Boolean, anime: Boolean, onClick: () -> Unit) {
    val allumage = remember(monde.decennie) { Animatable(if (bouclee && !anime) 1f else 0f) }
    LaunchedEffect(bouclee, anime) {
        when {
            bouclee && anime -> allumage.animateTo(1f, tween(1_500))
            bouclee -> allumage.snapTo(1f)
            else -> allumage.snapTo(0f)
        }
    }

    val eteinte = MaterialTheme.colorScheme.outline
    val fondFacade = monde.accent.copy(alpha = 0.10f)
    Box(
        Modifier
            .fillMaxWidth()
            .background(monde.fond)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .height(112.dp)
            .clickable(onClick = onClick)
            .drawBehind { facadeDeMarquise(fondFacade, monde.accent, eteinte, allumage.value, ampoules = 9) }
            .semantics { contentDescription = "Années ${monde.decennie}, ${if (bouclee) monde.titreVoyageur else "décennie en cours"}" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(top = 18.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ANNÉES ${monde.decennie}",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                color = if (bouclee) monde.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (bouclee) monde.titreVoyageur.uppercase() else "en cours de tournage",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = if (bouclee) LiserreDore.copy(alpha = allumage.value) else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Le liseré doré d'un photogramme ouvert — une décoration ponctuelle, comme `PapierJauni`. */
private val LiserreDore = Color(0xFFE6B94A)
