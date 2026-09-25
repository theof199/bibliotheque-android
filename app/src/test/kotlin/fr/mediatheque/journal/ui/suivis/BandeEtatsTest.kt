package fr.mediatheque.journal.ui.suivis

import fr.mediatheque.journal.FakeJournalApi.Companion.filmDe
import org.junit.Assert.assertEquals
import org.junit.Test

/** La bande d'un cycle : ses rangées, l'état de chaque case, et le compte qui la suit. */
class BandeEtatsTest {
    private fun vu(id: Int) = filmDe(id, "Vu $id", 1980 + id, entryId = "e-$id")
    private fun aVoir(id: Int) = filmDe(id, "A voir $id", 1980 + id)
    private fun perdu(id: Int) = filmDe(id, "Perdu $id", 1980 + id, introuvable = true)

    // Huit films : une rangée de six, puis deux, dans l'ordre du back. Mutation : oublier
    // `chunked` rendrait une seule rangée de huit.
    @Test
    fun `disposerBande coupe en rangees de six, la derniere plus courte`() {
        val films = (1..8).map(::aVoir)
        val rangees = disposerBande(films, masquerIntrouvables = true)
        assertEquals(listOf(6, 2), rangees.map { it.size })
        assertEquals((1..8).toList(), rangees.flatten().map { it.film.tmdb_id })
    }

    @Test
    fun `disposerBande respecte parRangee et rend rien pour une liste vide`() {
        assertEquals(listOf(3, 3, 1), disposerBande((1..7).map(::aVoir), true, parRangee = 3).map { it.size })
        assertEquals(emptyList<List<CaseBande>>(), disposerBande(emptyList(), true))
    }

    // Un seul PROCHAIN, le premier non vu non introuvable ; les autres non vus PAS_ENCORE.
    // Mutation : marquer PROCHAIN tout non vu en donnerait deux.
    @Test
    fun `disposerBande marque vu, prochain et pas encore`() {
        val films = listOf(vu(1), vu(2), aVoir(3), aVoir(4))
        val etats = disposerBande(films, true).flatten().map { it.etat }
        assertEquals(listOf(EtatBande.VU, EtatBande.VU, EtatBande.PROCHAIN, EtatBande.PAS_ENCORE), etats)
    }

    // Masqués : l'introuvable quitte la bande, et le prochain saute par-dessus.
    @Test
    fun `disposerBande retire les introuvables quand on les masque`() {
        val films = listOf(vu(1), perdu(2), aVoir(3))
        val cases = disposerBande(films, masquerIntrouvables = true).flatten()
        assertEquals(listOf(1, 3), cases.map { it.film.tmdb_id })
        assertEquals(listOf(EtatBande.VU, EtatBande.PROCHAIN), cases.map { it.etat })
    }

    // Montrés : l'introuvable garde sa place, INTROUVABLE, jamais PROCHAIN. Mutation : tester
    // `vu` avant `introuvable` rendrait VU au film vu puis marqué perdu.
    @Test
    fun `disposerBande garde les introuvables a leur place sinon`() {
        val vuPuisPerdu = filmDe(4, "Vu puis perdu", 1984, entryId = "e-4", introuvable = true)
        val films = listOf(perdu(1), aVoir(2), vu(3), vuPuisPerdu)
        val etats = disposerBande(films, masquerIntrouvables = false).flatten().map { it.etat }
        assertEquals(listOf(EtatBande.INTROUVABLE, EtatBande.PROCHAIN, EtatBande.VU, EtatBande.INTROUVABLE), etats)
    }

    @Test
    fun `disposerBande sans prochain quand tout est vu`() {
        val etats = disposerBande(listOf(vu(1), vu(2)), true).flatten().map { it.etat }
        assertEquals(listOf(EtatBande.VU, EtatBande.VU), etats)
    }

    // Le compte suit la bande : « 1/2 » masqués, « 1/3 » sinon — jamais l'introuvable compté vu.
    @Test
    fun `compteBande suit ce que la bande dessine`() {
        val vuPuisPerdu = filmDe(4, "Vu puis perdu", 1984, entryId = "e-4", introuvable = true)
        val films = listOf(vu(1), perdu(2), aVoir(3), vuPuisPerdu)
        assertEquals(1 to 2, compteBande(films, masquerIntrouvables = true))
        assertEquals(1 to 4, compteBande(films, masquerIntrouvables = false))
        assertEquals(0 to 0, compteBande(emptyList(), true))
    }
}
