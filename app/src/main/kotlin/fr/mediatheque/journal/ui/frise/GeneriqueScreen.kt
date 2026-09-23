package fr.mediatheque.journal.ui.frise

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.theme.Corail

/**
 * Le générique de fin d'une décennie bouclée (brief du 16 septembre 2026, phase 2, item 9) : il
 * défile tout seul de bas en haut, dans la palette du monde, et se ferme d'un geste.
 *
 * Il ne charge rien : le `TamponDecennie` que `Screen.Generique` porte contient déjà tout — les
 * films, les deux dates, le titre de voyageur. C'est le même tampon que le passeport du profil,
 * calculé une fois par `tamponsPasseport` ; le générique se rejoue donc à l'identique depuis le
 * profil, des mois après.
 */
@Composable
fun GeneriqueScreen(tampon: TamponDecennie, pseudo: String, onFermer: () -> Unit) {
    val monde = mondeDeLaDecennie(tampon.decennie)
    val liste = rememberLazyListState()

    // Le défilement automatique, une fois : `animateScrollToItem` sur la dernière ligne, lentement.
    // Un geste du doigt le reprend à la main sans se battre avec lui — l'animation s'arrête dès
    // que la liste reçoit un autre défilement.
    LaunchedEffect(tampon.decennie) {
        liste.animateScrollToItem(index = tampon.films.size + 3)
    }

    Scaffold(containerColor = monde.fond) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind { motifDeMonde(monde.motif, monde.accent, monde.decennie) }
                .clickable(onClick = onFermer),
        ) {
            LazyColumn(
                state = liste,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(120.dp)) }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ANNÉES ${tampon.decennie}".uppercase(),
                            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 3.sp),
                            color = monde.accent,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "vues par $pseudo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${monde.nom} · ${monde.sousTitre}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }

                if (tampon.films.isEmpty()) {
                    item {
                        Text(
                            "Aucun film de ces années-là au journal.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(tampon.films, key = { "${it.annee}-${it.titre}" }) { film ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                film.annee.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = monde.accent,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(
                                film.titre,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Le compte des festivals de la décennie (décision 4 du brief du 21 septembre
                // 2026, « les récompenses ») : après la liste des films, absent si aucune de ses
                // dix années n'a de récompense.
                if (tampon.recompenses.isNotEmpty()) {
                    item {
                        Text(
                            phraseRecompenses(tampon.recompenses),
                            style = MaterialTheme.typography.bodyMedium,
                            color = monde.accent,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (tampon.premiereEntree != null && tampon.derniereEntree != null) {
                            Text(
                                "Du ${formatDate(tampon.premiereEntree)} au ${formatDate(tampon.derniereEntree)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            tampon.titreVoyageur.uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                            color = OrDuGenerique,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(40.dp))
                        Text(
                            "Fin",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item {
                    TextButton(onClick = onFermer) {
                        Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * Le tampon d'une décennie bouclée, dans le passeport du profil (brief, item 10) : un cercle
 * corail penché de −8°, la décennie, la date, le titre de voyageur. Toucher rejoue le générique.
 */
@Composable
fun TamponPasseport(tampon: TamponDecennie, onOuvrir: () -> Unit) {
    val monde = mondeDeLaDecennie(tampon.decennie)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOuvrir)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                // Le tampon est penché de −8° (brief, item 10) : un tampon droit a l'air imprimé,
                // un tampon penché a l'air posé à la main.
                .rotate(-8f)
                .drawBehind {
                    drawCircle(Corail, radius = size.minDimension / 2f, style = Stroke(width = 4f))
                    drawCircle(Corail.copy(alpha = 0.55f), radius = size.minDimension / 2f - 7f, style = Stroke(width = 1.5f))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tampon.decennie.toString().takeLast(3),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp),
                color = Corail,
            )
        }
        Column {
            Text(
                "Années ${tampon.decennie}" + (tampon.derniereEntree?.let { " · ${formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(tampon.titreVoyageur, style = MaterialTheme.typography.bodyMedium, color = monde.accent)
        }
    }
}

/** L'or des lettres lumineuses du générique — la même teinte que le liseré d'un photogramme fait. */
private val OrDuGenerique = Color(0xFFE6B94A)
