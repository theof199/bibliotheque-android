package fr.mediatheque.journal.ui.realisateurs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.FilmDeRealisateur
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock

/**
 * La fiche d'un réalisateur suivi (brief du 15 septembre 2026) : sa filmographie dans l'ordre,
 * de la plus ancienne sortie à la plus récente, avec sa note à droite quand je l'ai vu. Le
 * premier film non vu porte une pastille corail « à voir » — c'est celui que la liste annonce
 * sous son nom, et celui que l'accueil met dans « Ensuite » quand c'est lui le réalisateur en
 * cours.
 *
 * Empilée depuis `Screen.Realisateurs`, sans barre du bas. Elle lit le `ViewModel` partagé
 * plutôt que de recharger : la liste a déjà tiré les filmographies.
 */
@Composable
fun RealisateurScreen(
    vm: RealisateursViewModel,
    tmdbId: Int,
    onBack: () -> Unit,
    onOuvrirVu: (JournalItem) -> Unit,
    onOuvrirAVoir: (SearchResult) -> Unit,
    onSupprimer: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val realisateur = ui.realisateurs.firstOrNull { it.tmdb_id == tmdbId }
    val etat = ui.filmographies[tmdbId] ?: EtatFilmographie.EnAttente
    var confirmation by remember { mutableStateOf(false) }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Ne plus suivre ${realisateur?.name ?: "ce réalisateur"} ?") },
            text = { Text("Sa filmographie disparaîtra de la liste. Tes films vus, eux, restent au journal.") },
            confirmButton = {
                TextButton(onClick = { confirmation = false; onSupprimer() }) { Text("Ne plus suivre") }
            },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Annuler") } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    realisateur?.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmation = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Ne plus suivre")
                }
            }

            when (etat) {
                EtatFilmographie.EnAttente -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                EtatFilmographie.Indisponible -> ErrorBlock(
                    "Sa filmographie est indisponible.",
                    retryable = true,
                    onRetry = { vm.rechargerFilmographie(tmdbId) },
                    modifier = Modifier.padding(16.dp),
                )

                is EtatFilmographie.Pret -> {
                    // L'index plutôt que l'identifiant : c'est la *place* du premier film non vu
                    // qui porte la pastille, et deux entrées ne peuvent pas se la disputer.
                    val indexProchain = etat.films.indexOfFirst { it.vu == null }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(etat.films) { index, film ->
                            LigneFilm(
                                film = film,
                                aVoir = index == indexProchain,
                                onClick = {
                                    val entree = film.vu?.let { ui.entrees[it.entry_id] }
                                    // Un film vu ouvre *son* entrée de journal, jamais un
                                    // formulaire de création : la rouvrir en création ajouterait
                                    // un second visionnage au lieu de corriger celui-ci. Tant que
                                    // le journal n'est pas relu (`chargerEntrees`), l'entrée
                                    // manque et la ligne ne fait rien — mieux que d'ouvrir le
                                    // mauvais écran.
                                    when {
                                        entree != null -> onOuvrirVu(entree)
                                        film.vu == null -> onOuvrirAVoir(film.toSearchResult(realisateur?.name))
                                        else -> Unit
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneFilm(film: FilmDeRealisateur, aVoir: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            film.year?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Cover(film.cover_url, film.title, 30.dp, 45.dp)
        Text(film.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val note = film.vu?.rating
        when {
            note != null -> Text(
                "$note",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Note $note sur 10" },
            )

            aVoir -> Text(
                "à voir",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
