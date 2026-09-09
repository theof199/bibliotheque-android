package fr.mediatheque.journal.ui

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
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
}
