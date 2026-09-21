package fr.mediatheque.journal.ui.realisateur

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.RealisateurCredit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Le nom d'un réalisateur, touchable partout où il s'affiche (décision 3 du brief du 21 septembre
 * 2026, « la page réalisateur ») : fiche du Voyage, écran de correction, carte de soirée, fiche
 * simple d'un film, un seul composant pour les quatre.
 *
 * `nomConnu` porte le nom déjà affiché ailleurs (`film.realisateur` du Voyage, `media.director` de
 * la correction, le nom de la page dont vient la fiche simple) : affiché tel quel, sans attendre
 * le back. Nul sur la carte de soirée, qui n'a aucun nom à afficher tant que rien n'est résolu —
 * ce composant le résout alors lui-même pour l'afficher, dès la composition.
 *
 * Le tap appelle toujours `RealisateurResolveur.resoudre(filmTmdbId)` (en cache, décision 3) : un
 * seul → `onOuvrirRealisateur` directement ; plusieurs → une feuille, un nom par ligne ; aucun →
 * un bandeau, qui s'efface seul après deux secondes (jumeau de `showBriefly`, `Navigation.kt`, sans
 * `SnackbarHostState` : ce composant n'en tient pas un lui-même).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomRealisateurTouchable(
    filmTmdbId: Int,
    nomConnu: String?,
    resolveur: RealisateurResolveur,
    onOuvrirRealisateur: (Int) -> Unit,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val etats by resolveur.etats.collectAsState()
    val scope = rememberCoroutineScope()
    var plusieurs by remember { mutableStateOf<List<RealisateurCredit>?>(null) }
    var bandeau by remember { mutableStateOf(false) }

    if (nomConnu == null) {
        LaunchedEffect(filmTmdbId) { resolveur.resoudre(filmTmdbId) }
    }

    val etat = etats[filmTmdbId]
    val nomAffiche = nomConnu ?: (etat as? EtatRealisateursFilm.Pret)?.realisateurs?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.name }

    if (!nomAffiche.isNullOrEmpty()) {
        Text(
            nomAffiche,
            style = style,
            color = color,
            modifier = modifier.clickable {
                scope.launch {
                    when (val resultat = resultatTapRealisateur(resolveur.resoudre(filmTmdbId))) {
                        is ResultatRealisateur.Un -> onOuvrirRealisateur(resultat.tmdbId)
                        is ResultatRealisateur.Plusieurs -> plusieurs = resultat.realisateurs
                        ResultatRealisateur.Aucun -> bandeau = true
                    }
                }
            },
        )
    }

    if (bandeau) {
        LaunchedEffect(Unit) { delay(2_000); bandeau = false }
        Text(
            "TMDB ne connaît pas le réalisateur de ce film",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    plusieurs?.let { liste ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { plusieurs = null }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                liste.forEach { realisateur ->
                    Text(
                        realisateur.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { plusieurs = null; onOuvrirRealisateur(realisateur.tmdb_id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
