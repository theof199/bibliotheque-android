package fr.mediatheque.journal.ui.celebrations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.FilmEnregistre
import fr.mediatheque.journal.ui.form.CartonCard
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.theme.Fond
import fr.mediatheque.journal.ui.theme.Limelight
import fr.mediatheque.journal.ui.theme.Or
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La célébration d'un film enregistré (complément du 23 septembre 2026 à l'habillage « papier et
 * pellicule ») : plein écran, le clap de l'icône — redessiné ici en Compose, une base et un volet
 * rayé, jamais l'`AndroidView` de `ClapAvatar.kt`, dont l'animation vit dans un
 * `AnimatedVectorDrawable` XML et n'offre pas la courbe demandée — descend et claque (−35° → 0°,
 * 550 ms), un éclair blanc et une secousse suivent, puis l'année du film en Limelight or et le
 * carton du chroniqueur qui monte depuis le bas.
 *
 * Le carton est le **même** `CartonViewModel` que celui que `HomeScreen` affiche déjà sous son
 * bandeau « Enregistré » (`Root.kt` le construit une fois, indexé sur `cartonTmdbId`, et le passe
 * ici comme là-bas) : aucune seconde instance, aucun second appel réseau. Ce calque ne fait que le
 * révéler un instant plus tôt, avant que la pile ne retombe sur l'accueil où il continue de vivre.
 *
 * La jauge des essentiels de l'année ouverte, que le brief demande aussi, est volontairement
 * absente : elle suppose de comparer une progression avant/après la sauvegarde, ce qu'aucun
 * `AnneeViewModel` n'est garanti d'avoir déjà chargé à cet instant précis du parcours (le film peut
 * tout aussi bien venir de la recherche que du Voyage) — un geste à construire à part, pas une
 * valeur approximée ici.
 *
 * Piloté par un `Channel` à un coup (`Navigator.filmsEnregistres`) : jamais rejoué au retour ni à
 * la recomposition, un `Channel` ne redonnant pas ce qu'il a déjà rendu à un collecteur.
 */
@Composable
fun FilmEnregistreCalque(film: FilmEnregistre, carton: CartonViewModel?, onFermer: () -> Unit) {
    val haptique = LocalHapticFeedback.current
    val rotationVolet = remember { Animatable(-35f) }
    val secousse = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var flashVisible by remember { mutableStateOf(false) }
    var contenuVisible by remember { mutableStateOf(false) }

    LaunchedEffect(film) {
        // Le clap claque : 550 ms, la courbe du brief (0.7, 0, 0.9, 0.4).
        rotationVolet.animateTo(0f, tween(550, easing = CubicBezierEasing(0.7f, 0f, 0.9f, 0.4f)))
        haptique.performHapticFeedback(HapticFeedbackType.Confirm)
        flashVisible = true
        // La secousse, 300 ms, en même temps que l'éclair.
        launch {
            listOf(Offset(3f, -2f), Offset(-3f, 2f), Offset(2f, 1f), Offset.Zero).forEach { cible ->
                secousse.animateTo(cible, tween(75))
            }
        }
        delay(500)
        flashVisible = false
        contenuVisible = true
        // Fermeture seule après 4 s (le brief), ou plus tôt d'un tap ailleurs dans l'écran.
        delay(4_000)
        onFermer()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = onFermer),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ClapCompose(
                rotationVolet.value,
                modifier = Modifier.size(120.dp, 110.dp).offset(x = secousse.value.x.dp, y = secousse.value.y.dp),
            )
            AnimatedVisibility(visible = contenuVisible, enter = fadeIn(tween(200))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    film.annee?.let { annee ->
                        Text(
                            annee.toString(),
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Limelight, fontSize = 34.sp),
                            color = Or,
                        )
                    }
                    if (carton != null) {
                        val cartonUi by carton.ui.collectAsState()
                        // Le carton qui monte depuis le bas, 600 ms, la courbe du brief (0.2, 0.8, 0.2, 1).
                        AnimatedVisibility(
                            visible = contenuVisible,
                            enter = slideInVertically(
                                animationSpec = tween(600, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
                                initialOffsetY = { it },
                            ) + fadeIn(tween(600)),
                        ) {
                            CartonCard(cartonUi, attente = true, onDismiss = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
        // L'éclair blanc, 500 ms, au moment où le clap claque.
        if (flashVisible) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }
    }
}

/**
 * Le clap de l'icône, redessiné en Compose : une base sombre, un volet rayé qui pivote de
 * `rotationVolet` degrés autour de son coin bas-gauche — jumeau visuel de `ic_launcher_foreground`,
 * mais animable image par image, ce que l'`AnimatedVectorDrawable` de `ClapAvatar.kt` n'offre pas.
 */
@Composable
private fun ClapCompose(rotationVolet: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val corpsHaut = size.height * 0.30f
        // Le corps du clap.
        drawRoundRect(
            color = Fond,
            topLeft = Offset(0f, corpsHaut),
            size = Size(size.width, size.height - corpsHaut),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, corpsHaut),
            size = Size(size.width, size.height - corpsHaut),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx()),
        )
        // Le volet, pivoté à son coin bas-gauche (la maquette : `transform-origin:8px 100%`).
        val pivot = Offset(8.dp.toPx(), corpsHaut)
        rotate(rotationVolet, pivot) {
            val voletH = size.height * 0.24f
            val voletTop = corpsHaut - voletH * 0.7f
            drawRoundRect(
                color = Fond,
                topLeft = Offset(-3.dp.toPx(), voletTop),
                size = Size(size.width + 6.dp.toPx(), voletH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            // Les rayures du volet — des bandes blanches diagonales, répétées.
            val pas = 16.dp.toPx()
            var x = -voletH
            while (x < size.width + voletH) {
                rotate(25f, Offset(x, voletTop + voletH / 2f)) {
                    drawRect(Color.White, topLeft = Offset(x - pas * 0.28f, voletTop - 4f), size = Size(pas * 0.42f, voletH + 8f))
                }
                x += pas
            }
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(-3.dp.toPx(), voletTop),
                size = Size(size.width + 6.dp.toPx(), voletH),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
    }
}
