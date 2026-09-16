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
import androidx.compose.material3.TextButton
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
import fr.mediatheque.journal.api.dto.EssentielVoyage
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.showBriefly
import kotlin.math.PI
import kotlin.math.sin
import java.time.LocalDate

/**
 * Le Voyage, la carte (brief du 16 septembre 2026, phase 2) — l'écran qui remplace le calendrier
 * du siècle sur l'onglet « Frise ».
 *
 * Une pellicule serpente du haut vers le bas, un photogramme par année, regroupés en **mondes**
 * (une décennie, `Mondes.kt`) que ferme une marquise de cinéma. En tête, le HUD : le chapitre, les
 * années faites, les récompenses de festival. En bas, la carte « Prochaine étape ». Le clap de
 * l'icône marque l'année en cours et claque quand la frontière avance.
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
    onVoirEssentiel: (PlexFilm) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val anneeActuelle = remember { LocalDate.now().year }
    val liste = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var claques by remember { mutableIntStateOf(0) }
    var decennieAllumee by remember { mutableIntStateOf(0) }

    val cellules = remember(ui.voyage, ui.annees, ui.decennies, anneeActuelle) {
        construireCarte(ui, anneeActuelle)
    }
    val frontiere = ui.voyage.frontiere

    // À l'ouverture, la liste défile jusqu'à l'année en cours (brief, item 1). `frontiere` en clé
    // plutôt que `Unit` : si elle avance pendant qu'on est sur l'écran, la carte suit le clap.
    LaunchedEffect(frontiere, cellules.size) {
        val index = cellules.indexOfFirst { it is Cellule.Annee && it.annee == frontiere }
        if (index >= 0) liste.scrollToItem(index)
    }

    // La frontière qui avance (brief, item 9) : le clap claque, la snackbar dit l'année dans la
    // boîte, et une décennie bouclée allume sa marquise puis ouvre son générique.
    LaunchedEffect(Unit) {
        vm.avancees.collect { avancee ->
            claques += 1
            val recompense = recompenseDeLAnnee(avancee.anneeBouclee, vm.ui.value.voyage)
            snackbar.showBriefly(
                "${avancee.anneeBouclee} dans la boîte !" + (recompense?.let { " · ${it.singulier}" } ?: ""),
            )
            avancee.decennieBouclee?.let { decennie ->
                decennieAllumee = decennie
                vm.ui.value.passeport.firstOrNull { it.decennie == decennie }?.let(onOpenGenerique)
            }
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }

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
            ProchaineEtapeCard(
                essentiel = ui.voyage.frontiere?.let { prochaineEtape(ui.voyage.parAnnee[it]?.essentiels.orEmpty()) },
                annee = ui.voyage.frontiere,
                demande = ui.demandes,
                onVoir = onVoirEssentiel,
                onDemander = vm::demander,
            )
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
        val recompense: Recompense?,
        val vus: Int,
        val essentielsFaits: Int,
        val essentielsTotal: Int,
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
                recompense = recompenseFaite(statutAnneeVoyage(fragment?.statut), fragment?.essentiels_total, fragment?.essentiels_faits),
                vus = fragment?.vus ?: groupe.vus.size,
                essentielsFaits = fragment?.essentiels_faits ?: 0,
                essentielsTotal = fragment?.essentiels_total ?: 0,
                groupe = groupe,
                xEntree = ancreDe(rang - 1) / 2f + ancreDe(rang) / 2f,
                xAncre = ancreDe(rang),
                xSortie = ancreDe(rang) / 2f + ancreDe(rang + 1) / 2f,
            )
            rang += 1
        }
        cellules += Cellule.Marquise(
            monde = monde,
            bouclee = annees.isNotEmpty() && annees.all { statutAnneeVoyage(ui.voyage.parAnnee[it]?.statut) == StatutAnneeVoyage.FAITE },
            rayon = ui.decennies.firstOrNull { it.decennie == decennie }
                ?: DecennieFrise(decennie, 0, 0, emptyList(), (decennie until decennie + 10).map { AnneeDecennie(it, 0, 0) }),
        )
    }
    return cellules
}

// --- Les morceaux de l'écran -------------------------------------------------------------------

/** Le HUD : « Chapitre I · Les origines », « 3 années faites · 1898 en cours », les récompenses. */
@Composable
private fun Hud(voyage: VoyageUi) {
    val faites = voyage.parAnnee.values.count { statutAnneeVoyage(it.statut) == StatutAnneeVoyage.FAITE }
    val recompenses = phraseRecompenses(voyage)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            voyage.frontiere?.let { chapitreDe(it) } ?: "Le Voyage",
            style = MaterialTheme.typography.titleLarge,
            color = voyage.frontiere?.let { mondeDe(it).accent } ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "$faites ${if (faites <= 1) "année faite" else "années faites"}" +
                (voyage.frontiere?.let { " · $it en cours" } ?: ""),
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

/** Le photogramme : 54 × 40 dp, posé sur la bande (brief, item 1). */
private val LARGEUR_PHOTOGRAMME = 54.dp
private val HAUTEUR_PHOTOGRAMME = 40.dp

@Composable
private fun CelluleAnnee(cellule: Cellule.Annee, claques: Int, onClick: () -> Unit) {
    val monde = cellule.monde
    val enCours = cellule.statut == StatutAnneeVoyage.OUVERTE
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

        // Le cône de lumière corail de l'année en cours (brief, item 1) : un dégradé radial posé
        // sous le photogramme, jamais une couleur de plus dans le thème.
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
    val faite = cellule.statut == StatutAnneeVoyage.FAITE
    val enCours = cellule.statut == StatutAnneeVoyage.OUVERTE
    val recompense = cellule.recompense
    val description = when {
        faite -> "${cellule.annee}, année faite" + (recompense?.let { ", ${it.singulier}" } ?: "")
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
                        faite -> it.border(1.5.dp, LiserreDore, shape)
                        enCours -> it.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                        else -> it.dashedBorder(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), cornerRadius = 3.dp, strokeWidth = 1.dp)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (cellule.affiche != null && (faite || enCours)) {
                Cover(cellule.affiche, "${cellule.annee}", LARGEUR_PHOTOGRAMME, HAUTEUR_PHOTOGRAMME)
            } else {
                Text(
                    cellule.annee.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (faite || enCours) monde.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val sous = when {
            enCours -> "Tu es ici · ${cellule.essentielsFaits}/${cellule.essentielsTotal}"
            faite -> cellule.annee.toString()
            cellule.vus > 0 -> "${cellule.vus} vu${if (cellule.vus > 1) "s" else ""} en avance"
            else -> "à tourner"
        }
        Row(
            Modifier.padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (faite && recompense != null) {
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

/**
 * La porte d'un monde : une marquise de cinéma (brief, item 4). Ses ampoules sont éteintes tant
 * que la décennie n'est pas bouclée, et s'allument une à une (une seconde et demie) quand elle
 * vient de l'être — d'un coup, sans animation, sur une décennie bouclée depuis longtemps qu'on ne
 * fait que dérouler.
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

/**
 * La carte « Prochaine étape », fixée en bas (brief, item 6) : le premier essentiel de l'année en
 * cours qui n'est ni vu ni introuvable, son état, et le geste qui va avec — « Voir » (formulaire
 * pré-rempli) s'il est sur le Plex, « Demander sur Sir » sinon.
 */
@Composable
private fun ProchaineEtapeCard(
    essentiel: EssentielVoyage?,
    annee: Int?,
    demande: Set<Int>,
    onVoir: (PlexFilm) -> Unit,
    onDemander: (Int) -> Unit,
) {
    if (essentiel == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(essentiel.cover_url, essentiel.title, 40.dp, 60.dp)
        Column(Modifier.weight(1f)) {
            Text("Prochaine étape", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(essentiel.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${essentiel.realisateur}${essentiel.year?.let { ", $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            essentiel.etat == "sur_le_plex" -> TextButton(onClick = { onVoir(essentiel.versPlexFilm(annee)) }) { Text("Voir") }
            essentiel.tmdb_id in demande -> Text(
                "demandé",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> TextButton(onClick = { onDemander(essentiel.tmdb_id) }) { Text("Demander sur Sir") }
        }
    }
}

/** Le même formulaire pré-rempli qu'un « à voir » du Plex (`PlexFilm.toSearchResult()`). */
internal fun EssentielVoyage.versPlexFilm(annee: Int?): PlexFilm = PlexFilm(
    tmdb_id = tmdb_id,
    title = title,
    original_title = title,
    year = year ?: annee,
    cover_url = cover_url,
    demande_le = "",
)

/** Le liseré doré d'un photogramme fait (brief, item 1) — une décoration ponctuelle, comme `PapierJauni`. */
private val LiserreDore = Color(0xFFE6B94A)
