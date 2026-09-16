package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier

/**
 * Le détail d'une année de la Frise (brief du 15 septembre 2026), augmenté du Voyage (brief du
 * 16 septembre 2026, phase 1 « le moteur ») : le cartouche kitsch (récit et faits, ou plié sur une
 * année verrouillée) puis « Les essentiels » — une ligne par film essentiel, avec mon état sur
 * chacun — avant la grille des films vus cette année-là, inchangée.
 */
@Composable
fun AnneeScreen(
    annee: AnneeFrise,
    vm: AnneeViewModel,
    onBack: () -> Unit,
    onOpenVu: (JournalItem) -> Unit,
    onOpenAVoir: (PlexFilm) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var feuillePour by remember { mutableStateOf<EssentielAnneeUi?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            val ecart = 8.dp
            val largeur = (maxWidth - ecart * 2) / 3
            val hauteur = largeur * 1.5f

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                        Text(annee.annee?.toString() ?: "Sans année", style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (ui.statutVoyage != null) {
                    item { Cartouche(ui) }
                }

                if (ui.essentiels.isNotEmpty()) {
                    item { Text("Les essentiels", style = MaterialTheme.typography.titleMedium) }
                    items(ui.essentiels, key = { it.tmdbId }) { essentiel ->
                        LigneEssentiel(
                            essentiel = essentiel,
                            onOuvrirPlex = { onOpenAVoir(essentiel.toPlexFilm(ui.annee)) },
                            onDemander = { vm.demander(essentiel.tmdbId) },
                            onLongClick = { if (essentiel.etat != "vu") feuillePour = essentiel },
                        )
                    }
                }

                if (annee.vus.isEmpty()) {
                    item {
                        Text(
                            "Rien vu cette année.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        TuilesEnLignes(annee.vus.chunked(3), ecart) { item ->
                            Box(Modifier.clickable { onOpenVu(item) }) {
                                Cover(item.media.cover_url, item.media.title, largeur, hauteur)
                                item.entry.rating?.let { note ->
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .clearAndSetSemantics { contentDescription = "Note $note sur 10" },
                                    ) {
                                        Text(
                                            "$note",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (annee.aVoir.isNotEmpty()) {
                    item { Text("À voir sur le Plex", style = MaterialTheme.typography.titleMedium) }
                    item {
                        TuilesEnLignes(annee.aVoir.chunked(3), ecart) { film ->
                            Box(
                                Modifier
                                    .clickable { onOpenAVoir(film) }
                                    .dashedBorder(MaterialTheme.colorScheme.onSurfaceVariant, cornerRadius = 8.dp),
                            ) {
                                Cover(film.cover_url, film.title, largeur, hauteur)
                            }
                        }
                    }
                }
            }
        }
    }

    feuillePour?.let { essentiel ->
        IntrouvableEssentielSheet(
            essentiel = essentiel,
            onMarquer = { feuillePour = null; vm.marquerIntrouvable(essentiel.tmdbId) },
            onRetirer = { feuillePour = null; vm.retirerIntrouvable(essentiel.tmdbId) },
            onDismiss = { feuillePour = null },
        )
    }
}

/**
 * Le cartouche kitsch (papier jauni sur le fond noir, cadre ornementé simple) : le récit et les
 * faits d'une année faite ou ouverte, relus toutes les cinq secondes tant que la chronique est en
 * préparation (dix fois au plus) ; plié — fond, cadenas, compte — sur une année verrouillée.
 */
@Composable
private fun Cartouche(ui: AnneeUi) {
    val shape = RoundedCornerShape(8.dp)
    val plie = ui.statutVoyage == StatutAnneeVoyage.VERROUILLEE

    Box(
        Modifier
            .fillMaxWidth()
            .background(if (plie) MaterialTheme.colorScheme.surfaceContainerHigh else PapierJauni, shape)
            .let { if (plie) it else it.border(1.dp, CadrePapier, shape).ornemente() }
            .padding(16.dp),
    ) {
        if (plie) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    ui.essentielsTotal?.let { total ->
                        val restants = total - (ui.essentielsFaits ?: 0)
                        val motEssentiel = if (restants == 1) "essentiel t’attend" else "essentiels t’attendent"
                        val avance = (ui.essentielsFaits ?: 0).takeIf { it > 0 }
                        "$restants $motEssentiel" + (avance?.let { " · $it vus en avance" } ?: "")
                    } ?: "Cette année n’est pas encore ouverte",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ui.annee.toString(), style = MaterialTheme.typography.titleLarge, color = TextePapier)
                    if (ui.statutVoyage == StatutAnneeVoyage.FAITE) {
                        Text("★", style = MaterialTheme.typography.titleMedium, color = TextePapier)
                    }
                }
                when (ui.etatChronique) {
                    EtatChronique.PRETE -> {
                        ui.recit?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextePapier) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ui.faits.forEach { fait ->
                                Text("· $fait", style = MaterialTheme.typography.bodyMedium, color = TextePapier)
                            }
                        }
                    }
                    EtatChronique.EN_PREPARATION -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TextePapier)
                        Text("Le chroniqueur écrit…", style = MaterialTheme.typography.bodyMedium, color = TextePapier)
                    }
                    EtatChronique.ABANDON -> Text(
                        "Le chroniqueur reviendra plus tard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextePapier,
                    )
                    EtatChronique.NON_CONFIGURE -> {}
                }
            }
        }
    }
}

/** Le cadre ornementé simple du cartouche : un second liseré, en retrait, dans le même ton que le premier. */
private fun Modifier.ornemente(): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = CadrePapier,
        topLeft = androidx.compose.ui.geometry.Offset(4.dp.toPx(), 4.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneEssentiel(
    essentiel: EssentielAnneeUi,
    onOuvrirPlex: () -> Unit,
    onDemander: () -> Unit,
    onLongClick: () -> Unit,
) {
    val introuvable = essentiel.etat == "introuvable"
    Row(
        Modifier
            .fillMaxWidth()
            .let {
                if (essentiel.etat == "vu") it else it.combinedClickable(
                    onClick = { if (essentiel.etat == "sur_le_plex") onOuvrirPlex() },
                    onLongClick = onLongClick,
                )
            }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            essentiel.rang.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Cover(essentiel.coverUrl, essentiel.title, 40.dp, 60.dp)
        Column(Modifier.weight(1f)) {
            Text(
                essentiel.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (introuvable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(essentiel.realisateur, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(essentiel.pourquoi, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            essentiel.etat == "vu" -> essentiel.note?.let { note ->
                Text(
                    "$note",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Note $note sur 10" },
                )
            }
            essentiel.etat == "sur_le_plex" -> Text(
                "sur ton Plex",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            introuvable -> Text(
                "introuvable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            essentiel.etat == "a_trouver" && essentiel.demande -> Text(
                "demandé",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            essentiel.etat == "a_trouver" -> TextButton(onClick = onDemander) { Text("Demander sur Sir") }
        }
    }
}

/** Jumelle d'`IntrouvableSheet` (`ui/suivis/FicheSuiviScreen.kt`), pour un essentiel du Voyage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntrouvableEssentielSheet(
    essentiel: EssentielAnneeUi,
    onMarquer: () -> Unit,
    onRetirer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (essentiel.etat == "introuvable") {
                TextButton(onClick = onRetirer) { Text("Le remettre à voir") }
            } else {
                TextButton(onClick = onMarquer) { Text("Marquer introuvable") }
            }
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    }
}

/** Le même formulaire pré-rempli qu'un « à voir » du Plex (`PlexFilm.toSearchResult()`). */
private fun EssentielAnneeUi.toPlexFilm(annee: Int?): PlexFilm = PlexFilm(
    tmdb_id = tmdbId,
    title = title,
    original_title = title,
    year = year ?: annee,
    cover_url = coverUrl,
    demande_le = "",
)

/** Une grille de tuiles, trois par ligne — même agencement que l'accueil et « Au ciné ». */
@Composable
private fun <T> TuilesEnLignes(rangees: List<List<T>>, ecart: Dp, tuile: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ecart)) {
        rangees.forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(ecart), modifier = Modifier.fillMaxWidth()) {
                rangee.forEach { tuile(it) }
            }
        }
    }
}

/**
 * Le liseré pointillé qui distingue une tuile « à voir » d'une tuile vue (brief du 15 septembre
 * 2026) : pas de note, pas de coche, juste ce contour. Repris tel quel (couleur libre) par
 * l'étagère du rayon d'une décennie (`DecennieScreen`, brief du 16 septembre 2026), d'où la
 * visibilité de paquet.
 */
internal fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
    }
