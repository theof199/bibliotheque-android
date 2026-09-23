package fr.mediatheque.journal.ui.celebrations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.FilmEnregistre
import fr.mediatheque.journal.ui.form.CartonCard
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.frise.ClapAvatar
import fr.mediatheque.journal.ui.theme.Limelight
import fr.mediatheque.journal.ui.theme.Or
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La durée du claquement du volet, lue dans `res/animator/ic_launcher_volet_claque.xml` (120 ms
 * pour s'armer à −46°, puis 260 ms pour claquer à 0° avec rebond) : 380 ms au total, la même valeur
 * que `MainActivity.SPLASH_ICON_ANIMATION_DURATION_MS` — les trois doivent rester égales.
 */
private const val DUREE_CLAQUEMENT_MS = 380L

/**
 * La célébration d'un film enregistré (complément du 23 septembre 2026 à l'habillage « papier et
 * pellicule », retouchée le même jour après un premier essai sur le téléphone — « le clap est un
 * peu mal fait ») : plein écran, le clap **de l'icône elle-même** (`ClapAvatar.kt`, l'`AndroidView`
 * qui joue `R.drawable.ic_launcher_animated`), affiché en grand et centré, plutôt qu'un jumeau
 * redessiné à la main en Compose — celui-là ne rendait pas aussi bien. L'éclair blanc et la
 * secousse se calent sur l'instant où le volet se ferme (`DUREE_CLAQUEMENT_MS`, ci-dessus), lu dans
 * le XML de l'animateur plutôt que deviné, puis l'année du film en Limelight or et le carton du
 * chroniqueur qui monte depuis le bas.
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
    // `ClapAvatar` ne rejoue son `AnimatedVectorDrawable` que sur un changement de `claques`
    // (`ClapAvatar.kt`) : partir de 0 puis l'incrémenter dans l'effet ci-dessous, plutôt que de
    // partir déjà à 1, est ce qui produit ce changement à la toute première composition.
    var claques by remember { mutableIntStateOf(0) }
    val secousse = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var flashVisible by remember { mutableStateOf(false) }
    var contenuVisible by remember { mutableStateOf(false) }

    LaunchedEffect(film) {
        claques += 1
        // L'éclair, la secousse et l'haptique se calent sur l'instant où le volet se ferme, pas
        // sur une durée codée à la main.
        delay(DUREE_CLAQUEMENT_MS)
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
            ClapAvatar(
                claques = claques,
                description = "${film.titre} enregistré",
                modifier = Modifier.size(160.dp).offset(x = secousse.value.x.dp, y = secousse.value.y.dp),
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
