package fr.mediatheque.journal.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Un message à montrer sur l'accueil, une fois — « Enregistré ». Un
     * `Channel` plutôt qu'un `State` : un `State` remis à `null` après
     * lecture change de valeur, ce qui change la clé du `LaunchedEffect` qui
     * l'affiche et annule sa coroutine avant que la snackbar n'ait fini —
     * elle clignote une frame (revue de la vague finale, Critique 1). Un
     * événement à un coup ne se relit pas : `receiveAsFlow()` sur un
     * `Channel` le rend, une fois, à qui collecte, sans jamais changer de clé.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Compteur dédié à `Screen.Search`, incrémenté seulement quand `push` y entre — jamais à un
     * `pop`, jamais sur un `push` vers un autre écran. Il sert à ne remettre à zéro la recherche
     * qu'à l'entrée depuis l'accueil (revue de la vague finale, mineur 8) : voir le commentaire
     * dans `Root.kt` pour pourquoi ce compteur vit hors du `Crossfade`.
     */
    var searchVisits by mutableIntStateOf(0)
        private set

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        if (screen is Screen.Search) searchVisits++
        stack = stack + screen
    }

    fun pop() { if (canPop) stack = stack.dropLast(1) }

    /** Retour à l'accueil, pile vidée, avec un mot à dire. */
    fun home(message: String? = null) {
        if (message != null) _messages.trySend(message)
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
 *
 * `withTimeoutOrNull` plutôt qu'un `delay` suivi d'un `dismiss()` séparé : ce
 * dernier referme la snackbar *courante* du `SnackbarHostState`, pas
 * forcément la sienne — si un second appel s'est glissé entre-temps, il
 * fermerait celui de l'autre et laisserait le sien orphelin, `Indefinite`,
 * que plus personne ne referme (revue de la tâche 5). L'annulation par le
 * timeout efface elle-même `currentSnackbarData`.
 */
suspend fun SnackbarHostState.showBriefly(message: String) {
    withTimeoutOrNull(2_000) { showSnackbar(message, duration = SnackbarDuration.Indefinite) }
}
