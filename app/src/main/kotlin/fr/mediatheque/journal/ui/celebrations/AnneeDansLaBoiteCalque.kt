package fr.mediatheque.journal.ui.celebrations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import fr.mediatheque.journal.ui.frise.FrontiereAvancee
import fr.mediatheque.journal.ui.frise.Recompense
import fr.mediatheque.journal.ui.theme.Animation
import fr.mediatheque.journal.ui.theme.Corail
import fr.mediatheque.journal.ui.theme.Limelight
import fr.mediatheque.journal.ui.theme.Or
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * « Année dans la boîte » (complément du 23 septembre 2026 à l'habillage « papier et pellicule »,
 * geste 11) : remplace l'ancienne snackbar « *1898* dans la boîte ! ». Une amorce de pellicule
 * (compte à rebours 5-4-3, balayage conique) puis la récompense de l'année, dorée et lustrée, sous
 * une pluie de perforations, « *1898* DANS LA BOÎTE » en Limelight, un bilan simplifié, et le
 * photogramme de l'année suivante qui se dévoile.
 *
 * Le bilan complet du brief (récompense · essentiels *x* sur *y* · le mot du chroniqueur) est
 * réduit à la seule récompense : les deux autres lignes supposent une progression et un paragraphe
 * de chronique que `FriseViewModel`/`VoyageScreen` n'ont pas — seul un `AnneeViewModel` déjà chargé
 * pour cette année précise les connaît, et rien ne garantit qu'il le soit au moment où la frontière
 * avance depuis la Frise elle-même. Même raisonnement, même choix documenté que pour la jauge du
 * geste 9.
 *
 * Le ticket qui suit (« Utiliser » / « Garder »), demandé « dans le même calque », reste porté par
 * `TicketCalque` (`Root.kt`), un calque distinct : les deux s'enchaînent à l'écran (celui-ci se
 * referme, `ticketAMontrer` étant déjà là ou arrivant dans la foulée), mais ne partagent pas un seul
 * composable — les fusionner aurait dû faire porter au calque du ticket, déjà câblé sur l'ouverture
 * de la Frise indépendamment de toute célébration, une seconde raison d'exister.
 */
@Composable
fun AnneeDansLaBoiteCalque(avancee: FrontiereAvancee, recompense: Recompense?, onTermine: () -> Unit) {
    val haptique = LocalHapticFeedback.current
    var phase by remember(avancee) { mutableStateOf(PhaseBoite.COMPTE) }
    var chiffre by remember(avancee) { mutableIntStateOf(5) }
    val balayage = remember(avancee) { Animatable(0f) }
    val pluie = remember(avancee) { Animatable(0f) }

    LaunchedEffect(avancee) {
        listOf(5, 4, 3).forEach { n ->
            chiffre = n
            haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
            launch { balayage.animateTo(balayage.value + 1f, tween(700, easing = LinearEasing)) }
            delay(700)
        }
        phase = PhaseBoite.RECOMPENSE
        haptique.performHapticFeedback(HapticFeedbackType.Confirm)
        launch { pluie.animateTo(1f, tween(2_200, easing = LinearEasing)) }
        delay(2_200)
        phase = PhaseBoite.REVELATION
        delay(1_600)
        onTermine()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(200))) },
            label = "annee-dans-la-boite",
        ) { etat ->
            when (etat) {
                PhaseBoite.COMPTE -> AmorceCompteARebours(chiffre, balayage.value)
                PhaseBoite.RECOMPENSE, PhaseBoite.REVELATION ->
                    ContenuRecompense(avancee.anneeBouclee, recompense, etat == PhaseBoite.REVELATION)
            }
        }
        if (phase != PhaseBoite.COMPTE) PluieDePerforations(pluie.value)
    }
}

private enum class PhaseBoite { COMPTE, RECOMPENSE, REVELATION }

/** L'amorce de pellicule : un cercle gris, un balayage conique qui tourne, le chiffre en Limelight 90 sp. */
@Composable
private fun AmorceCompteARebours(chiffre: Int, tours: Float) {
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF3B362F))
            val brush = Brush.sweepGradient(listOf(Color(0xFFCFC7B8).copy(alpha = 0.05f), Color(0xFFCFC7B8).copy(alpha = 0.35f)))
            rotate(tours * 360f) { drawCircle(brush) }
            drawCircle(Color(0xFFCFC7B8), style = Stroke(width = 3.dp.toPx()))
        }
        Text(chiffre.toString(), style = MaterialTheme.typography.displayLarge.copy(fontFamily = Limelight, fontSize = 90.sp), color = Color(0xFFE8E0D0))
    }
}

/**
 * La récompense, le titre en Limelight, le bilan simplifié, et l'année suivante qui se dévoile.
 *
 * Le glyphe vectoriel d'origine (`Embleme`) cède la place, pour cette seule apparition (brief des
 * animations Lottie du 23 septembre 2026, soir), au trophée animé `trophee-1.json` — recoloré or
 * `#E6B94A` / corail `#FF6B57` par propriété dynamique : le fichier ne porte que des oranges
 * (`assets/lottie/LICENCES.md`), qui passent toutes à l'or ; la branche corail, écrite pour tout
 * fichier qui porterait aussi des rouges, reste inerte ici — assumé, pas une erreur de lecture des
 * couleurs d'origine. `Embleme` continue de servir ailleurs (la carte du Voyage, le cartouche de
 * l'année, le passeport, le tampon « perdu ») : seule cette apparition change.
 */
@Composable
private fun ContenuRecompense(anneeBouclee: Int, recompense: Recompense?, revele: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        recompense?.let {
            Animation(
                nom = "trophee-1",
                iterations = 1,
                proprietes = rememberLottieDynamicProperties(
                    rememberLottieDynamicProperty(
                        property = LottieProperty.COLOR,
                        keyPath = arrayOf("**"),
                    ) { frameInfo ->
                        val original = Color(frameInfo.startValue)
                        // Oranges (vert dominant) vers l'or, rouges (vert faible) vers le corail.
                        (if (original.green > 0.5f) Or else Corail).toArgb()
                    },
                ),
                modifier = Modifier.size(72.dp),
            )
        }
        Text(
            "$anneeBouclee\nDANS LA BOÎTE",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Limelight, letterSpacing = 2.sp),
            color = Color(0xFFF2EDE6),
            textAlign = TextAlign.Center,
        )
        recompense?.let {
            Text(it.singulier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (revele) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Le photogramme de l'année suivante, dont le cadre se dévoile : pointillé gris puis
                // corail plein, plutôt qu'un flou (`Modifier.blur` exige l'API 31, ce dépôt promet 26).
                val corail = MaterialTheme.colorScheme.primary
                var devoile by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(100); devoile = true }
                Box(
                    Modifier
                        .size(48.dp, 36.dp)
                        .border(if (devoile) 2.dp else 1.dp, if (devoile) corail else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                )
                Text("s'ouvre à toi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Une pluie de perforations or, 30 particules, canvas, rotation — jusqu'à `progression` (0..1). */
@Composable
private fun PluieDePerforations(progression: Float) {
    val particules = remember {
        List(30) { Particule(x = Random.nextFloat(), retard = Random.nextFloat() * 0.3f, vitesse = 0.8f + Random.nextFloat() * 0.4f, rotationDepart = Random.nextFloat() * 360f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        particules.forEach { particule ->
            val t = ((progression - particule.retard) / particule.vitesse).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val y = size.height * t
            val x = size.width * particule.x
            rotate(particule.rotationDepart + t * 540f, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = Or.copy(alpha = (1f - t * 0.7f)),
                    topLeft = Offset(x - 4.dp.toPx(), y - 5.5.dp.toPx()),
                    size = Size(8.dp.toPx(), 11.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

private data class Particule(val x: Float, val retard: Float, val vitesse: Float, val rotationDepart: Float)
