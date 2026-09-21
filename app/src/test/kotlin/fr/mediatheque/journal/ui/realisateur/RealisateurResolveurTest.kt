package fr.mediatheque.journal.ui.realisateur

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `RealisateurResolveur` (brief du 21 septembre 2026, « la page réalisateur », décision 3) : le
 * cache par film qui sert tous les écrans à nom de réalisateur touchable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealisateurResolveurTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()

    // Un second appel sur le même film ne rappelle jamais le back (décision 3 du brief).
    // Mutation : retirer la vérification du cache avant l'appel fait tomber cette assertion (deux
    // appels au lieu d'un).
    @Test
    fun `resoudre sert le second appel du cache, sans rappeler le back`() = runTest(dispatcher) {
        api.onRealisateursDuFilm = { listOf(FakeJournalApi.realisateurCredit(525, "Christopher Nolan")) }
        val resolveur = RealisateurResolveur(api)

        val premier = resolveur.resoudre(27205)
        val second = resolveur.resoudre(27205)

        assertEquals(listOf(FakeJournalApi.realisateurCredit(525, "Christopher Nolan")), premier)
        assertEquals(premier, second)
        assertEquals(listOf("realisateursDuFilm 27205"), api.calls)
    }

    // Deux films distincts ne partagent pas leur entrée de cache. Mutation : une clé unique pour
    // tout le cache (au lieu d'une carte par `tmdbId`) ferait tomber cette assertion.
    @Test
    fun `resoudre garde une entree de cache par film`() = runTest(dispatcher) {
        api.onRealisateursDuFilm = { id -> listOf(FakeJournalApi.realisateurCredit(id, "Personne $id")) }
        val resolveur = RealisateurResolveur(api)

        resolveur.resoudre(1)
        resolveur.resoudre(2)
        resolveur.resoudre(1)

        assertEquals(listOf("realisateursDuFilm 1", "realisateursDuFilm 2"), api.calls)
    }
}
