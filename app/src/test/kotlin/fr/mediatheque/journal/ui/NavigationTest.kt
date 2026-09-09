package fr.mediatheque.journal.ui

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTest {
    @Test
    fun `showBriefly referme la snackbar apres deux secondes, pas avant`() = runTest {
        val host = SnackbarHostState()
        launch { host.showBriefly("Enregistré") }

        advanceTimeBy(1_999)
        assertNotNull(host.currentSnackbarData)

        advanceTimeBy(2)
        assertNull(host.currentSnackbarData)
    }

    // `home()` envoie sur un `Channel` plutôt que sur un `State` remis à `null` (Critique 1 de la
    // vague finale) : un `State` change de valeur à sa propre lecture, ce qui coupait la coroutine
    // de `HomeScreen` avant la fin des deux secondes de la snackbar. Mutation : faire de `home()`
    // un `trySend` répété deux fois casse la première assertion (deux messages reçus, pas un) ;
    // ne plus envoyer casse la même assertion (aucun message reçu).
    @Test
    fun `home envoie le message une seule fois, pas deux`() = runTest {
        val nav = Navigator()
        val recus = mutableListOf<String>()
        val job = launch { nav.messages.collect { recus += it } }

        nav.home("Enregistré")
        runCurrent()
        job.cancel()

        assertEquals(listOf("Enregistré"), recus)
    }

    // `home(null)` (le cas d'une suppression sans message, ou d'un retour sans rien à dire) ne
    // doit rien envoyer sur le canal : sinon la prochaine snackbar affichée serait vide.
    @Test
    fun `home sans message n envoie rien`() = runTest {
        val nav = Navigator()
        val recus = mutableListOf<String>()
        val job = launch { nav.messages.collect { recus += it } }

        nav.home(null)
        runCurrent()
        job.cancel()

        assertEquals(emptyList<String>(), recus)
    }

    // `visitCounter` distingue une entrée depuis l'accueil (`push`) d'un retour (`pop`) : c'est ce
    // qui garde `LaunchedEffect(nav.visitCounter)` dans `Root.kt` de rejouer `search.reset()` au
    // retour du formulaire (mineur 8 de la vague finale). Mutation : incrémenter aussi dans `pop`,
    // ou ne jamais incrémenter dans `push`, fait tomber respectivement la deuxième et la première
    // assertion.
    @Test
    fun `push incremente le compteur de visite, jamais pop`() {
        val nav = Navigator()

        nav.push(Screen.Search)
        val premiereVisite = nav.visitCounter

        nav.pop()
        val apresPop = nav.visitCounter
        assertEquals("pop ne doit rien incrementer", premiereVisite, apresPop)

        nav.push(Screen.Search)
        val secondeVisite = nav.visitCounter
        assertNotEquals("un second push doit changer la valeur", premiereVisite, secondeVisite)
    }
}
