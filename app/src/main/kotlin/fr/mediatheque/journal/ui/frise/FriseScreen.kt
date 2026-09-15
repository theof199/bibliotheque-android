package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * La Frise (brief du 15 septembre 2026) : le cinéma du propriétaire, année
 * par année — ses vus, et ce qu'il lui reste à voir sur son Plex. En tête,
 * « Tu en es à *année* » (ou « Tout vu jusqu'ici »), absente si Seerr n'est
 * pas configuré côté back. La liste défile jusqu'à `anneeEnCours` à
 * l'ouverture, une fois les données là.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriseScreen(vm: FriseViewModel, onOpenAnnee: (AnneeFrise) -> Unit, bottomBar: @Composable () -> Unit) {
    val ui by vm.ui.collectAsState()
    val liste = rememberLazyListState()
    var dejaDefile by remember { mutableStateOf(false) }

    LaunchedEffect(ui.annees) {
        if (dejaDefile || ui.annees.isEmpty() || ui.anneeEnCours == null) return@LaunchedEffect
        dejaDefile = true
        indexDeLigne(ui.annees, ui.anneeEnCours)?.let { liste.scrollToItem(it) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            if (ui.plexConfigure) {
                Text(
                    ui.anneeEnCours?.let { "Tu en es à $it" } ?: "Tout vu jusqu’ici",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(state = liste, contentPadding = PaddingValues(bottom = 16.dp)) {
                var decennieCourante: Int? = null
                ui.annees.forEach { annee ->
                    val decennie = annee.annee?.let { (it / 10) * 10 }
                    if (decennie != decennieCourante) {
                        decennieCourante = decennie
                        stickyHeader {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Text(
                                    decennie?.let { "Années $it" } ?: "Sans année",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    item { LigneAnnee(annee, enCours = annee.annee == ui.anneeEnCours, onClick = { onOpenAnnee(annee) }) }
                }
            }
        }
    }
}

@Composable
private fun LigneAnnee(annee: AnneeFrise, enCours: Boolean, onClick: () -> Unit) {
    val total = annee.vus.size + annee.aVoir.size
    val progression = if (total == 0) 1f else annee.vus.size.toFloat() / total
    val texte = if (annee.aVoir.isEmpty()) {
        "${annee.vus.size} vus"
    } else {
        "${annee.vus.size} vus · ${annee.aVoir.size} à voir"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            annee.annee?.toString() ?: "Sans année",
            style = MaterialTheme.typography.titleMedium,
            color = if (enCours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { progression },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/**
 * L'index, dans la `LazyColumn` ci-dessus, de la ligne de l'année `cible` — en comptant les
 * en-têtes de décennie qui la précèdent, une par décennie nouvelle rencontrée dans l'ordre
 * (même règle que la boucle de rendu de `FriseScreen`, tenue à jour avec elle). `null` si
 * `cible` n'apparaît dans aucune année.
 */
private fun indexDeLigne(annees: List<AnneeFrise>, cible: Int?): Int? {
    var decennieCourante: Int? = null
    var indexRendu = 0
    for (annee in annees) {
        val decennie = annee.annee?.let { (it / 10) * 10 }
        if (decennie != decennieCourante) {
            decennieCourante = decennie
            indexRendu++
        }
        if (annee.annee == cible) return indexRendu
        indexRendu++
    }
    return null
}
