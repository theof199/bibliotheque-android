package fr.mediatheque.journal.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Les six écrans. Un écran qui a besoin d'une donnée la porte. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Form(val result: SearchResult) : Screen
    data object Profile : Screen
    data object Films : Screen
    data class Edit(val item: JournalItem) : Screen
}

/**
 * Une pile, et c'est tout. Pas de bibliothèque de navigation : six écrans, un
 * seul chemin, et `Crossfade` pour le fondu du design §7.
 */
class Navigator {
    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set

    /** Un message à montrer sur l'accueil, une fois — « Enregistré ». */
    var pendingMessage by mutableStateOf<String?>(null)

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) { stack = stack + screen }

    fun pop() { if (canPop) stack = stack.dropLast(1) }

    /** Retour à l'accueil, pile vidée, avec un mot à dire. */
    fun home(message: String? = null) {
        pendingMessage = message
        stack = listOf(Screen.Home)
    }
}

@Composable
fun rememberNavigator(): Navigator = remember { Navigator() }

/**
 * Deux secondes (design §6), jamais la durée par défaut de Material : l'API
 * `SnackbarHostState.showSnackbar` n'a pas de paramètre de durée libre, donc
 * une snackbar indéfinie qu'on referme nous-mêmes après le délai (décision 3
 * de la tâche 5). Centralisé ici, un seul endroit, pour que la tâche 6
 * (le geste « Enregistré ») s'en serve aussi plutôt que de le répéter.
 */
suspend fun SnackbarHostState.showBriefly(message: String) = coroutineScope {
    val job = launch { showSnackbar(message, duration = SnackbarDuration.Indefinite) }
    delay(2_000)
    currentSnackbarData?.dismiss()
    job.join()
}
