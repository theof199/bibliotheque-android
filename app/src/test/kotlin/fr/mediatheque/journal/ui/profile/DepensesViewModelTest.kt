package fr.mediatheque.journal.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les dépenses au chroniqueur (décision 2 du brief du 21 septembre 2026, « les dépenses ») : la
 * ligne du mois courant, avec et sans mois, et le tri des mois précédents — fonctions pures,
 * jumelles de `trierPortefeuille` (`VoyageCarteTest`).
 */
class DepensesViewModelTest {

    @Test
    fun `la ligne du mois courant, avec une decimale et une virgule`() {
        assertEquals("Ce mois-ci : 12,7 centimes · 10 appels", ligneMoisCourant(DepenseMoisUi("2026-09", 10, 12.7)))
    }

    @Test
    fun `sans le mois, la ligne figee a zero`() {
        assertEquals("Ce mois-ci : 0 centime · 0 appel", ligneMoisCourant(null))
    }

    // Mutation : sans le pluriel conditionnel, "1 appels" resterait au pluriel.
    @Test
    fun `un seul appel reste au singulier, plusieurs au pluriel`() {
        assertEquals("Ce mois-ci : 4,2 centimes · 1 appel", ligneMoisCourant(DepenseMoisUi("2026-09", 1, 4.2)))
        assertEquals("Ce mois-ci : 4,2 centimes · 2 appels", ligneMoisCourant(DepenseMoisUi("2026-09", 2, 4.2)))
    }

    @Test
    fun `un mois precedent, en toutes lettres`() {
        assertEquals("Août 2026 : 4,2 centimes · 3 appels", ligneMoisPrecedent(DepenseMoisUi("2026-08", 3, 4.2)))
    }

    @Test
    fun `les mois precedents sont tries du plus recent au plus ancien, mois courant excepte`() {
        val mois = listOf(
            DepenseMoisUi("2026-06", 2, 1.0),
            DepenseMoisUi("2026-09", 10, 12.7),
            DepenseMoisUi("2026-08", 3, 4.2),
            DepenseMoisUi("2026-07", 1, 0.5),
        )

        val precedents = triMoisPrecedents(mois, moisCourant = "2026-09")

        // Mutation : un tri croissant, ou l'oubli d'exclure "2026-09", changerait cet ordre.
        assertEquals(listOf("2026-08", "2026-07", "2026-06"), precedents.map { it.mois })
    }
}
