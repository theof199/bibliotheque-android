package fr.mediatheque.journal.frise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le magasin des mondes visités (delta de Léon du 25 septembre 2026, « pavillon par pavillon »,
 * §D) : une décennie par ligne, jamais rejouée. `InMemoryMondesVisitesStore` seul est testé ici —
 * jumeau de la place de `RecentSearchesTest.kt`, qui ne teste pas non plus la variante
 * `SharedPreferences`, propre à l'Android instrumenté.
 */
class MondesVisitesStoreTest {

    @Test
    fun `un magasin neuf ne connait aucun monde`() {
        assertTrue(InMemoryMondesVisitesStore().lire().isEmpty())
    }

    // Mutation : remplacer `value + decennie` par `setOf(decennie)` oublierait les décennies déjà
    // marquées.
    @Test
    fun `marquer ajoute une decennie sans perdre les precedentes`() {
        val magasin = InMemoryMondesVisitesStore()

        magasin.marquer(1890)
        magasin.marquer(1900)

        assertEquals(setOf(1890, 1900), magasin.lire())
    }

    // Marquer deux fois la même décennie ne la duplique pas — un `Set`, pas une liste.
    @Test
    fun `marquer deux fois la meme decennie ne change rien`() {
        val magasin = InMemoryMondesVisitesStore()

        magasin.marquer(1890)
        magasin.marquer(1890)

        assertEquals(setOf(1890), magasin.lire())
    }
}
