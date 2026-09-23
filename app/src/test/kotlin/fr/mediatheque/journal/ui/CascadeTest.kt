package fr.mediatheque.journal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `delaiCascade` : le retard avant l'entrée du `index`-ième élément d'une cascade, 40 ms d'écart,
 * plafonné à 400 ms. Fonction pure, sans Compose.
 */
class CascadeTest {

    // 40 ms d'écart, index par index. Mutation : un autre écart (par exemple 50) fait échouer au
    // premier index non nul.
    @Test
    fun `delaiCascade avance de 40 ms par index`() {
        assertEquals(0, delaiCascade(0))
        assertEquals(40, delaiCascade(1))
        assertEquals(200, delaiCascade(5))
    }

    // Plafonné à 400 ms (le dixième élément) : au-delà, le délai n'augmente plus. Mutation :
    // retirer le `coerceAtMost` fait grimper le délai sans fin sur une longue liste.
    @Test
    fun `delaiCascade plafonne a 400 ms au-dela du dixieme element`() {
        assertEquals(400, delaiCascade(10))
        assertEquals(400, delaiCascade(11))
        assertEquals(400, delaiCascade(50))
    }

    // Un index négatif (ne devrait jamais arriver, mais ne doit pas produire un délai négatif qui
    // ferait démarrer une animation avant l'écran lui-même). Mutation : retirer le
    // `coerceAtLeast(0)` renverrait un délai négatif pour un index négatif.
    @Test
    fun `delaiCascade ne descend jamais sous zero`() {
        assertEquals(0, delaiCascade(-3))
    }
}
