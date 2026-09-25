package fr.mediatheque.journal.ui.films

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les mutateurs de `FiltresFilmsViewModel` (« Mes films · le hall », 24 septembre 2026). Aucun
 * appel réseau, aucune coroutine : chaque geste de l'écran change `filtres` tout de suite.
 */
class FiltresFilmsViewModelTest {
    private val vm = FiltresFilmsViewModel()

    @Test
    fun `demarre sur les filtres par defaut`() {
        assertEquals(FiltresFilms(), vm.filtres.value)
    }

    @Test
    fun `setTexte pose le texte tel quel`() {
        vm.setTexte("Miya")
        assertEquals("Miya", vm.filtres.value.texte)
    }

    // Un tap inverse, un second revient. Mutation : ne basculer que dans un sens casse cette
    // assertion.
    @Test
    fun `basculerOrdreDate alterne recents et anciens d abord`() {
        vm.basculerOrdreDate()
        assertEquals(TriFilms.DATE_ASC, vm.filtres.value.tri)
        vm.basculerOrdreDate()
        assertEquals(TriFilms.DATE_DESC, vm.filtres.value.tri)
    }

    // Depuis un tri par note, la puce Date ramène au tri par date par défaut, récents d'abord.
    // Mutation : aller aux anciens d'abord casse cette assertion.
    @Test
    fun `basculerOrdreDate depuis le tri par note revient aux recents d abord`() {
        vm.setTri(TriFilms.NOTE_DESC)
        vm.basculerOrdreDate()
        assertEquals(TriFilms.DATE_DESC, vm.filtres.value.tri)
    }

    @Test
    fun `setTri pose le tri`() {
        vm.setTri(TriFilms.NOTE_DESC)
        assertEquals(TriFilms.NOTE_DESC, vm.filtres.value.tri)
    }

    // Cocher, cocher une autre, décocher la première : plusieurs notes à la fois. Mutation :
    // remplacer au lieu d'ajouter, ou un `+` sans retrait, casse cette assertion.
    @Test
    fun `basculerNote coche puis decoche`() {
        vm.basculerNote(4)
        vm.basculerNote(7)
        assertEquals(setOf(4, 7), vm.filtres.value.notes)
        vm.basculerNote(4)
        assertEquals(setOf(7), vm.filtres.value.notes)
    }

    // L'« Effacer » de la feuille Note décoche toutes les notes, et seulement elles. Mutation :
    // tout remettre à zéro casse cette assertion.
    @Test
    fun `effacerNotes decoche toutes les notes sans toucher au reste`() {
        vm.setTri(TriFilms.NOTE_DESC)
        vm.basculerNote(4)
        vm.basculerNote(7)
        vm.basculerReaction("adore")
        vm.effacerNotes()
        assertEquals(FiltresFilms(tri = TriFilms.NOTE_DESC, reactions = setOf("adore")), vm.filtres.value)
    }

    // Cocher, cocher une autre, décocher la première. Mutation : un `+` sans retrait casse cette
    // assertion.
    @Test
    fun `basculerReaction coche puis decoche`() {
        vm.basculerReaction("adore")
        vm.basculerReaction("nul")
        assertEquals(setOf("adore", "nul"), vm.filtres.value.reactions)
        vm.basculerReaction("adore")
        assertEquals(setOf("nul"), vm.filtres.value.reactions)
    }

    // L'« Effacer » de la feuille Réaction décoche toutes les réactions, et seulement elles.
    // Mutation : tout remettre à zéro casse cette assertion.
    @Test
    fun `effacerReactions decoche toutes les reactions sans toucher au reste`() {
        vm.basculerNote(8)
        vm.basculerReaction("adore")
        vm.basculerReaction("nul")
        vm.effacerReactions()
        assertEquals(FiltresFilms(notes = setOf(8)), vm.filtres.value)
    }

    // « Effacer » remet tout à zéro, les quatre champs à la fois. Mutation : oublier l'un d'eux
    // casse cette assertion.
    @Test
    fun `effacer remet tous les filtres a zero`() {
        vm.setTexte("leon")
        vm.setTri(TriFilms.NOTE_DESC)
        vm.basculerNote(6)
        vm.basculerReaction("adore")
        vm.effacer()
        assertEquals(FiltresFilms(), vm.filtres.value)
    }
}
