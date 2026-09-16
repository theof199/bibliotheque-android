package fr.mediatheque.journal.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.ImportLetterboxdCandidate
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.ui.ErrorBlock

/**
 * L'import Letterboxd (brief du 16 septembre 2026) : un seul écran pour les deux états du
 * design (§5) — « Import en cours… » tant que `ui` ne porte ni rapport ni erreur, puis le
 * rapport ou le message d'erreur (ZIP illisible, en-têtes fausses, panne réseau…), rendu comme
 * partout ailleurs (`ErrorBlock`).
 *
 * Le rapport donne « *N* importés · *M* déjà présents » en tête, puis les lignes non reconnues
 * — nom, année, et leurs candidats s'il y en a : toucher l'un d'eux ouvre le formulaire
 * pré-rempli pour ce film, à la date et la note que le back a lues sur cette ligne
 * (`onCandidat`) — puis les erreurs. « Terminé » revient au profil, qui recharge ses chiffres à
 * chaque entrée (`ProfileScreen`, déjà le cas).
 */
@Composable
fun RapportImportScreen(
    ui: LetterboxdImportUi,
    onBack: () -> Unit,
    onCandidat: (ImportLetterboxdCandidate, Int) -> Unit,
    onTermine: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text("Import Letterboxd", style = MaterialTheme.typography.titleLarge)
            }

            when {
                ui.error != null -> Box(
                    Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorBlock(ui.error.message ?: "", retryable = false, onRetry = {})
                }
                ui.rapport == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Import en cours…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> Rapport(ui.rapport, onCandidat, onTermine, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Rapport(
    rapport: ImportLetterboxdResponse,
    onCandidat: (ImportLetterboxdCandidate, Int) -> Unit,
    onTermine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "${rapport.importes} importés · ${rapport.deja_presents} déjà présents",
            style = MaterialTheme.typography.titleMedium,
        )

        if (rapport.non_reconnus.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Non reconnus", style = MaterialTheme.typography.titleMedium)
            rapport.non_reconnus.forEach { ligne ->
                Column(Modifier.padding(top = 8.dp)) {
                    Text(titreAnnee(ligne.name, ligne.year), style = MaterialTheme.typography.bodyLarge)
                    if (ligne.candidats.isEmpty()) {
                        Text(
                            "Aucun candidat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ligne.candidats.forEach { candidat ->
                            ListItem(
                                headlineContent = { Text(titreAnnee(candidat.title, candidat.year)) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                                modifier = Modifier.clickable { onCandidat(candidat, ligne.ligne) },
                            )
                        }
                    }
                }
            }
        }

        if (rapport.erreurs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Erreurs", style = MaterialTheme.typography.titleMedium)
            rapport.erreurs.forEach {
                Text(
                    "Ligne ${it.ligne} : ${it.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onTermine, modifier = Modifier.fillMaxWidth()) { Text("Terminé") }
    }
}

private fun titreAnnee(titre: String, annee: Int?): String = if (annee != null) "$titre ($annee)" else titre
