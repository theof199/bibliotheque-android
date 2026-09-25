package fr.mediatheque.journal.ui.fiche

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Les règles pures des trois fiches (« la fiche · trois visages », 25 septembre 2026) : durée,
 * année · durée, étiquette de décennie, désordre des puces. Sans Compose, sans réseau.
 */
class FicheEtatsTest {

    // Sous l'heure, les minutes seules. Mutation : écrire « 0 h 47 » casse cette assertion.
    @Test
    fun `formatDuree ecrit les minutes seules sous l heure`() {
        assertEquals("47 min", formatDuree(47))
    }

    @Test
    fun `formatDuree ecrit les heures puis les minutes`() {
        assertEquals("1 h 47", formatDuree(107))
    }

    // Mutation : oublier le `padStart` rend « 2 h 3 ».
    @Test
    fun `formatDuree garde deux chiffres aux minutes passe l heure`() {
        assertEquals("2 h 03", formatDuree(123))
    }

    @Test
    fun `anneeEtDuree rend l annee seule sans duree`() {
        assertEquals("2019", anneeEtDuree(2019, null))
    }

    @Test
    fun `anneeEtDuree joint l annee et la duree par un point median`() {
        assertEquals("1912 · 2 h 03", anneeEtDuree(1912, 123))
    }

    @Test
    fun `anneeEtDuree rend la duree seule sans annee`() {
        assertEquals("1 h 47", anneeEtDuree(null, 107))
    }

    // Mutation : rendre une chaîne vide au lieu de `null` ferait écrire un « · » orphelin.
    @Test
    fun `anneeEtDuree est nulle sans annee ni duree`() {
        assertNull(anneeEtDuree(null, null))
    }

    @Test
    fun `etiquetteDecennie nomme la decennie et son monde`() {
        assertEquals("Années 1990 · Le blockbuster", etiquetteDecennie(1994))
    }

    // La bascule se fait au millésime rond, comme `mondeDe`.
    @Test
    fun `etiquetteDecennie range 1899 dans les annees 1890`() {
        assertEquals("Années 1890 · Les origines", etiquetteDecennie(1899))
    }

    // Au-delà du dernier monde, le nom reste le sien mais la décennie est celle de l'année.
    // Mutation : lire `Monde.decennie` rendrait « Années 2020 ».
    @Test
    fun `etiquetteDecennie garde la decennie de l annee au dela du dernier monde`() {
        assertEquals("Années 2030 · Aujourd’hui", etiquetteDecennie(2031))
    }

    @Test
    fun `etiquetteDecennie est nulle sans annee`() {
        assertNull(etiquetteDecennie(null))
    }

    @Test
    fun `inclinaisonPuce suit le cycle moins trois, deux, moins un et demi`() {
        assertEquals(listOf(-3f, 2f, -1.5f), (0..2).map(::inclinaisonPuce))
    }

    @Test
    fun `inclinaisonPuce reprend le cycle a la quatrieme puce`() {
        assertEquals(inclinaisonPuce(0), inclinaisonPuce(3))
    }

    @Test
    fun `decalagePuce suit le cycle zero, deux, moins un`() {
        assertEquals(listOf(0, 2, -1, 0), (0..3).map(::decalagePuce))
    }
}
