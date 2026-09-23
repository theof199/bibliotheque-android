package fr.mediatheque.journal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * La cascade d'entrée (habillage « papier et pellicule » du 23 septembre 2026, geste 8) : les
 * grilles et listes apparaissent en fondu et montée de 12 dp, 40 ms d'écart entre deux éléments, à
 * la première composition de l'écran seulement — jamais au défilement, jamais au retour sur un
 * écran resté dans la pile.
 *
 * `rememberPorteCascade` pose la porte, une fois, au sommet de l'écran (`HomeScreen`,
 * `AnneeScreen`, `RealisateurScreen`) : elle reste ouverte le temps que la cascade initiale se
 * déclenche, puis se referme et le reste pour toute la vie de cette composition d'écran — la même
 * composition que `rememberSaveableStateHolder` (design §7) garde vivante quand un autre écran se
 * pousse par-dessus, donc jamais rejouée à un retour.
 *
 * `EntreeEnCascade` capture l'état de la porte **une seule fois**, à la première composition de
 * *cet* élément : un élément qui entre plus tard (scrolled dans la vue après la fenêtre initiale,
 * ou re-composé après un aller-retour hors de la fenêtre de recyclage d'une liste) lit une porte
 * déjà fermée et s'affiche tout de suite, sans animation.
 */
private const val ECART_CASCADE_MS = 40

/** Le plafond du délai (10 éléments à 40 ms) : au-delà, tout retard de plus serait juste de l'attente. */
private const val PLAFOND_CASCADE_MS = 400

/** La durée pendant laquelle la porte reste ouverte : assez pour que le dernier délai plafonné se soit déclenché, plus la durée de son animation. */
private const val FENETRE_PORTE_MS = PLAFOND_CASCADE_MS + 250

private val MONTEE_CASCADE = 12.dp
private const val DUREE_CASCADE_MS = 220

/**
 * Le délai avant l'entrée du `index`-ième élément, plafonné (fonction pure, testée) : mutation —
 * retirer le `coerceAtMost` fait grimper le délai sans fin sur une longue liste, la fin de la
 * cascade paraissant alors être un chargement bloqué plutôt qu'un simple décalage.
 */
fun delaiCascade(index: Int): Int = (index.coerceAtLeast(0) * ECART_CASCADE_MS).coerceAtMost(PLAFOND_CASCADE_MS)

/** La porte de la cascade d'un écran : vraie le temps de la fenêtre initiale, fausse ensuite, pour de bon. */
@Composable
fun rememberPorteCascade(): Boolean {
    var ouverte by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FENETRE_PORTE_MS.toLong())
        ouverte = false
    }
    return ouverte
}

/**
 * Habille `content` d'un fondu et d'une montée de [MONTEE_CASCADE], retardés de [delaiCascade] —
 * seulement si `porteOuverte` valait vrai à la première composition de cet élément précis.
 */
@Composable
fun EntreeEnCascade(index: Int, porteOuverte: Boolean, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val animer = remember { porteOuverte }
    if (!animer) {
        content(modifier)
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(delaiCascade(index).toLong())
        visible = true
    }
    val progression by animateFloatAsState(if (visible) 1f else 0f, tween(DUREE_CASCADE_MS), label = "cascade")
    content(
        modifier.graphicsLayer {
            alpha = progression
            translationY = (1f - progression) * MONTEE_CASCADE.toPx()
        },
    )
}
