package fr.mediatheque.journal

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.mediatheque.journal.ui.Root
import fr.mediatheque.journal.ui.theme.JournalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
