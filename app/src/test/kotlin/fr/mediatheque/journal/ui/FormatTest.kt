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
}
