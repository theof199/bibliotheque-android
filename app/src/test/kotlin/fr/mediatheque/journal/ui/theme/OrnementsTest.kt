package fr.mediatheque.journal.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `opacitePerforation` : quelle perforation est déjà allumée en or, le long de la bande — la
 * célébration d'une salle bouclée (geste 10) en allume une à une. Fonction pure, sans Compose.
 */
class OrnementsTest {

    // Sous le compte d'allumées, une perforation reste au grain de base (0,5). Mutation : renvoyer
    // 1f par défaut ferait croire toute la bande allumée avant la première célébration.
    @Test
    fun `une perforation au-dela du compte allumees reste au grain de base`() {
        assertEquals(0.5f, opacitePerforation(index = 3, allumees = 0), 0f)
        assertEquals(0.5f, opacitePerforation(index = 3, allumees = 2), 0f)
    }

    // À la frontière, l'index égal au compte n'est pas encore allumé : `allumees` compte des
    // perforations, pas un dernier index inclus. Mutation : `index <= allumees` allumerait une de
    // trop à chaque étape.
    @Test
    fun `l'index egal au compte allumees n'est pas encore allume`() {
        assertEquals(0.5f, opacitePerforation(index = 2, allumees = 2), 0f)
        assertEquals(1f, opacitePerforation(index = 1, allumees = 2), 0f)
        assertEquals(1f, opacitePerforation(index = 0, allumees = 2), 0f)
    }
}
