package fr.mediatheque.journal.ui.celebrations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.frise.Monde
import fr.mediatheque.journal.ui.theme.Animation
import fr.mediatheque.journal.ui.theme.CadreOrne
import fr.mediatheque.journal.ui.theme.Limelight
import kotlinx.coroutines.delay

/**
 * Le carton-titre d'un monde (geste 22 du complément du 23 septembre 2026 à l'habillage) : plein
 * écran façon cinéma muet — fond noir, `CadreOrne` or, le nom du monde en Limelight — à la première
 * entrée dans ce monde en défilant la Frise (`mondeEntre`, `VoyageCarte.kt`, et la mémoire des
 * mondes déjà présentés cette session, `rememberSaveable` dans `VoyageScreen`).
 *
 * Depuis le brief des animations Lottie du 23 septembre 2026 (soir), le carton est précédé d'un
 * projecteur (`projecteur-1.json`) qui joue une fois, 1 s, avant que le carton-titre n'apparaisse —
 * le carton, lui, reste affiché ensuite. 1,8 s d'affichage du carton avant de prévenir `onTermine`
 * (donc 2,8 s au total depuis l'entrée dans le monde) ; le fondu d'entrée et de sortie est porté par
 * l'appelant (`AnimatedVisibility`, même montage que le calque du ticket dans `Root.kt`), pas ici —
 * ce composant ne fait que dessiner le carton et compter le temps. Un tap, à tout instant, passe.
 */
@Composable
fun CartonTitreMonde(monde: Monde, onTermine: () -> Unit) {
    var cartonVisible by remember(monde.decennie) { mutableStateOf(false) }
    LaunchedEffect(monde.decennie) {
        delay(1_000)
        cartonVisible = true
        delay(1_800)
        onTermine()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onTermine),
        contentAlignment = Alignment.Center,
    ) {
        if (!cartonVisible) {
            Animation(nom = "projecteur-1", iterations = 1, modifier = Modifier.size(160.dp))
        } else {
            CadreOrne(modifier = Modifier.fillMaxWidth().padding(32.dp).height(160.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        monde.nom.uppercase(),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = Limelight, letterSpacing = 3.sp, fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
