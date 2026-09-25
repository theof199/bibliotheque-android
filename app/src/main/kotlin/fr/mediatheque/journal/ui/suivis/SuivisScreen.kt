package fr.mediatheque.journal.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.EtatVide
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * Ce que je suis — réalisateurs ou sagas (brief du 15 septembre 2026,
 * l'onglet « Réalisateurs » devient « Suivis ») : une ligne par entité, sa
 * photo ou son affiche ronde, son nom, et ce qu'il me reste à en voir. Deux
 * segments en tête (`SingleChoiceSegmentedButtonRow`) choisissent la source ;
 * le « + » ouvre la recherche de la source affichée ; le retour de celle-ci
 * fait apparaître « Ajouté » ici, en snackbar.
 */
@Composable
fun SuivisScreen(
    vm: SuivisViewModel,
    onAjouter: () -> Unit,
    onOuvrir: (SourceSuivi, Int) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val etat = if (ui.source == SourceSuivi.REALISATEURS) ui.realisateurs else ui.sagas
    val snackbar = remember { SnackbarHostState() }
    // Clé fixe, même raison que sur l'accueil : `vm.messages` est un événement à un coup, et une
    // clé qui bougerait à chaque message couperait la snackbar avant ses deux secondes.
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SourceSuivi.entries.forEachIndexed { index, source ->
                        SegmentedButton(
                            selected = ui.source == source,
                            onClick = { vm.selectionnerSource(source) },
                            shape = SegmentedButtonDefaults.itemShape(index, SourceSuivi.entries.size),
                            label = { Text(source.titre) },
                        )
                    }
                }
                IconButton(onClick = onAjouter) {
                    IconeTabler("plus", ui.source.libelleAjouter)
                }
            }

            etat.error?.let { e ->
                ErrorBlock(
                    e.message ?: "",
                    retryable = e.retryable,
                    onRetry = { vm.refresh(ui.source) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (etat.entites.isEmpty() && etat.error == null && !etat.loading) {
                // État vide (point 16 de la revue du 24 septembre 2026) : une icône, la phrase
                // déjà là (`libelleVide`), une action — « Ajouter » ouvre la même recherche que le
                // « + » de l'en-tête, plutôt qu'un texte seul et invisible sans lui.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EtatVide(
                        icone = if (ui.source == SourceSuivi.REALISATEURS) "user" else "movie",
                        phrase = ui.source.libelleVide,
                        libelleAction = "Ajouter",
                        onAction = onAjouter,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(etat.entites, key = { it.tmdbId }) { entite ->
                        CarteSuivi(
                            entite = entite,
                            etatFilmographie = etat.filmographies[entite.tmdbId] ?: EtatFilmographie.EnAttente,
                            onClick = { onOuvrir(ui.source, entite.tmdbId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Une carte par entité suivie (point 12 de la revue du 24 septembre 2026) : portrait ou affiche,
 * nom, une barre de progression « *N* sur *M* », puis l'affiche et le titre du prochain film à
 * voir — remplace la simple ligne (portrait 40 dp, nom, texte résumé) d'avant cette revue.
 * `EtatFilmographie.EnAttente`/`Indisponible` gardent un texte seul (`libelleLigne`), sans barre ni
 * prochain film à montrer.
 */
@Composable
private fun CarteSuivi(entite: EntiteSuivie, etatFilmographie: EtatFilmographie, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Portrait(entite.imageUrl, entite.nom, 48.dp)
            Text(entite.nom, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        when (etatFilmographie) {
            EtatFilmographie.EnAttente, EtatFilmographie.Indisponible -> Text(
                libelleLigne(etatFilmographie),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is EtatFilmographie.Pret -> {
                val films = etatFilmographie.films
                val vus = filmsVus(films)
                val total = films.size
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) vus.toFloat() / total else 0f },
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    )
                    Text(
                        "$vus sur $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                prochainAVoir(films)?.let { prochain ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(prochain.cover_url, prochain.title, 32.dp, 48.dp)
                        Text(titreEtAnnee(prochain), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * La photo d'une personne, ou l'affiche d'une saga : ronde, ou l'initiale sur
 * la même pastille quand TMDB n'en a pas. Jumeau de `Cover` (`ui/Cover.kt`)
 * pour les affiches rectangulaires — un `contentDescription` toujours posé,
 * photo ou non (design §8) ; rien *pendant* le chargement (aucun indicateur,
 * aucun repli tant que la requête est en vol — le commentaire d'ici disait
 * « rien pendant le chargement » sans plus de précision, corrigé par le geste
 * 9 du peaufinage du 23 septembre 2026, qui ajoute le fondu ci-dessous),
 * l'initiale restant le geste de l'absence d'image, pas celui d'une attente.
 *
 * `lisere` (Suivis, rétrospectives et cycles, 25 septembre 2026) : un trait intérieur de 1 dp, or
 * à 35 %, sur la photo comme sur l'initiale — le jumeau rond du liseré à 22 % des affiches.
 */
@Composable
fun Portrait(url: String?, name: String, taille: Dp, modifier: Modifier = Modifier, lisere: Boolean = false) {
    val description = "Photo de $name"
    val trait = if (lisere) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), CircleShape)
    } else {
        Modifier
    }
    val initiale: @Composable () -> Unit = {
        Box(
            Modifier.size(taille).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).then(trait),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (url == null) {
        Box(modifier.clearAndSetSemantics { contentDescription = description }) { initiale() }
    } else {
        val contexte = LocalContext.current
        SubcomposeAsyncImage(
            // `crossfade(200)` (geste 9) : jumeau de `Cover`, la photo apparaît en fondu.
            model = ImageRequest.Builder(contexte).data(url).crossfade(200).build(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            loading = {},
            error = { initiale() },
            modifier = modifier.size(taille).clip(CircleShape).then(trait),
        )
    }
}
