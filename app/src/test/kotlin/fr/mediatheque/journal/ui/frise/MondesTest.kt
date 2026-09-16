package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les mondes du Voyage (brief du 16 septembre 2026, phase 2) : quelle décennie porte quelle
 * année, et quel numéro de chapitre. Fonctions pures, sans réseau ni Compose.
 */
class MondesTest {

    // La bascule se fait au millésime rond. Mutation : `(annee - 1890 + 1) / 10`, ou un
    // `annee / 10 * 10 - 10`, décale la frontière d'un an et fait échouer 1899 ou 1900.
    @Test
    fun `mondeDe bascule entre 1899 et 1900, pas avant, pas apres`() {
        assertEquals(1890, mondeDe(1890).decennie)
        assertEquals(1890, mondeDe(1895).decennie)
        assertEquals(1890, mondeDe(1899).decennie)
        assertEquals(1900, mondeDe(1900).decennie)
        assertEquals(1900, mondeDe(1901).decennie)
    }

    // Une année antérieure au premier monde rejoint les origines plutôt que de sortir de la
    // liste. Mutation : retirer le `coerceIn` fait lever un `IndexOutOfBoundsException` ici —
    // `(1888 - 1890).floorDiv(10)` vaut -1, pas 0 (c'est bien `floorDiv`, pas `/`, qui tronque
    // vers zéro et masquerait le défaut jusqu'à 1880).
    @Test
    fun `mondeDe ramene une annee d'avant 1890 aux origines`() {
        assertEquals(1890, mondeDe(1888).decennie)
        assertEquals(1890, mondeDe(1875).decennie)
        assertEquals("Les origines", mondeDe(1875).nom)
    }

    // Et au-delà du dernier monde, la dernière décennie — un film de 2031 ne sort pas de la carte.
    @Test
    fun `mondeDe ramene une annee d'apres le dernier monde au dernier`() {
        assertEquals(2020, mondeDe(2026).decennie)
        assertEquals(2020, mondeDe(2031).decennie)
    }

    // Les quatorze mondes se suivent de dix en dix, sans trou ni doublon : c'est ce que
    // `mondeDe` suppose pour indexer par soustraction.
    @Test
    fun `les mondes couvrent 1890 a 2020, de dix en dix`() {
        assertEquals((1890..2020 step 10).toList(), MONDES.map { it.decennie })
        assertEquals(MONDES.size, MONDES.map { it.titreVoyageur }.distinct().size)
    }

    // Le chapitre commence à I, pas à zéro ni à II. Mutation : `index` au lieu de `index + 1`
    // rend "" pour le premier monde ; `index + 2` rend "II".
    @Test
    fun `chapitreRomain part de I et compte en romain`() {
        assertEquals("I", chapitreRomain(0))
        assertEquals("II", chapitreRomain(1))
        assertEquals("IV", chapitreRomain(3))
        assertEquals("V", chapitreRomain(4))
        assertEquals("IX", chapitreRomain(8))
        assertEquals("X", chapitreRomain(9))
        assertEquals("XIV", chapitreRomain(13))
        // Une quinzième décennie sortira « XV » sans qu'on retouche la fonction.
        assertEquals("XV", chapitreRomain(14))
    }

    @Test
    fun `chapitreDe nomme le chapitre et son monde`() {
        assertEquals("Chapitre I · Les origines", chapitreDe(1895))
        assertEquals("Chapitre II · La féerie", chapitreDe(1902))
    }
}
