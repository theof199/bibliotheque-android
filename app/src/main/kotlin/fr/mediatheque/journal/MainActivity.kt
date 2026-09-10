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

// Duree totale du clap qui claque sur l'ecran de demarrage (Android 12+),
// egale a la somme des deux `objectAnimator` de
// res/animator/ic_launcher_volet_claque.xml (450 + 250 ms). Les deux valeurs
// doivent rester egales.
private const val SPLASH_ICON_ANIMATION_DURATION_MS = 700L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sur Android 12+, l'ecran de demarrage systeme se retire des que le
        // premier contenu (l'accueil, deja compose derriere) est pret,
        // souvent avant la fin de l'animation du clap : on le garde jusqu'au
        // bout, sans retarder l'accueil au-dela de la duree de l'animation.
        // API du framework (`Activity.getSplashScreen()`, API 31), pas de
        // dependance ajoutee.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.postDelayed(
                    { splashScreenView.remove() },
                    SPLASH_ICON_ANIMATION_DURATION_MS,
                )
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
