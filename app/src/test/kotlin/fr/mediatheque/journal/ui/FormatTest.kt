package fr.mediatheque.journal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `une date en toutes lettres, en francais`() = assertEquals("3 septembre 2026", formatDate("2026-09-03"))
    @Test fun `le premier du mois est ordinalise, 1er pas 1`() = assertEquals("1er septembre 2026", formatDate("2026-09-01"))
    @Test fun `realisateur et annee, separes d une virgule`() = assertEquals("Hayao Miyazaki, 2001", subtitle("Hayao Miyazaki", 2001))
    @Test fun `l un sans l autre`() {
        assertEquals("2001", subtitle(null, 2001))
        assertEquals("Hayao Miyazaki", subtitle("Hayao Miyazaki", null))
        assertEquals("", subtitle(null, null))
    }

    // Décision 2 du brief du 21 septembre 2026, « les dépenses ».
    @Test fun `un mois en toutes lettres, capitalise`() = assertEquals("Août 2026", formatMoisAnnee("2026-08"))
    @Test fun `un mois a deux chiffres reste capitalise`() = assertEquals("Décembre 2025", formatMoisAnnee("2025-12"))

    @Test fun `une decimale, virgule a la francaise`() {
        assertEquals("12,7", formatCentimes(12.7))
        // Mutation : sans l'arrondi à une décimale, ce serait "101,16".
        assertEquals("101,2", formatCentimes(101.16))
    }

    // Revue du 24 septembre 2026, point 4, « titres sans doublon ». Mutation : retirer le
    // `takeIf` (toujours renvoyer `titreOriginal` tel quel) fait tomber la deuxième assertion ;
    // comparer sans tenir compte de la casse ferait tomber la troisième, qui doit rester distincte.
    @Test fun `le titre original ne s affiche que s il differe`() {
        assertEquals("Metropolis", titreOriginalAffiche("Le Voyage de Chihiro", "Metropolis"))
        assertEquals(null, titreOriginalAffiche("Metropolis", "Metropolis"))
        assertEquals(null, titreOriginalAffiche("Metropolis", null))
        assertEquals("METROPOLIS", titreOriginalAffiche("Metropolis", "METROPOLIS"))
    }
}
