package fr.mediatheque.journal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Le chargeur commun des animations Lottie des célébrations (brief du 23 septembre 2026, soir) :
 * onze animations récoltées sous licence libre, six retenues, jamais téléchargées — les JSON
 * vivent dans `app/src/main/assets/lottie/<nom>.json`, la licence de chacune dans
 * `assets/lottie/LICENCES.md` (README, « Les animations »).
 *
 * `iterations` (une seule fois par défaut ; `com.airbnb.lottie.compose.LottieConstants.IterateForever`
 * pour boucler) et `vitesse` passent tels quels à `LottieAnimation`. `proprietes` porte un
 * recolorage (`rememberLottieDynamicProperties`) là où le brief en demande un — les appelants qui
 * pilotent leur propre progression (la bobine de tirer-pour-rafraîchir, `Ornements.kt`) n'utilisent
 * pas ce composable et appellent `LottieAnimation` directement avec un `progress` explicite.
 */
@Composable
fun Animation(
    nom: String,
    modifier: Modifier = Modifier,
    iterations: Int = 1,
    vitesse: Float = 1f,
    proprietes: LottieDynamicProperties? = null,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/$nom.json"))
    LottieAnimation(
        composition = composition,
        iterations = iterations,
        speed = vitesse,
        dynamicProperties = proprietes,
        modifier = modifier,
    )
}
