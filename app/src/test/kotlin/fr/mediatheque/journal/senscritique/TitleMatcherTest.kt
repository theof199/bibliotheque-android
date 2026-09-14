package fr.mediatheque.journal.senscritique

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mêmes cas que `importer-senscritique.test.ts` (biblio-back) : accents, ponctuation,
 * articles, ±1 an, année nulle, `originalTitle`, ambigu, aucun, titre égal mais année hors
 * tolérance — brief du 14 septembre 2026.
 */
class TitleMatcherTest {

    private fun candidat(id: Long, title: String, originalTitle: String? = null, year: Int? = null) =
        ExternalCandidate(id, title, originalTitle, year)

    // --- normaliserTitre ---

    @Test
    fun `met en minuscules, retire les accents et la ponctuation`() {
        assertEquals("emilie paris", normaliserTitre("Émilie, Paris !"))
    }

    @Test
    fun `retire les articles francais mais pas les prepositions`() {
        assertEquals("choses de vie", normaliserTitre("Les Choses de la vie"))
    }

    @Test
    fun `retire l article elide une fois la ponctuation otee`() {
        assertEquals("invitation", normaliserTitre("L'Invitation"))
    }

    @Test
    fun `rend identiques une apostrophe typographique et une apostrophe simple`() {
        assertEquals(normaliserTitre("On l’appelait Robin des Bois"), normaliserTitre("On l'appelait Robin des Bois"))
    }

    // --- anneesCompatibles ---

    @Test
    fun `accepte la meme annee`() {
        assertTrue(anneesCompatibles(2010, 2010))
    }

    @Test
    fun `tolere un ecart d un an dans les deux sens`() {
        assertTrue(anneesCompatibles(2011, 2010))
        assertTrue(anneesCompatibles(2009, 2010))
    }

    @Test
    fun `refuse un ecart de deux ans`() {
        assertFalse(anneesCompatibles(2012, 2010))
    }

    @Test
    fun `accepte n importe quelle annee candidate quand notre cote n en connait pas`() {
        assertTrue(anneesCompatibles(null, 1902))
        assertTrue(anneesCompatibles(null, null))
    }

    @Test
    fun `refuse quand le candidat n a pas d annee mais nous si`() {
        assertFalse(anneesCompatibles(2010, null))
    }

    // --- apparierCandidat ---

    @Test
    fun `retient un candidat unique de meme annee et meme titre`() {
        val film = MatchableFilm("Inception", null, 2010)
        val resultat = apparierCandidat(film, listOf(candidat(1, "Inception", year = 2010)))
        assertTrue(resultat is Appariement.Apparie)
        assertEquals(1L, (resultat as Appariement.Apparie).candidat.productId)
    }

    @Test
    fun `correspond par l originalTitle quand notre titre differe de celui du candidat`() {
        val film = MatchableFilm("Le Voyage de Chihiro", "Sen to Chihiro no kamikakushi", 2001)
        val resultat = apparierCandidat(film, listOf(candidat(1, "Spirited Away", "Sen to Chihiro no kamikakushi", 2001)))
        assertTrue(resultat is Appariement.Apparie)
    }

    @Test
    fun `refuse ambigu si deux candidats correspondent`() {
        val film = MatchableFilm("Inception", null, 2010)
        val resultat = apparierCandidat(
            film,
            listOf(candidat(1, "Inception", year = 2010), candidat(2, "Inception", year = 2011)),
        )
        assertTrue(resultat is Appariement.Ambigu)
        assertEquals(2, (resultat as Appariement.Ambigu).candidats.size)
    }

    @Test
    fun `refuse aucun si aucun candidat ne correspond`() {
        val film = MatchableFilm("Inception", null, 2010)
        val resultat = apparierCandidat(film, listOf(candidat(1, "Batman", year = 2025)))
        assertEquals(Appariement.Aucun, resultat)
    }

    // Le filtre d'annee doit s'appliquer avant celui du titre : un titre identique mais une annee
    // hors tolerance ne doit pas passer.
    @Test
    fun `refuse aucun un titre identique quand l annee est hors tolerance`() {
        val film = MatchableFilm("Inception", null, 2010)
        val resultat = apparierCandidat(film, listOf(candidat(1, "Inception", year = 2015)))
        assertEquals(Appariement.Aucun, resultat)
    }

    // --- fautRepliOriginalTitle ---

    @Test
    fun `faut repli quand aucun candidat et l originalTitle differe du titre`() {
        val film = MatchableFilm("Le Voyage de Chihiro", "Sen to Chihiro no kamikakushi", 2001)
        assertTrue(fautRepliOriginalTitle(film, Appariement.Aucun))
    }

    @Test
    fun `pas de repli quand un candidat unique est deja trouve`() {
        val film = MatchableFilm("Le Voyage de Chihiro", "Sen to Chihiro no kamikakushi", 2001)
        val trouve = Appariement.Apparie(candidat(1, "Le Voyage de Chihiro", year = 2001))
        assertFalse(fautRepliOriginalTitle(film, trouve))
    }

    @Test
    fun `pas de repli sur un ambigu — reprendre le titre ne desambiguiserait rien`() {
        val film = MatchableFilm("Le Voyage de Chihiro", "Sen to Chihiro no kamikakushi", 2001)
        val ambigu = Appariement.Ambigu(listOf(candidat(1, "Le Voyage de Chihiro", year = 2001), candidat(2, "Le Voyage de Chihiro", year = 2001)))
        assertFalse(fautRepliOriginalTitle(film, ambigu))
    }

    @Test
    fun `pas de repli sans originalTitle`() {
        val film = MatchableFilm("Inception", null, 2010)
        assertFalse(fautRepliOriginalTitle(film, Appariement.Aucun))
    }

    @Test
    fun `pas de repli quand l originalTitle normalise au meme titre`() {
        val film = MatchableFilm("Inception", "INCEPTION", 2010)
        assertFalse(fautRepliOriginalTitle(film, Appariement.Aucun))
    }
}
