package fr.mediatheque.journal.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.FiltreDesature
import fr.mediatheque.journal.ui.TamponPerdu
import fr.mediatheque.journal.ui.frise.TeinteSepia
import fr.mediatheque.journal.ui.lisereOr
import fr.mediatheque.journal.ui.theme.IconeTabler

/** Six colonnes, un gap de 6 dp entre elles et entre les rangées. */
private const val COLONNES = 6
private val GAP = 6.dp
private val FORME_CASE = RoundedCornerShape(6.dp)

/**
 * La bande d'un cycle (Suivis, rétrospectives et cycles, 25 septembre 2026) : ses films en rangées
 * de six (`disposerBande`), colonnes égales, chaque affiche 2:3 arrondie à 6 et liserée d'or à 22 %,
 * son année dessous en chiffres tabulaires.
 *
 * La largeur d'une case se mesure une fois pour toute la bande (`BoxWithConstraints`) :
 * `(largeur disponible − 5 gaps) / 6`, la hauteur à 1,5 fois — `Cover` veut des dp fixes. Une
 * rangée courte garde ces colonnes, alignée à gauche : ses places vides sont des `Spacer` de même
 * poids, si bien que trois films prennent la moitié de la carte (≈ 172 dp sur un écran de 360).
 *
 * États (`EtatBande`) : vu en couleur avec une pastille or cochée ; le prochain liseré de corail
 * 1,5 dp, son année en corail gras ; pas encore désaturé, voilé de sépia, à 45 % ; introuvable de
 * même, avec le tampon « PERDU » réduit par-dessus.
 */
@Composable
fun BandeCycle(rangees: List<List<CaseBande>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val largeurCase = (maxWidth - GAP * (COLONNES - 1)) / COLONNES
        val hauteurCase = largeurCase * 1.5f
        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            rangees.forEach { rangee ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    rangee.forEach { case ->
                        CaseDeBande(case, largeurCase, hauteurCase, Modifier.weight(1f))
                    }
                    repeat(COLONNES - rangee.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CaseDeBande(case: CaseBande, largeur: Dp, hauteur: Dp, modifier: Modifier = Modifier) {
    val film = case.film
    val attenuee = case.etat == EtatBande.PAS_ENCORE || case.etat == EtatBande.INTROUVABLE
    val description = when (case.etat) {
        EtatBande.VU -> "${film.title}, vu"
        EtatBande.PROCHAIN -> "${film.title}, prochain à voir"
        EtatBande.PAS_ENCORE -> "${film.title}, pas encore"
        EtatBande.INTROUVABLE -> "${film.title}, introuvable"
    }
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            val trait = if (case.etat == EtatBande.PROCHAIN) {
                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, FORME_CASE)
            } else {
                Modifier.lisereOr(shape = FORME_CASE)
            }
            Box(Modifier.alpha(if (attenuee) 0.45f else 1f)) {
                Cover(
                    film.cover_url,
                    film.title,
                    largeur,
                    hauteur,
                    modifier = trait,
                    colorFilter = if (attenuee) FiltreDesature else null,
                    shape = FORME_CASE,
                )
                if (attenuee) {
                    Box(Modifier.size(largeur, hauteur).background(TeinteSepia.copy(alpha = 0.35f), FORME_CASE))
                }
            }
            if (case.etat == EtatBande.INTROUVABLE) {
                TamponPerdu(taille = largeur * 0.6f, modifier = Modifier.align(Alignment.Center))
            }
            if (case.etat == EtatBande.VU) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    IconeTabler("check", null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(11.dp))
                }
            }
        }
        val prochain = case.etat == EtatBande.PROCHAIN
        Text(
            film.year?.toString() ?: "—",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                fontWeight = if (prochain) FontWeight.Bold else null,
            ),
            color = if (prochain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
