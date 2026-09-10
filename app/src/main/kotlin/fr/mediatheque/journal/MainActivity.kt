package fr.mediatheque.journal

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.mediatheque.journal.ui.Root
import fr.mediatheque.journal.ui.theme.JournalTheme
import java.time.Duration
import java.time.Instant

// Plafond du temps qu'on attend avant de retirer l'ecran de demarrage,
// egal a la duree totale du clap : la somme des deux `objectAnimator` de
// res/animator/ic_launcher_volet_claque.xml (450 + 250 ms).
private const val SPLASH_ICON_ANIMATION_DURATION_MS = 700L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sur Android 12+, l'ecran de demarrage systeme se retire des que le
        // premier contenu (l'accueil, deja compose derriere) est pret : sur
        // un demarrage a froid, ca peut arriver bien apres la fin des
        // 700 ms de l'animation du clap (l'accueil met plus longtemps a se
        // preparer), et sur un demarrage tiede, bien avant. Le but n'est pas
        // de garder l'ecran 700 ms : c'est de ne jamais couper l'animation,
        // sans jamais retarder l'accueil au-dela d'elle. On calcule donc le
        // temps qui reste a jouer, borne a [0, 700] ms. API du framework
        // (`Activity.getSplashScreen()`, API 31), pas de dependance ajoutee.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                val debut = splashScreenView.iconAnimationStart
                val duree = splashScreenView.iconAnimationDuration
                val restant = if (debut != null && duree != null) {
                    Duration.between(Instant.now(), debut.plus(duree))
                        .toMillis()
                        .coerceIn(0L, SPLASH_ICON_ANIMATION_DURATION_MS)
                } else {
                    0L
                }
                splashScreenView.postDelayed({ splashScreenView.remove() }, restant)
            }
        }
        // `SystemBarStyle.dark(...)` veut dire « barre sombre, icônes
        // claires » — le nom prête à confusion, c'est bien ce que le design
        // demande (barres transparentes, icônes claires).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            JournalTheme {
                Root((application as App).container)
            }
        }
    }
}
