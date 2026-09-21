package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le carnet d'une année (brief du 22 septembre 2026, « le carnet ») : le libellé du bouton, la
 * ligne « Fabriqué le… », les lignes du bloc « Carnets » du profil, le nom de fichier, et l'année
 * proposée après un ticket. Fonctions pures, sans réseau ni `ViewModel`.
 */
class CarnetEtatsTest {

    private fun carnet(fabriqueLe: String = "2026-09-21T10:00:00.000Z", pages: Int = 12) = CarnetUi(fabriqueLe, pages)

    // Mutation : tester `carnet == null` avant `carnetEnCours` ferait dire « Refaire le carnet » à
    // la place de « Le carnet se fabrique… » sur un carnet qu'on est déjà en train de refaire.
    @Test
    fun `libelleBoutonCarnet dit se fabrique meme quand un carnet existe deja, l'emportant sur Refaire`() {
        assertEquals("Le carnet se fabrique…", libelleBoutonCarnet(carnet(), carnetEnCours = true))
    }

    @Test
    fun `libelleBoutonCarnet dit se fabrique sans carnet existant non plus`() {
        assertEquals("Le carnet se fabrique…", libelleBoutonCarnet(null, carnetEnCours = true))
    }

    @Test
    fun `libelleBoutonCarnet dit Faire le carnet sans carnet, hors fabrication`() {
        assertEquals("Faire le carnet", libelleBoutonCarnet(null, carnetEnCours = false))
    }

    @Test
    fun `libelleBoutonCarnet dit Refaire le carnet quand un carnet existe deja, hors fabrication`() {
        assertEquals("Refaire le carnet", libelleBoutonCarnet(carnet(), carnetEnCours = false))
    }

    // Mutation : additionner sans l'accord donnerait « 12 page » ou « 1 pages ».
    @Test
    fun `ligneFabriqueLeCarnet accorde la page au singulier`() {
        assertEquals("Fabriqué le 21 septembre 2026 · 1 page", ligneFabriqueLeCarnet(carnet(pages = 1)))
    }

    @Test
    fun `ligneFabriqueLeCarnet accorde les pages au pluriel`() {
        assertEquals("Fabriqué le 21 septembre 2026 · 12 pages", ligneFabriqueLeCarnet(carnet(pages = 12)))
    }

    // Mutation : lister le carnet fabriqué ET l'année en fabrication ferait apparaître 1895 deux
    // fois pendant un « Refaire » — la fabrication en cours doit l'emporter, seule.
    @Test
    fun `lignesCarnetsProfil n'affiche l'annee qu'une fois quand elle est a la fois fabriquee et en cours de refaire`() {
        val lignes = lignesCarnetsProfil(listOf(CarnetProfilUi(1895, 12, "2026-09-21T10:00:00.000Z")), enCours = listOf(1895))

        assertEquals(listOf(LigneCarnetProfil.EnFabrication(1895)), lignes)
    }

    @Test
    fun `lignesCarnetsProfil trie par annee croissante, fabriques et en fabrication melanges`() {
        val lignes = lignesCarnetsProfil(
            carnets = listOf(CarnetProfilUi(1897, 8, "2026-09-20T10:00:00.000Z"), CarnetProfilUi(1895, 12, "2026-09-21T10:00:00.000Z")),
            enCours = listOf(1896),
        )

        assertEquals(listOf(1895, 1896, 1897), lignes.map { it.annee })
    }

    @Test
    fun `texteLigneCarnetProfil d'un carnet fabrique donne annee, pages accordees et date`() {
        val ligne = LigneCarnetProfil.Fabrique(CarnetProfilUi(1895, 1, "2026-09-21T10:00:00.000Z"))

        assertEquals("1895 · 1 page · 21 septembre 2026", texteLigneCarnetProfil(ligne))
    }

    @Test
    fun `texteLigneCarnetProfil d'une annee en fabrication le dit, sans page ni date`() {
        assertEquals("1896 · en fabrication", texteLigneCarnetProfil(LigneCarnetProfil.EnFabrication(1896)))
    }

    @Test
    fun `nomFichierCarnet est carnet-annee point pdf`() {
        assertEquals("carnet-1895.pdf", nomFichierCarnet(1895))
    }

    // Mutation : proposer `apres` (l'année que le ticket ouvre) plutôt qu'`anneeBouclee` (celle
    // qu'on quitte) inverserait la proposition — le carnet de 1896 n'existe pas encore.
    @Test
    fun `anneeProposeeCarnet propose l'annee quittee, jamais la decennie bouclee`() {
        assertEquals(1895, anneeProposeeCarnet(FrontiereAvancee(anneeBouclee = 1895, decennieBouclee = 1890)))
    }
}
