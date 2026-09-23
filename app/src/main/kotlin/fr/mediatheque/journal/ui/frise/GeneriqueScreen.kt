package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.theme.Corail
import fr.mediatheque.journal.ui.theme.Limelight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Le générique de fin d'une décennie bouclée (brief du 16 septembre 2026, phase 2, item 9 ;
 * célébration complète depuis l'habillage « papier et pellicule » du 23 septembre 2026, geste 13) :
 * une marquise dont les ampoules s'allument une à une, puis un générique qui déroule sur fond sépia,
 * puis le tampon du passeport qui s'abat. Il ne charge rien : le `TamponDecennie` que
 * `Screen.Generique` porte contient déjà tout — les films, les deux dates, le titre de voyageur.
 * C'est le même tampon que le passeport du profil, calculé une fois par `tamponsPasseport` ; le
 * générique se rejoue donc à l'identique depuis le profil, des mois après, marquise et tampon
 * compris. Un tap, à n'importe quelle phase, ferme l'écran.
 */
private enum class PhaseGenerique { MARQUISE, DEFILEMENT, TAMPON }

private const val NB_AMPOULES = 12
private const val ECART_AMPOULE_MS = 120L
private const val DUREE_DEFILEMENT_MS = 5_500

@Composable
fun GeneriqueScreen(tampon: TamponDecennie, pseudo: String, onFermer: () -> Unit) {
    val monde = mondeDeLaDecennie(tampon.decennie)
    val haptique = LocalHapticFeedback.current
    var phase by remember(tampon.decennie) { mutableStateOf(PhaseGenerique.MARQUISE) }
    var ampoulesAllumees by remember(tampon.decennie) { mutableIntStateOf(0) }

    LaunchedEffect(tampon.decennie) {
        repeat(NB_AMPOULES) {
            delay(ECART_AMPOULE_MS)
            ampoulesAllumees += 1
        }
        delay(400)
        phase = PhaseGenerique.DEFILEMENT
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(monde.fond)
            .clickable(onClick = onFermer),
    ) {
        when (phase) {
            PhaseGenerique.MARQUISE -> Marquise(tampon.decennie, ampoulesAllumees, monde.accent)
            PhaseGenerique.DEFILEMENT ->
                Defilement(tampon, pseudo, monde, onTermine = { phase = PhaseGenerique.TAMPON })
            PhaseGenerique.TAMPON -> TamponQuiTombe(tampon, haptique)
        }
    }
}

/**
 * La marquise : « ANNÉES 1890 · complet » entre deux rangées d'ampoules qui s'allument une à une,
 * 120 ms d'écart (le brief) — `ampoulesAllumees` avance à ce rythme depuis `GeneriqueScreen`.
 */
@Composable
private fun Marquise(decennie: Int, ampoulesAllumees: Int, accent: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            RangeeAmpoules(ampoulesAllumees, accent)
            Text(
                "ANNÉES $decennie · complet".uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = Limelight, letterSpacing = 3.sp),
                color = Color(0xFFF2EDE6),
                textAlign = TextAlign.Center,
            )
            RangeeAmpoules(ampoulesAllumees, accent)
        }
    }
}

@Composable
private fun RangeeAmpoules(allumees: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(NB_AMPOULES) { index ->
            val allumee = index < allumees
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (allumee) accent else accent.copy(alpha = 0.18f), CircleShape),
            )
        }
    }
}

/**
 * Le générique lui-même : défile sur fond sépia, 5,5 s, linéaire (le brief), jusqu'à son point bas,
 * puis prévient `onTermine`. Les rôles (`rolesDuGenerique`) viennent d'abord, puis les deux dates,
 * puis le titre de voyageur en Limelight or, puis « FIN ».
 *
 * `snapshotFlow` attend que la mise en page connaisse sa hauteur totale (`scroll.maxValue`, nul au
 * premier passage) avant d'animer vers elle : sans cette attente, `animateScrollTo` viserait 0 et le
 * générique resterait figé en haut de son fond sépia pendant 5,5 s.
 */
@Composable
private fun Defilement(tampon: TamponDecennie, pseudo: String, monde: Monde, onTermine: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(tampon.decennie) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        scroll.animateScrollTo(scroll.maxValue, tween(DUREE_DEFILEMENT_MS, easing = LinearEasing))
        onTermine()
    }
    Box(Modifier.fillMaxSize().background(TeinteSepia)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(500.dp))
            Text(
                "ANNÉES ${tampon.decennie}".uppercase(),
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 3.sp),
                color = monde.accent,
                textAlign = TextAlign.Center,
            )
            Text(
                "vues par $pseudo",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFECE2CC),
            )
            Spacer(Modifier.height(48.dp))

            rolesDuGenerique(tampon).forEach { role ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        role.etiquette.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = monde.accent,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        role.valeur,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFF2EDE6),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            if (tampon.premiereEntree != null && tampon.derniereEntree != null) {
                Text(
                    "Du ${formatDate(tampon.premiereEntree)} au ${formatDate(tampon.derniereEntree)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFECE2CC),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(48.dp))
            Text(
                tampon.titreVoyageur.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = Limelight,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = OrDuGenerique,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            Text("FIN", style = MaterialTheme.typography.displaySmall, color = Color(0xFFF2EDE6))
            Spacer(Modifier.height(500.dp))
        }
    }
}

/**
 * Le tampon du passeport qui s'abat (le brief) : cercle double corail, rotation −8°, échelle
 * 4 → 1 en 350 ms, haptique `Confirm` — le générique (toujours sur son fond sépia) s'assombrit sous
 * un voile noir animé de concert.
 */
@Composable
private fun TamponQuiTombe(tampon: TamponDecennie, haptique: HapticFeedback) {
    val echelle = remember { Animatable(4f) }
    val voile = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { voile.animateTo(0.62f, tween(350)) }
        echelle.animateTo(1f, tween(350, easing = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)))
        haptique.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    Box(Modifier.fillMaxSize().background(TeinteSepia)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = voile.value)))
        Box(
            Modifier
                .align(Alignment.Center)
                .size(150.dp)
                .graphicsLayer {
                    scaleX = echelle.value
                    scaleY = echelle.value
                    rotationZ = -8f
                }
                .drawBehind {
                    drawCircle(Corail, radius = size.minDimension / 2f, style = Stroke(width = 6.dp.toPx()))
                    drawCircle(
                        Corail.copy(alpha = 0.55f),
                        radius = size.minDimension / 2f - 16.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tampon.decennie.toString().takeLast(3),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = Limelight, letterSpacing = 1.sp),
                color = Corail,
                textAlign = TextAlign.Center,
            )
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
