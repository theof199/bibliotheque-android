package fr.mediatheque.journal.ui.celebrations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.FeuilleDeLecture
import fr.mediatheque.journal.ui.FilmEnregistre
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.form.etatFeuilleCarton
import fr.mediatheque.journal.ui.theme.Animation
import fr.mediatheque.journal.ui.theme.Limelight
import fr.mediatheque.journal.ui.theme.Or
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'instant de l'impact (brief des animations Lottie du 23 septembre 2026, soir), lu dans
 * `assets/lottie/clap-2.json` plutôt que deviné : le calque « burst for clapper » s'y arme à la
 * frame 53 sur 145 au total (29,97 im/s, `assets/lottie/LICENCES.md`) — 53 / 29,97 ≈ 1 768 ms,
 * proche du repli à 40 % de la durée que le brief prévoyait sans cette lecture (≈ 1 935 ms), mais
 * effectivement lu dans l'animation.
 */
private const val INSTANT_IMPACT_MS = 1_768L

/**
 * La célébration d'un film enregistré (complément du 23 septembre 2026 à l'habillage « papier et
 * pellicule », remplacée le même jour, en soirée, par une animation Lottie récoltée sous licence
 * libre — `clap-2.json`, `assets/lottie/LICENCES.md` — à la place du clap de l'icône redessiné en
 * `ClapAvatar`, qui ne rendait pas aussi bien) : plein écran, le clap joué en grand et centré.
 * L'éclair blanc et la secousse se calent sur l'instant de l'impact (`INSTANT_IMPACT_MS`,
 * ci-dessus), puis l'année du film en Limelight or.
 *
 * Une fois le clap joué (décision 4 du brief du 24 septembre 2026, « le voyage revu »), la feuille
 * de lecture du carton s'ouvre — chargement tant que le statut n'est pas prêt, fermable sans
 * attendre. `carton` est le **même** `CartonViewModel` que celui que `Root.kt` construit une fois,
 * indexé sur `cartonTmdbId`, partagé avec « Le film » de l'accueil et des fiches : aucune seconde
 * instance, aucun second appel réseau.
 *
 * La jauge des essentiels de l'année ouverte, que le brief demande aussi, est volontairement
 * absente : elle suppose de comparer une progression avant/après la sauvegarde, ce qu'aucun
 * `AnneeViewModel` n'est garanti d'avoir déjà chargé à cet instant précis du parcours (le film peut
 * tout aussi bien venir de la recherche que du Voyage) — un geste à construire à part, pas une
 * valeur approximée ici.
 *
 * Piloté par un `Channel` à un coup (`Navigator.filmsEnregistres`) : jamais rejoué au retour ni à
 * la recomposition, un `Channel` ne redonnant pas ce qu'il a déjà rendu à un collecteur — la
 * composition Lottie repart donc d'elle-même à chaque nouvelle apparition du calque, sans compteur
 * à incrémenter comme `ClapAvatar` en avait besoin.
 */
@Composable
fun FilmEnregistreCalque(film: FilmEnregistre, carton: CartonViewModel?, onFermer: () -> Unit) {
    val haptique = LocalHapticFeedback.current
    val secousse = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var flashVisible by remember { mutableStateOf(false) }
    var contenuVisible by remember { mutableStateOf(false) }
    // La feuille du carton (décision 4) : ouverte d'elle-même une fois le clap joué, fermable sans
    // attendre — indépendante de la fermeture du calque, qui suit son propre délai ci-dessous.
    var feuilleCartonOuverte by remember { mutableStateOf(false) }

    LaunchedEffect(film) {
        // L'éclair, la secousse et l'haptique se calent sur l'instant de l'impact, pas sur une
        // durée codée à la main.
        delay(INSTANT_IMPACT_MS)
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
        if (carton != null) feuilleCartonOuverte = true
        // Fermeture seule après 4 s (le brief), ou plus tôt d'un tap ailleurs dans l'écran.
        delay(4_000)
        onFermer()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = onFermer),
        contentAlignment = Alignment.Center,
    ) {
        // Le clap agrandi (retour du propriétaire, 23 septembre 2026 soir : « un peu petit ») :
        // 260 dp, ou 70 % de la largeur de l'écran si c'est plus petit (un téléphone étroit).
        val tailleClap = minOf(260.dp, maxWidth * 0.7f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Animation(
                nom = "clap-2",
                modifier = Modifier
                    .size(tailleClap)
                    .offset(x = secousse.value.x.dp, y = secousse.value.y.dp)
                    .semantics { contentDescription = "${film.titre} enregistré" },
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
                }
            }
        }
        // L'éclair blanc, 500 ms, au moment où le clap claque.
        if (flashVisible) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }
    }

    if (feuilleCartonOuverte && carton != null) {
        val cartonUi by carton.ui.collectAsState()
        FeuilleDeLecture(
            titre = cartonUi.titre ?: film.titre,
            etat = etatFeuilleCarton(cartonUi),
            onDismiss = { feuilleCartonOuverte = false },
        )
    }
}
