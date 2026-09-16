package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * La Frise, devenue calendrier du siècle (brief du 16 septembre 2026, remplaçant celui du 15) :
 * le cinéma du propriétaire, une ligne par décennie, une case par année — ses vus en teintes de
 * corail, ce qu'il lui reste à voir en liseré. En tête, « Tu en es à *année* → » (ou « Tout vu
 * jusqu'ici »), absente si Seerr n'est pas configuré côté back. Sans défilement si la grille
 * tient sur l'écran ; sinon la grille seule défile, la légende et « Sans année » restant fixes.
 */
@Composable
fun FriseScreen(
    vm: FriseViewModel,
    onOpenAnnee: (AnneeFrise) -> Unit,
    onOpenDecennie: (DecennieFrise) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val anneeActuelle = remember { LocalDate.now().year }

    fun anneeFrise(annee: Int): AnneeFrise = ui.annees.firstOrNull { it.annee == annee } ?: AnneeFrise(annee, emptyList(), emptyList())

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            if (ui.plexConfigure) {
                val enCours = ui.anneeEnCours
                Text(
                    enCours?.let { "Tu en es à $it →" } ?: "Tout vu jusqu’ici",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (enCours != null) it.clickable { onOpenAnnee(anneeFrise(enCours)) } else it }
                        .padding(16.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ui.decennies.forEach { decennie ->
                    LigneDecennie(
                        decennie = decennie,
                        anneeEnCours = ui.anneeEnCours,
                        anneeActuelle = anneeActuelle,
                        onOpenAnnee = { onOpenAnnee(anneeFrise(it)) },
                        onOpenDecennie = { onOpenDecennie(decennie) },
                    )
                }
            }
            Legende(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            ui.annees.firstOrNull { it.annee == null }?.let { sansAnnee ->
                val texte = if (sansAnnee.aVoir.isEmpty()) {
                    "Sans année · ${sansAnnee.vus.size} vus"
                } else {
                    "Sans année · ${sansAnnee.vus.size} vus · ${sansAnnee.aVoir.size} à voir"
                }
                Text(
                    texte,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAnnee(sansAnnee) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun LigneDecennie(
    decennie: DecennieFrise,
    anneeEnCours: Int?,
    anneeActuelle: Int,
    onOpenAnnee: (Int) -> Unit,
    onOpenDecennie: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            decennie.decennie.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp).clickable(onClick = onOpenDecennie).padding(vertical = 4.dp),
        )
        decennie.annees.forEach { annee ->
            if (annee.annee > anneeActuelle) {
                // Les années futures sont invisibles (le constat, point 1) : un espace muet
                // garde la case à sa place dans la grille sans rien montrer ni rien permettre.
                Spacer(Modifier.weight(1f).aspectRatio(1f))
            } else {
                CaseAnnee(
                    annee = annee.annee,
                    vus = annee.vus,
                    aVoir = annee.aVoir,
                    enCours = annee.annee == anneeEnCours,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAnnee(annee.annee) },
                )
            }
        }
    }
}

@Composable
private fun CaseAnnee(annee: Int, vus: Int, aVoir: Int, enCours: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape: Shape = RoundedCornerShape(3.dp)
    val couleur = couleurDeCase(vus)
    val motVu = if (vus <= 1) "vu" else "vus"
    Box(
        modifier
            .aspectRatio(1f)
            // Le contour blanc de l'année en cours (2 dp) se pose en dehors, avant l'inset qui
            // laisse place au liseré corail de l'à-voir (1,5 dp) : deux anneaux concentriques,
            // jamais superposés sur le même bord.
            .let { if (enCours) it.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else it }
            .let { if (enCours) it.padding(2.dp) else it }
            .clip(shape)
            .background(couleur ?: MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .let { if (couleur == null) it.border(1.dp, MaterialTheme.colorScheme.outline, shape) else it }
            .let { if (aVoir > 0) it.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$annee, $vus $motVu, $aVoir à voir" },
    )
}

/** La légende du calendrier, sur une ligne : « 1 vu », « 5 et plus », « à voir ». */
@Composable
private fun Legende(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendeSwatch(couleurDeCase(1)!!, "1 vu")
        LegendeSwatch(couleurDeCase(5)!!, "5 et plus")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            Text("à voir", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendeSwatch(couleur: Color, texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(couleur))
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
